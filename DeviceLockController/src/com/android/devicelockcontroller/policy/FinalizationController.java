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

import com.android.devicelockcontroller.provision.grpc.DeviceFinalizeClient.ReportDeviceProgramCompleteResponse;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Controller that handles finalizing the device when the restrictions are cleared and the device
 * should no longer be financed.
 */
public interface FinalizationController {

    /**
     * Enforces the disk state.
     *
     * @param force if true, forcefully reset the state to the disk state and enforce it.
     *              Otherwise, only enforce if the state is uninitialized
     * @return future for when the state has been enforced
     */
    ListenableFuture<Void> enforceDiskState(boolean force);

    /**
     * Notifies the controller that the device restrictions have been cleared and that the device
     * should start the finalization process.
     *
     * @return future for when the clear has been handled
     */
    ListenableFuture<Void> notifyRestrictionsCleared();

    /**
     * Finalize a device that is not enrolled in the program, without reporting to the backend.
     *
     * @return future for when finalization has been handled
     */
    ListenableFuture<Void> finalizeNotEnrolledDevice();

    /**
     * Notifies the controller of the result of reporting the finalization state to the server
     *
     * @param response from the server
     * @return future for when the report has been handled
     */
    ListenableFuture<Void> notifyFinalizationReportResult(
            ReportDeviceProgramCompleteResponse response);
}
