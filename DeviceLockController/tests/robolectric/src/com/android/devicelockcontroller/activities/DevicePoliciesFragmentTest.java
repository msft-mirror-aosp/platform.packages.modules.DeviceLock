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


import static com.android.devicelockcontroller.common.DeviceLockConstants.ACTION_START_DEVICE_FINANCING_PROVISIONING;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Looper;
import android.widget.ImageView;

import com.android.devicelockcontroller.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDrawable;

/**
 * Tests for {@link DevicePoliciesFragment}
 *
 * TODO(b/332973351): Add more unit tests for different scenarios
 */
@RunWith(RobolectricTestRunner.class)
public final class DevicePoliciesFragmentTest {
    private ActivityController<EmptyTestFragmentActivity> mActivityController;
    private EmptyTestFragmentActivity mActivity;

    @Before
    public void setUp() {
        Intent intent = new Intent();
        intent.setAction(ACTION_START_DEVICE_FINANCING_PROVISIONING);
        mActivityController = Robolectric.buildActivity(EmptyTestFragmentActivity.class, intent);
        mActivity = mActivityController.get();
        mActivity.setFragment(new DevicePoliciesFragment());
        mActivityController.setup();
    }

    @Test
    public void onViewCreated_headerIconDrawableIsCorrect() {
        shadowOf(Looper.getMainLooper()).idle();

        // Check header icon
        ShadowDrawable drawable = Shadows.shadowOf(((ImageView) mActivity.findViewById(
                R.id.header_icon)).getDrawable());
        assertThat(drawable.getCreatedFromResId()).isEqualTo(
                DevicePoliciesViewModel.HEADER_DRAWABLE_ID);
    }

    @Test
    public void onViewCreated_headerIconNotImportantForAccessibility() {
        shadowOf(Looper.getMainLooper()).idle();

        ImageView imageView = mActivity.findViewById(R.id.header_icon);
        assertThat(imageView.isImportantForAccessibility()).isFalse();
    }
}
