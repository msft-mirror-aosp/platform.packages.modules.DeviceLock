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

package com.android.devicelockcontroller.provision.grpc.impl;

import static com.android.devicelockcontroller.proto.ClientProvisionFailureReason.PROVISION_FAILURE_REASON_COUNTRY_INFO_UNAVAILABLE;
import static com.android.devicelockcontroller.proto.ClientProvisionFailureReason.PROVISION_FAILURE_REASON_DEADLINE_PASSED;
import static com.android.devicelockcontroller.proto.ClientProvisionFailureReason.PROVISION_FAILURE_REASON_NOT_IN_ELIGIBLE_COUNTRY;
import static com.android.devicelockcontroller.proto.ClientProvisionFailureReason.PROVISION_FAILURE_REASON_PLAY_INSTALLATION_FAILED;
import static com.android.devicelockcontroller.proto.ClientProvisionFailureReason.PROVISION_FAILURE_REASON_PLAY_TASK_UNAVAILABLE;
import static com.android.devicelockcontroller.proto.ClientProvisionFailureReason.PROVISION_FAILURE_REASON_POLICY_ENFORCEMENT_FAILED;
import static com.android.devicelockcontroller.proto.ClientProvisionFailureReason.PROVISION_FAILURE_REASON_UNSPECIFIED;

import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.ArraySet;

import androidx.annotation.GuardedBy;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.devicelockcontroller.common.DeviceId;
import com.android.devicelockcontroller.common.DeviceLockConstants;
import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceIdType;
import com.android.devicelockcontroller.common.DeviceLockConstants.DeviceProvisionState;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason;
import com.android.devicelockcontroller.proto.ClientDeviceIdentifier;
import com.android.devicelockcontroller.proto.ClientProvisionFailureReason;
import com.android.devicelockcontroller.proto.ClientProvisionState;
import com.android.devicelockcontroller.proto.DeviceIdentifierType;
import com.android.devicelockcontroller.proto.DeviceLockCheckinServiceGrpc;
import com.android.devicelockcontroller.proto.DeviceLockCheckinServiceGrpc.DeviceLockCheckinServiceBlockingStub;
import com.android.devicelockcontroller.proto.GetDeviceCheckinStatusRequest;
import com.android.devicelockcontroller.proto.IsDeviceInApprovedCountryRequest;
import com.android.devicelockcontroller.proto.PauseDeviceProvisioningReason;
import com.android.devicelockcontroller.proto.PauseDeviceProvisioningRequest;
import com.android.devicelockcontroller.proto.ReportDeviceProvisionStateRequest;
import com.android.devicelockcontroller.proto.UpdateFcmTokenRequest;
import com.android.devicelockcontroller.provision.grpc.DeviceCheckInClient;
import com.android.devicelockcontroller.provision.grpc.GetDeviceCheckInStatusGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.IsDeviceInApprovedCountryGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.PauseDeviceProvisioningGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.ReportDeviceProvisionStateGrpcResponse;
import com.android.devicelockcontroller.provision.grpc.UpdateFcmTokenGrpcResponse;
import com.android.devicelockcontroller.util.LogUtil;
import com.android.devicelockcontroller.util.ThreadAsserts;

import com.google.common.base.Strings;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.okhttp.OkHttpChannelBuilder;

import java.util.concurrent.TimeUnit;

/**
 * A client for the {@link  com.android.devicelockcontroller.proto.DeviceLockCheckinServiceGrpc}
 * service.
 * <p>
 * gRPC calls will attempt to use a non-VPN network if the default network fails.
 *
 * TODO(b/336639719): Add unit test coverage for the VPN fallback logic
 */
@Keep
public final class DeviceCheckInClientImpl extends DeviceCheckInClient {

    private static final String TAG = DeviceCheckInClientImpl.class.getSimpleName();
    private static final long GRPC_DEADLINE_MS = 10_000L;

