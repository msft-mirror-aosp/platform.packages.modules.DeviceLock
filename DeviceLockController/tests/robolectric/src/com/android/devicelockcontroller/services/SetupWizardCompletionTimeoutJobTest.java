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
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;

import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(RobolectricTestRunner.class)
public final class SetupWizardCompletionTimeoutJobTest {
    private TestDeviceLockControllerApplication mTestApp;
    private ProvisionStateController mMockProvisionStateController;
    private SetupWizardCompletionTimeoutJobService mJob;
    private static final long TIMEOUT_MILLIS = 1000;

    @Before
    public void setUp() throws Exception {
        mTestApp = ApplicationProvider.getApplicationContext();
        mMockProvisionStateController = mTestApp.getProvisionStateController();

        mJob = new SetupWizardCompletionTimeoutJobService(mTestApp);
    }

    @Test
    public void doWork_suwComplete_doesNotStartFlow()
            throws InterruptedException, ExecutionException, TimeoutException {
        // Device setup is complete
        ContentResolver contentResolver = mTestApp.getContentResolver();
        Settings.Secure.putInt(contentResolver, Settings.Secure.USER_SETUP_COMPLETE, 1);

        boolean result = mJob.onStartJob(/* params= */ null);

        assertThat(result).isFalse();

        verify(mMockProvisionStateController, never()).setNextStateForEvent(anyInt());
    }

    @Test
    public void doWork_suwNotComplete_notUnprovisioned_doesNotStartFlow()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Device setup is not complete
        ContentResolver contentResolver = mTestApp.getContentResolver();
        Settings.Secure.putInt(contentResolver, Settings.Secure.USER_SETUP_COMPLETE, 0);

        when(mMockProvisionStateController.getState())
                .thenReturn(Futures.immediateFuture(PROVISION_IN_PROGRESS));

        boolean result = mJob.onStartJob(/* params= */ null);

        assertThat(result).isTrue();

        mJob.mFuture.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        verify(mMockProvisionStateController, never()).setNextStateForEvent(anyInt());
    }

    @Test
    public void doWork_suwNotComplete_unprovisioned_provisionNotReady_doesNotStartFlow()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Device setup is not complete
        ContentResolver contentResolver = mTestApp.getContentResolver();
        Settings.Secure.putInt(contentResolver, Settings.Secure.USER_SETUP_COMPLETE, 0);

        when(mMockProvisionStateController.getState())
                .thenReturn(Futures.immediateFuture(UNPROVISIONED));

        GlobalParametersClient.getInstance().setProvisionReady(false).get();

        boolean result = mJob.onStartJob(/* params= */ null);

        assertThat(result).isTrue();

        mJob.mFuture.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        verify(mMockProvisionStateController, never()).setNextStateForEvent(anyInt());
    }

    @Test
    public void doWork_suwNotComplete_unprovisioned_provisionReady_startsFlow()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Device setup is not complete
        ContentResolver contentResolver = mTestApp.getContentResolver();
        Settings.Secure.putInt(contentResolver, Settings.Secure.USER_SETUP_COMPLETE, 0);

        when(mMockProvisionStateController.getState())
                .thenReturn(Futures.immediateFuture(UNPROVISIONED));
        when(mMockProvisionStateController.setNextStateForEvent(anyInt()))
                .thenReturn(Futures.immediateVoidFuture());

        GlobalParametersClient.getInstance().setProvisionReady(true).get();

        boolean result = mJob.onStartJob(/* params= */ null);

        assertThat(result).isTrue();

        mJob.mFuture.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        Executors.newSingleThreadExecutor().submit(
                () -> assertThat(UserParameters.isSetupWizardTimedOut(mTestApp)).isTrue()).get();

        verify(mMockProvisionStateController).setNextStateForEvent(eq(PROVISION_READY));
    }
}
