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

package com.android.devicelockcontroller.provision.worker;

import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_READY;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_IN_PROGRESS;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.testing.TestingExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public final class SetupWizardCompletionTimeoutWorkerTest {
    private TestDeviceLockControllerApplication mTestApp;
    private SetupWizardCompletionTimeoutWorker mWorker;
    private ProvisionStateController mMockProvisionStateController;

    @Before
    public void setUp() throws Exception {
        mTestApp = ApplicationProvider.getApplicationContext();
        mMockProvisionStateController = mTestApp.getProvisionStateController();
        mWorker = TestListenableWorkerBuilder.from(
                        mTestApp, SetupWizardCompletionTimeoutWorker.class)
                .setWorkerFactory(
                        new WorkerFactory() {
                            @Override
                            public ListenableWorker createWorker(
                                    @NonNull Context context, @NonNull String workerClassName,
                                    @NonNull WorkerParameters workerParameters) {
                                return workerClassName.equals(
                                        SetupWizardCompletionTimeoutWorker.class.getName())
                                        ? new SetupWizardCompletionTimeoutWorker(context,
                                        workerParameters,
                                        TestingExecutors.sameThreadScheduledExecutor())
                                        : null;
                            }
                        }).build();
    }

    @Test
    public void doWork_suwComplete_doesNotStartFlow() {
        // Device setup is complete
        ContentResolver contentResolver = mTestApp.getContentResolver();
        Settings.Secure.putInt(contentResolver, Settings.Secure.USER_SETUP_COMPLETE, 1);

        assertThat(Futures.getUnchecked(mWorker.startWork()))
                .isEqualTo(ListenableWorker.Result.success());

        verify(mMockProvisionStateController, never()).setNextStateForEvent(anyInt());
    }

    @Test
    public void doWork_suwNotComplete_notUnprovisioned_doesNotStartFlow() {
        // Device setup is not complete
        ContentResolver contentResolver = mTestApp.getContentResolver();
        Settings.Secure.putInt(contentResolver, Settings.Secure.USER_SETUP_COMPLETE, 0);

        when(mMockProvisionStateController.getState())
                .thenReturn(Futures.immediateFuture(PROVISION_IN_PROGRESS));

        assertThat(Futures.getUnchecked(mWorker.startWork()))
                .isEqualTo(ListenableWorker.Result.success());

        verify(mMockProvisionStateController, never()).setNextStateForEvent(anyInt());
    }

    @Test
    public void doWork_suwNotComplete_unprovisioned_provisionNotReady_doesNotStartFlow()
            throws ExecutionException, InterruptedException {
        // Device setup is not complete
        ContentResolver contentResolver = mTestApp.getContentResolver();
        Settings.Secure.putInt(contentResolver, Settings.Secure.USER_SETUP_COMPLETE, 0);

        when(mMockProvisionStateController.getState())
                .thenReturn(Futures.immediateFuture(UNPROVISIONED));

        GlobalParametersClient.getInstance().setProvisionReady(false).get();

        assertThat(Futures.getUnchecked(mWorker.startWork()))
                .isEqualTo(ListenableWorker.Result.success());

        verify(mMockProvisionStateController, never()).setNextStateForEvent(anyInt());
    }

    @Test
    public void doWork_suwNotComplete_unprovisioned_provisionReady_startsFlow()
            throws ExecutionException, InterruptedException {
        // Device setup is not complete
        ContentResolver contentResolver = mTestApp.getContentResolver();
        Settings.Secure.putInt(contentResolver, Settings.Secure.USER_SETUP_COMPLETE, 0);

        when(mMockProvisionStateController.getState())
                .thenReturn(Futures.immediateFuture(UNPROVISIONED));
        when(mMockProvisionStateController.setNextStateForEvent(anyInt()))
                .thenReturn(Futures.immediateVoidFuture());

        GlobalParametersClient.getInstance().setProvisionReady(true).get();

        assertThat(Futures.getUnchecked(mWorker.startWork()))
                .isEqualTo(ListenableWorker.Result.success());

        Executors.newSingleThreadExecutor().submit(
                () -> assertThat(UserParameters.isSetupWizardTimedOut(mTestApp)).isTrue()).get();

        verify(mMockProvisionStateController).setNextStateForEvent(eq(PROVISION_READY));
    }
}
