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

package com.android.devicelockcontroller.provision.grpc.impl;

import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;

import static com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState.PROVISION_STATE_UNSPECIFIED;
import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.UNKNOWN_REASON;
import static com.android.devicelockcontroller.common.DeviceLockConstants.USER_DEFERRED_DEVICE_PROVISIONING;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.ArraySet;

import androidx.test.core.app.ApplicationProvider;

import com.android.devicelockcontroller.proto.DeviceLockCheckinServiceGrpc;
import com.android.devicelockcontroller.proto.GetDeviceCheckinStatusRequest;
import com.android.devicelockcontroller.proto.GetDeviceCheckinStatusResponse;
import com.android.devicelockcontroller.proto.IsDeviceInApprovedCountryRequest;
import com.android.devicelockcontroller.proto.IsDeviceInApprovedCountryResponse;
import com.android.devicelockcontroller.proto.PauseDeviceProvisioningRequest;
import com.android.devicelockcontroller.proto.PauseDeviceProvisioningResponse;
import com.android.devicelockcontroller.proto.ReportDeviceProvisionStateRequest;
import com.android.devicelockcontroller.proto.ReportDeviceProvisionStateResponse;
import com.android.devicelockcontroller.proto.UpdateFcmTokenRequest;
import com.android.devicelockcontroller.proto.UpdateFcmTokenResponse;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.IsDeviceInApprovedCountryGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.PauseDeviceProvisioningGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionStateGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.UpdateFcmTokenGrpcResponse;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for {@link DeviceCheckInClientImpl}.
 */
@RunWith(RobolectricTestRunner.class)
public final  class DeviceCheckinClientImplTest {

    private static final String TEST_CARRIER_INFO = "1234567890";
    private static final String TEST_HOST_NAME = "test.host.name";
    private static final int TEST_PORT_NUMBER = 7777;
    private static final String TEST_REGISTERED_ID = "1234567890";
    private static final String TEST_FCM_TOKEN = "token";
    private static final int NON_VPN_NET_ID = 10;

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule
    public final GrpcCleanupRule mGrpcCleanup = new GrpcCleanupRule();

    private final ExecutorService mBgExecutor = Executors.newSingleThreadExecutor();
    private final List<ManagedChannel> mCreatedChannels = new ArrayList<>();

    private Context mContext;
    private ShadowConnectivityManager mShadowConnectivityManager;
    private Network mNonVpnNetwork;
    // Use different server names to mimic different network connections
    private String mDefaultNetworkServerName;
    private String mNonVpnServerName;
    private DeviceCheckInClientImpl mDeviceCheckInClientImpl;

    private String mReceivedFcmToken;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();

        mDefaultNetworkServerName = InProcessServerBuilder.generateName();
        mNonVpnServerName = InProcessServerBuilder.generateName();

        ConnectivityManager connectivityManager = mContext.getSystemService(
                ConnectivityManager.class);
        mShadowConnectivityManager = Shadows.shadowOf(connectivityManager);
        mNonVpnNetwork = ShadowNetwork.newInstance(NON_VPN_NET_ID);

