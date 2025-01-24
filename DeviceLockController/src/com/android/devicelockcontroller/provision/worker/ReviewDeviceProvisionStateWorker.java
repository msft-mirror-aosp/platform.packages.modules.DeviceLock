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

package com.android.devicelockcontroller.provision.worker;

import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_FAILURE;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_FAILED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_SUCCEEDED;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.Operation;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.WorkManagerExceptionHandler.WorkFailureAlarmReceiver;
import com.android.devicelockcontroller.common.DeviceLockConstants;
import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.receivers.NextProvisionFailedStepReceiver;
import com.android.devicelockcontroller.receivers.ResetDeviceReceiver;
import com.android.devicelockcontroller.receivers.ResumeProvisionReceiver;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.TimeUnit;

/**
 * A worker class to ensure provision failure is detected and reported. Provision can fail
 * undetected for a number of reasons. If provision does not complete successfully after 1 day, and
 * no alarms to resume it have been set, it should be assumed that it failed and reported.
 */
public final class ReviewDeviceProvisionStateWorker extends AbstractCheckInWorker {

    public static final String REVIEW_DEVICE_PROVISION_STATE_WORK_NAME =
            "review-device-provision-state";
    private static final float MILLISECONDS_IN_A_DAY = 86400000F;

    /**
     * Schedules this job daily with an initial delay of 26 hours.
     *
     * <p>The 26 hour initial delay provides a 2 hour buffer so that the 1 day condition evaluated
     * in {@code #hasProvisionFailedOr1DayLapsedSinceProvisioning} is met during the first run.
     *
     */
    public static void scheduleDailyReview(WorkManager workManager) {
        PeriodicWorkRequest work =
                new PeriodicWorkRequest.Builder(ReviewDeviceProvisionStateWorker.class, 1,
                        TimeUnit.DAYS)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY)
                        .setInitialDelay(26, TimeUnit.HOURS)
                        .build();
        ListenableFuture<Operation.State.SUCCESS> result =
                workManager
                        .enqueueUniquePeriodicWork(
                                REVIEW_DEVICE_PROVISION_STATE_WORK_NAME,
                                ExistingPeriodicWorkPolicy.UPDATE, work)
                        .getResult();
        Futures.addCallback(
                result,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Operation.State.SUCCESS result) {
                        // no-op
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // Log an error but don't reset the device (non critical failure).
                        LogUtil.e(TAG, "Failed to enqueue 'review provision state' work");
                    }
                },
                MoreExecutors.directExecutor());
    }

    public static void cancelJobs(WorkManager workManager) {
        // Executing jobs will still run but it will certainly cancel all jobs
        workManager.cancelUniqueWork(REVIEW_DEVICE_PROVISION_STATE_WORK_NAME);
    }

    public ReviewDeviceProvisionStateWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams,
            ListeningExecutorService executorService) {
        this(context, workerParams, /* client= */ null, executorService);
    }

    @VisibleForTesting
    ReviewDeviceProvisionStateWorker(
            @NonNull Context context,
            @NonNull WorkerParameters workerParams,
            DeviceCheckInClient client,
            ListeningExecutorService executorService) {
        super(context, workerParams, client, executorService);
    }

    private boolean anyAlarmsScheduled() {
        return isAlarmSet(ResetDeviceReceiver.class)
                || isAlarmSet(NextProvisionFailedStepReceiver.class)
                || isAlarmSet(ResumeProvisionReceiver.class)
                || isAlarmSet(WorkFailureAlarmReceiver.class);
    }

    private boolean isAlarmSet(Class<? extends BroadcastReceiver> receiverClass) {
        return PendingIntent.getBroadcast(
                mContext, /* ignored */
                0,
                new Intent(mContext, receiverClass),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_NO_CREATE
                        | PendingIntent.FLAG_IMMUTABLE)
                != null;
    }

    private boolean hasProvisionFailedOr1DayLapsedSinceProvisioning(int provisionState) {
        float daysLapsed =
                (SystemClock.elapsedRealtime() - UserParameters.getProvisioningStartTimeMillis(
                        mContext))
                        / MILLISECONDS_IN_A_DAY;

        return provisionState == PROVISION_FAILED || daysLapsed >= 1.0F;
    }

    private boolean hasProvisionSucceeded(int provisionState) {
        return provisionState == PROVISION_SUCCEEDED;
    }

    private ListenableFuture<Integer> getProvisionState() {
        return mExecutorService.submit(() -> UserParameters.getProvisionState(mContext));
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return Futures.transform(
                getProvisionState(),
                provisionState -> {
                    if (hasProvisionSucceeded(provisionState)) {
                        ReviewDeviceProvisionStateWorker.cancelJobs(
                                WorkManager.getInstance(mContext));
                        return Result.success();
                    }

                    if (!hasProvisionFailedOr1DayLapsedSinceProvisioning(provisionState)) {
                        return Result.success();
                    }

                    if (anyAlarmsScheduled()) {
                        // Do nothing, check back tomorrow and the other day until the alarms are
                        // cleared
                        return Result.success();
                    }

                    ReportDeviceProvisionStateWorker.reportSetupFailed(
                            WorkManager.getInstance(mContext),
                            DeviceLockConstants.ProvisionFailureReason.DEADLINE_PASSED);
                    ((PolicyObjectsProvider) mContext).getProvisionStateController()
                            .postSetNextStateForEventRequest(PROVISION_FAILURE);
                    ReviewDeviceProvisionStateWorker.cancelJobs(WorkManager.getInstance(mContext));

                    return Result.success();
                },
                mExecutorService);
    }
}
