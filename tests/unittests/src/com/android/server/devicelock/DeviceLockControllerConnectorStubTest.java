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

package com.android.server.devicelock;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.OutcomeReceiver;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.devicelock.flags.Flags;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Test for device lock controller connector stub.
 */
@RunWith(RobolectricTestRunner.class)
public final class DeviceLockControllerConnectorStubTest {
    private DeviceLockControllerConnectorStub mDeviceLockControllerConnectorStub;
    private static final int TIMEOUT_SEC = 5;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setup() throws Exception {
        mDeviceLockControllerConnectorStub = new DeviceLockControllerConnectorStub();
    }

    @Test
    public void lockDevice_withUndefinedState_shouldLockDevice()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device lock state is UNDEFINED

        // Locking the device succeeds
        lockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @Test
    public void lockDevice_withLockedState_shouldLockDevice()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device lock state is LOCKED
        lockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        // Locking the device succeeds
        lockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @Test
    public void lockDevice_withUnlockedState_shouldLockDevice()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device lock state is unlocked
        unlockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        // Locking the device succeeds
        lockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @Test
    @DisableFlags(Flags.FLAG_CLEAR_DEVICE_RESTRICTIONS)
    public void lockDevice_withClearedState_shouldThrowException()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device state is CLEARED
        clearDeviceRestrictionsAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        // Locking the device fails
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> lockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS));
        assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void unlockDevice_withUndefinedState_shouldUnlockDevice()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device state is UNDEFINED

        // Unlocking the device succeeds
        unlockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @Test
    public void unlockDevice_withLockedState_shouldUnlockDevice()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device state is LOCKED
        lockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        // Unlocking the device succeeds
        unlockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @Test
    public void unlockDevice_withUnlockedState_shouldUnlockDevice()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device state is UNLOCKED
        unlockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        // Unlocking the device succeeds
        unlockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @Test
    @DisableFlags(Flags.FLAG_CLEAR_DEVICE_RESTRICTIONS)
    public void unlockDevice_withClearedState_shouldThrowException()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device state is CLEARED
        clearDeviceRestrictionsAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        // Unlocking the device fails
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> unlockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS));
        assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void clearDeviceRestrictions_withUndefinedState_shouldClearDeviceRestrictions()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device state is UNDEFINED

        // Clearing the device succeeds
        clearDeviceRestrictionsAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @Test
    public void clearDeviceRestrictions_withLockedState_shouldClearDeviceRestrictions()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device state is LOCKED
        lockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        // Clearing the device succeeds
        clearDeviceRestrictionsAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @Test
    public void clearDeviceRestrictions_withUnlockedState_shouldClearDeviceRestrictions()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device state is UNLOCKED
        unlockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        // Clearing the device succeeds
        clearDeviceRestrictionsAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    @Test
    @DisableFlags(Flags.FLAG_CLEAR_DEVICE_RESTRICTIONS)
    public void clearDeviceRestrictions_withClearedState_shouldThrowException()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device state is CLEARED
        clearDeviceRestrictionsAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        // Clearing the device fails
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> clearDeviceRestrictionsAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS));
        assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void isDeviceLocked_withUndefinedState_shouldThrownException() {
        // Given the device state is UNDEFINED

        // Retrieving locked status fails
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> isDeviceLockedAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS));
        assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void isDeviceLocked_withLockedState_shouldReturnTrue()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device state is LOCKED
        lockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        // Device is reported locked
        assertThat(isDeviceLockedAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void isDeviceLocked_withUnlockedState_shouldReturnFalse()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device state is UNLOCKED
        unlockDeviceAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        // Device is reported not locked
        assertThat(isDeviceLockedAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS)).isFalse();
    }

    @Test
    @DisableFlags(Flags.FLAG_CLEAR_DEVICE_RESTRICTIONS)
    public void isDeviceLocked_withClearedState_shouldThrownException()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Given the device state is CLEARED
        clearDeviceRestrictionsAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);

        // Retrieving locked status fails
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> isDeviceLockedAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS));
        assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException.class);
    }

    private ListenableFuture<Void> lockDeviceAsync() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mDeviceLockControllerConnectorStub.lockDevice(
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // Used only for debugging.
                    return "lockDevice operation";
                });
    }

    private ListenableFuture<Void> unlockDeviceAsync() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mDeviceLockControllerConnectorStub.unlockDevice(
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // Used only for debugging.
                    return "unlockDevice operation";
                });
    }

    private ListenableFuture<Void> clearDeviceRestrictionsAsync() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mDeviceLockControllerConnectorStub.clearDeviceRestrictions(
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Void result) {
                                    completer.set(null);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // Used only for debugging.
                    return "clearDeviceRestriction operation";
                });
    }

    private ListenableFuture<Boolean> isDeviceLockedAsync() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mDeviceLockControllerConnectorStub.isDeviceLocked(
                            new OutcomeReceiver<>() {
                                @Override
                                public void onResult(Boolean locked) {
                                    completer.set(locked);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // Used only for debugging.
                    return "isDeviceLocked operation";
                });
    }

}
