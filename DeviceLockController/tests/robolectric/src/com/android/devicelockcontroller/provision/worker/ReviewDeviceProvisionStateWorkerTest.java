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

import static com.google.common.truth.Truth.assertThat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.ListenableWorker;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestListenableWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.receivers.ResumeProvisionReceiver;
import com.android.devicelockcontroller.storage.UserParameters;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public final class ReviewDeviceProvisionStateWorkerTest {

    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();
    @Mock
    private DeviceCheckInClient mClient;
    private ReviewDeviceProvisionStateWorker mWorker;
    private TestDeviceLockControllerApplication mTestApp;

    private WorkManager mWorkManager;

    @Before
    public void setUp() throws Exception {
        mTestApp = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(
                mTestApp,
                new Configuration.Builder()
                        .setMinimumLoggingLevel(android.util.Log.DEBUG)
                        .setExecutor(new SynchronousExecutor())
                        .build());
        mWorker =
                TestListenableWorkerBuilder.from(mTestApp, ReviewDeviceProvisionStateWorker.class)
                        .setWorkerFactory(
                                new WorkerFactory() {
                                    @Override
                                    public ListenableWorker createWorker(
                                            @NonNull Context context,
                                            @NonNull String workerClassName,
                                            @NonNull WorkerParameters workerParameters) {
                                        return workerClassName.equals(
                                                ReviewDeviceProvisionStateWorker.class.getName())
                                                ? new ReviewDeviceProvisionStateWorker(
                                                context,
                                                workerParameters,
                                                mClient,
                                                MoreExecutors.listeningDecorator(
                                                        Executors.newSingleThreadExecutor()))
                                                : null;
                                    }
                                })
                        .build();
        mWorkManager = WorkManager.getInstance(mTestApp);
    }

    @Test
    public void doWork_responseSuccessAndCancelJobs_whenProvisionStateSucceeded()
            throws ExecutionException, InterruptedException {
        ReviewDeviceProvisionStateWorker.scheduleDailyReview(mWorkManager);

        Executors.newSingleThreadExecutor().submit(
                () ->
                        UserParameters.setProvisionState(
                                mTestApp,
                                ProvisionStateController.ProvisionState.PROVISION_SUCCEEDED))
                .get();

        Result jobStartResult = Futures.getUnchecked(mWorker.startWork());

        assertThat(jobStartResult).isEqualTo(Result.success());
        List<WorkInfo> workInfos = Futures.getUnchecked(mWorkManager.getWorkInfosForUniqueWork(
                ReviewDeviceProvisionStateWorker.REVIEW_DEVICE_PROVISION_STATE_WORK_NAME));
        assertThat(workInfos).hasSize(1);
        assertThat(workInfos.get(0).getState()).isEqualTo(WorkInfo.State.CANCELLED);
    }

    @Test
    public void scheduleDailyReview_shouldEnqueueJob() {
        ReviewDeviceProvisionStateWorker.scheduleDailyReview(mWorkManager);

        List<WorkInfo> workInfos = Futures.getUnchecked(mWorkManager.getWorkInfosForUniqueWork(
                ReviewDeviceProvisionStateWorker.REVIEW_DEVICE_PROVISION_STATE_WORK_NAME));
        assertThat(workInfos).hasSize(1);
        assertThat(workInfos.get(0).getState()).isEqualTo(WorkInfo.State.ENQUEUED);
        assertThat(workInfos.get(0).getTags())
                .contains(
                        "com.android.devicelockcontroller.provision.worker"
                                + ".ReviewDeviceProvisionStateWorker");
    }

    @Test
    public void doWork_responseSuccess_whenNoProvisionState() {
        ReviewDeviceProvisionStateWorker.scheduleDailyReview(mWorkManager);

        Result jobStartResult = Futures.getUnchecked(mWorker.startWork());

        assertThat(jobStartResult).isEqualTo(Result.success());
        List<WorkInfo> workInfos = Futures.getUnchecked(mWorkManager.getWorkInfosForUniqueWork(
                ReviewDeviceProvisionStateWorker.REVIEW_DEVICE_PROVISION_STATE_WORK_NAME));
        assertThat(workInfos).hasSize(1);
        assertThat(workInfos.get(0).getState()).isEqualTo(WorkInfo.State.ENQUEUED);
        assertThat(workInfos.get(0).getTags())
                .contains(
                        "com.android.devicelockcontroller.provision.worker"
                                + ".ReviewDeviceProvisionStateWorker");
    }

    @Test
    public void doWork_responseSuccess_whenProvisionStateInProgress()
            throws ExecutionException, InterruptedException {
        ReviewDeviceProvisionStateWorker.scheduleDailyReview(mWorkManager);
        Executors.newSingleThreadExecutor().submit(() -> UserParameters.setProvisionState(
                mTestApp,
                ProvisionStateController.ProvisionState.PROVISION_IN_PROGRESS))
                .get();

        Result jobStartResult = Futures.getUnchecked(mWorker.startWork());

        assertThat(jobStartResult).isEqualTo(Result.success());
        List<WorkInfo> workInfos = Futures.getUnchecked(mWorkManager.getWorkInfosForUniqueWork(
                ReviewDeviceProvisionStateWorker.REVIEW_DEVICE_PROVISION_STATE_WORK_NAME));
        assertThat(workInfos).hasSize(1);
        assertThat(workInfos.get(0).getState()).isEqualTo(WorkInfo.State.ENQUEUED);
        assertThat(workInfos.get(0).getTags())
                .contains(
                        "com.android.devicelockcontroller.provision.worker"
                                + ".ReviewDeviceProvisionStateWorker");
    }

    @Test
    public void doWork_responseSuccess_whenProvisionStartedLessThanADay()
            throws ExecutionException, InterruptedException {
        ReviewDeviceProvisionStateWorker.scheduleDailyReview(mWorkManager);
        Executors.newSingleThreadExecutor().submit(() ->
                        UserParameters.setProvisioningStartTimeMillis(
                                mTestApp,
                                SystemClock.elapsedRealtime() - (long) (1000 * 60 * 60
                                                * 23.90)))
                .get();

        Result jobStartResult = Futures.getUnchecked(mWorker.startWork());

        assertThat(jobStartResult).isEqualTo(Result.success());
        List<WorkInfo> workInfos = Futures.getUnchecked(mWorkManager.getWorkInfosForUniqueWork(
                ReviewDeviceProvisionStateWorker.REVIEW_DEVICE_PROVISION_STATE_WORK_NAME));
        assertThat(workInfos).hasSize(1);
        assertThat(workInfos.get(0).getState()).isEqualTo(WorkInfo.State.ENQUEUED);
        assertThat(workInfos.get(0).getTags())
                .contains(
                        "com.android.devicelockcontroller.provision.worker"
                                + ".ReviewDeviceProvisionStateWorker");
    }

    @Test
    public void doWork_responseSuccessCancelJobAndScheduleReportFailureJob_whenProvisionFailed()
            throws ExecutionException, InterruptedException {
        ReviewDeviceProvisionStateWorker.scheduleDailyReview(mWorkManager);
        Executors.newSingleThreadExecutor()
                .submit(
                        () ->
                                UserParameters.setProvisionState(
                                        mTestApp,
                                        ProvisionStateController.ProvisionState.PROVISION_FAILED))
                .get();

        Result jobStartResult = Futures.getUnchecked(mWorker.startWork());

        assertThat(jobStartResult).isEqualTo(Result.success());
        List<WorkInfo> reviewDeviceStateWorkInfos = Futures.getUnchecked(mWorkManager
                .getWorkInfosForUniqueWork(
                        ReviewDeviceProvisionStateWorker.REVIEW_DEVICE_PROVISION_STATE_WORK_NAME));
        assertThat(reviewDeviceStateWorkInfos).hasSize(1);
        assertThat(reviewDeviceStateWorkInfos.get(0).getState()).isEqualTo(
                WorkInfo.State.CANCELLED);
        assertThat(reviewDeviceStateWorkInfos.get(0).getTags())
                .contains(
                        "com.android.devicelockcontroller.provision.worker"
                                + ".ReviewDeviceProvisionStateWorker");
        List<WorkInfo> reportDeviceStateWorkInfos =
                Futures.getUnchecked(
                        mWorkManager.getWorkInfosForUniqueWork(
                                ReportDeviceProvisionStateWorker.REPORT_PROVISION_STATE_WORK_NAME));
        assertThat(reportDeviceStateWorkInfos).hasSize(1);
        assertThat(reportDeviceStateWorkInfos.get(0).getState()).isEqualTo(WorkInfo.State.ENQUEUED);
        assertThat(reportDeviceStateWorkInfos.get(0).getTags())
                .contains(
                        "com.android.devicelockcontroller.provision.worker"
                                + ".ReportDeviceProvisionStateWorker");
    }

    @Test
    public void doWork_responseSuccess_whenProvisionFailedAndAlarmScheduled()
            throws ExecutionException, InterruptedException {
        ReviewDeviceProvisionStateWorker.scheduleDailyReview(mWorkManager);
        Executors.newSingleThreadExecutor().submit(() -> UserParameters.setProvisionState(
                mTestApp,
                ProvisionStateController.ProvisionState.PROVISION_FAILED))
                .get();
        long countDownBase = SystemClock.elapsedRealtime() + Duration.ofHours(4).toMillis();
        AlarmManager alarmManager = mTestApp.getSystemService(AlarmManager.class);
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        mTestApp, /* ignored */
                        0,
                        new Intent(mTestApp, ResumeProvisionReceiver.class),
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        Objects.requireNonNull(alarmManager)
                .setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, countDownBase, pendingIntent);

        Result jobStartResult = Futures.getUnchecked(mWorker.startWork());

        assertThat(jobStartResult).isEqualTo(Result.success());
        List<WorkInfo> workInfos =
                Futures.getUnchecked(
                        mWorkManager.getWorkInfosForUniqueWork(
                                ReviewDeviceProvisionStateWorker
                                        .REVIEW_DEVICE_PROVISION_STATE_WORK_NAME));
        assertThat(workInfos).hasSize(1);
        assertThat(workInfos.get(0).getState()).isEqualTo(WorkInfo.State.ENQUEUED);
        assertThat(workInfos.get(0).getTags()).contains(
                "com.android.devicelockcontroller.provision.worker"
                        + ".ReviewDeviceProvisionStateWorker");
    }
}
