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

import static com.android.devicelockcontroller.provision.grpc.UpdateFcmTokenGrpcResponse.FcmTokenResult.RESULT_FAILURE;
import static com.android.devicelockcontroller.provision.grpc.UpdateFcmTokenGrpcResponse.FcmTokenResult.RESULT_SUCCESS;
import static com.android.devicelockcontroller.provision.grpc.UpdateFcmTokenGrpcResponse.FcmTokenResult.RESULT_UNSPECIFIED;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.FcmRegistrationTokenProvider;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.UpdateFcmTokenGrpcResponse;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

public final class UpdateFcmTokenWorker extends AbstractCheckInWorker {

    private final AbstractDeviceCheckInHelper mCheckInHelper;
    private final FcmRegistrationTokenProvider mFcmRegistrationTokenProvider;

    public UpdateFcmTokenWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParameters,
            ListeningExecutorService executorService) {
        this(context, workerParameters, new DeviceCheckInHelper(context),
                (FcmRegistrationTokenProvider) context.getApplicationContext(), /* client= */ null,
                executorService);
    }

    @VisibleForTesting
    UpdateFcmTokenWorker(@NonNull Context context, @NonNull WorkerParameters workerParameters,
            AbstractDeviceCheckInHelper helper,  FcmRegistrationTokenProvider tokenProvider,
            DeviceCheckInClient client, ListeningExecutorService executorService) {
        super(context, workerParameters, client, executorService);
        mFcmRegistrationTokenProvider = tokenProvider;
        mCheckInHelper = helper;
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return Futures.transformAsync(
                mExecutorService.submit(mCheckInHelper::getDeviceUniqueIds),
                deviceIds -> {
                    if (deviceIds.isEmpty()) {
                        LogUtil.w(TAG, "Update fcm failed. No device identifier available!");
                        return Futures.immediateFuture(Result.failure());
                    }
                    ListenableFuture<String> fcmRegistrationToken =
                            mFcmRegistrationTokenProvider.getFcmRegistrationToken();
                    return Futures.whenAllSucceed(mClient, fcmRegistrationToken).call(() -> {
                        DeviceCheckInClient client = Futures.getDone(mClient);
                        String fcmToken = Futures.getDone(fcmRegistrationToken);
                        UpdateFcmTokenGrpcResponse response = client.updateFcmToken(
                                deviceIds, fcmToken);
                        if (response.hasRecoverableError()) {
                            LogUtil.w(TAG, "Update FCM failed w/ recoverable error " + response
                                    + "\nRetrying...");
                            return Result.retry();
                        }
                        if (!response.isSuccessful()) {
                            LogUtil.d(TAG, "Update FCM failed: " + response);
                            return Result.failure();
                        }
                        if (response.getFcmTokenResult() != RESULT_SUCCESS) {
                            if (response.getFcmTokenResult() == RESULT_FAILURE) {
                                // This can happen if there is a failed precondition e.g.
                                // device is finalized or it hasn't checked in yet. In both cases,
                                // we should not retry the job
                                LogUtil.e(TAG, "Update FCM got successful response but server "
                                        + "indicated failure");
                            } else if (response.getFcmTokenResult() == RESULT_UNSPECIFIED) {
                                LogUtil.e(TAG, "Update FCM got successful response but it was "
                                        + "unspecified");
                            }
                            return Result.failure();
                        }
                        return Result.success();
                    }, mExecutorService);
                }, mExecutorService);
    }
}
