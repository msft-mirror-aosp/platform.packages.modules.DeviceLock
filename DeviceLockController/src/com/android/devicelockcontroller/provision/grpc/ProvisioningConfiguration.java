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

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_ALLOWLIST;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_APP_PROVIDER_NAME;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_DISABLE_OUTGOING_CALLS;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_DOWNLOAD_URL;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_SETUP_ACTIVITY;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_SIGNATURE_CHECKSUM;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * A data structure class that contains information necessary to device provisioning for DeviceLock
 * program.
 */
public final class ProvisioningConfiguration {

    // The URL to download the kiosk app for non-GMS devices.
    private final String mKioskAppDownloadUrl;
    // The package name of the kiosk app, e.g. "com.foo.bar".
    private final String mKioskAppProviderName;
    // The name of the provider of the kiosk app, e.g. "Foo Bar Inc".

    private final String mKioskAppPackageName;
    // The checksum used to sign the kiosk app for verifying the validity of the kiosk app.
    private final String mKioskAppSignatureChecksum;
    // The package component of the activity of the kiosk app that the user
    // would interact when the device is locked (i.e. this activity allows the
    // user to make a payment), e.g. "com.foo.bar/com.foo.bar.MainActivity".
    private final String mKioskAppMainActivity;
    // The list of apps that a user can use when the device is locked.
    private final List<String> mKioskAppAllowlistPackages;
    // Whether the user can make phone calls when the device is locked.
    private final boolean mKioskAppEnableOutgoingCalls;

    // Whether notifications are shown to the user when the device is locked.
    private final boolean mKioskAppEnableEnableNotifications;

    // Whether installing application from unknown sources is disallowed on this device once
    // provisioned.
    private final boolean mDisallowInstallingFromUnknownSources;

    public ProvisioningConfiguration(
            String kioskAppDownloadUrl, String kioskAppProviderName,
            String kioskAppPackageName, String kioskAppSignatureChecksum,
            String kioskAppMainActivity, List<String> kioskAppAllowlistPackages,
            boolean kioskAppEnableOutgoingCalls, boolean kioskAppEnableEnableNotifications,
            boolean disallowInstallingFromUnknownSources) {
        mKioskAppDownloadUrl = kioskAppDownloadUrl;
        mKioskAppProviderName = kioskAppProviderName;
        mKioskAppPackageName = kioskAppPackageName;
        mKioskAppSignatureChecksum = kioskAppSignatureChecksum;
        mKioskAppMainActivity = kioskAppMainActivity;
        mKioskAppAllowlistPackages = kioskAppAllowlistPackages;
        mKioskAppEnableOutgoingCalls = kioskAppEnableOutgoingCalls;
        mKioskAppEnableEnableNotifications = kioskAppEnableEnableNotifications;
        mDisallowInstallingFromUnknownSources = disallowInstallingFromUnknownSources;
    }

    public Bundle toBundle() {
        final Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, mKioskAppPackageName);
        bundle.putString(EXTRA_KIOSK_DOWNLOAD_URL, mKioskAppDownloadUrl);
        bundle.putString(EXTRA_KIOSK_SIGNATURE_CHECKSUM, mKioskAppSignatureChecksum);
        bundle.putString(EXTRA_KIOSK_SETUP_ACTIVITY, mKioskAppMainActivity);
        bundle.putBoolean(EXTRA_KIOSK_DISABLE_OUTGOING_CALLS, mKioskAppEnableOutgoingCalls);
        bundle.putBoolean(EXTRA_KIOSK_ENABLE_NOTIFICATIONS_IN_LOCK_TASK_MODE,
                mKioskAppEnableEnableNotifications);
        bundle.putStringArrayList(EXTRA_KIOSK_ALLOWLIST,
                new ArrayList<>(mKioskAppAllowlistPackages));
        bundle.putString(EXTRA_KIOSK_APP_PROVIDER_NAME, mKioskAppProviderName);
        bundle.putBoolean(EXTRA_DISALLOW_INSTALLING_FROM_UNKNOWN_SOURCES,
                mDisallowInstallingFromUnknownSources);
        return bundle;
    }
}
