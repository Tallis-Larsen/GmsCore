/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.os.ParcelFileDescriptor;

import com.google.android.gms.wearable.internal.IChannelStreamCallbacks;
import com.google.android.gms.wearable.internal.IWearableCallbacks;

import org.microg.wearable.proto.Request;

/**
 * Channels: two-way streams between one app on each side, carried in Request field 9.
 * Lifecycle on the wire: OPEN, OPEN_ACK, data and data acks, CLOSE.
 */
public class ChannelManager {
    private final WearableImpl wearable;

    public ChannelManager(WearableImpl wearable) {
        this.wearable = wearable;
    }

    public void openChannel(String packageName, String targetNodeId, String path, IWearableCallbacks callbacks) {
    }

    public void closeChannel(String packageName, String token, int errorCode, IWearableCallbacks callbacks) {
    }

    public void getInputStream(String packageName, String token, IChannelStreamCallbacks streamCallbacks, IWearableCallbacks callbacks) {
    }

    public void getOutputStream(String packageName, String token, IChannelStreamCallbacks streamCallbacks, IWearableCallbacks callbacks) {
    }

    public void writeInputToFd(String packageName, String token, ParcelFileDescriptor fd, IWearableCallbacks callbacks) {
    }

    public void readOutputFromFd(String packageName, String token, ParcelFileDescriptor fd, long offset, long length, IWearableCallbacks callbacks) {
    }

    public void onChannelRequest(Request request) {
    }

    public void onPeerDisconnected(String nodeId) {
    }
}
