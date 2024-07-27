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

import android.devicelock.ParcelableException;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests for {@link android.devicelock.ParcelableException}.
 */
@RunWith(RobolectricTestRunner.class)
public final class ParcelableExceptionTest {
    private static final String EXCEPTION_MESSAGE = "TEST_EXCEPTION_MESSAGE";

    @Test
    public void parcelableExceptionShouldReturnOriginalException() {
        Exception exception = new Exception(EXCEPTION_MESSAGE);
        ParcelableException parcelableException = new ParcelableException(exception);

        Exception cause = parcelableException.getException();

        assertThat(cause).isNotNull();
        assertThat(cause.getMessage()).isEqualTo(EXCEPTION_MESSAGE);
    }

    @Test
    public void parcelableExceptionShouldParcelAndUnparcel() {
        Parcel parcel = Parcel.obtain();
        try {
            Exception exception = new Exception(EXCEPTION_MESSAGE);
            ParcelableException inParcelable = new ParcelableException(exception);
            parcel.writeParcelable(inParcelable, 0);
            parcel.setDataPosition(0);
            ParcelableException outParcelable = parcel.readParcelable(
                    ParcelableException.class.getClassLoader(), ParcelableException.class);
            assertThat(outParcelable).isNotNull();
            assertThat(inParcelable.getException().getMessage())
                    .isEqualTo(outParcelable.getException().getMessage());
        } finally {
            parcel.recycle();
        }
    }
}
