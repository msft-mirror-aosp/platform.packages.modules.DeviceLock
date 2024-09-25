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

package com.android.devicelockcontroller.provision.grpc;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import io.grpc.Status;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An abstract class that is used to encapsulate the response for updating the FCM registration
 * token.
 */
public abstract class UpdateFcmTokenGrpcResponse extends GrpcResponse {
    /** Definitions for FCM token results. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    FcmTokenResult.RESULT_UNSPECIFIED,
                    FcmTokenResult.RESULT_SUCCESS,
                    FcmTokenResult.RESULT_FAILURE
            }
    )
    public @interface FcmTokenResult {
        /** Result unspecified */
        int RESULT_UNSPECIFIED = 0;
        /** FCM registration token successfully updated */
        int RESULT_SUCCESS = 1;
        /** FCM registration token falied to update */
        int RESULT_FAILURE = 2;
    }

    public UpdateFcmTokenGrpcResponse() {
        mStatus = null;
    }

    public UpdateFcmTokenGrpcResponse(@NonNull Status status) {
        super(status);
    }

    /**
     * Get result of updating FCM registration token.
     *
     * @return one of {@link FcmTokenResult}
     */
    @FcmTokenResult
    public abstract int getFcmTokenResult();
}
