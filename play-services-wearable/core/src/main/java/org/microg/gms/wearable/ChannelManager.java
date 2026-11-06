/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.wearable;

import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.wearable.internal.ChannelEventParcelable;
import com.google.android.gms.wearable.internal.ChannelParcelable;
import com.google.android.gms.wearable.internal.CloseChannelResponse;
import com.google.android.gms.wearable.internal.GetChannelInputStreamResponse;
import com.google.android.gms.wearable.internal.GetChannelOutputStreamResponse;
import com.google.android.gms.wearable.internal.IChannelStreamCallbacks;
import com.google.android.gms.wearable.internal.IWearableCallbacks;
import com.google.android.gms.wearable.internal.OpenChannelResponse;

import org.microg.gms.common.PackageUtils;
import org.microg.wearable.proto.ChannelControlRequest;
import org.microg.wearable.proto.ChannelControlType;
import org.microg.wearable.proto.ChannelDataAckRequest;
import org.microg.wearable.proto.ChannelDataHeader;
import org.microg.wearable.proto.ChannelDataRequest;
import org.microg.wearable.proto.ChannelOrigin;
import org.microg.wearable.proto.ChannelRequest;
import org.microg.wearable.proto.Request;
import org.microg.wearable.proto.RootMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import okio.ByteString;

public class ChannelManager {
    private static final String TAG = "GmsWearChannel";
    private static final long OPEN_TIMEOUT = 30000;
    private static final int MAX_PAYLOAD_SIZE = 8192;

    private static final int EVENT_CLOSED = 2;
    private static final int CLOSE_REASON_DISCONNECTED = 1;
    private static final int CLOSE_REASON_REMOTE_CLOSE = 2;
    private static final int CLOSE_REASON_LOCAL_CLOSE = 3;

    private final WearableImpl wearable;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Channel> channelsByToken = new HashMap<>();
    private final Map<String, Channel> channelsByKey = new HashMap<>();
    private final Map<String, AtomicInteger> requestIds = new HashMap<>();

    public ChannelManager(WearableImpl wearable) {
        this.wearable = wearable;
    }

    public void openChannel(String packageName, String targetNodeId, String path, IWearableCallbacks callbacks) {
        if (!wearable.isConnected(targetNodeId)) {
            Log.d(TAG, "Tried to open channel to unreachable node " + targetNodeId);
            respondOpen(callbacks, CommonStatusCodes.ERROR, null);
            return;
        }
        Channel channel = new Channel();
        channel.nodeId = targetNodeId;
        channel.packageName = packageName;
        channel.signatureDigest = PackageUtils.firstSignatureDigest(wearable.getContext(), packageName);
        channel.path = path;
        channel.channelId = random.nextLong() & Long.MAX_VALUE;
        channel.token = "chl-" + targetNodeId + "-" + Long.toHexString(channel.channelId);
        channel.openCallbacks = callbacks;
        channel.openTimeout = () -> onOpenTimeout(channel);
        synchronized (this) {
            channelsByToken.put(channel.token, channel);
            channelsByKey.put(channel.key(), channel);
        }
        Log.d(TAG, "Opening channel " + channel.token + " for " + path);
        wearable.networkHandler.postDelayed(channel.openTimeout, OPEN_TIMEOUT);
        boolean sent = send(channel, new ChannelRequest.Builder().control(new ChannelControlRequest.Builder()
                .type(ChannelControlType.CHANNEL_CONTROL_OPEN)
                .channelId(channel.channelId)
                .fromChannelOpener(true)
                .packageName(packageName)
                .signatureDigest(channel.signatureDigest)
                .path(path)
                .allowOverMetered(true)
                .build()));
        if (!sent) {
            wearable.networkHandler.removeCallbacks(channel.openTimeout);
            remove(channel);
            respondOpen(callbacks, CommonStatusCodes.INTERNAL_ERROR, null);
        }
    }

    public void closeChannel(String packageName, String token, int errorCode, boolean withError, IWearableCallbacks callbacks) {
        Channel channel = getChannel(packageName, token);
        int statusCode = CommonStatusCodes.SUCCESS;
        if (channel == null) {
            statusCode = CommonStatusCodes.ERROR;
        } else {
            send(channel, new ChannelRequest.Builder().control(new ChannelControlRequest.Builder()
                    .type(ChannelControlType.CHANNEL_CONTROL_CLOSE)
                    .channelId(channel.channelId)
                    .fromChannelOpener(true)
                    .packageName(channel.packageName)
                    .signatureDigest(channel.signatureDigest)
                    .closeErrorCode(errorCode)
                    .build()));
            onClosed(channel, CLOSE_REASON_LOCAL_CLOSE, errorCode);
        }
        try {
            if (withError) {
                callbacks.onCloseChannelWithErrorResponse(new CloseChannelResponse(statusCode));
            } else {
                callbacks.onCloseChannelResponse(new CloseChannelResponse(statusCode));
            }
        } catch (RemoteException e) {
            Log.w(TAG, e);
        }
    }

