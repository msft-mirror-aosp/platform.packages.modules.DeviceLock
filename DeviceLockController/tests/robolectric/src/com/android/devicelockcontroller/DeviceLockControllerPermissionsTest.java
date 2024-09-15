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

package com.android.devicelockcontroller;

import static com.google.common.truth.Truth.assertThat;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.content.pm.PermissionInfo;
import android.util.ArrayMap;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public final class DeviceLockControllerPermissionsTest {
    public static final String MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER =
            "com.android.devicelockcontroller.permission."
                    + "MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER";
    public static final String START_KIOSK_SETUP_ACTIVITY =
            "com.android.devicelock.permission.START_KIOSK_SETUP_ACTIVITY";
    public static final String RECEIVE_CLEAR_BROADCAST =
            "com.android.devicelock.permission.RECEIVE_CLEAR_BROADCAST";
    private TestDeviceLockControllerApplication mTestApp;
    private ArrayMap<String, PermissionInfo> mNameToDeclaredPermissionInfo;
    private List<String> mRequestedPermissions;

    @Before
    public void setUp() throws NameNotFoundException {
        mTestApp = ApplicationProvider.getApplicationContext();
        String packageName = mTestApp.getPackageName();
        PackageManager packageManager = mTestApp.getPackageManager();
        PackageInfo packageInfo =
                packageManager.getPackageInfo(packageName,
                        PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));
        mNameToDeclaredPermissionInfo = new ArrayMap<>();
        for (PermissionInfo declaredPermissionInfo: packageInfo.permissions) {
            mNameToDeclaredPermissionInfo.put(declaredPermissionInfo.name, declaredPermissionInfo);
        }
        mRequestedPermissions = Arrays.asList(packageInfo.requestedPermissions);
    }

    @Test
    public void deviceLockControllerDeclaresManageDeviceLockServiceFromControllerPermission() {
        assertThat(mNameToDeclaredPermissionInfo)
                .containsKey(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER);

        assertThat(mNameToDeclaredPermissionInfo.get(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER)
                .getProtection()).isEqualTo(PermissionInfo.PROTECTION_SIGNATURE);
    }

    @Test
    public void deviceLockControllerDeclaresStartKioskSetupActivityPermission() {
        assertThat(mNameToDeclaredPermissionInfo).containsKey(START_KIOSK_SETUP_ACTIVITY);

        assertThat(mNameToDeclaredPermissionInfo.get(START_KIOSK_SETUP_ACTIVITY)
                .getProtection()).isEqualTo(PermissionInfo.PROTECTION_SIGNATURE);
    }

    @Test
    public void deviceLockControllerDeclaresReceiveClearRestrictionsPermission() {
        assertThat(mNameToDeclaredPermissionInfo).containsKey(RECEIVE_CLEAR_BROADCAST);

        assertThat(mNameToDeclaredPermissionInfo.get(RECEIVE_CLEAR_BROADCAST)
                .getProtection()).isEqualTo(PermissionInfo.PROTECTION_SIGNATURE);
    }

    @Test
    public void deviceLockControllerRequestsManageDeviceLockServiceFromControllerPermission() {
        assertThat(mRequestedPermissions).contains(MANAGE_DEVICE_LOCK_SERVICE_FROM_CONTROLLER);
    }

    @Test
    public void deviceLockControllerRequestsStartKioskSetupActivityPermission() {
        assertThat(mRequestedPermissions).contains(START_KIOSK_SETUP_ACTIVITY);
    }

    // Controller declares but not requests this permission, since it's used between kiosk
    // and system service component.
    @Test
    public void deviceLockControllerDoesNotRequestReceiveClearRestrictionsPermission() {
        assertThat(mRequestedPermissions).doesNotContain(RECEIVE_CLEAR_BROADCAST);
    }
}
