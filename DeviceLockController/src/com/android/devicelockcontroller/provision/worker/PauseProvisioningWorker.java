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

import static com.android.devicelockcontroller.common.DeviceLockConstants.REASON_UNSPECIFIED;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.PauseDeviceProvisioningGrpcResponse;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;

/**
 * A worker class dedicated to request pause of provisioning for device lock program.
 */
public final class PauseProvisioningWorker extends AbstractCheckInWorker {

    public static final String KEY_PAUSE_DEVICE_PROVISIONING_REASON =
            "PAUSE_DEVICE_PROVISIONING_REASON";
    @VisibleForTesting
    static final String KEY_SHOULD_FORCE_PROVISION = "should-force-provision";

    public PauseProvisioningWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @VisibleForTesting
    PauseProvisioningWorker(@NonNull Context context, @NonNull WorkerParameters workerParams,
            DeviceCheckInClient client) {
        super(context, workerParams, client);
    }

    @NonNull
    @Override
    public Result doWork() {
        final int reason = getInputData().getInt(KEY_PAUSE_DEVICE_PROVISIONING_REASON,
                REASON_UNSPECIFIED);
        PauseDeviceProvisioningGrpcResponse response =
                Futures.getUnchecked(mClient).pauseDeviceProvisioning(reason);
        if (response.isSuccessful()) {
            boolean shouldForceProvisioning = response.shouldForceProvisioning();
            Futures.getUnchecked(GlobalParametersClient.getInstance().setProvisionForced(
                    shouldForceProvisioning));
            return Result.success(new Data.Builder().putBoolean(KEY_SHOULD_FORCE_PROVISION,
                    shouldForceProvisioning).build());
        }
        LogUtil.w(TAG, "Pause provisioning request failed: " + response);
        return Result.failure();
    }
}