    public void getInputStream(String packageName, String token, IChannelStreamCallbacks streamCallbacks, IWearableCallbacks callbacks) {
        Channel channel = getChannel(packageName, token);
        ParcelFileDescriptor[] pipe = null;
        if (channel != null) {
            synchronized (channel) {
                if (channel.inputWriter == null && !channel.closed) {
                    try {
                        pipe = ParcelFileDescriptor.createPipe();
                        channel.inputCallbacks = streamCallbacks;
                        channel.inputWriter = new Thread(new InputWriter(channel, new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])), "WearChannelIn-" + channel.token);
                        channel.inputWriter.start();
                    } catch (IOException e) {
                        Log.w(TAG, e);
                    }
                }
            }
        }
        try {
            callbacks.onGetChannelInputStreamResponse(new GetChannelInputStreamResponse(pipe != null ? CommonStatusCodes.SUCCESS : CommonStatusCodes.ERROR, pipe != null ? pipe[0] : null));
        } catch (RemoteException e) {
            Log.w(TAG, e);
        } finally {
            closeQuietly(pipe != null ? pipe[0] : null);
        }
    }

    public void getOutputStream(String packageName, String token, IChannelStreamCallbacks streamCallbacks, IWearableCallbacks callbacks) {
        Channel channel = getChannel(packageName, token);
        ParcelFileDescriptor[] pipe = null;
        if (channel != null) {
            synchronized (channel) {
                if (channel.outputReader == null && !channel.closed) {
                    try {
                        pipe = ParcelFileDescriptor.createPipe();
                        channel.outputCallbacks = streamCallbacks;
                        channel.outputStream = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]);
                        channel.outputReader = new Thread(new OutputReader(channel, channel.outputStream), "WearChannelOut-" + channel.token);
                        channel.outputReader.start();
                    } catch (IOException e) {
                        Log.w(TAG, e);
                    }
                }
            }
        }
        try {
            callbacks.onGetChannelOutputStreamResponse(new GetChannelOutputStreamResponse(pipe != null ? CommonStatusCodes.SUCCESS : CommonStatusCodes.ERROR, pipe != null ? pipe[1] : null));
        } catch (RemoteException e) {
            Log.w(TAG, e);
        } finally {
            closeQuietly(pipe != null ? pipe[1] : null);
        }
    }

    public void onChannelRequest(Request request, String peerNodeId) {
        ChannelRequest channelRequest = request.channel;
        if (channelRequest == null) return;
        if (channelRequest.minimumVersion != null && channelRequest.minimumVersion > 1) {
            Log.d(TAG, "Dropping ChannelRequest with unsupported version " + channelRequest.minimumVersion);
            return;
        }
        if (channelRequest.control != null) {
            onControl(channelRequest.control, peerNodeId);
        } else if (channelRequest.data_ != null && channelRequest.data_.header_ != null) {
            onData(channelRequest.data_, peerNodeId);
        } else if (channelRequest.dataAck != null && channelRequest.dataAck.header_ != null) {
            onDataAck(channelRequest.dataAck.header_, peerNodeId);
        } else {
            Log.d(TAG, "Unknown ChannelRequest: " + channelRequest);
        }
    }

    public void onPeerDisconnected(String nodeId) {
        for (Channel channel : getChannels()) {
            if (channel.nodeId.equals(nodeId)) {
                onClosed(channel, CLOSE_REASON_DISCONNECTED, 0);
            }
        }
    }

    private void onControl(ChannelControlRequest control, String peerNodeId) {
        Channel channel = findChannel(peerNodeId, control.channelId, control.fromChannelOpener);
        ChannelControlType type = control.type != null ? control.type : ChannelControlType.CHANNEL_CONTROL_UNKNOWN;
        switch (type) {
            case CHANNEL_CONTROL_OPEN_ACK:
                if (channel == null) {
                    Log.d(TAG, "OPEN_ACK for unknown channel " + control.channelId);
                    return;
                }
                IWearableCallbacks callbacks;
                synchronized (channel) {
                    callbacks = channel.openCallbacks;
                    channel.openCallbacks = null;
                }
                if (callbacks != null) {
                    wearable.networkHandler.removeCallbacks(channel.openTimeout);
                    Log.d(TAG, "Channel " + channel.token + " opened");
                    respondOpen(callbacks, CommonStatusCodes.SUCCESS, channel);
                }
                break;
            case CHANNEL_CONTROL_CLOSE:
                if (channel != null) {
                    Log.d(TAG, "Channel " + channel.token + " closed by peer");
                    onClosed(channel, CLOSE_REASON_REMOTE_CLOSE, control.closeErrorCode != null ? control.closeErrorCode : 0);
                }
                break;
            case CHANNEL_CONTROL_OPEN:
                Log.w(TAG, "Channels opened by the peer are not supported yet: " + control.path);
                break;
            default:
                Log.d(TAG, "Unknown channel control: " + control);
        }
    }

    private void onData(ChannelDataRequest data, String peerNodeId) {
        ChannelDataHeader header = data.header_;
        Channel channel = findChannel(peerNodeId, header.channelId, header.fromChannelOpener);
        if (channel == null) {
            Log.d(TAG, "Data for unknown channel " + header.channelId);
            return;
        }
        boolean finalMessage = data.finalMessage != null && data.finalMessage;
        synchronized (channel) {
            if (data.payload != null && data.payload.size() > 0) {
                channel.pendingInput.add(data.payload.toByteArray());
            }
            if (finalMessage) {
                channel.pendingInput.add(new byte[0]);
            }
        }
        send(channel, new ChannelRequest.Builder().dataAck(new ChannelDataAckRequest.Builder()
                .header_(new ChannelDataHeader.Builder()
                        .channelId(channel.channelId)
                        .fromChannelOpener(channel.opener)
                        .requestId(header.requestId)
                        .build())
                .finalMessage(finalMessage)
                .build()));
    }

    private void onDataAck(ChannelDataHeader header, String peerNodeId) {
        Channel channel = findChannel(peerNodeId, header.channelId, header.fromChannelOpener);
        if (channel == null || header.requestId == null) return;
        synchronized (channel) {
            channel.lastAckedRequestId = Math.max(channel.lastAckedRequestId, header.requestId);
            channel.notifyAll();
        }
    }

    private void waitForAck(Channel channel, long requestId) throws InterruptedException {
        synchronized (channel) {
            while (!channel.closed && channel.lastAckedRequestId < requestId) {
                channel.wait();
            }
        }
    }

    private long sendData(Channel channel, byte[] payload, int length, boolean finalMessage) {
        long requestId;
        synchronized (channel) {
            requestId = channel.nextDataRequestId++;
        }
        send(channel, new ChannelRequest.Builder().data_(new ChannelDataRequest.Builder()
                .header_(new ChannelDataHeader.Builder()
                        .channelId(channel.channelId)
                        .fromChannelOpener(channel.opener)
                        .requestId(requestId)
                        .build())
                .payload(ByteString.of(payload, 0, length))
                .finalMessage(finalMessage)
                .build()));
        return requestId;
    }

    private boolean send(Channel channel, ChannelRequest.Builder channelRequest) {
        AtomicInteger requestId;
        synchronized (requestIds) {
            requestId = requestIds.get(channel.nodeId);
            if (requestId == null) {
                requestId = new AtomicInteger();
                requestIds.put(channel.nodeId, requestId);
            }
        }
        Request request = new Request.Builder()
                .requestId(requestId.incrementAndGet())
                .packageName(channel.packageName)
                .signatureDigest(channel.signatureDigest)
                .targetNodeId(channel.nodeId)
                .unknown5(0)
                .path("")
                .sourceNodeId(wearable.getLocalNodeId())
                .channel(channelRequest
                        .minimumVersion(0)
                        .origin(ChannelOrigin.ORIGIN_CHANNEL_API)
                        .build())
                .generation(0)
                .priority(1)
                .build();
        return wearable.writeMessage(channel.nodeId, new RootMessage.Builder().channelRequest(request).build());
    }

    private void onOpenTimeout(Channel channel) {
        IWearableCallbacks callbacks;
        synchronized (channel) {
            callbacks = channel.openCallbacks;
            channel.openCallbacks = null;
        }
        if (callbacks != null) {
            Log.d(TAG, "Opening channel " + channel.token + " timed out");
            remove(channel);
            respondOpen(callbacks, CommonStatusCodes.TIMEOUT, null);
        }
    }

    private void onClosed(Channel channel, int closeReason, int errorCode) {
        IWearableCallbacks openCallbacks;
        IChannelStreamCallbacks inputCallbacks;
        IChannelStreamCallbacks outputCallbacks;
        InputStream outputStream;
        synchronized (channel) {
            if (channel.closed) return;
            channel.closed = true;
            channel.notifyAll();
            openCallbacks = channel.openCallbacks;
            channel.openCallbacks = null;
            inputCallbacks = channel.inputCallbacks;
            outputCallbacks = channel.outputCallbacks;
            outputStream = channel.outputStream;
            channel.pendingInput.add(new byte[0]);
        }
        remove(channel);
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException ignored) {
            }
        }
        if (openCallbacks != null) {
            wearable.networkHandler.removeCallbacks(channel.openTimeout);
            respondOpen(openCallbacks, CommonStatusCodes.ERROR, null);
            return;
        }
        for (IChannelStreamCallbacks streamCallbacks : new IChannelStreamCallbacks[]{inputCallbacks, outputCallbacks}) {
            if (streamCallbacks == null) continue;
            try {
                streamCallbacks.onChannelClosed(closeReason, errorCode);
            } catch (RemoteException e) {
                Log.w(TAG, e);
            }
        }
        ChannelEventParcelable event = new ChannelEventParcelable();
        event.channel = channel.toParcelable();
        event.eventType = EVENT_CLOSED;
        event.closeReason = closeReason;
        event.appSpecificErrorCode = errorCode;
        wearable.sendChannelEvent(channel.packageName, channel.path, event);
    }

    private static void respondOpen(IWearableCallbacks callbacks, int statusCode, Channel channel) {
        try {
            callbacks.onOpenChannelResponse(new OpenChannelResponse(statusCode, channel != null ? channel.toParcelable() : null));
        } catch (RemoteException e) {
            Log.w(TAG, e);
        }
    }

    private synchronized Channel getChannel(String packageName, String token) {
        Channel channel = channelsByToken.get(token);
        if (channel != null && !channel.packageName.equals(packageName)) {
            Log.w(TAG, packageName + " tried to use channel of " + channel.packageName);
            return null;
        }
        return channel;
    }

    private synchronized Channel findChannel(String nodeId, Long channelId, Boolean fromChannelOpener) {
        if (channelId == null) return null;
        boolean opener = fromChannelOpener == null || !fromChannelOpener;
        return channelsByKey.get(Channel.key(nodeId, channelId, opener));
    }

    private synchronized ArrayList<Channel> getChannels() {
        return new ArrayList<>(channelsByToken.values());
    }

    private synchronized void remove(Channel channel) {
        channelsByToken.remove(channel.token);
        channelsByKey.remove(channel.key());
    }

    private static void closeQuietly(ParcelFileDescriptor fd) {
        if (fd == null) return;
        try {
            fd.close();
        } catch (IOException ignored) {
        }
    }

    private class InputWriter implements Runnable {
        private final Channel channel;
        private final OutputStream out;

        private InputWriter(Channel channel, OutputStream out) {
            this.channel = channel;
            this.out = out;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    byte[] payload = channel.pendingInput.take();
                    if (payload.length == 0) break;
                    out.write(payload);
                    out.flush();
                }
            } catch (IOException | InterruptedException e) {
                Log.d(TAG, "Input stream of " + channel.token + " ended: " + e.getMessage());
            } finally {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private class OutputReader implements Runnable {
        private final Channel channel;
        private final InputStream in;

        private OutputReader(Channel channel, InputStream in) {
            this.channel = channel;
            this.in = in;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[MAX_PAYLOAD_SIZE];
            try {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (channel.closed) return;
                    if (read > 0) waitForAck(channel, sendData(channel, buffer, read, false));
                }
                if (!channel.closed) sendData(channel, buffer, 0, true);
            } catch (IOException | InterruptedException e) {
                Log.d(TAG, "Output stream of " + channel.token + " ended: " + e.getMessage());
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static class Channel {
        private String token;
        private String nodeId;
        private String packageName;
        private String signatureDigest;
        private String path;
        private long channelId;
        private final boolean opener = true;
        private IWearableCallbacks openCallbacks;
        private Runnable openTimeout;
        private long nextDataRequestId;
        private long lastAckedRequestId = -1;
        private final LinkedBlockingQueue<byte[]> pendingInput = new LinkedBlockingQueue<>();
        private Thread inputWriter;
        private Thread outputReader;
        private InputStream outputStream;
        private IChannelStreamCallbacks inputCallbacks;
        private IChannelStreamCallbacks outputCallbacks;
        private volatile boolean closed;

        private String key() {
            return key(nodeId, channelId, opener);
        }

        private static String key(String nodeId, long channelId, boolean opener) {
            return nodeId + ":" + channelId + ":" + opener;
        }

        private ChannelParcelable toParcelable() {
            return new ChannelParcelable(token, nodeId, path);
        }
    }
}
