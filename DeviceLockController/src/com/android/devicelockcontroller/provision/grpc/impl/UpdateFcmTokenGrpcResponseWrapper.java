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

package com.android.devicelockcontroller.provision.grpc.impl;

import com.android.devicelockcontroller.proto.UpdateFcmTokenResponse;
import com.android.devicelockcontroller.provision.grpc.UpdateFcmTokenGrpcResponse;

import io.grpc.Status;

/**
 * A wrapper class for {@link UpdateFcmTokenGrpcResponse}.
 */
public final class UpdateFcmTokenGrpcResponseWrapper extends UpdateFcmTokenGrpcResponse {
    private UpdateFcmTokenResponse mResponse;

    public UpdateFcmTokenGrpcResponseWrapper(Status status) {
        super(status);
    }

    public UpdateFcmTokenGrpcResponseWrapper(UpdateFcmTokenResponse response) {
        super();
        mResponse = response;
    }

    @Override
    @FcmTokenResult
    public int getFcmTokenResult() {
        if (mResponse == null) {
            return FcmTokenResult.RESULT_UNSPECIFIED;
        }
        return switch (mResponse.getResult()) {
            case UPDATE_FCM_TOKEN_RESULT_UNSPECIFIED -> FcmTokenResult.RESULT_UNSPECIFIED;
            case UPDATE_FCM_TOKEN_RESULT_SUCCESS -> FcmTokenResult.RESULT_SUCCESS;
            case UPDATE_FCM_TOKEN_RESULT_FAILURE -> FcmTokenResult.RESULT_FAILURE;
            default -> throw new IllegalStateException(
                    "Unexpected update FCM result: " + mResponse.getResult());
        };
    }
}
