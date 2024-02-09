/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.devicelockcontroller.policy;

import static androidx.work.WorkInfo.State.SUCCEEDED;

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_KIOSK;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_PAUSE;
import static com.android.devicelockcontroller.provision.worker.IsDeviceInApprovedCountryWorker.BACKOFF_DELAY;
import static com.android.devicelockcontroller.provision.worker.IsDeviceInApprovedCountryWorker.KEY_IS_IN_APPROVED_COUNTRY;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.sqlite.SQLiteException;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LifecycleOwner;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Operation;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.PlayInstallPackageTaskClassProvider;
import com.android.devicelockcontroller.activities.DeviceLockNotificationManager;
import com.android.devicelockcontroller.activities.ProvisioningProgress;
import com.android.devicelockcontroller.activities.ProvisioningProgressController;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason;
import com.android.devicelockcontroller.provision.worker.IsDeviceInApprovedCountryWorker;
import com.android.devicelockcontroller.provision.worker.PauseProvisioningWorker;
import com.android.devicelockcontroller.provision.worker.ReportDeviceProvisionStateWorker;
import com.android.devicelockcontroller.receivers.ResumeProvisionReceiver;
import com.android.devicelockcontroller.schedule.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.schedule.DeviceLockControllerSchedulerProvider;
import com.android.devicelockcontroller.stats.StatsLogger;
import com.android.devicelockcontroller.stats.StatsLoggerProvider;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of {@link ProvisionHelper}.
 */
public final class ProvisionHelperImpl implements ProvisionHelper {
    private static final String TAG = "ProvisionHelperImpl";
    private static final String FILENAME = "device-lock-controller-provisioning-preferences";
    private static final String USE_PREINSTALLED_KIOSK_PREF =
            "debug.devicelock.usepreinstalledkiosk";
    private static final String WORK_TAG_APPROVED_COUNTRY = "TAG_APPROVED_COUNTRY";
    private static final String WORK_TAG_PLAY_INSTALL = "TAG_PLAY_INSTALL";
    @VisibleForTesting
    static final long POLL_DELAY_MS = 1000;
    private static final int POLL_RETRIES = 300;
    private static volatile SharedPreferences sSharedPreferences;

    @VisibleForTesting
    static synchronized SharedPreferences getSharedPreferences(Context context) {
        if (sSharedPreferences == null) {
            sSharedPreferences = context.createDeviceProtectedStorageContext().getSharedPreferences(
                    FILENAME, Context.MODE_PRIVATE);
        }
        return sSharedPreferences;
    }

    private final Context mContext;
    private final ProvisionStateController mStateController;
    private final Executor mExecutor;
    private final ScheduledExecutorService mScheduledExecutorService;
    private final DeviceLockControllerScheduler mScheduler;

    public ProvisionHelperImpl(Context context, ProvisionStateController stateController) {
        this(context, stateController, Executors.newCachedThreadPool(),
                Executors.newSingleThreadScheduledExecutor());
    }

    @VisibleForTesting
    ProvisionHelperImpl(Context context, ProvisionStateController stateController,
            Executor executor, ScheduledExecutorService executorService) {
        mContext = context;
        mStateController = stateController;
        DeviceLockControllerSchedulerProvider schedulerProvider =
                (DeviceLockControllerSchedulerProvider) mContext.getApplicationContext();
        mScheduler = schedulerProvider.getDeviceLockControllerScheduler();
        mExecutor = executor;
        mScheduledExecutorService = executorService;
    }

