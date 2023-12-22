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

package com.android.devicelockcontroller.stats;

import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__GET_DEVICE_CHECK_IN_STATUS;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__IS_DEVICE_IN_APPROVED_COUNTRY;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__PAUSE_DEVICE_PROVISIONING;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__REPORT_DEVICE_PROVISION_STATE;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_KIOSK_APP_REQUEST_REPORTED;
import static com.android.devicelockcontroller.DevicelockStatsLog.DEVICE_LOCK_PROVISIONING_COMPLETE_REPORTED;

import com.android.devicelockcontroller.DevicelockStatsLog;

import com.android.modules.expresslog.Counter;

import java.util.concurrent.TimeUnit;

public final class StatsLoggerImpl implements StatsLogger{
    // The Telemetry Express metric ID for the counter of device reset due to failure of mandatory
    // provisioning. As defined in
    // platform/frameworks/proto_logging/stats/express/catalog/device_lock.cfg
    static final String TEX_ID_DEVICE_RESET_PROVISION_MANDATORY =
            "device_lock.value_resets_unsuccessful_provisioning_mandatory";
    // The Telemetry Express metric ID for the counter of device reset due to failure of deferred
    // provisioning. As defined in
    // platform/frameworks/proto_logging/stats/express/catalog/device_lock.cfg
    static final String TEX_ID_DEVICE_RESET_PROVISION_DEFERRED =
            "device_lock.value_resets_unsuccessful_provisioning_deferred";
    // The Telemetry Express metric ID for the counter of a successful check in request. As
    // defined in platform/frameworks/proto_logging/stats/express/catalog/device_lock.cfg
    static final String TEX_ID_SUCCESSFUL_CHECK_IN_RESPONSE_COUNT =
            "device_lock.value_successful_check_in_response_count";
    // The Telemetry Express metric ID for the counter of a successful provisioning. As
    // defined in platform/frameworks/proto_logging/stats/express/catalog/device_lock.cfg
    static final String TEX_ID_SUCCESSFUL_PROVISIONING_COUNT =
            "device_lock.value_successful_provisioning_count";
    private static final String TAG = "StatsLogger";

    @Override
    public void logGetDeviceCheckInStatus() {
        DevicelockStatsLog.write(DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__GET_DEVICE_CHECK_IN_STATUS);
    }

    @Override
    public void logPauseDeviceProvisioning() {
        DevicelockStatsLog.write(DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__PAUSE_DEVICE_PROVISIONING);
    }

    @Override
    public void logReportDeviceProvisionState() {
        DevicelockStatsLog.write(DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__REPORT_DEVICE_PROVISION_STATE);
    }

    @Override
    public void logIsDeviceInApprovedCountry() {
        DevicelockStatsLog.write(DevicelockStatsLog.DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED,
                DEVICE_LOCK_CHECK_IN_REQUEST_REPORTED__TYPE__IS_DEVICE_IN_APPROVED_COUNTRY);
    }

    @Override
    public void logKioskAppRequest(int uid) {
        DevicelockStatsLog.write(DEVICE_LOCK_KIOSK_APP_REQUEST_REPORTED, uid);
    }

    @Override
    public void logProvisioningComplete(long timeSpentInProvisioningMillis) {
        DevicelockStatsLog.write(DEVICE_LOCK_PROVISIONING_COMPLETE_REPORTED,
                TimeUnit.MILLISECONDS.toSeconds(timeSpentInProvisioningMillis));
    }

    @Override
    public void logDeviceReset(boolean isProvisioningMandatory) {
        if (isProvisioningMandatory) {
            Counter.logIncrement(TEX_ID_DEVICE_RESET_PROVISION_MANDATORY);
        } else {
            Counter.logIncrement(TEX_ID_DEVICE_RESET_PROVISION_DEFERRED);
        }
    }

    @Override
    public void logSuccessfulCheckIn() {
        Counter.logIncrement(TEX_ID_SUCCESSFUL_CHECK_IN_RESPONSE_COUNT);
    }

    @Override
    public void logSuccessfulProvisioning() {
        Counter.logIncrement(TEX_ID_SUCCESSFUL_PROVISIONING_COUNT);
    }
}
