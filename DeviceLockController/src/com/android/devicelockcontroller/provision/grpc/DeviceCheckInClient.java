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

package com.android.devicelockcontroller.provision.grpc;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.devicelockcontroller.proto.DeviceLockCheckinServiceGrpc;
import com.android.devicelockcontroller.proto.DeviceLockCheckinServiceGrpc.DeviceLockCheckinServiceBlockingStub;
import com.android.devicelockcontroller.proto.GetDeviceCheckinStatusRequest;
import com.android.devicelockcontroller.proto.PauseDeviceProvisioningRequest;
import com.android.devicelockcontroller.proto.ReportDeviceProvisionCompleteRequest;

import io.grpc.StatusRuntimeException;
import io.grpc.okhttp.OkHttpChannelBuilder;

/**
 * A client for the DeviceLockCheckinServiceGrpc service.
 */
public final class DeviceCheckInClient {
    private final DeviceLockCheckinServiceBlockingStub mBlockingStub;

    public DeviceCheckInClient(String host, int port) {
        mBlockingStub = DeviceLockCheckinServiceGrpc.newBlockingStub(
                OkHttpChannelBuilder
                        .forAddress(host, port)
                        .build());
    }

    @VisibleForTesting
    public DeviceCheckInClient(
            DeviceLockCheckinServiceBlockingStub blockingStub) {
        mBlockingStub = blockingStub;
    }

    /**
     * Get the device check in status from device lock server
     */
    @WorkerThread
    public GetDeviceCheckInStatusResponseWrapper getDeviceCheckInStatus(
            GetDeviceCheckinStatusRequest request) {
        try {
            return new GetDeviceCheckInStatusResponseWrapper(
                    mBlockingStub.getDeviceCheckinStatus(request));
        } catch (StatusRuntimeException e) {
            return new GetDeviceCheckInStatusResponseWrapper(e.getStatus());
        }
    }

    /**
     * Client request to pause device provisioning for a later time
     */
    @WorkerThread
    public PauseDeviceProvisioningResponseWrapper pauseDeviceProvisioning(
            PauseDeviceProvisioningRequest request) {
        try {
            return new PauseDeviceProvisioningResponseWrapper(
                    mBlockingStub.pauseDeviceProvisioning(request));
        } catch (StatusRuntimeException e) {
            return new PauseDeviceProvisioningResponseWrapper(e.getStatus());
        }
    }

    /**
     * Client to report provision has completed to the Device Lock server.
     */
    @WorkerThread
    public ReportDeviceProvisionCompleteResponseWrapper reportDeviceProvisioningComplete(
            ReportDeviceProvisionCompleteRequest request) {
        try {
            return new ReportDeviceProvisionCompleteResponseWrapper(
                    mBlockingStub.reportDeviceProvisionComplete(request));
        } catch (StatusRuntimeException e) {
            return new ReportDeviceProvisionCompleteResponseWrapper(e.getStatus());
        }
    }
}
