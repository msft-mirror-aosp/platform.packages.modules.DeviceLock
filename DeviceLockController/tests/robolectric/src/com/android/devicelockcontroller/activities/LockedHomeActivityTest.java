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


import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Intent;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.TestDeviceLockControllerApplication;

import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivity;

/**
 * Tests for {@link LockedHomeActivity}.
 */
@RunWith(RobolectricTestRunner.class)
public final class LockedHomeActivityTest {

    private static final Class<? extends Activity> LOCK_TASK_ACTIVITY_CLASS =
            LandingActivity.class;

    private TestDeviceLockControllerApplication mContext;
    private LockedHomeActivity mActivity;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        when(mContext.getPolicyController().getLaunchIntentForCurrentState()).thenReturn(
                Futures.immediateFuture(new Intent(mContext, LOCK_TASK_ACTIVITY_CLASS)));

        mActivity = Robolectric.buildActivity(LockedHomeActivity.class).create().get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void startActivity_startsCurrentLockTaskIntent() {
        ShadowActivity shadowActivity = Shadows.shadowOf(mActivity);
        Intent next = shadowActivity.getNextStartedActivity();

        assertThat(next.getComponent().getClassName()).isEqualTo(
                LOCK_TASK_ACTIVITY_CLASS.getName());
    }
}
