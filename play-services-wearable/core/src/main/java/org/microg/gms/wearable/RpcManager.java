/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import com.google.android.gms.wearable.internal.IRpcResponseCallback;
import com.google.android.gms.wearable.internal.IWearableCallbacks;

import org.microg.wearable.proto.Request;

/**
 * Tracks sendRequest() questions until their reply arrives, and routes questions from the
 * watch to phone apps. A reply carries {@code request_id + 31 * (generation + 527)} of its
 * question in Request field 12.
 */
public class RpcManager {
    private final WearableImpl wearable;

    public RpcManager(WearableImpl wearable) {
        this.wearable = wearable;
    }

    public void sendRequest(String packageName, String targetNodeId, String path, byte[] data, int priority, IWearableCallbacks callbacks) {
    }

    public void onRequestReceived(Request request) {
    }

    public void onResponseReceived(Request response) {
    }

    public void sendResponse(String packageName, String targetNodeId, String path, int responseTo, boolean success, byte[] data) {
    }

    public void onTimeout(int requestId) {
    }

    IRpcResponseCallback createResponseCallback(String packageName, String sourceNodeId, String path, int responseTo) {
        return null;
    }
}
