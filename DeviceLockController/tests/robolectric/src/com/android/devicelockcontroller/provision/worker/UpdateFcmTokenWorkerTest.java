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

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceIdType.DEVICE_ID_TYPE_IMEI;
import static com.android.devicelockcontroller.provision.grpc.UpdateFcmTokenGrpcResponse.FcmTokenResult.RESULT_FAILURE;
import static com.android.devicelockcontroller.provision.grpc.UpdateFcmTokenGrpcResponse.FcmTokenResult.RESULT_SUCCESS;
import static com.android.devicelockcontroller.provision.grpc.UpdateFcmTokenGrpcResponse.FcmTokenResult.RESULT_UNSPECIFIED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.android.devicelockcontroller.FcmRegistrationTokenProvider;
import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.common.DeviceId;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.UpdateFcmTokenGrpcResponse;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.testing.TestingExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class UpdateFcmTokenWorkerTest {
    public static final ArraySet<DeviceId> TEST_DEVICE_IDS = new ArraySet<>(
            new DeviceId[]{new DeviceId(DEVICE_ID_TYPE_IMEI, "1234667890")});
    public static final ArraySet<DeviceId> EMPTY_DEVICE_IDS = new ArraySet<>(new DeviceId[]{});

    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();
    @Mock
    private AbstractDeviceCheckInHelper mHelper;
    @Mock
    private FcmRegistrationTokenProvider mFcmRegistrationTokenProvider;
    @Mock
    private DeviceCheckInClient mClient;
    @Mock
    private UpdateFcmTokenGrpcResponse mResponse;
    private UpdateFcmTokenWorker mWorker;
    private TestDeviceLockControllerApplication mContext =
            ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() throws Exception {
        when(mFcmRegistrationTokenProvider.getFcmRegistrationToken()).thenReturn(
                mContext.getFcmRegistrationToken());
        when(mClient.updateFcmToken(eq(TEST_DEVICE_IDS), any())).thenReturn(mResponse);
        mWorker = TestListenableWorkerBuilder.from(
                        mContext, UpdateFcmTokenWorker.class)
                .setWorkerFactory(
                        new WorkerFactory() {
                            @Override
                            public ListenableWorker createWorker(
                                    @NonNull Context context, @NonNull String workerClassName,
                                    @NonNull WorkerParameters workerParameters) {
                                return workerClassName.equals(UpdateFcmTokenWorker.class.getName())
                                        ? new UpdateFcmTokenWorker(
                                        context, workerParameters, mHelper,
                                        mFcmRegistrationTokenProvider, mClient,
                                        TestingExecutors.sameThreadScheduledExecutor())
                                        : null;
                            }
                        }).build();
    }

    @Test
    public void updateFcmToken_succeeds() {
        // GIVEN valid device ids and server response is successful
        when(mHelper.getDeviceUniqueIds()).thenReturn(TEST_DEVICE_IDS);
        when(mResponse.hasRecoverableError()).thenReturn(false);
        when(mResponse.isSuccessful()).thenReturn(true);
        when(mResponse.getFcmTokenResult()).thenReturn(RESULT_SUCCESS);

        // WHEN the work runs
        final Result result = Futures.getUnchecked(mWorker.startWork());

        // THEN the work succeeds
        assertThat(result).isEqualTo(Result.success());
    }

    @Test
    public void updateFcmToken_noDeviceIds_fails() {
        // GIVEN empty device ids
        when(mHelper.getDeviceUniqueIds()).thenReturn(new ArraySet<>());

        // WHEN the work runs
        final Result result = Futures.getUnchecked(mWorker.startWork());

        // THEN the work fails
        assertThat(result).isEqualTo(Result.failure());
    }

    @Test
    public void updateFcmToken_recoverableError_retries() {
        // GIVEN valid device ids and there is a recoverable error
        when(mHelper.getDeviceUniqueIds()).thenReturn(TEST_DEVICE_IDS);
        when(mResponse.hasRecoverableError()).thenReturn(true);
        when(mResponse.isSuccessful()).thenReturn(false);

        // WHEN the work runs
        final Result result = Futures.getUnchecked(mWorker.startWork());

        // THEN the work retries
        assertThat(result).isEqualTo(Result.retry());
    }

    @Test
    public void updateFcmToken_unrecoverableError_fails() {
        // GIVEN valid device ids and there is an unrecoverable error
        when(mHelper.getDeviceUniqueIds()).thenReturn(TEST_DEVICE_IDS);
        when(mResponse.hasRecoverableError()).thenReturn(false);
        when(mResponse.isSuccessful()).thenReturn(false);

        // WHEN the work runs
        final Result result = Futures.getUnchecked(mWorker.startWork());

        // THEN the work fails
        assertThat(result).isEqualTo(Result.failure());
    }

    @Test
    public void updateFcmToken_getsFcmResultFailure_fails() {
        // GIVEN valid device ids, successful response from server, but response indicates a failed
        // precondition
        when(mHelper.getDeviceUniqueIds()).thenReturn(TEST_DEVICE_IDS);
        when(mResponse.hasRecoverableError()).thenReturn(false);
        when(mResponse.isSuccessful()).thenReturn(true);
        when(mResponse.getFcmTokenResult()).thenReturn(RESULT_FAILURE);

        // WHEN the work runs
        final Result result = Futures.getUnchecked(mWorker.startWork());

        // THEN the work fails
        assertThat(result).isEqualTo(Result.failure());
    }

    @Test
    public void updateFcmToken_getsFcmResultUnspecified_fails() {
        // GIVEN valid device ids, successful response from server, but response indicates
        // and unspecified value
        when(mHelper.getDeviceUniqueIds()).thenReturn(TEST_DEVICE_IDS);
        when(mResponse.hasRecoverableError()).thenReturn(false);
        when(mResponse.isSuccessful()).thenReturn(true);
        when(mResponse.getFcmTokenResult()).thenReturn(RESULT_UNSPECIFIED);

        // WHEN the work runs
        final Result result = Futures.getUnchecked(mWorker.startWork());

        // THEN the work fails
        assertThat(result).isEqualTo(Result.failure());
    }
}
