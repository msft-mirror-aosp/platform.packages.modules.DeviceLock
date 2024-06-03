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

import static android.view.View.FOCUSABLE_AUTO;
import static android.view.View.NOT_FOCUSABLE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;


/**
 * Tests for {@link DevicePolicyGroupViewHolder}
 */
@RunWith(RobolectricTestRunner.class)
public final class DevicePolicyGroupViewHolderTest {
    private static final int MAX_ITEM_VIEWS = 4;
    private static final int TITLE_ID = R.string.locked_section_title;
    private static final int POLICY_ID = R.string.settings_notifications;
    private static final int POLICY_WITH_PROVIDER_NAME_ID = R.string.settings_allowlisted_apps;
    private static final int POLICY_WITH_PROVIDER_NAME_AND_URL_ID =
            R.string.locked_backup_and_restore_text;
    private static final int DRAWABLE_ID = R.drawable.ic_lock_outline_24px;
    private static final String TEST_PROVIDER_NAME = "Test Provider";
    private Context mContext;
    private DevicePolicyGroupViewHolder mDevicePolicyGroupViewHolder;
    private TextView mGroupTitleTextView;
    private ViewGroup mDevicePolicyItems;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();

        View itemView = LayoutInflater.from(mContext).inflate(
                R.layout.item_device_policy_group, /* root= */ null);
        mDevicePolicyGroupViewHolder = new DevicePolicyGroupViewHolder(itemView);
        mGroupTitleTextView = itemView.findViewById(R.id.text_view_device_policy_group_title);
        mDevicePolicyItems = itemView.findViewById(R.id.device_policy_items);
        for (int i = 0; i < MAX_ITEM_VIEWS; i++) {
            View devicePolicyItemView = LayoutInflater.from(mContext).inflate(
                    R.layout.item_device_policy, mDevicePolicyItems, false);
            mDevicePolicyItems.addView(devicePolicyItemView);
        }
    }

    @Test
    public void bind_bindsIdsToItemView() {
        DevicePolicyGroup policyGroup = new DevicePolicyGroup.Builder()
                .setTitleTextId(TITLE_ID)
                .addDevicePolicy(DRAWABLE_ID, POLICY_ID)
                .build();

        mDevicePolicyGroupViewHolder.bind(policyGroup, MAX_ITEM_VIEWS, TEST_PROVIDER_NAME);

        assertThat(mGroupTitleTextView.getText()).isEqualTo(mContext.getString(TITLE_ID));
        TextView childOne = (TextView) mDevicePolicyItems.getChildAt(0);
        assertThat(childOne.getText()).isEqualTo(mContext.getString(POLICY_ID));
        assertThat(Shadows.shadowOf(childOne.getCompoundDrawablesRelative()[0])
                .getCreatedFromResId()).isEqualTo(DRAWABLE_ID);
    }

    @Test
    public void bind_makesItemGroupFocusableButChildrenNotFocusable() {
        DevicePolicyGroup policyGroup = new DevicePolicyGroup.Builder()
                .setTitleTextId(TITLE_ID)
                .addDevicePolicy(DRAWABLE_ID, POLICY_ID)
                .addDevicePolicy(DRAWABLE_ID, POLICY_ID)
                .build();

        mDevicePolicyGroupViewHolder.bind(policyGroup, MAX_ITEM_VIEWS, TEST_PROVIDER_NAME);

        assertThat(mDevicePolicyItems.getFocusable()).isEqualTo(FOCUSABLE_AUTO);
        TextView childOne = (TextView) mDevicePolicyItems.getChildAt(0);
        TextView childTwo = (TextView) mDevicePolicyItems.getChildAt(1);
        assertThat(childOne.getFocusable()).isEqualTo(NOT_FOCUSABLE);
        assertThat(childTwo.getFocusable()).isEqualTo(NOT_FOCUSABLE);
    }

    @Test
    public void bind_bindsIdWithProviderNameToView() {
        DevicePolicyGroup policyGroup = new DevicePolicyGroup.Builder()
                .setTitleTextId(TITLE_ID)
                .addDevicePolicy(DRAWABLE_ID, POLICY_WITH_PROVIDER_NAME_ID)
                .build();

        mDevicePolicyGroupViewHolder.bind(policyGroup, MAX_ITEM_VIEWS, TEST_PROVIDER_NAME);

        TextView childOne = (TextView) mDevicePolicyItems.getChildAt(0);
        assertThat(childOne.getText()).isEqualTo(
                mContext.getString(POLICY_WITH_PROVIDER_NAME_ID, TEST_PROVIDER_NAME));
    }

    @Test
    public void bind_bindsIdWithProviderNameAndUrlToView() {
        DevicePolicyGroup policyGroup = new DevicePolicyGroup.Builder()
                .setTitleTextId(TITLE_ID)
                .addDevicePolicy(DRAWABLE_ID, POLICY_WITH_PROVIDER_NAME_AND_URL_ID)
                .build();

        mDevicePolicyGroupViewHolder.bind(policyGroup, MAX_ITEM_VIEWS, TEST_PROVIDER_NAME);

        TextView childOne = (TextView) mDevicePolicyItems.getChildAt(0);
        assertThat(childOne.getText().toString()).isEqualTo(
                Html.fromHtml(mContext.getString(
                        POLICY_WITH_PROVIDER_NAME_AND_URL_ID, TEST_PROVIDER_NAME),
                        Html.FROM_HTML_MODE_COMPACT).toString());
        assertThat(childOne.getMovementMethod()).isInstanceOf(LinkMovementMethod.class);
    }

    @Test
    public void bind_policyAtMaxItems_allItemViewsVisible() {
        DevicePolicyGroup.Builder policyGroupBuilder = new DevicePolicyGroup.Builder()
                .setTitleTextId(TITLE_ID);
        for (int i = 0; i < MAX_ITEM_VIEWS; i++) {
            policyGroupBuilder.addDevicePolicy(DRAWABLE_ID, POLICY_ID);
        }
        DevicePolicyGroup policyGroup = policyGroupBuilder.build();

        mDevicePolicyGroupViewHolder.bind(policyGroup, MAX_ITEM_VIEWS, TEST_PROVIDER_NAME);

        for (int i = 0; i < mDevicePolicyItems.getChildCount(); i++) {
            assertThat(mDevicePolicyItems.getChildAt(i).getVisibility()).isEqualTo(View.VISIBLE);
        }
    }

    @Test
    public void bind_policyUnderMaxItems_onlyBoundItemViewsVisible() {
        DevicePolicyGroup policyGroup = new DevicePolicyGroup.Builder()
                .setTitleTextId(TITLE_ID)
                .addDevicePolicy(DRAWABLE_ID, POLICY_ID)
                .addDevicePolicy(DRAWABLE_ID, POLICY_ID)
                .build();

        mDevicePolicyGroupViewHolder.bind(policyGroup, MAX_ITEM_VIEWS, TEST_PROVIDER_NAME);

        assertThat(mDevicePolicyItems.getChildAt(0).getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mDevicePolicyItems.getChildAt(1).getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mDevicePolicyItems.getChildAt(2).getVisibility()).isEqualTo(View.GONE);
        assertThat(mDevicePolicyItems.getChildAt(3).getVisibility()).isEqualTo(View.GONE);
    }
}
