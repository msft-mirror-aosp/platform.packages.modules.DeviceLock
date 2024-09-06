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

package com.android.devicelockcontroller.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.android.devicelockcontroller.policy.DevicePolicyController;
import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

/**
 * Activity that handles home action when the device is locked.
 * Uses the existing intent resolution logic to trampoline to the locked activity.
 */
public final class LockedHomeActivity extends Activity {
    private static final String TAG = LockedHomeActivity.class.getSimpleName();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DevicePolicyController devicePolicyController =
                ((PolicyObjectsProvider) getApplicationContext()).getPolicyController();
        Futures.addCallback(devicePolicyController.getLaunchIntentForCurrentState(),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        if (intent != null) {
                            startActivity(intent);
                        }
                        finish();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(TAG, "Failed to acquire launch intent for current state", t);
                    }
                }, getMainExecutor());
    }
}
