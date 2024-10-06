/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.devicelockcontroller.services;

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED;

import static com.android.devicelockcontroller.common.DeviceLockConstants.SETUP_WIZARD_TIMEOUT_JOB_ID;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_READY;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.net.NetworkRequest;
import android.provider.Settings;

import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class SetupWizardCompletionTimeoutJobService extends JobService {
    private static final String TAG = "SetupWizardCompletionTimeoutJobService";
    private static final long TIMEOUT_MINUTES = 60;

    private final ListeningExecutorService mListeningExecutorService =
            MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    private final Context mContext;

    @VisibleForTesting
    ListenableFuture<Void> mFuture;

    /**
     * Create an instance of the job.
     */
    public SetupWizardCompletionTimeoutJobService() {
        super();
        mContext = this;
    }

    @VisibleForTesting
    SetupWizardCompletionTimeoutJobService(Context context) {
        super();
        mContext = context;
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        LogUtil.i(TAG, "Starting job");

        // If SUW is already finished, there's nothing left to do.
        if (isUserSetupComplete()) {
            return false;
        }

        // SUW did not finished in the allotted time. If the device is still unprovisioned
        // and provisioning information is ready, start the provisioning flow.
        Context appContext = mContext.getApplicationContext();
        PolicyObjectsProvider policyObjects =
                (PolicyObjectsProvider) appContext;
        ProvisionStateController provisionStateController =
                policyObjects.getProvisionStateController();

        mFuture = Futures.transformAsync(provisionStateController.getState(),
                state -> {
                    UserParameters.setSetupWizardTimedOut(appContext);

                    if (state != UNPROVISIONED) {
                        return Futures.immediateVoidFuture();
                    }

                    GlobalParametersClient globalParametersClient =
                            GlobalParametersClient.getInstance();
                    return Futures.transformAsync(globalParametersClient.isProvisionReady(),
                        isReady -> {
                            if (isReady) {
                                LogUtil.i(TAG, "Starting provisioning flow since "
                                        + "SUW did not complete in " + TIMEOUT_MINUTES
                                        + " minutes");
                                return Futures.transform(provisionStateController
                                        .setNextStateForEvent(PROVISION_READY),
                                        unused -> null,
                                        mListeningExecutorService);
                            }
                            return Futures.immediateVoidFuture();
                        }, mListeningExecutorService);
                }, mListeningExecutorService);

        Futures.addCallback(mFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(Void result) {
                LogUtil.i(TAG, "Job completed");

                jobFinished(params, /* wantsReschedule= */ false);
            }

            @Override
            public void onFailure(Throwable t) {
                LogUtil.e(TAG, "Job failed", t);

                jobFinished(params, /* wantsReschedule= */ true);
            }
        }, mListeningExecutorService);

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.i(TAG, "Stopping job");

        if (mFuture != null) {
            mFuture.cancel(true);
        }

        return true;
    }

    /**
     * Schedule a job that starts the provisioning flow in case SetupWizard does not complete
     * in the allotted time.
     */
    public static void scheduleSetupWizardCompletionTimeoutJob(Context context) {
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);

        if (jobScheduler.getPendingJob(SETUP_WIZARD_TIMEOUT_JOB_ID) != null) {
            LogUtil.w(TAG, "Job already scheduled");

            return;
        }

        ComponentName componentName =
                new ComponentName(context, SetupWizardCompletionTimeoutJobService.class);
        long delayMillis = TimeUnit.MINUTES.toMillis(TIMEOUT_MINUTES);

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NET_CAPABILITY_TRUSTED)
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_VPN)
                .build();

        JobInfo jobInfo = new JobInfo.Builder(SETUP_WIZARD_TIMEOUT_JOB_ID, componentName)
                .setMinimumLatency(delayMillis)
                .setRequiredNetwork(request)
                .build();

        int schedulingResult = jobScheduler.schedule(jobInfo);

        if (schedulingResult == JobScheduler.RESULT_SUCCESS) {
            LogUtil.i(TAG, "Job scheduled");
        } else {
            LogUtil.e(TAG, "Failed to schedule job");
        }
    }

    /**
     * Cancel the job that starts the provisioning flow if SetupWizard does not complete in
     * the allotted time.
     */
    public static void cancelSetupWizardCompletionTimeoutJob(Context context) {
        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);

        jobScheduler.cancel(SETUP_WIZARD_TIMEOUT_JOB_ID);

        LogUtil.i(TAG, "Job cancelled");
    }

    private boolean isUserSetupComplete() {
        return Settings.Secure.getInt(
                mContext.getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
    }
}