        final ClientInterceptor clientInterceptor = new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions,
                    Channel next) {
                return next.newCall(method, callOptions);
            }
        };

        // Call this to set static field dependencies
        DeviceCheckInClient.getInstance(mContext, TEST_HOST_NAME, TEST_PORT_NUMBER,
                clientInterceptor, TEST_REGISTERED_ID);

        mDeviceCheckInClientImpl = new DeviceCheckInClientImpl(
                clientInterceptor,
                connectivityManager,
                (host, port, socketFactory) -> {
                    final String serverName = (socketFactory == mNonVpnNetwork.getSocketFactory())
                            ? mNonVpnServerName : mDefaultNetworkServerName;
                    ManagedChannel newChannel =
                            InProcessChannelBuilder.forName(serverName).directExecutor().build();
                    mCreatedChannels.add(newChannel);
                    return mGrpcCleanup.register(newChannel);
                });
    }

    @Test
    public void getCheckInStatus_succeeds() throws Exception {
        // GIVEN the service succeeds through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeSucceedingService())
                .build()
                .start());

        // WHEN we ask for the check in status
        AtomicReference<GetDeviceCheckInStatusGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                mDeviceCheckInClientImpl.getDeviceCheckInStatus(
                        new ArraySet<>(), TEST_CARRIER_INFO, TEST_FCM_TOKEN)))
                .get();

        // THEN the response is successful
        assertThat(response.get().isSuccessful()).isTrue();
        assertThat(mReceivedFcmToken).isEqualTo(TEST_FCM_TOKEN);
    }

    @Test
    public void getCheckInStatus_noFcmToken_succeeds() throws Exception {
        // GIVEN the service succeeds through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeSucceedingService())
                .build()
                .start());

        // WHEN we ask for the check in status without an FCM token
        AtomicReference<GetDeviceCheckInStatusGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                        mDeviceCheckInClientImpl.getDeviceCheckInStatus(
                                new ArraySet<>(), TEST_CARRIER_INFO,
                                /* fcmRegistrationToken= */ null)))
                .get();

        // THEN the response is successful
        assertThat(response.get().isSuccessful()).isTrue();
        assertThat(mReceivedFcmToken).isEmpty();
    }

    @Test
    public void getCheckInStatus_emptyFcmToken_succeeds() throws Exception {
        // GIVEN the service succeeds through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeSucceedingService())
                .build()
                .start());

        // WHEN we ask for the check in status with an empty FCM token
        AtomicReference<GetDeviceCheckInStatusGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                        mDeviceCheckInClientImpl.getDeviceCheckInStatus(
                                new ArraySet<>(), TEST_CARRIER_INFO, "")))
                .get();

        // THEN the response is successful
        assertThat(response.get().isSuccessful()).isTrue();
        assertThat(mReceivedFcmToken).isEmpty();
    }

    @Test
    public void getCheckInStatus_blankFcmToken_succeeds() throws Exception {
        // GIVEN the service succeeds through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeSucceedingService())
                .build()
                .start());

        // WHEN we ask for the check in status with a blank FCM token
        AtomicReference<GetDeviceCheckInStatusGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                        mDeviceCheckInClientImpl.getDeviceCheckInStatus(
                                new ArraySet<>(), TEST_CARRIER_INFO, "   ")))
                .get();

        // THEN the response is successful
        assertThat(response.get().isSuccessful()).isTrue();
        assertThat(mReceivedFcmToken).isEmpty();
    }

    @Test
    public void getCheckInStatus_noDefaultConnectivity_fallsBackToNonVpn() throws Exception {
        // GIVEN a non-VPN network is connected with connectivity
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            NetworkCapabilities capabilities =
                    Shadows.shadowOf(new NetworkCapabilities()).addCapability(
                            NET_CAPABILITY_VALIDATED);
            callback.onCapabilitiesChanged(mNonVpnNetwork, capabilities);
        }

        // GIVEN the service fails through the default network and succeeds through the non-VPN
        // network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mNonVpnServerName)
                .directExecutor()
                .addService(makeSucceedingService())
                .build()
                .start());

        // WHEN we ask for the check in status
        AtomicReference<GetDeviceCheckInStatusGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                mDeviceCheckInClientImpl.getDeviceCheckInStatus(
                        new ArraySet<>(), TEST_CARRIER_INFO, TEST_FCM_TOKEN)))
                .get();

        // THEN the response is successful
        assertThat(response.get().isSuccessful()).isTrue();
    }

    @Test
    public void getCheckInStatus_noConnectivityOrNonVpnNetwork_isNotSuccessful() throws Exception {
        // GIVEN non-VPN network connects and then loses connectivity
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            callback.onUnavailable();
        }

        // GIVEN the service fails through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());

        // WHEN we ask for the check in status
        AtomicReference<GetDeviceCheckInStatusGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                mDeviceCheckInClientImpl.getDeviceCheckInStatus(
                        new ArraySet<>(), TEST_CARRIER_INFO, TEST_FCM_TOKEN)))
                .get();

        // THEN the response is unsuccessful
        assertThat(response.get().isSuccessful()).isFalse();
        assertThat(response.get().hasRecoverableError()).isTrue();
    }

    @Test
    public void getCheckInStatus_lostNonVpnConnection_isNotSuccessful()
            throws Exception {
        // GIVEN no connectable non-VPN networks
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            NetworkCapabilities capabilities =
                    Shadows.shadowOf(new NetworkCapabilities()).addCapability(
                            NET_CAPABILITY_VALIDATED);
            callback.onCapabilitiesChanged(mNonVpnNetwork, capabilities);
            callback.onLost(mNonVpnNetwork);
        }

        // GIVEN the service fails through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());

        // WHEN we ask for the check in status
        AtomicReference<GetDeviceCheckInStatusGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                mDeviceCheckInClientImpl.getDeviceCheckInStatus(
                        new ArraySet<>(), TEST_CARRIER_INFO, TEST_FCM_TOKEN)))
                .get();

        // THEN the response is unsuccessful
        assertThat(response.get().isSuccessful()).isFalse();
        assertThat(response.get().hasRecoverableError()).isTrue();
    }

    @Test
    public void isDeviceInApprovedCountry_succeeds() throws Exception {
        // GIVEN the service succeeds through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeSucceedingService())
                .build()
                .start());

        // WHEN we check if device is in a approved country
        AtomicReference<IsDeviceInApprovedCountryGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                mDeviceCheckInClientImpl.isDeviceInApprovedCountry(TEST_CARRIER_INFO)))
                .get();

        // THEN the response is successful
        assertThat(response.get().isSuccessful()).isTrue();
    }

    @Test
    public void isDeviceInApprovedCountry_noDefaultConnectivity_fallsBackToNonVpn()
            throws Exception {
        // GIVEN a non-VPN network is connected with connectivity
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            NetworkCapabilities capabilities =
                    Shadows.shadowOf(new NetworkCapabilities()).addCapability(
                            NET_CAPABILITY_VALIDATED);
            callback.onCapabilitiesChanged(mNonVpnNetwork, capabilities);
        }

        // GIVEN the service fails through the default network and succeeds through the non-VPN
        // network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mNonVpnServerName)
                .directExecutor()
                .addService(makeSucceedingService())
                .build()
                .start());

        // WHEN we check if device is in a approved country
        AtomicReference<IsDeviceInApprovedCountryGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                        mDeviceCheckInClientImpl.isDeviceInApprovedCountry(TEST_CARRIER_INFO)))
                .get();

        // THEN the response is successful
        assertThat(response.get().isSuccessful()).isTrue();
    }

    @Test
    public void isDeviceInApprovedCountry_noConnectivityOrNonVpnNetwork_isNotSuccessful()
            throws Exception {
        // GIVEN non-VPN network connects and then loses connectivity
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            callback.onUnavailable();
        }

        // GIVEN the service fails through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());

        // WHEN we check if device is in a approved country
        AtomicReference<IsDeviceInApprovedCountryGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                        mDeviceCheckInClientImpl.isDeviceInApprovedCountry(TEST_CARRIER_INFO)))
                .get();

        // THEN the response is unsuccessful
        assertThat(response.get().isSuccessful()).isFalse();
        assertThat(response.get().hasRecoverableError()).isTrue();
    }

    @Test
    public void isDeviceInApprovedCountry_lostNonVpnConnection_isNotSuccessful()
            throws Exception {
        // GIVEN no connectable non-VPN networks
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            NetworkCapabilities capabilities =
                    Shadows.shadowOf(new NetworkCapabilities()).addCapability(
                            NET_CAPABILITY_VALIDATED);
            callback.onCapabilitiesChanged(mNonVpnNetwork, capabilities);
            callback.onLost(mNonVpnNetwork);
        }

        // GIVEN the service fails through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());

        // WHEN we check if device is in a approved country
        AtomicReference<IsDeviceInApprovedCountryGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                        mDeviceCheckInClientImpl.isDeviceInApprovedCountry(TEST_CARRIER_INFO)))
                .get();

        // THEN the response is unsuccessful
        assertThat(response.get().isSuccessful()).isFalse();
        assertThat(response.get().hasRecoverableError()).isTrue();
    }

    @Test
    public void pauseProvisioning_succeeds() throws Exception {
        // GIVEN the service succeeds through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeSucceedingService())
                .build()
                .start());

        // WHEN we pause provisioning
        AtomicReference<PauseDeviceProvisioningGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                        mDeviceCheckInClientImpl.pauseDeviceProvisioning(
                                USER_DEFERRED_DEVICE_PROVISIONING)))
                .get();

        // THEN the response is successful
        assertThat(response.get().isSuccessful()).isTrue();
    }

    @Test
    public void pauseProvisioning_noDefaultConnectivity_fallsBackToNonVpn()
            throws Exception {
        // GIVEN a non-VPN network is connected with connectivity
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            NetworkCapabilities capabilities =
                    Shadows.shadowOf(new NetworkCapabilities()).addCapability(
                            NET_CAPABILITY_VALIDATED);
            callback.onCapabilitiesChanged(mNonVpnNetwork, capabilities);
        }

        // GIVEN the service fails through the default network and succeeds through the non-VPN
        // network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mNonVpnServerName)
                .directExecutor()
                .addService(makeSucceedingService())
                .build()
                .start());

        // WHEN we pause provisioning
        AtomicReference<PauseDeviceProvisioningGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                        mDeviceCheckInClientImpl.pauseDeviceProvisioning(
                                USER_DEFERRED_DEVICE_PROVISIONING)))
                .get();

        // THEN the response is successful
        assertThat(response.get().isSuccessful()).isTrue();
    }

    @Test
    public void pauseProvisioning_noConnectivityOrNonVpnNetwork_isNotSuccessful()
            throws Exception {
        // GIVEN non-VPN network connects and then loses connectivity
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            callback.onUnavailable();
        }

        // GIVEN the service fails through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());

        // WHEN we pause provisioning
        AtomicReference<PauseDeviceProvisioningGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                        mDeviceCheckInClientImpl.pauseDeviceProvisioning(
                                USER_DEFERRED_DEVICE_PROVISIONING)))
                .get();

        // THEN the response is unsuccessful
        assertThat(response.get().isSuccessful()).isFalse();
        assertThat(response.get().hasRecoverableError()).isTrue();
    }

    @Test
    public void pauseProvisioning_lostNonVpnConnection_isNotSuccessful()
            throws Exception {
        // GIVEN no connectable non-VPN networks
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            NetworkCapabilities capabilities =
                    Shadows.shadowOf(new NetworkCapabilities()).addCapability(
                            NET_CAPABILITY_VALIDATED);
            callback.onCapabilitiesChanged(mNonVpnNetwork, capabilities);
            callback.onLost(mNonVpnNetwork);
        }

        // GIVEN the service fails through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());

        // WHEN we pause provisioning
        AtomicReference<PauseDeviceProvisioningGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                mDeviceCheckInClientImpl.pauseDeviceProvisioning(
                        USER_DEFERRED_DEVICE_PROVISIONING)))
                .get();

        // THEN the response is unsuccessful
        assertThat(response.get().isSuccessful()).isFalse();
        assertThat(response.get().hasRecoverableError()).isTrue();
    }

    @Test
    public void reportProvisioningState_succeeds() throws Exception {
        // GIVEN the service succeeds through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeSucceedingService())
                .build()
                .start());

        // WHEN we report device provisioning state
        AtomicReference<ReportDeviceProvisionStateGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                mDeviceCheckInClientImpl.reportDeviceProvisionState(
                        PROVISION_STATE_UNSPECIFIED,
                        /* isSuccessful= */ true,
                        UNKNOWN_REASON))).get();

        // THEN the response is successful
        assertThat(response.get().isSuccessful()).isTrue();
    }

    @Test
    public void reportProvisioningState_noDefaultConnectivity_fallsBackToNonVpn()
            throws Exception {
        // GIVEN a non-VPN network is connected with connectivity
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            NetworkCapabilities capabilities =
                    Shadows.shadowOf(new NetworkCapabilities()).addCapability(
                            NET_CAPABILITY_VALIDATED);
            callback.onCapabilitiesChanged(mNonVpnNetwork, capabilities);
        }

        // GIVEN the service fails through the default network and succeeds through the non-VPN
        // network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mNonVpnServerName)
                .directExecutor()
                .addService(makeSucceedingService())
                .build()
                .start());

        // WHEN we report device provisioning state
        AtomicReference<ReportDeviceProvisionStateGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                mDeviceCheckInClientImpl.reportDeviceProvisionState(
                        PROVISION_STATE_UNSPECIFIED,
                        /* isSuccessful= */ true,
                        UNKNOWN_REASON))).get();

        // THEN the response is successful
        assertThat(response.get().isSuccessful()).isTrue();
    }

    @Test
    public void reportProvisioningState_noConnectivityOrNonVpnNetwork_isNotSuccessful()
            throws Exception {
        // GIVEN non-VPN network connects and then loses connectivity
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            callback.onUnavailable();
        }

        // GIVEN the service fails through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());

        // WHEN we report device provisioning state
        AtomicReference<ReportDeviceProvisionStateGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                        mDeviceCheckInClientImpl.reportDeviceProvisionState(
                                PROVISION_STATE_UNSPECIFIED,
                                /* isSuccessful= */ true,
                                UNKNOWN_REASON))).get();

        // THEN the response is unsuccessful
        assertThat(response.get().isSuccessful()).isFalse();
        assertThat(response.get().hasRecoverableError()).isTrue();
    }

    @Test
    public void reportProvisioningState_lostNonVpnConnection_isNotSuccessful()
            throws Exception {
        // GIVEN no connectable non-VPN networks
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            NetworkCapabilities capabilities =
                    Shadows.shadowOf(new NetworkCapabilities()).addCapability(
                            NET_CAPABILITY_VALIDATED);
            callback.onCapabilitiesChanged(mNonVpnNetwork, capabilities);
            callback.onLost(mNonVpnNetwork);
        }

        // GIVEN the service fails through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());

        // WHEN we report device provisioning state
        AtomicReference<ReportDeviceProvisionStateGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                mDeviceCheckInClientImpl.reportDeviceProvisionState(
                        PROVISION_STATE_UNSPECIFIED,
                        /* isSuccessful= */ true,
                        UNKNOWN_REASON))).get();

        // THEN the response is unsuccessful
        assertThat(response.get().isSuccessful()).isFalse();
        assertThat(response.get().hasRecoverableError()).isTrue();
    }

    @Test
    public void updateFcmToken_succeeds() throws Exception {
        // GIVEN the service succeeds through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeSucceedingService())
                .build()
                .start());

        // WHEN we update FCM token
        AtomicReference<UpdateFcmTokenGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                mDeviceCheckInClientImpl.updateFcmToken(
                        new ArraySet<>(), TEST_FCM_TOKEN))).get();

        // THEN the response is successful
        assertThat(response.get().isSuccessful()).isTrue();
        assertThat(mReceivedFcmToken).isEqualTo(TEST_FCM_TOKEN);
    }

    @Test
    public void updateFcmToken_noDefaultConnectivity_fallsBackToNonVpn()
            throws Exception {
        // GIVEN a non-VPN network is connected with connectivity
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            NetworkCapabilities capabilities =
                    Shadows.shadowOf(new NetworkCapabilities()).addCapability(
                            NET_CAPABILITY_VALIDATED);
            callback.onCapabilitiesChanged(mNonVpnNetwork, capabilities);
        }

        // GIVEN the service fails through the default network and succeeds through the non-VPN
        // network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mNonVpnServerName)
                .directExecutor()
                .addService(makeSucceedingService())
                .build()
                .start());

        // WHEN we update FCM token
        AtomicReference<UpdateFcmTokenGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                mDeviceCheckInClientImpl.updateFcmToken(
                        new ArraySet<>(), TEST_FCM_TOKEN))).get();

        // THEN the response is successful
        assertThat(response.get().isSuccessful()).isTrue();
        assertThat(mReceivedFcmToken).isEqualTo(TEST_FCM_TOKEN);
    }

    @Test
    public void updateFcmToken_noConnectivityOrNonVpnNetwork_isNotSuccessful()
            throws Exception {
        // GIVEN non-VPN network connects and then loses connectivity
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            callback.onUnavailable();
        }

        // GIVEN the service fails through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());

        // WHEN we update FCM token
        AtomicReference<UpdateFcmTokenGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                mDeviceCheckInClientImpl.updateFcmToken(
                        new ArraySet<>(), TEST_FCM_TOKEN))).get();

        // THEN the response is unsuccessful
        assertThat(response.get().isSuccessful()).isFalse();
    }

    @Test
    public void updateFcmToken_lostNonVpnConnection_isNotSuccessful()
            throws Exception {
        // GIVEN no connectable non-VPN networks
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            NetworkCapabilities capabilities =
                    Shadows.shadowOf(new NetworkCapabilities()).addCapability(
                            NET_CAPABILITY_VALIDATED);
            callback.onCapabilitiesChanged(mNonVpnNetwork, capabilities);
            callback.onLost(mNonVpnNetwork);
        }

        // GIVEN the service fails through the default network
        mGrpcCleanup.register(InProcessServerBuilder
                .forName(mDefaultNetworkServerName)
                .directExecutor()
                .addService(makeFailingService())
                .build()
                .start());

        // WHEN we update FCM token
        AtomicReference<UpdateFcmTokenGrpcResponse> response = new AtomicReference<>();
        mBgExecutor.submit(() -> response.set(
                mDeviceCheckInClientImpl.updateFcmToken(
                        new ArraySet<>(), TEST_FCM_TOKEN))).get();

        // THEN the response is unsuccessful
        assertThat(response.get().isSuccessful()).isFalse();
        assertThat(response.get().hasRecoverableError()).isTrue();
    }

    @Test
    public void cleanUp_unregistersNetworkCallback() {
        // WHEN we call clean up
        mDeviceCheckInClientImpl.cleanUp();

        // THEN the callback is no longer registered
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        assertThat(networkCallbacks.isEmpty()).isTrue();
    }

    @Test
    public void cleanUp_shutsDownChannels() {
        // GIVEN non-VPN channel is active
        Set<ConnectivityManager.NetworkCallback> networkCallbacks =
                mShadowConnectivityManager.getNetworkCallbacks();
        for (ConnectivityManager.NetworkCallback callback : networkCallbacks) {
            NetworkCapabilities capabilities =
                    Shadows.shadowOf(new NetworkCapabilities()).addCapability(
                            NET_CAPABILITY_VALIDATED);
            callback.onCapabilitiesChanged(mNonVpnNetwork, capabilities);
        }

        // WHEN we call clean up
        mDeviceCheckInClientImpl.cleanUp();

        // THEN all created channels are shut down
        for (int i = 0; i < mCreatedChannels.size(); i++) {
            ManagedChannel channel = mCreatedChannels.get(i);
            assertThat(channel.isShutdown()).isTrue();
        }
    }

    private DeviceLockCheckinServiceGrpc.DeviceLockCheckinServiceImplBase makeSucceedingService() {
        return new DeviceLockCheckinServiceGrpc.DeviceLockCheckinServiceImplBase() {
            @Override
            public void getDeviceCheckinStatus(GetDeviceCheckinStatusRequest req,
                    StreamObserver<GetDeviceCheckinStatusResponse> responseObserver) {
                mReceivedFcmToken = req.getFcmRegistrationToken();
                GetDeviceCheckinStatusResponse response = GetDeviceCheckinStatusResponse
                        .newBuilder()
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }

            @Override
            public void isDeviceInApprovedCountry(IsDeviceInApprovedCountryRequest req,
                    StreamObserver<IsDeviceInApprovedCountryResponse> responseObserver) {
                IsDeviceInApprovedCountryResponse response = IsDeviceInApprovedCountryResponse
                        .newBuilder()
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }

            @Override
            public void pauseDeviceProvisioning(PauseDeviceProvisioningRequest req,
                    StreamObserver<PauseDeviceProvisioningResponse> responseObserver) {
                PauseDeviceProvisioningResponse response = PauseDeviceProvisioningResponse
                        .newBuilder()
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }

            @Override
            public void reportDeviceProvisionState(ReportDeviceProvisionStateRequest req,
                    StreamObserver<ReportDeviceProvisionStateResponse> responseObserver) {
                ReportDeviceProvisionStateResponse response = ReportDeviceProvisionStateResponse
                        .newBuilder()
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }

            @Override
            public void updateFcmToken(UpdateFcmTokenRequest req,
                    StreamObserver<UpdateFcmTokenResponse> responseObserver) {
                mReceivedFcmToken = req.getFcmRegistrationToken();
                UpdateFcmTokenResponse response = UpdateFcmTokenResponse
                        .newBuilder()
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }

    private DeviceLockCheckinServiceGrpc.DeviceLockCheckinServiceImplBase makeFailingService() {
        return new DeviceLockCheckinServiceGrpc.DeviceLockCheckinServiceImplBase() {
            @Override
            public void getDeviceCheckinStatus(GetDeviceCheckinStatusRequest req,
                    StreamObserver<GetDeviceCheckinStatusResponse> responseObserver) {
                responseObserver.onError(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));
                responseObserver.onCompleted();
            }

            @Override
            public void isDeviceInApprovedCountry(IsDeviceInApprovedCountryRequest req,
                    StreamObserver<IsDeviceInApprovedCountryResponse> responseObserver) {
                responseObserver.onError(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));
                responseObserver.onCompleted();
            }

            @Override
            public void pauseDeviceProvisioning(PauseDeviceProvisioningRequest req,
                    StreamObserver<PauseDeviceProvisioningResponse> responseObserver) {
                responseObserver.onError(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));
                responseObserver.onCompleted();
            }

            @Override
            public void reportDeviceProvisionState(ReportDeviceProvisionStateRequest req,
                    StreamObserver<ReportDeviceProvisionStateResponse> responseObserver) {
                responseObserver.onError(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));
                responseObserver.onCompleted();
            }

            @Override
            public void updateFcmToken(UpdateFcmTokenRequest req,
                    StreamObserver<UpdateFcmTokenResponse> responseObserver) {
                responseObserver.onError(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));
                responseObserver.onCompleted();
            }
        };
    }
}
