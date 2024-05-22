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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.FcmRegistrationTokenProvider;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.Duration;

/**
 * Worker class to retry getting the FCM registration token if it was not successfully retrieved
 * with the initial check-in.
 */
public final class GetFcmTokenWorker extends ListenableWorker {

    static final Duration FCM_TOKEN_WORKER_INITIAL_DELAY = Duration.ofMinutes(1);
    static final Duration FCM_TOKEN_WORKER_BACKOFF_DELAY = Duration.ofMinutes(30);
    static final String FCM_TOKEN_WORK_NAME = "fcm-token";

    private final FcmRegistrationTokenProvider mFcmRegistrationTokenProvider;

    public GetFcmTokenWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParameters) {
        this(context, workerParameters,
                (FcmRegistrationTokenProvider) context.getApplicationContext());
    }

    @VisibleForTesting
    GetFcmTokenWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParameters,
            FcmRegistrationTokenProvider tokenProvider) {
        super(context, workerParameters);
        mFcmRegistrationTokenProvider = tokenProvider;
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return Futures.transform(mFcmRegistrationTokenProvider.getFcmRegistrationToken(),
                token -> {
                    if (Strings.isNullOrEmpty(token) || token.isBlank()) {
                        return Result.retry();
                    }
                    return Result.success();
                }, MoreExecutors.directExecutor()
        );
    }
}
