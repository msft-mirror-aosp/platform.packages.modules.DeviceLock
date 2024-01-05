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

package com.android.devicelockcontroller;

import android.annotation.CallbackExecutor;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.devicelock.DeviceLockManager;
import android.devicelock.IDeviceLockService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.RemoteCallback;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Implementation for SystemDeviceLockManager.
 */
public final class SystemDeviceLockManagerImpl implements SystemDeviceLockManager {
    private static final String TAG = "SystemDeviceLockManagerImpl";

    private final IDeviceLockService mIDeviceLockService;

    private static final String MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER =
            "com.android.devicelockcontroller.permission."
                    + "MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER";

    private SystemDeviceLockManagerImpl(Context context) {
        final DeviceLockManager deviceLockManager =
                context.getSystemService(DeviceLockManager.class);

        mIDeviceLockService = deviceLockManager.getService();
    }

    private SystemDeviceLockManagerImpl() {
        this(DeviceLockControllerApplication.getAppContext());
    }

    // Initialization-on-demand holder.
    private static final class SystemDeviceLockManagerHolder {
        static final SystemDeviceLockManagerImpl sSystemDeviceLockManager =
                new SystemDeviceLockManagerImpl();
    }

    /**
     * Returns the lazily initialized singleton instance of the SystemDeviceLockManager.
     */
    public static SystemDeviceLockManager getInstance() {
        return SystemDeviceLockManagerHolder.sSystemDeviceLockManager;
    }

    @Override
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    public void addFinancedDeviceKioskRole(@NonNull String packageName,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.addFinancedDeviceKioskRole(packageName,
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to add financed role to: "
                                + packageName);
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    public void removeFinancedDeviceKioskRole(@NonNull String packageName,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.removeFinancedDeviceKioskRole(packageName,
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to remove financed role from: "
                                + packageName);
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    public void setExemptFromActivityBackgroundStartRestriction(boolean exempt,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.setExemptFromActivityBackgroundStartRestriction(exempt,
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to change exempt from "
                                + "activity background start to: "
                                + (exempt ? "exempt" : "non exempt"));
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    public void setExemptFromHibernation(String packageName, boolean exempt,
            @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.setExemptFromHibernation(packageName, exempt,
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to change exempt from "
                                + "hibernation to: "
                                + (exempt ? "exempt" : "non exempt") + " for package: "
                                + packageName);
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    public void setExemptFromBatteryUsageRestriction(String packageName, boolean exempt,
            Executor executor, @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.setExemptFromBatteryUsageRestriction(packageName,
                    exempt, new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to change exempt from "
                                + "battery usage restriction to: "
                                + (exempt ? "exempt" : "non exempt") + " for package: "
                                + packageName);
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    public void enableKioskKeepalive(String packageName, Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.enableKioskKeepalive(packageName,
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to enable kiosk keepalive "
                                + "for package: " + packageName);
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    public void disableKioskKeepalive(Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.disableKioskKeepalive(
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to disable kiosk keepalive");
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    public void enableControllerKeepalive(Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.enableControllerKeepalive(
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to enable controller keepalive");
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    public void disableControllerKeepalive(Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.disableControllerKeepalive(
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to disable controller keepalive");
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    @Override
    @RequiresPermission(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
    public void setDeviceFinalized(boolean finalized, Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            mIDeviceLockService.setDeviceFinalized(finalized,
                    new RemoteCallback(result -> executor.execute(() -> {
                        processResult(result, callback, "Failed to set device finalized");
                    }), new Handler(Looper.getMainLooper())));
        } catch (RemoteException e) {
            executor.execute(() -> callback.onError(new RuntimeException(e)));
        }
    }

    private static void processResult(Bundle result,
            @NonNull OutcomeReceiver<Void, Exception> callback, String message) {
        final boolean remoteCallbackResult = result.getBoolean(
                IDeviceLockService.KEY_REMOTE_CALLBACK_RESULT);
        if (remoteCallbackResult) {
            callback.onResult(null /* result */);
        } else {
            callback.onError(new Exception(message));
        }
    }
}
