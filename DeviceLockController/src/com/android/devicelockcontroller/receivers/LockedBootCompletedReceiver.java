/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.devicelockcontroller.receivers;

import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_FAILED;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.PROVISION_PAUSED;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.UserManager;

import androidx.annotation.VisibleForTesting;

import com.android.devicelockcontroller.AbstractDeviceLockControllerScheduler;
import com.android.devicelockcontroller.DeviceLockControllerScheduler;
import com.android.devicelockcontroller.policy.PolicyObjectsInterface;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Handle {@link  Intent#ACTION_LOCKED_BOOT_COMPLETED}. This receiver runs for any user
 * (singleUser="false").
 * <p>
 * This receiver does the following:
 * 1. Enforce policies for the current device state;
 * 2. Record device boot timestamp
 * 3. Reschedule alarms if needed.
 */
public final class LockedBootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "LockedBootCompletedReceiver";
    private AbstractDeviceLockControllerScheduler mScheduler;
    private final Executor mExecutor;

    public LockedBootCompletedReceiver() {
        mExecutor = Executors.newSingleThreadExecutor();
    }

    @VisibleForTesting
    LockedBootCompletedReceiver(AbstractDeviceLockControllerScheduler scheduler,
            Executor executor) {
        mScheduler = scheduler;
        mExecutor = executor;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        LogUtil.d(TAG, "Locked Boot completed");
        if (!intent.getAction().equals(Intent.ACTION_LOCKED_BOOT_COMPLETED)) {
            return;
        }

        final boolean isUserProfile =
                context.getSystemService(UserManager.class).isProfile();
        if (isUserProfile) {
            return;
        }

        Instant bootTimeStamp = Instant.now(Clock.systemUTC()).minusMillis(
                SystemClock.elapsedRealtime());
        UserParameters.setBootTimeMillis(context, bootTimeStamp.toEpochMilli());

        ProvisionStateController stateController =
                ((PolicyObjectsInterface) context.getApplicationContext())
                        .getProvisionStateController();
        stateController.getDevicePolicyController().enforceCurrentPolicies();
        if (mScheduler == null) {
            mScheduler = new DeviceLockControllerScheduler(context, stateController);
        }
        Futures.addCallback(stateController.getState(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(@ProvisionState Integer state) {
                        if (state == PROVISION_PAUSED) {
                            mScheduler.notifyRebootWhenProvisionPaused();
                        } else if (state == PROVISION_FAILED) {
                            mScheduler.notifyRebootWhenProvisionFailed();
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);
                    }
                }, mExecutor);

        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        Objects.requireNonNull(dpm).setUserControlDisabledPackages(/* admin= */ null,
                List.of(context.getPackageName()));
    }

}
