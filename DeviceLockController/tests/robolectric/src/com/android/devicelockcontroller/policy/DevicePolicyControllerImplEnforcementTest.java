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

package com.android.devicelockcontroller.policy;

import static com.android.devicelockcontroller.policy.DeviceStateController.DeviceState.UNDEFINED;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;
import com.android.devicelockcontroller.storage.GlobalParametersClient;

import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public final class DevicePolicyControllerImplEnforcementTest {
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock
    private ProvisionStateController mMockProvisionStateController;
    @Mock
    private DevicePolicyManager mMockDpm;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private PolicyHandler mMockUserRestrictionsPolicyHandler;
    @Mock
    private PolicyHandler mMockAppOpsPolicyHandler;
    @Mock
    private PolicyHandler mMockLockTaskModePolicyHandler;
    @Mock
    private PolicyHandler mMockPackagePolicyHandler;
    @Mock
    private PolicyHandler mMockRolePolicyHandler;
    @Mock
    private PolicyHandler mMockKioskKeepAlivePolicyHandler;
    @Mock
    private PolicyHandler mMockControllerKeepAlivePolicyHandler;
    @Mock
    private PolicyHandler mMockNotificationsPolicyHandler;

    private DevicePolicyController mDevicePolicyController;
    private TestDeviceLockControllerApplication mTestApp;

    private void setupPolicyHandler(PolicyHandler policyHandler) {
        when(policyHandler.onUnprovisioned()).thenReturn(Futures.immediateFuture(true));
        when(policyHandler.onProvisioned()).thenReturn(Futures.immediateFuture(true));
        when(policyHandler.onProvisionInProgress()).thenReturn(Futures.immediateFuture(true));
        when(policyHandler.onProvisionPaused()).thenReturn(Futures.immediateFuture(true));
        when(policyHandler.onProvisionFailed()).thenReturn(Futures.immediateFuture(true));
        when(policyHandler.onLocked()).thenReturn(Futures.immediateFuture(true));
        when(policyHandler.onUnlocked()).thenReturn(Futures.immediateFuture(true));
        when(policyHandler.onCleared()).thenReturn(Futures.immediateFuture(true));
    }

    @Before
    public void setUp() {
        mTestApp = ApplicationProvider.getApplicationContext();
        ExecutorService bgExecutor = Executors.newSingleThreadExecutor();

        setupPolicyHandler(mMockUserRestrictionsPolicyHandler);
        setupPolicyHandler(mMockAppOpsPolicyHandler);
        setupPolicyHandler(mMockLockTaskModePolicyHandler);
        setupPolicyHandler(mMockPackagePolicyHandler);
        setupPolicyHandler(mMockRolePolicyHandler);
        setupPolicyHandler(mMockKioskKeepAlivePolicyHandler);
        setupPolicyHandler(mMockControllerKeepAlivePolicyHandler);
        setupPolicyHandler(mMockNotificationsPolicyHandler);

        mDevicePolicyController =
                new DevicePolicyControllerImpl(mTestApp,
                        mMockDpm,
                        mMockUserManager,
                        mMockUserRestrictionsPolicyHandler,
                        mMockAppOpsPolicyHandler,
                        mMockLockTaskModePolicyHandler,
                        mMockPackagePolicyHandler,
                        mMockRolePolicyHandler,
                        mMockKioskKeepAlivePolicyHandler,
                        mMockControllerKeepAlivePolicyHandler,
                        mMockNotificationsPolicyHandler,
                        mMockProvisionStateController,
                        bgExecutor);
    }

    @Test
    public void enforceCurrentPolicies_withProvisionStateUnprovisioned_shouldCallOnUnprovisioned()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionStateController.ProvisionState.UNPROVISIONED));
        GlobalParametersClient.getInstance().setDeviceState(UNDEFINED).get();

        mDevicePolicyController.enforceCurrentPolicies().get();

        verify(mMockUserRestrictionsPolicyHandler).onUnprovisioned();
        verifyNoMoreInteractions(mMockUserRestrictionsPolicyHandler);

        verify(mMockAppOpsPolicyHandler).onUnprovisioned();
        verifyNoMoreInteractions(mMockAppOpsPolicyHandler);

        verify(mMockLockTaskModePolicyHandler).onUnprovisioned();
        verifyNoMoreInteractions(mMockLockTaskModePolicyHandler);

        verify(mMockPackagePolicyHandler).onUnprovisioned();
        verifyNoMoreInteractions(mMockPackagePolicyHandler);

        verify(mMockRolePolicyHandler).onUnprovisioned();
        verifyNoMoreInteractions(mMockRolePolicyHandler);

        verify(mMockKioskKeepAlivePolicyHandler).onUnprovisioned();
        verifyNoMoreInteractions(mMockKioskKeepAlivePolicyHandler);

        verify(mMockControllerKeepAlivePolicyHandler).onUnprovisioned();
        verifyNoMoreInteractions(mMockControllerKeepAlivePolicyHandler);

        verify(mMockNotificationsPolicyHandler).onUnprovisioned();
        verifyNoMoreInteractions(mMockNotificationsPolicyHandler);
    }

    @Test
    public void enforceCurrentPolicies_withProvisionStateProgress_shouldCallOnProvisionInProgress()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionStateController.ProvisionState.PROVISION_IN_PROGRESS));
        GlobalParametersClient.getInstance().setDeviceState(UNDEFINED).get();

        mDevicePolicyController.enforceCurrentPolicies().get();

        verify(mMockUserRestrictionsPolicyHandler).onProvisionInProgress();
        verifyNoMoreInteractions(mMockUserRestrictionsPolicyHandler);

        verify(mMockAppOpsPolicyHandler).onProvisionInProgress();
        verifyNoMoreInteractions(mMockAppOpsPolicyHandler);

        verify(mMockLockTaskModePolicyHandler).onProvisionInProgress();
        verifyNoMoreInteractions(mMockLockTaskModePolicyHandler);

        verify(mMockPackagePolicyHandler).onProvisionInProgress();
        verifyNoMoreInteractions(mMockPackagePolicyHandler);

        verify(mMockRolePolicyHandler).onProvisionInProgress();
        verifyNoMoreInteractions(mMockRolePolicyHandler);

        verify(mMockKioskKeepAlivePolicyHandler).onProvisionInProgress();
        verifyNoMoreInteractions(mMockKioskKeepAlivePolicyHandler);

        verify(mMockControllerKeepAlivePolicyHandler).onProvisionInProgress();
        verifyNoMoreInteractions(mMockControllerKeepAlivePolicyHandler);

        verify(mMockNotificationsPolicyHandler).onProvisionInProgress();
        verifyNoMoreInteractions(mMockNotificationsPolicyHandler);
    }

    @Test
    public void enforceCurrentPolicies_withProvisionStateKioskProvisioned_shouldCallOnProvisioned()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionStateController.ProvisionState.KIOSK_PROVISIONED));
        GlobalParametersClient.getInstance().setDeviceState(UNDEFINED).get();

        mDevicePolicyController.enforceCurrentPolicies().get();

        verify(mMockUserRestrictionsPolicyHandler).onProvisioned();
        verifyNoMoreInteractions(mMockUserRestrictionsPolicyHandler);

        verify(mMockAppOpsPolicyHandler).onProvisioned();
        verifyNoMoreInteractions(mMockAppOpsPolicyHandler);

        verify(mMockLockTaskModePolicyHandler).onProvisioned();
        verifyNoMoreInteractions(mMockLockTaskModePolicyHandler);

        verify(mMockPackagePolicyHandler).onProvisioned();
        verifyNoMoreInteractions(mMockPackagePolicyHandler);

        verify(mMockRolePolicyHandler).onProvisioned();
        verifyNoMoreInteractions(mMockRolePolicyHandler);

        verify(mMockKioskKeepAlivePolicyHandler).onProvisioned();
        verifyNoMoreInteractions(mMockKioskKeepAlivePolicyHandler);

        verify(mMockControllerKeepAlivePolicyHandler).onProvisioned();
        verifyNoMoreInteractions(mMockControllerKeepAlivePolicyHandler);

        verify(mMockNotificationsPolicyHandler).onProvisioned();
        verifyNoMoreInteractions(mMockNotificationsPolicyHandler);
    }

    @Test
    public void enforceCurrentPolicies_withProvisionStatePaused_shouldCallOnProvisionPaused()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionStateController.ProvisionState.PROVISION_PAUSED));
        GlobalParametersClient.getInstance().setDeviceState(UNDEFINED).get();

        mDevicePolicyController.enforceCurrentPolicies().get();

        verify(mMockUserRestrictionsPolicyHandler).onProvisionPaused();
        verifyNoMoreInteractions(mMockUserRestrictionsPolicyHandler);

        verify(mMockAppOpsPolicyHandler).onProvisionPaused();
        verifyNoMoreInteractions(mMockAppOpsPolicyHandler);

        verify(mMockLockTaskModePolicyHandler).onProvisionPaused();
        verifyNoMoreInteractions(mMockLockTaskModePolicyHandler);

        verify(mMockPackagePolicyHandler).onProvisionPaused();
        verifyNoMoreInteractions(mMockPackagePolicyHandler);

        verify(mMockRolePolicyHandler).onProvisionPaused();
        verifyNoMoreInteractions(mMockRolePolicyHandler);

        verify(mMockKioskKeepAlivePolicyHandler).onProvisionPaused();
        verifyNoMoreInteractions(mMockKioskKeepAlivePolicyHandler);

        verify(mMockControllerKeepAlivePolicyHandler).onProvisionPaused();
        verifyNoMoreInteractions(mMockControllerKeepAlivePolicyHandler);

        verify(mMockNotificationsPolicyHandler).onProvisionPaused();
        verifyNoMoreInteractions(mMockNotificationsPolicyHandler);
    }

    @Test
    public void enforceCurrentPolicies_withProvisionStateFailed_shouldCallOnProvisionFailed()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionStateController.ProvisionState.PROVISION_FAILED));
        GlobalParametersClient.getInstance().setDeviceState(UNDEFINED).get();

        mDevicePolicyController.enforceCurrentPolicies().get();

        verify(mMockUserRestrictionsPolicyHandler).onProvisionFailed();
        verifyNoMoreInteractions(mMockUserRestrictionsPolicyHandler);

        verify(mMockAppOpsPolicyHandler).onProvisionFailed();
        verifyNoMoreInteractions(mMockAppOpsPolicyHandler);

        verify(mMockLockTaskModePolicyHandler).onProvisionFailed();
        verifyNoMoreInteractions(mMockLockTaskModePolicyHandler);

        verify(mMockPackagePolicyHandler).onProvisionFailed();
        verifyNoMoreInteractions(mMockPackagePolicyHandler);

        verify(mMockRolePolicyHandler).onProvisionFailed();
        verifyNoMoreInteractions(mMockRolePolicyHandler);

        verify(mMockKioskKeepAlivePolicyHandler).onProvisionFailed();
        verifyNoMoreInteractions(mMockKioskKeepAlivePolicyHandler);

        verify(mMockControllerKeepAlivePolicyHandler).onProvisionFailed();
        verifyNoMoreInteractions(mMockControllerKeepAlivePolicyHandler);

        verify(mMockNotificationsPolicyHandler).onProvisionFailed();
        verifyNoMoreInteractions(mMockNotificationsPolicyHandler);
    }

    @Test
    public void enforceCurrentPolicies_provisionSucceeded_deviceUnlocked_shouldCallOnUnlocked()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionStateController.ProvisionState.PROVISION_SUCCEEDED));
        GlobalParametersClient.getInstance().setDeviceState(
                DeviceStateController.DeviceState.UNLOCKED).get();

        mDevicePolicyController.enforceCurrentPolicies().get();

        verify(mMockUserRestrictionsPolicyHandler).onUnlocked();
        verifyNoMoreInteractions(mMockUserRestrictionsPolicyHandler);

        verify(mMockAppOpsPolicyHandler).onUnlocked();
        verifyNoMoreInteractions(mMockAppOpsPolicyHandler);

        verify(mMockLockTaskModePolicyHandler).onUnlocked();
        verifyNoMoreInteractions(mMockLockTaskModePolicyHandler);

        verify(mMockPackagePolicyHandler).onUnlocked();
        verifyNoMoreInteractions(mMockPackagePolicyHandler);

        verify(mMockRolePolicyHandler).onUnlocked();
        verifyNoMoreInteractions(mMockRolePolicyHandler);

        verify(mMockKioskKeepAlivePolicyHandler).onUnlocked();
        verifyNoMoreInteractions(mMockKioskKeepAlivePolicyHandler);

        verify(mMockControllerKeepAlivePolicyHandler).onUnlocked();
        verifyNoMoreInteractions(mMockControllerKeepAlivePolicyHandler);

        verify(mMockNotificationsPolicyHandler).onUnlocked();
        verifyNoMoreInteractions(mMockNotificationsPolicyHandler);
    }

    @Test
    public void enforceCurrentPolicies_provisionSucceeded_deviceLocked_shouldCallOnLocked()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionStateController.ProvisionState.PROVISION_SUCCEEDED));
        GlobalParametersClient.getInstance().setDeviceState(
                DeviceStateController.DeviceState.LOCKED).get();

        mDevicePolicyController.enforceCurrentPolicies().get();

        verify(mMockUserRestrictionsPolicyHandler).onLocked();
        verifyNoMoreInteractions(mMockUserRestrictionsPolicyHandler);

        verify(mMockAppOpsPolicyHandler).onLocked();
        verifyNoMoreInteractions(mMockAppOpsPolicyHandler);

        verify(mMockLockTaskModePolicyHandler).onLocked();
        verifyNoMoreInteractions(mMockLockTaskModePolicyHandler);

        verify(mMockPackagePolicyHandler).onLocked();
        verifyNoMoreInteractions(mMockPackagePolicyHandler);

        verify(mMockRolePolicyHandler).onLocked();
        verifyNoMoreInteractions(mMockRolePolicyHandler);

        verify(mMockKioskKeepAlivePolicyHandler).onLocked();
        verifyNoMoreInteractions(mMockKioskKeepAlivePolicyHandler);

        verify(mMockControllerKeepAlivePolicyHandler).onLocked();
        verifyNoMoreInteractions(mMockControllerKeepAlivePolicyHandler);

        verify(mMockNotificationsPolicyHandler).onLocked();
        verifyNoMoreInteractions(mMockNotificationsPolicyHandler);
    }

    @Test
    public void enforceCurrentPolicies_provisionSucceeded_deviceStateUndefined_shouldDoNothing()
            throws ExecutionException, InterruptedException {
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionStateController.ProvisionState.PROVISION_SUCCEEDED));
        GlobalParametersClient.getInstance().setDeviceState(UNDEFINED).get();

        mDevicePolicyController.enforceCurrentPolicies().get();

        verifyNoInteractions(mMockUserRestrictionsPolicyHandler);

        verifyNoInteractions(mMockAppOpsPolicyHandler);

        verifyNoInteractions(mMockLockTaskModePolicyHandler);

        verifyNoInteractions(mMockPackagePolicyHandler);

        verifyNoInteractions(mMockRolePolicyHandler);

        verifyNoInteractions(mMockKioskKeepAlivePolicyHandler);

        verifyNoInteractions(mMockControllerKeepAlivePolicyHandler);

        verifyNoInteractions(mMockNotificationsPolicyHandler);
    }

    @Test
    public void enforceCurrentPolicies_withDeviceStateCleared_shouldCallOnCleared()
            throws ExecutionException, InterruptedException {
        // Provision state can be anything.
        when(mMockProvisionStateController.getState()).thenReturn(Futures.immediateFuture(
                ProvisionStateController.ProvisionState.UNPROVISIONED));
        GlobalParametersClient.getInstance().setDeviceState(
                DeviceStateController.DeviceState.CLEARED).get();

        mDevicePolicyController.enforceCurrentPolicies().get();

        verify(mMockUserRestrictionsPolicyHandler).onCleared();
        verifyNoMoreInteractions(mMockUserRestrictionsPolicyHandler);

        verify(mMockAppOpsPolicyHandler).onCleared();
        verifyNoMoreInteractions(mMockAppOpsPolicyHandler);

        verify(mMockLockTaskModePolicyHandler).onCleared();
        verifyNoMoreInteractions(mMockLockTaskModePolicyHandler);

        verify(mMockPackagePolicyHandler).onCleared();
        verifyNoMoreInteractions(mMockPackagePolicyHandler);

        verify(mMockRolePolicyHandler).onCleared();
        verifyNoMoreInteractions(mMockRolePolicyHandler);

        verify(mMockKioskKeepAlivePolicyHandler).onCleared();
        verifyNoMoreInteractions(mMockKioskKeepAlivePolicyHandler);

        verify(mMockControllerKeepAlivePolicyHandler).onCleared();
        verifyNoMoreInteractions(mMockControllerKeepAlivePolicyHandler);

        verify(mMockNotificationsPolicyHandler).onCleared();
        verifyNoMoreInteractions(mMockNotificationsPolicyHandler);
    }
}
