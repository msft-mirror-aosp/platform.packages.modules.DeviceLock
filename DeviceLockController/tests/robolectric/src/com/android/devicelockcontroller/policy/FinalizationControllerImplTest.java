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

import static com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState.FINALIZED;
import static com.android.devicelockcontroller.policy.FinalizationControllerImpl.FinalizationState.FINALIZED_UNREPORTED;
import static com.android.devicelockcontroller.provision.worker.ReportDeviceLockProgramCompleteWorker.REPORT_DEVICE_LOCK_PROGRAM_COMPLETE_WORK_NAME;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.provision.grpc.DeviceFinalizeClient.ReportDeviceProgramCompleteResponse;
import com.android.devicelockcontroller.receivers.LockedBootCompletedReceiver;
import com.android.devicelockcontroller.storage.GlobalParametersClient;

import com.google.common.util.concurrent.ExecutionSequencer;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public final class FinalizationControllerImplTest {

    private static final int TIMEOUT_MS = 1000;

    private Context mContext;
    private FinalizationControllerImpl mFinalizationController;
    private FinalizationStateDispatchQueue mDispatchQueue;
    private ExecutionSequencer mExecutionSequencer = ExecutionSequencer.create();
    private Executor mLightweightExecutor = Executors.newCachedThreadPool();
    private GlobalParametersClient mGlobalParametersClient;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(mContext);

        mGlobalParametersClient = GlobalParametersClient.getInstance();
        mDispatchQueue = new FinalizationStateDispatchQueue(mExecutionSequencer);
    }

    @Test
    public void notifyRestrictionsCleared_startsReportingWork() throws Exception {
        mFinalizationController = makeFinalizationController();

        // WHEN restrictions are cleared
        ListenableFuture<Void> clearedFuture =
                mFinalizationController.notifyRestrictionsCleared();
        Futures.getChecked(clearedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // THEN work manager has work scheduled to report the device is finalized and the disk
        // value is set to unreported
        ListenableFuture<List<WorkInfo>> workInfosFuture = WorkManager.getInstance(mContext)
                .getWorkInfosForUniqueWork(REPORT_DEVICE_LOCK_PROGRAM_COMPLETE_WORK_NAME);
        List<WorkInfo> workInfos = Futures.getChecked(workInfosFuture, Exception.class);
        assertThat(workInfos).isNotEmpty();
        assertThat(mGlobalParametersClient.getFinalizationState().get())
                .isEqualTo(FINALIZED_UNREPORTED);
    }

    @Test
    public void reportingFinishedSuccessfully_disablesApplication() throws Exception {
        mFinalizationController = makeFinalizationController();

        // GIVEN the restrictions have been requested to clear
        ListenableFuture<Void> clearedFuture =
                mFinalizationController.notifyRestrictionsCleared();
        Futures.getChecked(clearedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // WHEN the work is reported successfully
        ReportDeviceProgramCompleteResponse successResponse =
                new ReportDeviceProgramCompleteResponse();
        ListenableFuture<Void> reportedFuture =
                mFinalizationController.notifyFinalizationReportResult(successResponse);
        Futures.getChecked(reportedFuture, Exception.class, TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // THEN the application is disabled and the disk value is set to finalized
        PackageManager pm = mContext.getPackageManager();
        assertThat(pm.getComponentEnabledSetting(
                new ComponentName(mContext, LockedBootCompletedReceiver.class)))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        // TODO(279517666): Assert checks that application itself is disabled when implemented
        assertThat(mGlobalParametersClient.getFinalizationState().get()).isEqualTo(FINALIZED);
    }

    @Test
    public void unreportedStateInitializedFromDisk_reportsWork() throws Exception {
        // GIVEN the state on disk is unreported
        Futures.getChecked(
                mGlobalParametersClient.setFinalizationState(FINALIZED_UNREPORTED),
                Exception.class);

        // WHEN the controller is initialized
        mFinalizationController = makeFinalizationController();
        Futures.getChecked(mFinalizationController.getStateInitializedFuture(), Exception.class,
                TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // THEN the state from disk is used and is applied immediately, reporting the work.
        ListenableFuture<List<WorkInfo>> workInfosFuture = WorkManager.getInstance(mContext)
                .getWorkInfosForUniqueWork(REPORT_DEVICE_LOCK_PROGRAM_COMPLETE_WORK_NAME);
        List<WorkInfo> workInfos = Futures.getChecked(workInfosFuture, Exception.class);
        assertThat(workInfos).isNotEmpty();
    }

    private FinalizationControllerImpl makeFinalizationController() {
        return new FinalizationControllerImpl(
                mContext, mDispatchQueue, mLightweightExecutor, TestWorker.class);
    }

    /**
     * Fake test worker that just finishes work immediately
     */
    private static final class TestWorker extends ListenableWorker {

        TestWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
            super(appContext, workerParams);
        }

        @NonNull
        @Override
        public ListenableFuture<Result> startWork() {
            return Futures.immediateFuture(Result.success());
        }
    }
}
