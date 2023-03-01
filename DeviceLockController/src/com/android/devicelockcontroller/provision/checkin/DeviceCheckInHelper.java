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

package com.android.devicelockcontroller.provision.checkin;

import static com.android.devicelockcontroller.common.DeviceLockConstants.DEVICE_ID_TYPE_IMEI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DEVICE_ID_TYPE_MEID;
import static com.android.devicelockcontroller.common.DeviceLockConstants.TOTAL_DEVICE_ID_TYPES;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Pair;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusResponseWrapper;
import com.android.devicelockcontroller.setup.UserPreferences;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.protobuf.Timestamp;

import java.time.Duration;
import java.time.Instant;

/**
 * Helper class to perform the device check in process with device lock backend server
 */
public final class DeviceCheckInHelper {
    @VisibleForTesting
    public static final String CHECK_IN_WORK_NAME = "checkIn";
    private static final String TAG = "DeviceCheckInHelper";
    private static final int CHECK_IN_INTERVAL_DAYS = 1;
    private final Context mAppContext;
    private final TelephonyManager mTelephonyManager;

    public DeviceCheckInHelper(Context appContext) {
        mAppContext = appContext;
        mTelephonyManager = mAppContext.getSystemService(TelephonyManager.class);
    }

    /**
     * Enqueue the DeviceCheckIn work request to WorkManager
     *
     * @param isExpedited If true, the work request should be expedited;
     */
    public void enqueueDeviceCheckInWork(boolean isExpedited) {
        enqueueDeviceCheckInWork(isExpedited, Duration.ZERO);
    }

    /**
     * Enqueue the DeviceCheckIn work request to WorkManager
     *
     * @param isExpedited If true, the work request should be expedited;
     * @param delay       The duration that need to be delayed before performing check-in.
     */
    public void enqueueDeviceCheckInWork(boolean isExpedited, Duration delay) {
        LogUtil.i(TAG, "enqueueDeviceCheckInWork");
        final OneTimeWorkRequest.Builder builder =
                new OneTimeWorkRequest.Builder(DeviceCheckInWorker.class)
                        .setConstraints(
                                new Constraints.Builder().setRequiredNetworkType(
                                        NetworkType.CONNECTED).build())
                        .setInitialDelay(delay)
                        .setBackoffCriteria(BackoffPolicy.LINEAR,
                                Duration.ofDays(CHECK_IN_INTERVAL_DAYS));
        if (isExpedited) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        WorkManager.getInstance(mAppContext).enqueueUniqueWork(CHECK_IN_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE, builder.build());
    }


    @NonNull
    ArraySet<Pair<Integer, String>> getDeviceUniqueIds() {
        final int deviceIdTypeBitmap = mAppContext.getResources().getInteger(
                R.integer.device_id_type_bitmap);
        if (deviceIdTypeBitmap < 0) {
            LogUtil.e(TAG, "getDeviceId: Cannot get device_id_type_bitmap");
        }

        return getDeviceAvailableUniqueIds(deviceIdTypeBitmap);
    }

    @VisibleForTesting
    ArraySet<Pair<Integer, String>> getDeviceAvailableUniqueIds(int deviceIdTypeBitmap) {

        final int totalSlotCount = mTelephonyManager.getActiveModemCount();
        final int maximumIdCount = TOTAL_DEVICE_ID_TYPES * totalSlotCount;
        final ArraySet<Pair<Integer, String>> deviceIds = new ArraySet<>(maximumIdCount);
        if (maximumIdCount == 0) return deviceIds;

        for (int i = 0; i < totalSlotCount; i++) {
            if ((deviceIdTypeBitmap & (1 << DEVICE_ID_TYPE_IMEI)) != 0) {
                final String imei = mTelephonyManager.getImei(i);

                if (imei != null) {
                    deviceIds.add(new Pair<>(DEVICE_ID_TYPE_IMEI, imei));
                }
            }

            if ((deviceIdTypeBitmap & (1 << DEVICE_ID_TYPE_MEID)) != 0) {
                final String meid = mTelephonyManager.getMeid(i);

                if (meid != null) {
                    deviceIds.add(new Pair<>(DEVICE_ID_TYPE_MEID, meid));
                }
            }
        }

        return deviceIds;
    }

    @NonNull
    String getCarrierInfo() {
        // TODO(b/267507927): Figure out if we need carrier info of all sims.
        return mTelephonyManager.getSimOperator();
    }

    boolean handleGetDeviceCheckInStatusResponse(
            GetDeviceCheckInStatusResponseWrapper response) {
        if (response == null) return false;
        LogUtil.d(TAG, "checkin succeed: " + response);
        switch (response.getDeviceCheckInStatus()) {
            case CLIENT_CHECKIN_STATUS_READY_FOR_PROVISION:
                //TODO: Handle provision configs
                return true;
            case CLIENT_CHECKIN_STATUS_RETRY_CHECKIN:
                GetDeviceCheckInStatusResponseWrapper.NextStepInformation nextStep =
                        response.getNextStepInformation();
                if (!nextStep.isNextCheckInInformationAvailable()) {
                    LogUtil.w(TAG, "Received retry response with out next check-in information");
                    return false;
                }
                Timestamp nextCheckinTime =
                        nextStep.getNextCheckInInformation().getNextCheckinTimestamp();

                final Duration delay = Duration.between(Instant.now(),
                        Instant.ofEpochSecond(
                                nextCheckinTime.getSeconds(),
                                nextCheckinTime.getNanos()));
                //TODO: Figure out whether there should be a minimum delay?
                if (delay.isNegative()) {
                    LogUtil.w(TAG, "Next check in date is not in the future");
                    return false;
                }
                enqueueDeviceCheckInWork(false, delay);
                return true;
            case CLIENT_CHECKIN_STATUS_STOP_CHECKIN:
                UserPreferences.setNeedCheckIn(mAppContext, false);
                return true;
        }
        return false;
    }
}
