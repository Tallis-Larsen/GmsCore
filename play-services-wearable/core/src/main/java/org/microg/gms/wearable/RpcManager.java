/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.wearable.WearableStatusCodes;
import com.google.android.gms.wearable.internal.IRpcResponseCallback;
import com.google.android.gms.wearable.internal.IWearableCallbacks;
import com.google.android.gms.wearable.internal.MessageEventParcelable;
import com.google.android.gms.wearable.internal.RpcResponse;

import org.microg.gms.common.PackageUtils;
import org.microg.wearable.proto.Request;
import org.microg.wearable.proto.RootMessage;

import java.util.HashMap;
import java.util.Map;

import okio.ByteString;

public class RpcManager {
    private static final String TAG = "GmsWearRpc";
    private static final long REQUEST_TIMEOUT = 60000;

    private final WearableImpl wearable;
    private final RpcHelper rpcHelper;
    private final Map<Integer, PendingRequest> pendingRequests = new HashMap<>();

    public RpcManager(WearableImpl wearable, RpcHelper rpcHelper) {
        this.wearable = wearable;
        this.rpcHelper = rpcHelper;
    }

    static int getRequestHash(int requestId, int generation) {
        return requestId + 31 * (generation + 527);
    }

    public void sendRequest(String packageName, String targetNodeId, String path, byte[] data, boolean highPriority, IWearableCallbacks callbacks) {
        if (!wearable.isConnected(targetNodeId)) {
            Log.d(TAG, targetNodeId + " seems not reachable");
            deliverResponse(callbacks, WearableStatusCodes.TARGET_NODE_NOT_CONNECTED, -1, new byte[0]);
            return;
        }
        RpcHelper.RpcConnectionState state = rpcHelper.useConnectionState(packageName, targetNodeId, path, highPriority);
        int requestHash = getRequestHash(state.lastRequestId, state.generation);
        PendingRequest pending = new PendingRequest(callbacks, () -> onTimeout(requestHash));
        synchronized (pendingRequests) {
            pendingRequests.put(requestHash, pending);
        }
        wearable.networkHandler.postDelayed(pending.timeout, REQUEST_TIMEOUT);
        Request request = new Request.Builder()
                .requestId(state.lastRequestId)
                .packageName(packageName)
                .signatureDigest(PackageUtils.firstSignatureDigest(wearable.getContext(), packageName))
                .targetNodeId(targetNodeId)
                .unknown5(0)
                .path(path)
                .rawData(ByteString.of(data != null ? data : new byte[0]))
                .sourceNodeId(wearable.getLocalNodeId())
                .generation(state.generation)
                .expectsResponse(true)
                .priority(highPriority ? 2 : 1)
                .build();
        if (!wearable.writeMessage(targetNodeId, new RootMessage.Builder().request18(request).build())) {
            removePending(requestHash);
            deliverResponse(callbacks, WearableStatusCodes.TARGET_NODE_NOT_CONNECTED, -1, new byte[0]);
        }
    }

    public void onRequestReceived(Request request, String peerNodeId) {
        MessageEventParcelable event = new MessageEventParcelable();
        event.requestId = getRequestHash(request.requestId, request.generation);
        event.path = request.path;
        event.data = request.rawData != null ? request.rawData.toByteArray() : null;
        event.sourceNodeId = TextUtils.isEmpty(request.sourceNodeId) ? peerNodeId : request.sourceNodeId;
        boolean highPriority = request.priority != null && request.priority == 2;
        IRpcResponseCallback callback = new IRpcResponseCallback.Stub() {
            private boolean responded;

            @Override
            public void onResponse(boolean success, byte[] data) {
                synchronized (this) {
                    if (!success) return;
                    if (responded) {
                        Log.d(TAG, "Ignoring second response for " + event.path);
                        return;
                    }
                    responded = true;
                }
                wearable.networkHandler.post(() -> sendResponse(request.packageName, event.sourceNodeId, event.path, event.requestId, highPriority, data));
            }
        };
        wearable.sendRequestReceived(request.packageName, event, callback);
    }

    public void onResponseReceived(Request response) {
        PendingRequest pending = removePending(response.responseTo);
        if (pending == null) {
            Log.d(TAG, "No pending request for response " + response.responseTo);
            return;
        }
        deliverResponse(pending.callbacks, CommonStatusCodes.SUCCESS, response.responseTo, response.rawData != null ? response.rawData.toByteArray() : new byte[0]);
    }

    private void sendResponse(String packageName, String targetNodeId, String path, int responseTo, boolean highPriority, byte[] data) {
        RpcHelper.RpcConnectionState state = rpcHelper.useConnectionState(packageName, targetNodeId, path, highPriority);
        Request response = new Request.Builder()
                .requestId(state.lastRequestId)
                .packageName(packageName)
                .signatureDigest(PackageUtils.firstSignatureDigest(wearable.getContext(), packageName))
                .targetNodeId(targetNodeId)
                .unknown5(0)
                .path(path)
                .rawData(ByteString.of(data != null ? data : new byte[0]))
                .sourceNodeId(wearable.getLocalNodeId())
                .generation(state.generation)
                .responseTo(responseTo)
                .priority(highPriority ? 2 : 1)
                .build();
        wearable.writeMessage(targetNodeId, new RootMessage.Builder().request18(response).build());
    }

    private void onTimeout(int requestHash) {
        PendingRequest pending = removePending(requestHash);
        if (pending != null) {
            Log.d(TAG, "Request " + requestHash + " timed out");
            deliverResponse(pending.callbacks, CommonStatusCodes.TIMEOUT, -1, new byte[0]);
        }
    }

    private PendingRequest removePending(int requestHash) {
        PendingRequest pending;
        synchronized (pendingRequests) {
            pending = pendingRequests.remove(requestHash);
        }
        if (pending != null) {
            wearable.networkHandler.removeCallbacks(pending.timeout);
        }
        return pending;
    }

    private static void deliverResponse(IWearableCallbacks callbacks, int statusCode, int requestId, byte[] data) {
        RpcResponse response = new RpcResponse();
        response.statusCode = statusCode;
        response.requestId = requestId;
        response.data = data;
        try {
            callbacks.onRpcResponse(response);
        } catch (RemoteException e) {
            Log.w(TAG, e);
        }
    }

    private static class PendingRequest {
        private final IWearableCallbacks callbacks;
        private final Runnable timeout;

        private PendingRequest(IWearableCallbacks callbacks, Runnable timeout) {
            this.callbacks = callbacks;
            this.timeout = timeout;
        }
    }
}
