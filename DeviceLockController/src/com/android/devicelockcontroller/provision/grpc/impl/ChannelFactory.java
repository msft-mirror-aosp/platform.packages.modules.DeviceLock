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

import androidx.annotation.Nullable;

import io.grpc.ManagedChannel;

import javax.net.SocketFactory;

/**
 * Factory that makes {@link io.grpc.ManagedChannel} that uses sockets from a provided
 * {@link javax.net.SocketFactory}.
 */
interface ChannelFactory {

    /**
     * Build channel which uses the default socket factory.
     *
     * @param host host name
     * @param port port
     * @return the newly created channel
     */
    default ManagedChannel buildChannel(String host, int port) {
        return buildChannel(host, port, null);
    }

    /**
     * Build channel which only uses sockets from the provided factory.
     *
     * @param host host name
     * @param port port
     * @param socketFactory null to use the default
     * @return the newly created channel
     */
    ManagedChannel buildChannel(String host, int port, @Nullable SocketFactory socketFactory);
}
