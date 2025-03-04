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

package com.android.devicelockcontroller.provision.worker;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.ListenableWorker.Result;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.android.devicelockcontroller.FcmRegistrationTokenProvider;
import com.android.devicelockcontroller.TestDeviceLockControllerApplication;

import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.ParameterizedRobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

@RunWith(ParameterizedRobolectricTestRunner.class)
public final class GetFcmTokenWorkerTest {

    @Rule
    public final MockitoRule mMocks = MockitoJUnit.rule();
    @Mock
    private FcmRegistrationTokenProvider mFcmRegistrationTokenProvider;

    private GetFcmTokenWorker mWorker;
    private TestDeviceLockControllerApplication mContext;

    @ParameterizedRobolectricTestRunner.Parameter(0)
    public String mFcmToken;

    @ParameterizedRobolectricTestRunner.Parameter(1)
    public Result mExpectedResult;

    /** Expected input and output for work result */
    @ParameterizedRobolectricTestRunner.Parameters(name =
            "mFcmToken {0} should result in mResult {1}")
    public static List<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
                {null, Result.retry()},
                {"", Result.retry()},
                {"  ", Result.retry()},
                {"validToken", Result.success()},
        });
    }

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        mWorker = TestListenableWorkerBuilder.from(mContext, GetFcmTokenWorker.class)
                .setWorkerFactory(
                        new WorkerFactory() {
                            @Override
                            public ListenableWorker createWorker(
                                    @NonNull Context context, @NonNull String workerClassName,
                                    @NonNull WorkerParameters workerParameters) {
                                return workerClassName.equals(GetFcmTokenWorker.class.getName())
                                        ? new GetFcmTokenWorker(context, workerParameters,
                                        mFcmRegistrationTokenProvider)
                                        // Return null for other workers to use default
                                        // factory for them
                                        : null;
                            }
                        }).build();
    }

    @Test
    public void startWork_returnsExpectedResult() {
        when(mFcmRegistrationTokenProvider.getFcmRegistrationToken()).thenReturn(
                Futures.immediateFuture(mFcmToken));

        // WHEN the work starts
        final Result result = Futures.getUnchecked(mWorker.startWork());

        // THEN the work succeeds with the expected result
        assertThat(result).isEqualTo(mExpectedResult);
    }
}