    @Override
    public void pauseProvision() {
        Futures.addCallback(Futures.transformAsync(
                        GlobalParametersClient.getInstance().setProvisionForced(true),
                        unused -> mStateController.setNextStateForEvent(PROVISION_PAUSE),
                        mExecutor),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void unused) {
                        createNotification();
                        WorkManager workManager = WorkManager.getInstance(mContext);
                        PauseProvisioningWorker.reportProvisionPausedByUser(workManager);
                        mScheduler.scheduleResumeProvisionAlarm();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException("Failed to delay setup", t);
                    }
                }, mExecutor);
    }

    @Override
    public void scheduleKioskAppInstallation(LifecycleOwner owner,
            ProvisioningProgressController progressController, boolean isMandatory) {
        LogUtil.v(TAG, "Schedule installation work");
        progressController.setProvisioningProgress(ProvisioningProgress.GETTING_DEVICE_READY);
        WorkManager workManager = WorkManager.getInstance(mContext);
        OneTimeWorkRequest isDeviceInApprovedCountryWork = getIsDeviceInApprovedCountryWork();

        final ListenableFuture<Operation.State.SUCCESS> enqueueResult =
                workManager.enqueueUniqueWork(IsDeviceInApprovedCountryWorker.class.getSimpleName(),
                ExistingWorkPolicy.REPLACE, isDeviceInApprovedCountryWork).getResult();
        FutureCallback<String> isInApprovedCountryCallback = new FutureCallback<>() {
            @Override
            public void onSuccess(String kioskPackage) {
                progressController.setProvisioningProgress(
                        ProvisioningProgress.INSTALLING_KIOSK_APP);
                if (getPreinstalledKioskAllowed(mContext)) {
                    try {
                        mContext.getPackageManager().getPackageInfo(kioskPackage,
                                ApplicationInfo.FLAG_INSTALLED);
                        LogUtil.i(TAG, "Kiosk app is pre-installed");
                        progressController.setProvisioningProgress(
                                ProvisioningProgress.OPENING_KIOSK_APP);
                        ReportDeviceProvisionStateWorker.reportSetupCompleted(workManager);
                        mStateController.postSetNextStateForEventRequest(PROVISION_KIOSK);
                    } catch (NameNotFoundException e) {
                        LogUtil.i(TAG, "Kiosk app is not pre-installed");
                        installFromPlay(owner, kioskPackage, isMandatory, progressController);
                    }
                } else {
                    installFromPlay(owner, kioskPackage, isMandatory, progressController);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.w(TAG, "Failed to install kiosk app!", t);
                handleFailure(ProvisionFailureReason.PLAY_INSTALLATION_FAILED, isMandatory,
                        progressController);
            }
        };
        Futures.addCallback(enqueueResult, new FutureCallback<Operation.State.SUCCESS>() {
            @Override
            public void onSuccess(Operation.State.SUCCESS result) {
                pollWorkResult(WORK_TAG_APPROVED_COUNTRY, new WorkFinishedCallback() {
                    @Override
                    public void onSuccess(WorkInfo workInfo) {
                        if (workInfo.getOutputData().getBoolean(KEY_IS_IN_APPROVED_COUNTRY,
                                false)) {
                            Futures.addCallback(
                                    SetupParametersClient.getInstance().getKioskPackage(),
                                    isInApprovedCountryCallback, mExecutor);
                        } else {
                            LogUtil.i(TAG, "Not in eligible country");
                            handleFailure(ProvisionFailureReason.NOT_IN_ELIGIBLE_COUNTRY,
                                    isMandatory, progressController);
                        }
                    }

                    @Override
                    public void onFailure(WorkInfo workInfo) {
                        LogUtil.w(TAG, "Failed to get country eligibility!");
                        handleFailure(ProvisionFailureReason.COUNTRY_INFO_UNAVAILABLE,
                                isMandatory,
                                progressController);
                    }
                }, POLL_RETRIES);
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Failed to enqueue 'device in approved country' work", t);
                if (t instanceof SQLiteException) {
                    mStateController.getDevicePolicyController().wipeDevice();
                } else {
                    LogUtil.e(TAG, "Not wiping device (non SQL exception)");
                }
            }
        }, mExecutor);
    }

    private void installFromPlay(LifecycleOwner owner, String kioskPackage, boolean isMandatory,
            ProvisioningProgressController progressController) {
        Context applicationContext = mContext.getApplicationContext();
        final Class<? extends ListenableWorker> playInstallTaskClass =
                ((PlayInstallPackageTaskClassProvider) applicationContext)
                        .getPlayInstallPackageTaskClass();
        if (playInstallTaskClass == null) {
            LogUtil.w(TAG, "Play installation not supported!");
            handleFailure(
                    ProvisionFailureReason.PLAY_TASK_UNAVAILABLE, isMandatory, progressController);
            return;
        }
        OneTimeWorkRequest playInstallPackageTask =
                getPlayInstallPackageTask(playInstallTaskClass, kioskPackage);
        WorkManager workManager = WorkManager.getInstance(mContext);
        final ListenableFuture<Operation.State.SUCCESS> enqueueResult =
                workManager.enqueueUniqueWork(playInstallTaskClass.getSimpleName(),
                        ExistingWorkPolicy.REPLACE, playInstallPackageTask).getResult();
        Futures.addCallback(enqueueResult, new FutureCallback<Operation.State.SUCCESS>() {
            @Override
            public void onSuccess(Operation.State.SUCCESS result) {
                pollWorkResult(WORK_TAG_PLAY_INSTALL, new WorkFinishedCallback() {
                    @Override
                    public void onSuccess(WorkInfo workInfo) {
                        progressController.setProvisioningProgress(
                                ProvisioningProgress.OPENING_KIOSK_APP);
                        ReportDeviceProvisionStateWorker.reportSetupCompleted(workManager);
                        mStateController.postSetNextStateForEventRequest(PROVISION_KIOSK);
                    }

                    @Override
                    public void onFailure(WorkInfo workInfo) {
                        LogUtil.w(TAG, "Play installation failed!");
                        handleFailure(ProvisionFailureReason.PLAY_INSTALLATION_FAILED,
                                isMandatory, progressController);
                    }
                }, POLL_RETRIES);
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Failed to enqueue 'play install' work", t);
                if (t instanceof SQLiteException) {
                    mStateController.getDevicePolicyController().wipeDevice();
                } else {
                    LogUtil.e(TAG, "Not wiping device (non SQL exception)");
                }
            }
        }, mExecutor);
    }

    private void handleFailure(@ProvisionFailureReason int reason, boolean isMandatory,
            ProvisioningProgressController progressController) {
        StatsLogger logger =
                ((StatsLoggerProvider) mContext.getApplicationContext()).getStatsLogger();
        switch (reason) {
            case ProvisionFailureReason.PLAY_TASK_UNAVAILABLE -> {
                logger.logProvisionFailure(
                        StatsLogger.ProvisionFailureReasonStats.PLAY_TASK_UNAVAILABLE);
            }
            case ProvisionFailureReason.PLAY_INSTALLATION_FAILED -> {
                logger.logProvisionFailure(
                        StatsLogger.ProvisionFailureReasonStats.PLAY_INSTALLATION_FAILED);
            }
            case ProvisionFailureReason.COUNTRY_INFO_UNAVAILABLE -> {
                logger.logProvisionFailure(
                        StatsLogger.ProvisionFailureReasonStats.COUNTRY_INFO_UNAVAILABLE);
            }
            case ProvisionFailureReason.NOT_IN_ELIGIBLE_COUNTRY -> {
                logger.logProvisionFailure(
                        StatsLogger.ProvisionFailureReasonStats.NOT_IN_ELIGIBLE_COUNTRY);
            }
            case ProvisionFailureReason.POLICY_ENFORCEMENT_FAILED -> {
                logger.logProvisionFailure(
                        StatsLogger.ProvisionFailureReasonStats.POLICY_ENFORCEMENT_FAILED);
            }
            default -> {
                logger.logProvisionFailure(StatsLogger.ProvisionFailureReasonStats.UNKNOWN);
            }
        }
        if (isMandatory) {
            ReportDeviceProvisionStateWorker.reportSetupFailed(
                    WorkManager.getInstance(mContext), reason);
            progressController.setProvisioningProgress(
                    ProvisioningProgress.getMandatoryProvisioningFailedProgress(reason));
            mScheduler.scheduleMandatoryResetDeviceAlarm();
        } else {
            // For non-mandatory provisioning, failure should only be reported after
            // user exits the provisioning UI; otherwise, it could be reported
            // multiple times if user choose to retry, which can break the
            // 7-days failure flow.
            progressController.setProvisioningProgress(
                    ProvisioningProgress.getNonMandatoryProvisioningFailedProgress(reason));
        }
    }

    @NonNull
    private static OneTimeWorkRequest getIsDeviceInApprovedCountryWork() {
        return new OneTimeWorkRequest.Builder(IsDeviceInApprovedCountryWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(
                        NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY)
                .addTag(WORK_TAG_APPROVED_COUNTRY)
                .build();
    }

    @NonNull
    private static OneTimeWorkRequest getPlayInstallPackageTask(
            Class<? extends ListenableWorker> playInstallTaskClass, String kioskPackageName) {
        return new OneTimeWorkRequest.Builder(playInstallTaskClass)
                .setInputData(new Data.Builder().putString(
                        EXTRA_KIOSK_PACKAGE, kioskPackageName).build())
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(
                        NetworkType.CONNECTED).build())
                .addTag(WORK_TAG_PLAY_INSTALL)
                .build();
    }

    private void createNotification() {
        LogUtil.d(TAG, "createNotification");
        Context context = mContext;

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                /* requestCode= */ 0, new Intent(context, ResumeProvisionReceiver.class),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        LocalDateTime resumeDateTime = LocalDateTime.now().plusHours(1);
        DeviceLockNotificationManager.sendDeferredProvisioningNotification(context, resumeDateTime,
                pendingIntent);
    }

    /**
     * Poll the work result to see when it's done.
     *
     * TODO(324318445): Do not use this workaround and instead observe the work result
     * @param workTag tag of the work
     * @param workFinishedCallback callback when work is complete
     * @param triesLeft number of tries to poll the work
     */
    private void pollWorkResult(String workTag, WorkFinishedCallback workFinishedCallback,
            int triesLeft) {
        WorkManager workManager = WorkManager.getInstance(mContext);
        Futures.addCallback(workManager.getWorkInfosByTag(workTag),
                new FutureCallback<List<WorkInfo>>() {
                    @Override
                    public void onSuccess(List<WorkInfo> workInfos) {
                        if (workInfos == null || workInfos.isEmpty()) {
                            LogUtil.w(TAG, "No work info associated with the tag: " + workTag);
                            return;
                        }
                        WorkInfo workInfo = workInfos.get(0);
                        WorkInfo.State state = workInfo.getState();
                        if (!state.isFinished()) {
                            if (triesLeft == 0) {
                                LogUtil.e(TAG, "Ran out of poll retries: " + workInfo);
                                workFinishedCallback.onFailure(workInfo);
                                return;
                            }
                            mScheduledExecutorService.schedule(() ->
                                    pollWorkResult(workTag, workFinishedCallback, triesLeft - 1),
                                    POLL_DELAY_MS,
                                    TimeUnit.MILLISECONDS);
                        } else {
                            LogUtil.d(TAG, "WorkInfo finished: " + workInfo);
                            if (state == SUCCEEDED) {
                                workFinishedCallback.onSuccess(workInfo);
                            } else {
                                workFinishedCallback.onFailure(workInfo);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);
                    }
                },
                mContext.getMainExecutor());
    }

    /**
     * Sets whether provisioning should skip play install if there is already a preinstalled kiosk
     * app.
     */
    public static void setPreinstalledKioskAllowed(Context context, boolean enabled) {
        getSharedPreferences(context).edit().putBoolean(USE_PREINSTALLED_KIOSK_PREF, enabled)
                .apply();
    }

    /**
     * Returns true if provisioning should skip play install if there is already a preinstalled
     * kiosk app. By default, this returns true for debuggable build.
     */
    private static boolean getPreinstalledKioskAllowed(Context context) {
        return Build.isDebuggable() && getSharedPreferences(context).getBoolean(
                USE_PREINSTALLED_KIOSK_PREF, Build.isDebuggable());
    }

    /**
     * Callback for when work is finished or fails.
     */
    private interface WorkFinishedCallback {
        /**
         * Called when work finishes in succeeded state
         */
        void onSuccess(WorkInfo workInfo);

        /**
         * Called when work finishes in failed state or work never runs
         */
        void onFailure(WorkInfo workInfo);
    }
}
