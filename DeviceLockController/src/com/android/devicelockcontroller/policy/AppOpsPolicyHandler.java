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

import android.os.OutcomeReceiver;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.devicelockcontroller.SystemDeviceLockManager;
import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executor;

final class AppOpsPolicyHandler implements PolicyHandler {
    private static final String TAG = "AppOpsPolicyHandler";
    private final SystemDeviceLockManager mSystemDeviceLockManager;
    private final Executor mBgExecutor;

    AppOpsPolicyHandler(SystemDeviceLockManager systemDeviceLockManager, Executor bgExecutor) {
        mSystemDeviceLockManager = systemDeviceLockManager;
        mBgExecutor = bgExecutor;
    }

    private ListenableFuture<Boolean> getExemptFromBackgroundStartRestrictionsFuture(
            boolean exempt) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mSystemDeviceLockManager.setExemptFromActivityBackgroundStartRestriction(exempt,
                            mBgExecutor,
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void unused) {
                                    completer.set(true);
                                }

                                @Override
                                public void onError(Exception error) {
                                    LogUtil.e(TAG, "Cannot set background start exemption", error);
                                    completer.set(false);
                                }
                            });
                    // Used only for debugging.
                    return "getExemptFromBackgroundStartRestrictionFuture";
                });
    }

    private ListenableFuture<Boolean> getExemptFromHibernationFuture(boolean exempt) {
        return Futures.transformAsync(SetupParametersClient.getInstance().getKioskPackage(),
                kioskPackageName -> kioskPackageName == null
                        ? Futures.immediateFuture(true)
                        : CallbackToFutureAdapter.getFuture(
                                completer -> {
                                    mSystemDeviceLockManager.setExemptFromHibernation(
                                            kioskPackageName, exempt,
                                            mBgExecutor,
                                            new OutcomeReceiver<>() {
                                                @Override
                                                public void onResult(Void unused) {
                                                    completer.set(true);
                                                }

                                                @Override
                                                public void onError(Exception error) {
                                                    LogUtil.e(TAG,
                                                            "Cannot set exempt from hibernation",
                                                            error);
                                                    // Also returns true here to make sure state
                                                    // transition success.
                                                    completer.set(true);
                                                }
                                            });
                                    // Used only for debugging.
                                    return "setExemptFromHibernationFuture";
                                }), MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> getExemptFromBatteryUsageRestrictionFuture(boolean exempt) {
        return Futures.transformAsync(SetupParametersClient.getInstance().getKioskPackage(),
                kioskPackageName -> kioskPackageName == null
                        ? Futures.immediateFuture(true)
                        : CallbackToFutureAdapter.getFuture(completer -> {
                            mSystemDeviceLockManager.setExemptFromBatteryUsageRestriction(
                                    kioskPackageName, exempt, mBgExecutor,
                                    new OutcomeReceiver<>() {
                                        @Override
                                        public void onResult(Void result) {
                                            completer.set(true);
                                        }

                                        @Override
                                        public void onError(@NonNull Exception error) {
                                            LogUtil.e(TAG,
                                                    "Cannot set exempt from battery usage "
                                                            + "restrictions",
                                                    error);
                                            // Also returns true here to make sure state
                                            // transition success.
                                            completer.set(true);
                                        }
                                    });
                            // Used only for debugging.
                            return "getExemptFromBatteryUsageRestrictionFuture";
                        }), MoreExecutors.directExecutor());
    }

    private ListenableFuture<Boolean> getExemptFromAllFutures(boolean exempt) {
        final ListenableFuture<Boolean> backgroundFuture =
                getExemptFromBackgroundStartRestrictionsFuture(exempt);
        final ListenableFuture<Boolean> hibernationFuture =
                getExemptFromHibernationFuture(exempt);
        final ListenableFuture<Boolean> batteryUsageFuture =
                getExemptFromBatteryUsageRestrictionFuture(exempt);
        return Futures.whenAllSucceed(backgroundFuture, hibernationFuture, batteryUsageFuture)
                .call(() -> Futures.getDone(backgroundFuture)
                                && Futures.getDone(hibernationFuture)
                                && Futures.getDone(batteryUsageFuture),
                        MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<Boolean> onProvisioned() {
        return getExemptFromAllFutures(/* exempt= */ true);
    }

    @Override
    public ListenableFuture<Boolean> onProvisionInProgress() {
        return getExemptFromBackgroundStartRestrictionsFuture(/* exempt= */ true);
    }

    @Override
    public ListenableFuture<Boolean> onProvisionFailed() {
        return getExemptFromBackgroundStartRestrictionsFuture(/* exempt= */ false);
    }

    @Override
    public ListenableFuture<Boolean> onCleared() {
        return getExemptFromAllFutures(/* exempt= */ false);
    }

    // Due to some reason, AppOpsManager does not persist exemption after reboot, therefore we
    // need to always set them from our end.
    @Override
    public ListenableFuture<Boolean> onLocked() {
        return getExemptFromAllFutures(/* exempt= */ true);
    }

    // Due to some reason, AppOpsManager does not persist exemption after reboot, therefore we
    // need to always set them from our end.
    @Override
    public ListenableFuture<Boolean> onUnlocked() {
        ListenableFuture<Boolean> hibernationFuture =
                getExemptFromHibernationFuture(/* exempt= */ true);
        ListenableFuture<Boolean> batteryUsageRestrictionFuture =
                getExemptFromBatteryUsageRestrictionFuture(/* exempt= */ true);
        return Futures.whenAllSucceed(hibernationFuture,
                batteryUsageRestrictionFuture).call(() ->
                        Futures.getDone(hibernationFuture)
                                && Futures.getDone(batteryUsageRestrictionFuture),
                MoreExecutors.directExecutor());
    }
}