    private final DeviceLockCheckinServiceBlockingStub mDefaultBlockingStub;
    private final ClientInterceptor mClientInterceptor;
    private final ConnectivityManager mConnectivityManager;
    private final ChannelFactory mChannelFactory;
    private final ManagedChannel mDefaultChannel;
    private final NetworkCallback mNetworkCallback = new NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(@NonNull Network network,
                @NonNull NetworkCapabilities networkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities);
            synchronized (DeviceCheckInClientImpl.this) {
                if (networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    if (!network.equals(mNonVpnNetwork)) {
                        onNonVpnNetworkChanged(network);
                    }
                } else {
                    if (network.equals(mNonVpnNetwork)) {
                        onNonVpnNetworkChanged(null);
                    }
                }
            }
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            synchronized (DeviceCheckInClientImpl.this) {
                if (network.equals(mNonVpnNetwork)) {
                    onNonVpnNetworkChanged(null);
                }
            }
        }

        @Override
        public void onUnavailable() {
            super.onUnavailable();
            synchronized (DeviceCheckInClientImpl.this) {
                onNonVpnNetworkChanged(null);
            }
        }
    };

    @Nullable
    @GuardedBy("this")
    private Network mNonVpnNetwork;
    @Nullable
    @GuardedBy("this")
    private ManagedChannel mNonVpnChannel;
    @Nullable
    @GuardedBy("this")
    private DeviceLockCheckinServiceBlockingStub mNonVpnBlockingStub;

    public DeviceCheckInClientImpl(ClientInterceptor clientInterceptor,
            ConnectivityManager connectivityManager) {
        this(clientInterceptor, connectivityManager,
                (host, port, socketFactory) -> OkHttpChannelBuilder
                        .forAddress(host, port)
                        .socketFactory(socketFactory)
                        .build());
    }

    DeviceCheckInClientImpl(ClientInterceptor clientInterceptor,
            ConnectivityManager connectivityManager,
            ChannelFactory channelFactory) {
        mClientInterceptor = clientInterceptor;
        mConnectivityManager = connectivityManager;
        mChannelFactory = channelFactory;
        mDefaultChannel = mChannelFactory.buildChannel(sHostName, sPortNumber);
        mDefaultBlockingStub = DeviceLockCheckinServiceGrpc.newBlockingStub(mDefaultChannel)
                .withInterceptors(clientInterceptor);
        HandlerThread handlerThread = new HandlerThread("NetworkCallbackThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        connectivityManager.registerBestMatchingNetworkCallback(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        .addCapability(
                                NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                        .addCapability(
                                NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                        .addCapability(
                                NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                mNetworkCallback,
                handler);
    }

    @Override
    public GetDeviceCheckInStatusGrpcResponse getDeviceCheckInStatus(
            ArraySet<DeviceId> deviceIds, String carrierInfo,
            @Nullable String fcmRegistrationToken) {
        ThreadAsserts.assertWorkerThread("getDeviceCheckInStatus");
        GetDeviceCheckInStatusGrpcResponse response =
                getDeviceCheckInStatus(deviceIds, carrierInfo, fcmRegistrationToken,
                        mDefaultBlockingStub);
        if (response.hasRecoverableError()) {
            DeviceLockCheckinServiceBlockingStub stub;
            synchronized (this) {
                if (mNonVpnBlockingStub == null) {
                    return response;
                }
                stub = mNonVpnBlockingStub;
            }
            LogUtil.d(TAG, "Non-VPN network fallback detected. Re-attempt check-in.");
            return getDeviceCheckInStatus(deviceIds, carrierInfo, fcmRegistrationToken, stub);
        }
        return response;
    }

    private GetDeviceCheckInStatusGrpcResponse getDeviceCheckInStatus(
            ArraySet<DeviceId> deviceIds, String carrierInfo,
            @Nullable String fcmRegistrationToken,
            @NonNull DeviceLockCheckinServiceBlockingStub stub) {
        try {
            return new GetDeviceCheckInStatusGrpcResponseWrapper(
                    stub.withDeadlineAfter(GRPC_DEADLINE_MS, TimeUnit.MILLISECONDS)
                            .getDeviceCheckinStatus(createGetDeviceCheckinStatusRequest(
                                    deviceIds, carrierInfo, fcmRegistrationToken)));
        } catch (StatusRuntimeException e) {
            return new GetDeviceCheckInStatusGrpcResponseWrapper(e.getStatus());
        }
    }

    @Override
    public IsDeviceInApprovedCountryGrpcResponse isDeviceInApprovedCountry(
            @Nullable String carrierInfo) {
        ThreadAsserts.assertWorkerThread("isDeviceInApprovedCountry");
        final IsDeviceInApprovedCountryGrpcResponse response =
                isDeviceInApprovedCountry(carrierInfo, mDefaultBlockingStub);
        if (response.hasRecoverableError()) {
            DeviceLockCheckinServiceBlockingStub stub;
            synchronized (this) {
                if (mNonVpnBlockingStub == null) {
                    return response;
                }
                stub = mNonVpnBlockingStub;
            }
            LogUtil.d(TAG, "Non-VPN network fallback detected. "
                    + "Re-attempt device in approved country.");
            return isDeviceInApprovedCountry(carrierInfo, stub);
        }
        return response;
    }

    private IsDeviceInApprovedCountryGrpcResponse isDeviceInApprovedCountry(
            @Nullable String carrierInfo, @NonNull DeviceLockCheckinServiceBlockingStub stub) {
        try {
            return new IsDeviceInApprovedCountryGrpcResponseWrapper(
                    stub.withDeadlineAfter(GRPC_DEADLINE_MS, TimeUnit.MILLISECONDS)
                            .isDeviceInApprovedCountry(createIsDeviceInApprovedCountryRequest(
                                    carrierInfo, sRegisteredId)));
        } catch (StatusRuntimeException e) {
            return new IsDeviceInApprovedCountryGrpcResponseWrapper(e.getStatus());
        }
    }

    @Override
    public PauseDeviceProvisioningGrpcResponse pauseDeviceProvisioning(int reason) {
        ThreadAsserts.assertWorkerThread("pauseDeviceProvisioning");
        final PauseDeviceProvisioningGrpcResponse response =
                pauseDeviceProvisioning(reason, mDefaultBlockingStub);
        if (response.hasRecoverableError()) {
            DeviceLockCheckinServiceBlockingStub stub;
            synchronized (this) {
                if (mNonVpnBlockingStub == null) {
                    return response;
                }
                stub = mNonVpnBlockingStub;
            }
            LogUtil.d(TAG, "Non-VPN network fallback detected. Re-attempt pause provisioning.");
            return pauseDeviceProvisioning(reason, stub);
        }
        return response;
    }

    private PauseDeviceProvisioningGrpcResponse pauseDeviceProvisioning(int reason,
            @NonNull DeviceLockCheckinServiceBlockingStub stub) {
        try {
            stub.withDeadlineAfter(GRPC_DEADLINE_MS, TimeUnit.MILLISECONDS)
                    .pauseDeviceProvisioning(
                            createPauseDeviceProvisioningRequest(sRegisteredId, reason));
            return new PauseDeviceProvisioningGrpcResponse();
        } catch (StatusRuntimeException e) {
            return new PauseDeviceProvisioningGrpcResponse(e.getStatus());
        }
    }

    @Override
    public ReportDeviceProvisionStateGrpcResponse reportDeviceProvisionState(
            int lastReceivedProvisionState, boolean isSuccessful,
            @ProvisionFailureReason int reason) {
        ThreadAsserts.assertWorkerThread("reportDeviceProvisionState");
        final ReportDeviceProvisionStateGrpcResponse response = reportDeviceProvisionState(
                lastReceivedProvisionState, isSuccessful, reason, mDefaultBlockingStub);
        if (response.hasRecoverableError()) {
            DeviceLockCheckinServiceBlockingStub stub;
            synchronized (this) {
                if (mNonVpnBlockingStub == null) {
                    return response;
                }
                stub = mNonVpnBlockingStub;
            }
            LogUtil.d(TAG, "Non-VPN network fallback detected. Re-attempt report provision state.");
            return reportDeviceProvisionState(
                    lastReceivedProvisionState, isSuccessful, reason, stub);
        }
        return response;
    }

    private ReportDeviceProvisionStateGrpcResponse reportDeviceProvisionState(
            int lastReceivedProvisionState, boolean isSuccessful,
            @ProvisionFailureReason int reason,
            @NonNull DeviceLockCheckinServiceBlockingStub stub) {
        try {
            return new ReportDeviceProvisionStateGrpcResponseWrapper(
                    stub.withDeadlineAfter(GRPC_DEADLINE_MS, TimeUnit.MILLISECONDS)
                            .reportDeviceProvisionState(
                                    createReportDeviceProvisionStateRequest(
                                            lastReceivedProvisionState,
                                            isSuccessful,
                                            sRegisteredId,
                                            reason)));
        } catch (StatusRuntimeException e) {
            return new ReportDeviceProvisionStateGrpcResponseWrapper(e.getStatus());
        }
    }

    @Override
    public UpdateFcmTokenGrpcResponse updateFcmToken(ArraySet<DeviceId> deviceIds,
            @NonNull String fcmRegistrationToken) {
        ThreadAsserts.assertWorkerThread("getDeviceCheckInStatus");
        UpdateFcmTokenGrpcResponse response =
                updateFcmToken(deviceIds, fcmRegistrationToken, mDefaultBlockingStub);
        if (response.hasRecoverableError()) {
            DeviceLockCheckinServiceBlockingStub stub;
            synchronized (this) {
                if (mNonVpnBlockingStub == null) {
                    return response;
                }
                stub = mNonVpnBlockingStub;
            }
            LogUtil.d(TAG, "Non-VPN network fallback detected. Re-attempt fcm token update.");
            return updateFcmToken(deviceIds, fcmRegistrationToken, stub);
        }
        return response;
    }

    private UpdateFcmTokenGrpcResponse updateFcmToken(
            ArraySet<DeviceId> deviceIds,
            @NonNull String fcmRegistrationToken,
            @NonNull DeviceLockCheckinServiceBlockingStub stub) {
        try {
            return new UpdateFcmTokenGrpcResponseWrapper(
                    stub.withDeadlineAfter(GRPC_DEADLINE_MS, TimeUnit.MILLISECONDS)
                            .updateFcmToken(createUpdateFcmTokenRequest(
                                    deviceIds, fcmRegistrationToken)));
        } catch (StatusRuntimeException e) {
            return new UpdateFcmTokenGrpcResponseWrapper(e.getStatus());
        }
    }

    @Override
    public void cleanUp() {
        super.cleanUp();
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
        mDefaultChannel.shutdown();
        synchronized (this) {
            if (mNonVpnChannel != null) {
                mNonVpnChannel.shutdown();
            }
        }
    }

    /**
     * Called when the best non-VPN network on the device has changed in order to update the
     * non-VPN gRPC stub.
     *
     * @param network the non-VPN network. Can be null to indicate that none are available
     */
    @GuardedBy("this")
    private void onNonVpnNetworkChanged(@Nullable Network network) {
        if (mNonVpnChannel != null) {
            mNonVpnChannel.shutdown();
        }
        if (network != null) {
            mNonVpnChannel = mChannelFactory.buildChannel(
                    sHostName, sPortNumber, network.getSocketFactory());
            mNonVpnBlockingStub =
                    DeviceLockCheckinServiceGrpc.newBlockingStub(mNonVpnChannel)
                            .withInterceptors(mClientInterceptor);
        } else {
            mNonVpnChannel = null;
            mNonVpnBlockingStub = null;
        }
        mNonVpnNetwork = network;
    }

    private static GetDeviceCheckinStatusRequest createGetDeviceCheckinStatusRequest(
            ArraySet<DeviceId> deviceIds, String carrierInfo,
            @Nullable String fcmRegistrationToken) {
        GetDeviceCheckinStatusRequest.Builder builder = GetDeviceCheckinStatusRequest.newBuilder();
        for (DeviceId deviceId : deviceIds) {
            builder.addClientDeviceIdentifiers(
                    ClientDeviceIdentifier.newBuilder()
                            .setDeviceIdentifierType(
                                    convertToProtoDeviceIdType(deviceId.getType()))
                            .setDeviceIdentifier(deviceId.getId()));
        }
        builder.setCarrierMccmnc(carrierInfo);
        builder.setDeviceManufacturer(android.os.Build.MANUFACTURER);
        builder.setDeviceModel(android.os.Build.MODEL);
        builder.setDeviceInternalName(android.os.Build.DEVICE);
        if (!Strings.isNullOrEmpty(fcmRegistrationToken) && !fcmRegistrationToken.isBlank()) {
            builder.setFcmRegistrationToken(fcmRegistrationToken);
        }
        return builder.build();
    }

    private static DeviceIdentifierType convertToProtoDeviceIdType(@DeviceIdType int deviceIdType) {
        return switch (deviceIdType) {
            case DeviceIdType.DEVICE_ID_TYPE_UNSPECIFIED ->
                    DeviceIdentifierType.DEVICE_IDENTIFIER_TYPE_UNSPECIFIED;
            case DeviceIdType.DEVICE_ID_TYPE_IMEI ->
                    DeviceIdentifierType.DEVICE_IDENTIFIER_TYPE_IMEI;
            case DeviceIdType.DEVICE_ID_TYPE_MEID ->
                    DeviceIdentifierType.DEVICE_IDENTIFIER_TYPE_MEID;
            default -> throw new IllegalStateException(
                    "Unexpected DeviceId type: " + deviceIdType);
        };
    }

    private static IsDeviceInApprovedCountryRequest createIsDeviceInApprovedCountryRequest(
            String carrierInfo, String registeredId) {
        return IsDeviceInApprovedCountryRequest.newBuilder()
                .setCarrierMccmnc(carrierInfo)
                .setRegisteredDeviceIdentifier(registeredId)
                .build();
    }

    private static PauseDeviceProvisioningRequest createPauseDeviceProvisioningRequest(
            String registeredId,
            @DeviceLockConstants.PauseDeviceProvisioningReason int reason) {
        return PauseDeviceProvisioningRequest.newBuilder()
                .setRegisteredDeviceIdentifier(registeredId)
                .setPauseDeviceProvisioningReason(
                        PauseDeviceProvisioningReason.forNumber(reason))
                .build();
    }

    private static ReportDeviceProvisionStateRequest createReportDeviceProvisionStateRequest(
            @DeviceProvisionState int lastReceivedProvisionState,
            boolean isSuccessful,
            String registeredId,
            @ProvisionFailureReason int reason) {
        ClientProvisionState state;
        switch (lastReceivedProvisionState) {
            case DeviceProvisionState.PROVISION_STATE_UNSPECIFIED:
                state = ClientProvisionState.CLIENT_PROVISION_STATE_UNSPECIFIED;
                break;
            case DeviceProvisionState.PROVISION_STATE_RETRY:
                state = ClientProvisionState.CLIENT_PROVISION_STATE_RETRY;
                break;
            case DeviceProvisionState.PROVISION_STATE_DISMISSIBLE_UI:
                state = ClientProvisionState.CLIENT_PROVISION_STATE_DISMISSIBLE_UI;
                break;
            case DeviceProvisionState.PROVISION_STATE_PERSISTENT_UI:
                state = ClientProvisionState.CLIENT_PROVISION_STATE_PERSISTENT_UI;
                break;
            case DeviceProvisionState.PROVISION_STATE_FACTORY_RESET:
                state = ClientProvisionState.CLIENT_PROVISION_STATE_FACTORY_RESET;
                break;
            case DeviceProvisionState.PROVISION_STATE_SUCCESS:
                state = ClientProvisionState.CLIENT_PROVISION_STATE_SUCCESS;
                break;
            default:
                throw new IllegalStateException(
                        "Unexpected value: " + lastReceivedProvisionState);
        }
        ClientProvisionFailureReason reasonProto;
        switch (reason) {
            case ProvisionFailureReason.UNKNOWN_REASON:
                reasonProto = PROVISION_FAILURE_REASON_UNSPECIFIED;
                break;
            case ProvisionFailureReason.PLAY_TASK_UNAVAILABLE:
                reasonProto = PROVISION_FAILURE_REASON_PLAY_TASK_UNAVAILABLE;
                break;
            case ProvisionFailureReason.PLAY_INSTALLATION_FAILED:
                reasonProto = PROVISION_FAILURE_REASON_PLAY_INSTALLATION_FAILED;
                break;
            case ProvisionFailureReason.COUNTRY_INFO_UNAVAILABLE:
                reasonProto = PROVISION_FAILURE_REASON_COUNTRY_INFO_UNAVAILABLE;
                break;
            case ProvisionFailureReason.NOT_IN_ELIGIBLE_COUNTRY:
                reasonProto = PROVISION_FAILURE_REASON_NOT_IN_ELIGIBLE_COUNTRY;
                break;
            case ProvisionFailureReason.POLICY_ENFORCEMENT_FAILED:
                reasonProto = PROVISION_FAILURE_REASON_POLICY_ENFORCEMENT_FAILED;
                break;
            case ProvisionFailureReason.DEADLINE_PASSED:
                reasonProto = PROVISION_FAILURE_REASON_DEADLINE_PASSED;
                break;
            default:
                throw new IllegalArgumentException("Unexpected value: " + reason);
        }
        ReportDeviceProvisionStateRequest.Builder builder =
                ReportDeviceProvisionStateRequest.newBuilder()
                        .setPreviousClientProvisionState(state)
                        .setProvisionSuccess(isSuccessful)
                        .setRegisteredDeviceIdentifier(registeredId);
        if (!isSuccessful) {
            builder.setClientProvisionFailureReason(reasonProto);
        }
        return builder.build();
    }

    private static UpdateFcmTokenRequest createUpdateFcmTokenRequest(ArraySet<DeviceId> deviceIds,
            @NonNull String fcmRegistrationToken) {
        UpdateFcmTokenRequest.Builder builder = UpdateFcmTokenRequest.newBuilder();
        for (DeviceId deviceId : deviceIds) {
            builder.addClientDeviceIdentifiers(
                    ClientDeviceIdentifier.newBuilder()
                            .setDeviceIdentifierType(
                                    convertToProtoDeviceIdType(deviceId.getType()))
                            .setDeviceIdentifier(deviceId.getId()));
        }
        builder.setFcmRegistrationToken(fcmRegistrationToken);
        return builder.build();
    }
}
