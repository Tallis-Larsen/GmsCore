/*
 * Copyright (C) 2013-2019 microG Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.microg.gms.wearable;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.data.DataHolder;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.ConnectionConfiguration;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.internal.CapabilityInfoParcelable;
import com.google.android.gms.wearable.internal.ChannelEventParcelable;
import com.google.android.gms.wearable.internal.IRpcResponseCallback;
import com.google.android.gms.wearable.internal.IWearableListener;
import com.google.android.gms.wearable.internal.MessageEventParcelable;
import com.google.android.gms.wearable.internal.NodeParcelable;
import com.google.android.gms.wearable.internal.PutDataRequest;

import org.microg.gms.common.Constants;
import org.microg.gms.common.PackageUtils;
import org.microg.gms.common.RemoteListenerProxy;
import org.microg.gms.common.Utils;
import org.microg.wearable.SocketConnectionThread;
import org.microg.wearable.WearableConnection;
import org.microg.wearable.proto.AckAsset;
import org.microg.wearable.proto.AppKey;
import org.microg.wearable.proto.AppKeys;
import org.microg.wearable.proto.Connect;
import org.microg.wearable.proto.FetchAsset;
import org.microg.wearable.proto.FilePiece;
import org.microg.wearable.proto.Request;
import org.microg.wearable.proto.RootMessage;
import org.microg.wearable.proto.SetAsset;
import org.microg.wearable.proto.SetDataItem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import okio.ByteString;

import static android.os.Build.VERSION.SDK_INT;

public class WearableImpl {

    private static final String TAG = "GmsWear";
    private static final String CAPABILITY_RINGABLE = "mdm/ringable";
    private static final byte[] CAPABILITY_DATA_STATIC = {'s'};

    private static final int WEAR_TCP_PORT = 5601;

    private final Context context;
    private final NodeDatabaseHelper nodeDatabase;
    private final ConfigurationDatabaseHelper configDatabase;
    private final Map<String, List<ListenerInfo>> listeners = new HashMap<String, List<ListenerInfo>>();
    private final Set<Node> connectedNodes = new HashSet<Node>();
    private final Map<String, WearableConnection> activeConnections = new ConcurrentHashMap<String, WearableConnection>();
    private final Map<String, String> activeConfigNames = new ConcurrentHashMap<String, String>();
    private final Map<String, BluetoothConnectionThread> bluetoothThreads = new HashMap<String, BluetoothConnectionThread>();
    private final Map<String, MessageHandler> messageHandlers = new HashMap<String, MessageHandler>();
    private RpcHelper rpcHelper;
    private final RpcManager rpcManager;
    private final ChannelManager channelManager;
    private SocketConnectionThread sct;
    private boolean permissionRequestActive;
    private ConnectionConfiguration[] configurations;
    private boolean configurationsUpdated = false;
    private ClockworkNodePreferences clockworkNodePreferences;
    private CountDownLatch networkHandlerLock = new CountDownLatch(1);
    public Handler networkHandler;

    public WearableImpl(Context context, NodeDatabaseHelper nodeDatabase, ConfigurationDatabaseHelper configDatabase) {
        this.context = context;
        this.nodeDatabase = nodeDatabase;
        this.configDatabase = configDatabase;
        this.clockworkNodePreferences = new ClockworkNodePreferences(context);
        this.rpcHelper = new RpcHelper(context);
        this.rpcManager = new RpcManager(this, rpcHelper);
        this.channelManager = new ChannelManager(this);
        new Thread(() -> {
            Looper.prepare();
            networkHandler = new Handler(Looper.myLooper());
            networkHandlerLock.countDown();
            publishStaticCapabilities();
            Looper.loop();
        }).start();
        for (ConnectionConfiguration config : getConfigurations()) {
            if (config.enabled) startBluetoothConnection(config);
        }
    }

    public RpcManager getRpcManager() {
        return rpcManager;
    }

    public ChannelManager getChannelManager() {
        return channelManager;
    }

    public String getLocalNodeId() {
        return clockworkNodePreferences.getLocalNodeId();
    }

    public DataItemRecord putDataItem(String packageName, String signatureDigest, String source, DataItemInternal dataItem) {
        DataItemRecord record = new DataItemRecord();
        record.packageName = packageName;
        record.signatureDigest = signatureDigest;
        record.deleted = false;
        record.source = source;
        record.dataItem = dataItem;
        record.v1SeqId = clockworkNodePreferences.getNextSeqId();
        if (record.source.equals(getLocalNodeId())) record.seqId = record.v1SeqId;
        nodeDatabase.putRecord(record);
        return record;
    }

    public DataItemRecord putDataItem(DataItemRecord record) {
        nodeDatabase.putRecord(record);
        if (!record.assetsAreReady) {
            for (Asset asset : record.dataItem.getAssets().values()) {
                if (!nodeDatabase.hasAsset(asset)) {
                    Log.d(TAG, "Asset is missing: " + asset);
                }
            }
        }
        Intent intent = new Intent("com.google.android.gms.wearable.DATA_CHANGED");
        intent.setPackage(record.packageName);
        intent.setData(record.dataItem.uri);
        invokeListeners(intent, listener -> listener.onDataChanged(record.toEventDataHolder()));
        sendCapabilityChanged(record);
        return record;
    }

    private Asset prepareAsset(String packageName, Asset asset) {
        if (asset.getFd() != null && asset.data == null) {
            try {
                asset.data = Utils.readStreamToEnd(new FileInputStream(asset.getFd().getFileDescriptor()));
            } catch (IOException e) {
                Log.w(TAG, e);
            }
        }
        if (asset.data != null) {
            String digest = calculateDigest(asset.data);
            File assetFile = createAssetFile(digest);
            boolean success = assetFile.exists();
            if (!success) {
                File tmpFile = new File(assetFile.getParent(), assetFile.getName() + ".tmp");

                try {
                    FileOutputStream stream = new FileOutputStream(tmpFile);
                    stream.write(asset.data);
                    stream.close();
                    success = tmpFile.renameTo(assetFile);
                } catch (IOException e) {
                    Log.w(TAG, e);
                }
            }
            if (success) {
                Log.d(TAG, "Successfully created asset file " + assetFile);
                return Asset.createFromRef(digest);
            } else {
                Log.w(TAG, "Failed creating asset file " + assetFile);
            }
        }
        return null;
    }

    public File createAssetFile(String digest) {
        File dir = new File(new File(context.getFilesDir(), "assets"), digest.substring(digest.length() - 2));
        dir.mkdirs();
        return new File(dir, digest + ".asset");
    }

    private File createAssetReceiveTempFile(String name) {
        File dir = new File(context.getFilesDir(), "piece");
        dir.mkdirs();
        return new File(dir, name);
    }

    private String calculateDigest(byte[] data) {
        try {
            return Base64.encodeToString(MessageDigest.getInstance("SHA1").digest(data), Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized ConnectionConfiguration[] getConfigurations() {
        if (configurations == null) {
            configurations = configDatabase.getAllConfigurations();
        }
        if (configurationsUpdated) {
            configurationsUpdated = false;
            ConnectionConfiguration[] newConfigurations = configDatabase.getAllConfigurations();
            for (ConnectionConfiguration configuration : configurations) {
                for (ConnectionConfiguration newConfiguration : newConfigurations) {
                    if (newConfiguration.name.equals(configuration.name)) {
                        newConfiguration.connected = configuration.connected;
                        newConfiguration.peerNodeId = configuration.peerNodeId;
                        newConfiguration.nodeId = configuration.nodeId;
                        break;
                    }
                }
            }
            configurations = newConfigurations;
        }
        Log.d(TAG, "Configurations reported: " + Arrays.toString(configurations));
        return configurations;
    }

    private void addConnectedNode(Node node) {
        synchronized (connectedNodes) {
            connectedNodes.add(node);
        }
        onConnectedNodes(getConnectedNodesParcelableList());
    }

    private void removeConnectedNode(String nodeId) {
        synchronized (connectedNodes) {
            for (Node connectedNode : new ArrayList<Node>(connectedNodes)) {
                if (connectedNode.getId().equals(nodeId))
                    connectedNodes.remove(connectedNode);
            }
        }
        onConnectedNodes(getConnectedNodesParcelableList());
    }


    public Context getContext() {
        return context;
    }

    public void syncToPeer(String peerNodeId, String nodeId, long seqId) {
        Log.d(TAG, "-- Start syncing over to " + peerNodeId + ", nodeId " + nodeId + " starting with seqId " + seqId);
        Cursor cursor = nodeDatabase.getModifiedDataItems(nodeId, seqId, true);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                if (!syncRecordToPeer(peerNodeId, DataItemRecord.fromCursor(cursor))) break;
            }
            cursor.close();
        }
        Log.d(TAG, "-- Done syncing over to " + peerNodeId + ", nodeId " + nodeId + " starting with seqId " + seqId);
    }


    void syncRecordToAll(DataItemRecord record) {
        for (String nodeId : new ArrayList<String>(activeConnections.keySet())) {
            if (isDataSyncEnabled(activeConfigNames.get(nodeId))) {
                syncRecordToPeer(nodeId, record);
            }
        }
    }

    private boolean syncRecordToPeer(String nodeId, DataItemRecord record) {
        for (Asset asset : record.dataItem.getAssets().values()) {
            try {
                syncAssetToPeer(nodeId, record, asset);
            } catch (Exception e) {
                Log.w(TAG, "Could not sync asset " + asset + " for " + nodeId + " and " + record, e);
                closeConnection(nodeId);
                return false;
            }
        }

        try {
            SetDataItem item = record.toSetDataItem();
            activeConnections.get(nodeId).writeMessage(new RootMessage.Builder().setDataItem(item).build());
        } catch (Exception e) {
            Log.w(TAG, e);
            closeConnection(nodeId);
            return false;
        }
        return true;
    }

    private void syncAssetToPeer(String nodeId, DataItemRecord record, Asset asset) throws IOException {
        RootMessage announceMessage = new RootMessage.Builder().setAsset(new SetAsset.Builder()
                .digest(asset.getDigest())
                .appKeys(new AppKeys.Builder().appKeys(Collections.singletonList(new AppKey.Builder().packageName(record.packageName).signatureDigest(record.signatureDigest).build())).build())
                .build()).hasAsset(true).build();
        activeConnections.get(nodeId).writeMessage(announceMessage);
        File assetFile = createAssetFile(asset.getDigest());
        String fileName = calculateDigest(announceMessage.encode());
        FileInputStream fis = new FileInputStream(assetFile);
        byte[] arr = new byte[12215];
        ByteString lastPiece = null;
        int c = 0;
        while ((c = fis.read(arr)) > 0) {
            if (lastPiece != null) {
                activeConnections.get(nodeId).writeMessage(new RootMessage.Builder().filePiece(new FilePiece.Builder().fileName(fileName).finalPiece(false).piece(lastPiece).build()).build());
            }
            lastPiece = ByteString.of(arr, 0, c);
        }
        activeConnections.get(nodeId).writeMessage(new RootMessage.Builder().filePiece(new FilePiece.Builder().fileName(fileName).finalPiece(true).piece(lastPiece).digest(asset.getDigest()).build()).build());
    }

    public void addAssetToDatabase(Asset asset, List<AppKey> appKeys) {
        nodeDatabase.putAsset(asset, false);
        for (AppKey appKey : appKeys) {
            nodeDatabase.allowAssetAccess(asset.getDigest(), appKey.packageName, appKey.signatureDigest);
        }
    }

    public long getCurrentSeqId(String nodeId) {
        return nodeDatabase.getCurrentSeqId(nodeId);
    }

    public void handleFilePiece(WearableConnection connection, String fileName, byte[] bytes, String finalPieceDigest) {
        File file = createAssetReceiveTempFile(fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file, true);
            fos.write(bytes);
            fos.close();
        } catch (IOException e) {
            Log.w(TAG, e);
        }
        if (finalPieceDigest != null) {
            // This is a final piece. If digest matches we're so happy!
            try {
                String digest = calculateDigest(Utils.readStreamToEnd(new FileInputStream(file)));
                if (digest.equals(finalPieceDigest)) {
                    if (file.renameTo(createAssetFile(digest))) {
                        nodeDatabase.markAssetAsPresent(digest);
                        connection.writeMessage(new RootMessage.Builder().ackAsset(new AckAsset.Builder().digest(digest).build()).build());
                    } else {
                        Log.w(TAG, "Could not rename to target file name. delete=" + file.delete());
                    }
                } else {
                    Log.w(TAG, "Received digest does not match. delete=" + file.delete());
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed working with temp file. delete=" + file.delete(), e);
            }
        }
    }

    public void onConnectReceived(WearableConnection connection, String configName, Connect connect) {
        for (ConnectionConfiguration config : getConfigurations()) {
            if (configName != null && configName.equals(config.name)) {
                if (!connect.id.equals(config.nodeId)) {
                    config.nodeId = connect.id;
                    configDatabase.putConfiguration(config);
                }
                config.peerNodeId = connect.id;
                config.connected = true;
            }
        }
        Log.d(TAG, "Adding connection to list of open connections: " + connection + " with connect " + connect);
        activeConnections.put(connect.id, connection);
        activeConfigNames.put(connect.id, configName);
        onPeerConnected(new NodeParcelable(connect.id, connect.name, 0, true));
        sendCapabilitiesChanged(connect.id);
        // Fetch missing assets
        Cursor cursor = nodeDatabase.listMissingAssets();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                try {
                    Log.d(TAG, "Fetch for " + cursor.getString(12));
                    connection.writeMessage(new RootMessage.Builder()
                            .fetchAsset(new FetchAsset.Builder()
                                    .assetName(cursor.getString(12))
                                    .packageName(cursor.getString(1))
                                    .signatureDigest(cursor.getString(2))
                                    .permission(false)
                                    .build()).build());
                } catch (IOException e) {
                    Log.w(TAG, e);
                    closeConnection(connect.id);
                }
            }
            cursor.close();
        }
    }

    public void onDisconnectReceived(WearableConnection connection, Connect connect) {
        for (ConnectionConfiguration config : getConfigurations()) {
            if (config.nodeId.equals(connect.id)) {
                config.connected = false;
            }
        }
        Log.d(TAG, "Removing connection from list of open connections: " + connection);
        activeConnections.remove(connect.id);
        activeConfigNames.remove(connect.id);
        channelManager.onPeerDisconnected(connect.id);
        onPeerDisconnected(new NodeParcelable(connect.id, connect.name));
        sendCapabilitiesChanged(connect.id);
    }

    public List<NodeParcelable> getConnectedNodesParcelableList() {
        List<NodeParcelable> nodes = new ArrayList<NodeParcelable>();
        synchronized (connectedNodes) {
            for (Node connectedNode : connectedNodes) {
                nodes.add(new NodeParcelable(connectedNode));
            }
        }
        return nodes;
    }

    interface ListenerInvoker {
        void invoke(IWearableListener listener) throws RemoteException;
    }

    private void invokeListeners(@Nullable Intent intent, ListenerInvoker invoker) {
        for (String packageName : new ArrayList<>(listeners.keySet())) {
            if (intent != null && intent.getPackage() != null && !intent.getPackage().equals(packageName)) continue;
            List<ListenerInfo> listeners = this.listeners.get(packageName);
            if (listeners == null) continue;
            for (int i = 0; i < listeners.size(); i++) {
                boolean filterMatched = false;
                if (intent != null) {
                    for (IntentFilter filter : listeners.get(i).filters) {
                        filterMatched |= filter.match(context.getContentResolver(), intent, false, TAG) > 0;
                    }
                }
                if (filterMatched || listeners.get(i).filters.length == 0) {
                    try {
                        invoker.invoke(listeners.get(i).listener);
                    } catch (RemoteException e) {
                        Log.w(TAG, "Registered listener at package " + packageName + " failed, removing.");
                        listeners.remove(i);
                        i--;
                    }
                }
            }
            if (listeners.isEmpty()) {
                this.listeners.remove(packageName);
            }
        }
        if (intent != null) {
            List<ResolveInfo> services = context.getPackageManager().queryIntentServices(intent, 0);
            for (ResolveInfo service : services) {
                Intent serviceIntent = new Intent(intent).setClassName(service.serviceInfo.packageName, service.serviceInfo.name);
                try {
                    invoker.invoke(RemoteListenerProxy.get(context, serviceIntent, IWearableListener.class, "com.google.android.gms.wearable.BIND_LISTENER"));
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to deliver event to " + serviceIntent, e);
                }
            }
        }
    }

    public void onPeerConnected(NodeParcelable node) {
        Log.d(TAG, "onPeerConnected: " + node);
        invokeListeners(createNodeChangedIntent(), listener -> listener.onPeerConnected(node));
        addConnectedNode(node);
    }

    public void onPeerDisconnected(NodeParcelable node) {
        Log.d(TAG, "onPeerDisconnected: " + node);
        invokeListeners(createNodeChangedIntent(), listener -> listener.onPeerDisconnected(node));
        removeConnectedNode(node.getId());
    }

    public void onConnectedNodes(List<NodeParcelable> nodes) {
        Log.d(TAG, "onConnectedNodes: " + nodes);
        invokeListeners(createNodeChangedIntent(), listener -> listener.onConnectedNodes(nodes));
    }

    public DataItemRecord putData(PutDataRequest request, String packageName) {
        DataItemInternal dataItem = new DataItemInternal(fixHost(request.getUri().getHost(), true), request.getUri().getPath());
        for (Map.Entry<String, Asset> assetEntry : request.getAssets().entrySet()) {
            Asset asset = prepareAsset(packageName, assetEntry.getValue());
            if (asset != null) {
                nodeDatabase.putAsset(asset, true);
                dataItem.addAsset(assetEntry.getKey(), asset);
            }
        }
        dataItem.data = request.getData();
        DataItemRecord record = putDataItem(packageName, PackageUtils.firstSignatureDigest(context, packageName), getLocalNodeId(), dataItem);
        syncRecordToAll(record);
        return record;
    }

    public DataHolder getDataItemsAsHolder(String packageName) {
        Cursor dataHolderItems = nodeDatabase.getDataItemsForDataHolder(packageName, PackageUtils.firstSignatureDigest(context, packageName));
        return new DataHolder(dataHolderItems, 0, null);
    }

    private String fixHost(String host, boolean nothingToLocal) {
        if (TextUtils.isEmpty(host) && nothingToLocal) return getLocalNodeId();
        if (TextUtils.isEmpty(host)) return null;
        if (host.equals("local")) return getLocalNodeId();
        return host;
    }

    public DataHolder getDataItemsByUriAsHolder(Uri uri, String packageName) {
        String firstSignature;
        try {
            firstSignature = PackageUtils.firstSignatureDigest(context, packageName);
        } catch (Exception e) {
            return null;
        }
        Cursor dataHolderItems = nodeDatabase.getDataItemsForDataHolderByHostAndPath(packageName, firstSignature, "*".equals(uri.getHost()) ? null : fixHost(uri.getHost(), false), uri.getPath());
        DataHolder dataHolder = new DataHolder(dataHolderItems, 0, null);
        Log.d(TAG, "Returning data holder of size " + dataHolder.getCount() + " for query " + uri);
        return dataHolder;
    }

    public synchronized void addListener(String packageName, IWearableListener listener, IntentFilter[] filters) {
        if (!listeners.containsKey(packageName)) {
            listeners.put(packageName, new ArrayList<ListenerInfo>());
        }
        listeners.get(packageName).add(new ListenerInfo(listener, filters));
    }

    public void removeListener(IWearableListener listener) {
        for (List<ListenerInfo> list : listeners.values()) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).listener.equals(listener)) {
                    list.remove(i);
                    i--;
                }
            }
        }
    }

    public void enableConnection(String name) {
        configDatabase.setEnabledState(name, true);
        configurationsUpdated = true;
        if (name.equals("server") && sct == null) {
            Log.d(TAG, "Starting server on :" + WEAR_TCP_PORT);
            (sct = SocketConnectionThread.serverListen(WEAR_TCP_PORT, new MessageHandler(context, this, configDatabase.getConfiguration(name)))).start();
        }
        ConnectionConfiguration config = configDatabase.getConfiguration(name);
        if (config != null) {
            startBluetoothConnection(config);
            if (isBluetoothConfiguration(config)) requestBluetoothPermission();
        }
        context.sendBroadcast(new Intent(context, TriggerReceiver.class));
    }

    public void disableConnection(String name) {
        configDatabase.setEnabledState(name, false);
        configurationsUpdated = true;
        if (name.equals("server") && sct != null) {
            activeConnections.remove(sct.getWearableConnection());
            sct.close();
            sct.interrupt();
            sct = null;
        }
        stopBluetoothConnection(name);
        context.sendBroadcast(new Intent(context, TriggerReceiver.class));
    }

    public void deleteConnection(String name) {
        stopBluetoothConnection(name);
        configDatabase.deleteConfiguration(name);
        configurationsUpdated = true;
        context.sendBroadcast(new Intent(context, TriggerReceiver.class));
    }

    public void createConnection(ConnectionConfiguration config) {
        if (config.nodeId == null) config.nodeId = getLocalNodeId();
        Log.d(TAG, "putConfig[nyp]: " + config);
        configDatabase.putConfiguration(config);
        configurationsUpdated = true;
        if (config.enabled) {
            startBluetoothConnection(config);
            if (isBluetoothConfiguration(config)) requestBluetoothPermission();
        }
        context.sendBroadcast(new Intent(context, TriggerReceiver.class));
    }

    public void updateConfiguration(ConnectionConfiguration config) {
        ConnectionConfiguration oldConfig = config.name != null ? configDatabase.getConfiguration(config.name) : null;
        if (oldConfig == null) return;
        boolean wasSyncEnabled = oldConfig.dataItemSyncEnabled;
        if (config.nodeId == null) config.nodeId = oldConfig.nodeId;
        configDatabase.putConfiguration(config);
        configurationsUpdated = true;
        MessageHandler handler;
        synchronized (bluetoothThreads) {
            handler = messageHandlers.get(config.name);
        }
        if (!wasSyncEnabled && config.dataItemSyncEnabled && handler != null) {
            networkHandler.post(handler::startDataSync);
        }
    }

    boolean isDataSyncEnabled(String configName) {
        for (ConnectionConfiguration config : getConfigurations()) {
            if (configName != null && configName.equals(config.name)) {
                return config.dataItemSyncEnabled;
            }
        }
        return true;
    }

    static boolean isBluetoothConfiguration(ConnectionConfiguration config) {
        return config.role == 1 && config.name != null && BluetoothAdapter.checkBluetoothAddress(config.address);
    }

    boolean hasBluetoothConnections() {
        synchronized (bluetoothThreads) {
            return !bluetoothThreads.isEmpty();
        }
    }

    void retryBluetoothConnections() {
        synchronized (bluetoothThreads) {
            for (BluetoothConnectionThread thread : bluetoothThreads.values()) {
                thread.retryNow();
            }
        }
    }

    public void requestCallPermission() {
        if (SDK_INT < 26) return;
        Intent intent = new Intent(context, AskPermissionActivity.class);
        intent.putExtra(AskPermissionActivityKt.EXTRA_PERMISSIONS, new String[]{Manifest.permission.ANSWER_PHONE_CALLS});
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void requestBluetoothPermission() {
        if (SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) return;
        synchronized (bluetoothThreads) {
            if (permissionRequestActive) return;
            permissionRequestActive = true;
        }
        Intent intent = new Intent(context, AskPermissionActivity.class);
        intent.putExtra(AskPermissionActivityKt.EXTRA_MESSENGER, new Messenger(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                synchronized (bluetoothThreads) {
                    permissionRequestActive = false;
                }
                if (msg.what == Activity.RESULT_OK) retryBluetoothConnections();
            }
        }));
        intent.putExtra(AskPermissionActivityKt.EXTRA_PERMISSIONS, new String[]{Manifest.permission.BLUETOOTH_CONNECT});
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        Log.d(TAG, "Started AskPermissionActivity");
    }

    private void startBluetoothConnection(ConnectionConfiguration config) {
        if (!isBluetoothConfiguration(config)) return;
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        if (adapter == null) {
            Log.w(TAG, "No Bluetooth adapter, can't connect to " + config.name);
            return;
        }
        synchronized (bluetoothThreads) {
            BluetoothConnectionThread thread = bluetoothThreads.get(config.name);
            if (thread != null && thread.isAlive() && thread.getAddress().equals(config.address)) return;
            if (thread != null) thread.close();
            Log.d(TAG, "Connecting to " + config.name + " over Bluetooth");
            MessageHandler handler = new MessageHandler(context, this, config);
            messageHandlers.put(config.name, handler);
            thread = new BluetoothConnectionThread(adapter, config.address, handler);
            bluetoothThreads.put(config.name, thread);
            thread.start();
        }
    }

    private void stopBluetoothConnection(String name) {
        synchronized (bluetoothThreads) {
            BluetoothConnectionThread thread = bluetoothThreads.remove(name);
            messageHandlers.remove(name);
            if (thread != null) thread.close();
        }
    }

    public String getNodeIdForAddress(String address) {
        for (ConnectionConfiguration config : getConfigurations()) {
            if (address != null && address.equals(config.address)) {
                return config.peerNodeId != null ? config.peerNodeId : config.nodeId;
            }
        }
        return null;
    }

    public int deleteDataItems(Uri uri, String packageName) {
        List<DataItemRecord> records = nodeDatabase.deleteDataItems(packageName, PackageUtils.firstSignatureDigest(context, packageName), fixHost(uri.getHost(), false), uri.getPath());
        for (DataItemRecord record : records) {
            syncRecordToAll(record);
        }
        return records.size();
    }

    public void sendMessageReceived(String packageName, MessageEventParcelable messageEvent) {
        Log.d(TAG, "onMessageReceived: " + messageEvent);
        Intent intent = new Intent("com.google.android.gms.wearable.MESSAGE_RECEIVED");
        intent.setPackage(packageName);
        intent.setData(createEventUri(messageEvent.getSourceNodeId(), messageEvent.getPath()));
        invokeListeners(intent, listener -> listener.onMessageReceived(messageEvent));
    }

    public void sendRequestReceived(String packageName, MessageEventParcelable request, IRpcResponseCallback callback) {
        Log.d(TAG, "onRequestReceived: " + request);
        Intent intent = new Intent("com.google.android.gms.wearable.REQUEST_RECEIVED");
        intent.setPackage(packageName);
        intent.setData(createEventUri(request.getSourceNodeId(), request.getPath()));
        invokeListeners(intent, listener -> listener.onRequestReceived(request, callback));
    }

    public void sendChannelEvent(String packageName, String path, ChannelEventParcelable event) {
        Log.d(TAG, "onChannelEvent: " + event.eventType + " " + path);
        Intent intent = new Intent("com.google.android.gms.wearable.CHANNEL_EVENT");
        intent.setPackage(packageName);
        intent.setData(createEventUri("", path));
        invokeListeners(intent, listener -> listener.onChannelEvent(event));
    }

    private void sendCapabilityChanged(DataItemRecord record) {
        if (Constants.GMS_PACKAGE_NAME.equals(record.packageName)) {
            sendCapabilityChanged(record.dataItem.uri.getPath());
        }
    }

    private void sendCapabilityChanged(String path) {
        if (path == null || !path.startsWith(CapabilityManager.PATH_PREFIX)) return;
        String[] segments = path.substring(CapabilityManager.PATH_PREFIX.length()).split("/");
        if (segments.length != 3 || !segments[1].equals(PackageUtils.firstSignatureDigest(context, segments[0]))) return;
        String capability = Uri.decode(segments[2]);
        CapabilityInfoParcelable info = new CapabilityManager(context, this, segments[0]).getCapability(capability, 1);
        Intent intent = new Intent("com.google.android.gms.wearable.CAPABILITY_CHANGED");
        intent.setPackage(segments[0]);
        intent.setData(createEventUri("", capability));
        invokeListeners(intent, listener -> listener.onConnectedCapabilityChanged(info));
    }

    private void sendCapabilitiesChanged(String nodeId) {
        try {
            networkHandlerLock.await();
        } catch (InterruptedException e) {
            Log.w(TAG, e);
            return;
        }
        networkHandler.post(() -> {
            Cursor cursor = nodeDatabase.getDataItemsForDataHolderByHostAndPath(Constants.GMS_PACKAGE_NAME, Constants.GMS_PACKAGE_SIGNATURE_SHA1, nodeId, CapabilityManager.PATH_PREFIX);
            if (cursor == null) return;
            try {
                int path = cursor.getColumnIndexOrThrow("path");
                while (cursor.moveToNext()) {
                    sendCapabilityChanged(cursor.getString(path));
                }
            } finally {
                cursor.close();
            }
        });
    }

    private static Intent createNodeChangedIntent() {
        return new Intent("com.google.android.gms.wearable.NODE_CHANGED").setData(createEventUri("", null));
    }

    private static Uri createEventUri(String host, String path) {
        Uri.Builder builder = new Uri.Builder().scheme("wear").authority(host);
        if (path != null) builder.path(path);
        return builder.build();
    }

    public boolean isConnected(String nodeId) {
        return nodeId != null && activeConnections.containsKey(nodeId);
    }

    public boolean writeMessage(String nodeId, RootMessage message) {
        WearableConnection connection = activeConnections.get(nodeId);
        if (connection == null) {
            Log.d(TAG, nodeId + " seems not reachable");
            return false;
        }
        try {
            connection.writeMessage(message);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Error while writing, closing link", e);
            closeConnection(nodeId);
            return false;
        }
    }

    public DataItemRecord putGmsDataItem(String path, byte[] data) {
        DataItemInternal dataItem = new DataItemInternal(getLocalNodeId(), path);
        dataItem.data = data;
        DataItemRecord record = putDataItem(Constants.GMS_PACKAGE_NAME, Constants.GMS_PACKAGE_SIGNATURE_SHA1, getLocalNodeId(), dataItem);
        syncRecordToAll(record);
        return record;
    }

    private void publishStaticCapabilities() {
        String path = CapabilityManager.PATH_PREFIX + Constants.GMS_PACKAGE_NAME + "/" + Constants.GMS_PACKAGE_SIGNATURE_SHA1 + "/" + Uri.encode(CAPABILITY_RINGABLE);
        DataItemRecord record = getGmsDataItem(path);
        if (record == null || record.deleted) putGmsDataItem(path, CAPABILITY_DATA_STATIC);
    }

    public void sendGmsMessage(String nodeId, String path) {
        networkHandler.post(() -> sendMessage(Constants.GMS_PACKAGE_NAME, Constants.GMS_PACKAGE_SIGNATURE_SHA1, nodeId, path, new byte[0], false));
    }

    public DataItemRecord getGmsDataItem(String path) {
        Cursor cursor = nodeDatabase.getDataItemsByHostAndPath(Constants.GMS_PACKAGE_NAME, Constants.GMS_PACKAGE_SIGNATURE_SHA1, getLocalNodeId(), path);
        DataItemRecord record = null;
        if (cursor != null) {
            if (cursor.moveToNext()) {
                record = DataItemRecord.fromCursor(cursor);
            }
            cursor.close();
        }
        return record;
    }

    public void deleteGmsDataItem(String path) {
        for (DataItemRecord record : nodeDatabase.deleteDataItems(Constants.GMS_PACKAGE_NAME, Constants.GMS_PACKAGE_SIGNATURE_SHA1, getLocalNodeId(), path)) {
            syncRecordToAll(record);
        }
    }

    public DataHolder getGmsDataItems(String path) {
        return new DataHolder(nodeDatabase.getDataItemsForDataHolderByHostAndPath(Constants.GMS_PACKAGE_NAME, Constants.GMS_PACKAGE_SIGNATURE_SHA1, null, path), 0, null);
    }

    public NodeParcelable getNodeParcelable(String nodeId) {
        for (NodeParcelable node : getConnectedNodesParcelableList()) {
            if (node.getId().equals(nodeId)) return node;
        }
        return new NodeParcelable(nodeId, nodeId, Integer.MAX_VALUE, false);
    }

    public DataItemRecord getDataItemByUri(Uri uri, String packageName) {
        Cursor cursor = nodeDatabase.getDataItemsByHostAndPath(packageName, PackageUtils.firstSignatureDigest(context, packageName), fixHost(uri.getHost(), true), uri.getPath());
        DataItemRecord record = null;
        if (cursor != null) {
            if (cursor.moveToNext()) {
                record = DataItemRecord.fromCursor(cursor);
            }
            cursor.close();
        }
        Log.d(TAG, "getDataItem: " + record);
        return record;
    }

    private IWearableListener getListener(String packageName, String action, Uri uri) {
        Intent intent = new Intent(action);
        intent.setPackage(packageName);
        intent.setData(uri);

        return RemoteListenerProxy.get(context, intent, IWearableListener.class, "com.google.android.gms.wearable.BIND_LISTENER");
    }

    private void closeConnection(String nodeId) {
        WearableConnection connection = activeConnections.get(nodeId);
        if (connection == null) return;
        try {
            connection.close();
        } catch (IOException e1) {
            Log.w(TAG, e1);
        }
        if (sct != null && connection == sct.getWearableConnection()) {
            sct.close();
            sct = null;
        }
        activeConnections.remove(nodeId);
        for (ConnectionConfiguration config : getConfigurations()) {
            if (nodeId.equals(config.nodeId) || nodeId.equals(config.peerNodeId)) {
                config.connected = false;
            }
        }
        onPeerDisconnected(new NodeParcelable(nodeId, "Wear device"));
        Log.d(TAG, "Closed connection to " + nodeId + " on error");
    }

    public int sendMessage(String packageName, String targetNodeId, String path, byte[] data) {
        return sendMessage(packageName, targetNodeId, path, data, false);
    }

    public int sendMessage(String packageName, String targetNodeId, String path, byte[] data, boolean highPriority) {
        return sendMessage(packageName, PackageUtils.firstSignatureDigest(context, packageName), targetNodeId, path, data, highPriority);
    }

    private int sendMessage(String packageName, String signatureDigest, String targetNodeId, String path, byte[] data, boolean highPriority) {
        if (activeConnections.containsKey(targetNodeId)) {
            WearableConnection connection = activeConnections.get(targetNodeId);
            RpcHelper.RpcConnectionState state = rpcHelper.useConnectionState(packageName, targetNodeId, path, highPriority);
            try {
                connection.writeMessage(new RootMessage.Builder().rpcRequest(new Request.Builder()
                        .targetNodeId(targetNodeId)
                        .unknown5(0)
                        .path(path)
                        .rawData(ByteString.of(data))
                        .packageName(packageName)
                        .signatureDigest(signatureDigest)
                        .sourceNodeId(getLocalNodeId())
                        .generation(state.generation)
                        .requestId(state.lastRequestId)
                        .priority(highPriority ? 2 : 1)
                        .build()).build());
            } catch (IOException e) {
                Log.w(TAG, "Error while writing, closing link", e);
                closeConnection(targetNodeId);
                return -1;
            }
            return (state.generation + 527) * 31 + state.lastRequestId;
        }
        Log.d(TAG, targetNodeId + " seems not reachable");
        return -1;
    }

    public void stop() {
        synchronized (bluetoothThreads) {
            for (BluetoothConnectionThread thread : bluetoothThreads.values()) {
                thread.close();
            }
            bluetoothThreads.clear();
            messageHandlers.clear();
        }
        try {
            this.networkHandlerLock.await();
            this.networkHandler.getLooper().quit();
        } catch (InterruptedException e) {
            Log.w(TAG, e);
        }
    }

    private class ListenerInfo {
        private IWearableListener listener;
        private IntentFilter[] filters;

        private ListenerInfo(IWearableListener listener, IntentFilter[] filters) {
            this.listener = listener;
            this.filters = filters;
        }
    }
}
