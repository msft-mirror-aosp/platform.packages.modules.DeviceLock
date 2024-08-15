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

package com.android.server.devicelock;

import android.annotation.IntDef;
import android.os.OutcomeReceiver;

import com.android.devicelock.flags.Flags;
import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stub implementation of the connector that is used when the device has had its restrictions
 * cleared so that we don't try to bind to a disabled package.
 */
public class DeviceLockControllerConnectorStub implements DeviceLockControllerConnector {
    // Pseudo states used for CTS conformance when a device is finalized.
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DevicePseudoState.UNDEFINED,
            DevicePseudoState.LOCKED,
            DevicePseudoState.UNLOCKED,
            DevicePseudoState.CLEARED,
    })
    private @interface DevicePseudoState {
        int UNDEFINED = 0;
        int UNLOCKED = 1;
        int LOCKED = 2;
        int CLEARED = 3;
    }

    @GuardedBy("this")
    private @DevicePseudoState int mPseudoState = DevicePseudoState.UNDEFINED;

    @Override
    public void unbind() {}

    @Override
    public void lockDevice(OutcomeReceiver<Void, Exception> callback) {
        synchronized (this) {
            if (setExceptionIfDeviceIsCleared(callback)) {
                return;
            }

            mPseudoState = DevicePseudoState.LOCKED;
        }
        callback.onResult(/* result= */ null);
    }

    @Override
    public void unlockDevice(OutcomeReceiver<Void, Exception> callback) {
        synchronized (this) {
            if (setExceptionIfDeviceIsCleared(callback)) {
                return;
            }

            mPseudoState = DevicePseudoState.UNLOCKED;
        }
        callback.onResult(/* result= */ null);
    }

    @Override
    public void isDeviceLocked(OutcomeReceiver<Boolean, Exception> callback) {
        boolean isLocked;

        synchronized (this) {
            if (setExceptionIfDeviceIsCleared(callback)) {
                return;
            }

            if (mPseudoState == DevicePseudoState.UNDEFINED) {
                setException(callback, "isLocked called before setting the lock state");

                return;
            }

            isLocked = mPseudoState == DevicePseudoState.LOCKED;
        }

        callback.onResult(isLocked);
    }

    @Override
    public void getDeviceId(OutcomeReceiver<String, Exception> callback) {
        setException(callback, "No registered Device ID found");
    }

    @Override
    public void clearDeviceRestrictions(OutcomeReceiver<Void, Exception> callback) {
        synchronized (this) {
            if (setExceptionIfDeviceIsCleared(callback)) {
                return;
            }

            mPseudoState = DevicePseudoState.CLEARED;
        }
        callback.onResult(/* result= */ null);
    }

    @Override
    public void onUserSwitching(OutcomeReceiver<Void, Exception> callback) {
        // Do not throw exception as we expect this to be called
        callback.onResult(/* result= */ null);
    }

    @Override
    public void onUserUnlocked(OutcomeReceiver<Void, Exception> callback) {
        // Do not throw exception as we expect this to be called
        callback.onResult(/* result= */ null);
    }

    @Override
    public void onUserSetupCompleted(OutcomeReceiver<Void, Exception> callback) {
        // Do not throw exception as we expect this to be called
        callback.onResult(/* result= */ null);
    }

    @Override
    public void onAppCrashed(boolean isKiosk, OutcomeReceiver<Void, Exception> callback) {
        setException(callback, "Device lock controller package is disabled");
    }

    private static void setException(OutcomeReceiver<?, Exception> callback, String message) {
        callback.onError(new IllegalStateException(message));
    }

    @GuardedBy("this")
    private boolean setExceptionIfDeviceIsCleared(OutcomeReceiver<?, Exception> callback) {
        if (Flags.clearDeviceRestrictions()) {
            return false;
        }

        if (mPseudoState == DevicePseudoState.CLEARED) {
            setException(callback, "Device has been cleared!");

            return true;
        }

        return false;
    }
}
