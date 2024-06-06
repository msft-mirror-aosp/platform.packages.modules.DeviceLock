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

import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionEvent.PROVISION_READY;
import static com.android.devicelockcontroller.policy.ProvisionStateController.ProvisionState.UNPROVISIONED;

import android.content.Context;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import com.android.devicelockcontroller.policy.PolicyObjectsProvider;
import com.android.devicelockcontroller.policy.ProvisionStateController;
import com.android.devicelockcontroller.storage.GlobalParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;
import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.TimeUnit;

public final class SetupWizardCompletionTimeoutWorker extends ListenableWorker {
    private static final String TAG = "SetupWizardCompletionTimeoutWorker";

    private static final String SETUP_WIZARD_COMPLETION_TIMEOUT_WORK_NAME =
            "setup-wizard-completion-timeout";
    private static final long TIMEOUT_MINUTES = 15;

    private final ListeningExecutorService mListeningExecutorService;
    private final Context mAppContext;

    public SetupWizardCompletionTimeoutWorker(@NonNull Context appContext,
            @NonNull WorkerParameters workerParams, ListeningExecutorService executorService) {
        super(appContext, workerParams);
        mAppContext = appContext;
        mListeningExecutorService = executorService;
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        // If SUW is already finished, there's nothing left to do.
        if (isUserSetupComplete()) {
            return Futures.immediateFuture(Result.success());
        }
        // SUW did not finished in the allotted time. If the device is still unprovisioned
        // and provisioning information is ready, start the provisioning flow.

        PolicyObjectsProvider policyObjects =
                (PolicyObjectsProvider) mAppContext;
        ProvisionStateController provisionStateController =
                policyObjects.getProvisionStateController();

        return Futures.transformAsync(provisionStateController.getState(),
                state -> {
                    UserParameters.setSetupWizardTimedOut(mAppContext);

                    if (state != UNPROVISIONED) {
                        return Futures.immediateFuture(Result.success());
                    }
                    GlobalParametersClient globalParametersClient =
                            GlobalParametersClient.getInstance();
                    return Futures.transformAsync(globalParametersClient.isProvisionReady(),
                            isReady -> {
                                if (isReady) {
                                    LogUtil.i(TAG, "Starting provisioning flow since "
                                            + "SUW did not complete in " + TIMEOUT_MINUTES
                                            + " minutes");
                                    return Futures.transform(provisionStateController
                                            .setNextStateForEvent(PROVISION_READY),
                                            unused -> Result.success(), mListeningExecutorService);
                                }
                                return Futures.immediateFuture(Result.success());
                            },
                            mListeningExecutorService);
                }, mListeningExecutorService);
    }

    /**
     * Schedule a worker that starts the provisioning flow in case SetupWizard does not complete
     * in the allotted time.
     */
    public static void scheduleSetupWizardCompletionTimeoutWork(Context context) {
        OneTimeWorkRequest workRequest =
                new OneTimeWorkRequest.Builder(SetupWizardCompletionTimeoutWorker.class)
                        .setInitialDelay(TIMEOUT_MINUTES, TimeUnit.MINUTES)
                        .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(SETUP_WIZARD_COMPLETION_TIMEOUT_WORK_NAME,
                        ExistingWorkPolicy.REPLACE, workRequest);
    }

    /**
     * Cancel the worker that starts the provisioning flow if SetupWizard does not complete in
     * the allotted time.
     */
    public static void cancelSetupWizardCompletionTimeoutWork(Context context) {
        WorkManager.getInstance(context)
                .cancelUniqueWork(SETUP_WIZARD_COMPLETION_TIMEOUT_WORK_NAME);
    }

    private boolean isUserSetupComplete() {
        return Settings.Secure.getInt(
                mAppContext.getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
    }
}
