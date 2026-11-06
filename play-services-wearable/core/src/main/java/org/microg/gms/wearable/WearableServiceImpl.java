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

import static android.os.Build.VERSION.SDK_INT;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.telecom.TelecomManager;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.internal.safeparcel.SafeParcelableSerializer;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.ConnectionConfiguration;
import com.google.android.gms.wearable.internal.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import android.accounts.Account;
import com.google.android.gms.wearable.AppRecommendationsRequest;
import com.google.android.gms.wearable.AppTheme;
import com.google.android.gms.wearable.ConnectionDelayConfig;
import com.google.android.gms.wearable.MessageOptions;
import com.google.android.gms.wearable.Term;
import com.google.android.gms.wearable.WearableStatusCodes;

import org.microg.gms.utils.ExtendedPackageInfo;
import org.microg.gms.wearable.core.R;
import org.microg.wearable.proto.PrivacySettings;

public class WearableServiceImpl extends IWearableService.Stub {
    private static final String TAG = "GmsWearSvcImpl";

    private static final String PATH_PRIVACY_SETTINGS = "/privacy_settings";
    private static final String APP_THEME_PREFERENCES = "wearable_app_themes";

    private final Context context;
    private final String packageName;
    private final WearableImpl wearable;
    private final Handler mainHandler;
    private final CapabilityManager capabilities;

    public WearableServiceImpl(Context context, WearableImpl wearable, String packageName) {
        this.context = context;
        this.wearable = wearable;
        this.packageName = packageName;
        this.capabilities = new CapabilityManager(context, wearable, packageName);
        this.mainHandler = new Handler(context.getMainLooper());
    }

    private void postMain(IWearableCallbacks callbacks, RemoteExceptionRunnable runnable) {
        mainHandler.post(new CallbackRunnable(callbacks) {
            @Override
            public void run(IWearableCallbacks callbacks) throws RemoteException {
                runnable.run();
            }
        });
    }

    private void postNetwork(IWearableCallbacks callbacks, RemoteExceptionRunnable runnable) {
        this.wearable.networkHandler.post(new CallbackRunnable(callbacks) {
            @Override
            public void run(IWearableCallbacks callbacks) throws RemoteException {
                runnable.run();
            }
        });
    }

    /*
     * Config
     */

    @Override
    public void putConfig(IWearableCallbacks callbacks, final ConnectionConfiguration config) throws RemoteException {
        postMain(callbacks, () -> {
            wearable.createConnection(config);
            callbacks.onStatus(Status.SUCCESS);
        });
    }

    @Override
    public void deleteConfig(IWearableCallbacks callbacks, final String name) throws RemoteException {
        postMain(callbacks, () -> {
            wearable.deleteConnection(name);
            callbacks.onStatus(Status.SUCCESS);
        });
    }

    @Override
    public void getConfigs(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "getConfigs");
        postMain(callbacks, () -> {
            try {
                callbacks.onGetConfigsResponse(new GetConfigsResponse(0, wearable.getConfigurations()));
            } catch (Exception e) {
                callbacks.onGetConfigsResponse(new GetConfigsResponse(8, new ConnectionConfiguration[0]));
            }
        });
    }


    @Override
    public void enableConfig(IWearableCallbacks callbacks, final String name) throws RemoteException {
        Log.d(TAG, "enableConfig: " + name);
        postMain(callbacks, () -> {
            wearable.enableConnection(name);
            callbacks.onStatus(Status.SUCCESS);
        });
    }

    @Override
    public void disableConfig(IWearableCallbacks callbacks, final String name) throws RemoteException {
        Log.d(TAG, "disableConfig: " + name);
        postMain(callbacks, () -> {
            wearable.disableConnection(name);
            callbacks.onStatus(Status.SUCCESS);
        });
    }

    /*
     * DataItems
     */

    @Override
    public void putData(IWearableCallbacks callbacks, final PutDataRequest request) throws RemoteException {
        Log.d(TAG, "putData: " + request.toString(true));
        this.wearable.networkHandler.post(new CallbackRunnable(callbacks) {
            @Override
            public void run(IWearableCallbacks callbacks) throws RemoteException {
                DataItemRecord record = wearable.putData(request, packageName);
                callbacks.onPutDataResponse(new PutDataResponse(0, record.toParcelable()));
            }
        });
    }

    @Override
    public void getDataItem(IWearableCallbacks callbacks, final Uri uri) throws RemoteException {
        Log.d(TAG, "getDataItem: " + uri);
        postMain(callbacks, () -> {
            DataItemRecord record = wearable.getDataItemByUri(uri, packageName);
            if (record != null) {
                callbacks.onGetDataItemResponse(new GetDataItemResponse(0, record.toParcelable()));
            } else {
                callbacks.onGetDataItemResponse(new GetDataItemResponse(0, null));
            }
        });
    }

    @Override
    public void getDataItems(final IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "getDataItems: " + callbacks);
        postMain(callbacks, () -> {
            callbacks.onDataItemChanged(wearable.getDataItemsAsHolder(packageName));
        });
    }

    @Override
    public void getDataItemsByUri(IWearableCallbacks callbacks, Uri uri) throws RemoteException {
        getDataItemsByUriWithFilter(callbacks, uri, 0);
    }

    @Override
    public void getDataItemsByUriWithFilter(IWearableCallbacks callbacks, final Uri uri, int typeFilter) throws RemoteException {
        Log.d(TAG, "getDataItemsByUri: " + uri);
        postMain(callbacks, () -> {
            callbacks.onDataItemChanged(wearable.getDataItemsByUriAsHolder(uri, packageName));
        });
    }

    @Override
    public void deleteDataItems(IWearableCallbacks callbacks, Uri uri) throws RemoteException {
        deleteDataItemsWithFilter(callbacks, uri, 0);
    }

    @Override
    public void deleteDataItemsWithFilter(IWearableCallbacks callbacks, final Uri uri, int typeFilter) throws RemoteException {
        Log.d(TAG, "deleteDataItems: " + uri);
        this.wearable.networkHandler.post(new CallbackRunnable(callbacks) {
            @Override
            public void run(IWearableCallbacks callbacks) throws RemoteException {
                callbacks.onDeleteDataItemsResponse(new DeleteDataItemsResponse(0, wearable.deleteDataItems(uri, packageName)));
            }
        });
    }

    @Override
    public void sendMessage(IWearableCallbacks callbacks, final String targetNodeId, final String path, final byte[] data) throws RemoteException {
        Log.d(TAG, "sendMessage: " + targetNodeId + " / " + path + ": " + (data == null ? null : Base64.encodeToString(data, Base64.NO_WRAP)));
        this.wearable.networkHandler.post(new CallbackRunnable(callbacks) {
            @Override
            public void run(IWearableCallbacks callbacks) throws RemoteException {
                SendMessageResponse sendMessageResponse = new SendMessageResponse();
                try {
                    sendMessageResponse.requestId = wearable.sendMessage(packageName, targetNodeId, path, data);
                    if (sendMessageResponse.requestId == -1) {
                        sendMessageResponse.statusCode = 4000;
                    }
                } catch (Exception e) {
                    sendMessageResponse.statusCode = 8;
                }
                mainHandler.post(() -> {
                    try {
                        callbacks.onSendMessageResponse(sendMessageResponse);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                });
            }
        });
    }

    @Override
    public void getFdForAsset(IWearableCallbacks callbacks, final Asset asset) throws RemoteException {
        Log.d(TAG, "getFdForAsset " + asset);
        postMain(callbacks, () -> {
            // TODO: Access control
            try {
                callbacks.onGetFdForAssetResponse(new GetFdForAssetResponse(0, ParcelFileDescriptor.open(wearable.createAssetFile(asset.getDigest()), ParcelFileDescriptor.MODE_READ_ONLY)));
            } catch (FileNotFoundException e) {
                callbacks.onGetFdForAssetResponse(new GetFdForAssetResponse(8, null));
            }
        });
    }

    @Override
    public void optInCloudSync(IWearableCallbacks callbacks, boolean enable) throws RemoteException {
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    @Deprecated
    public void getCloudSyncOptInDone(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "unimplemented Method: getCloudSyncOptInDone");
        callbacks.onGetCloudSyncOptInOutDoneResponse(new GetCloudSyncOptInOutDoneResponse(CommonStatusCodes.SUCCESS, true));
    }

    @Override
    public void setCloudSyncSetting(IWearableCallbacks callbacks, boolean enable) throws RemoteException {
        Log.d(TAG, "unimplemented Method: setCloudSyncSetting");
        callbacks.onStatus(enable ? new Status(WearableStatusCodes.FEATURE_DISABLED) : Status.SUCCESS);
    }

    @Override
    public void getCloudSyncSetting(IWearableCallbacks callbacks) throws RemoteException {
        callbacks.onGetCloudSyncSettingResponse(new GetCloudSyncSettingResponse(0, false));
    }

    @Override
    public void getCloudSyncOptInStatus(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "unimplemented Method: getCloudSyncOptInStatus");
        callbacks.onGetCloudSyncOptInStatusResponse(new GetCloudSyncOptInStatusResponse(CommonStatusCodes.SUCCESS, true, false));
    }

    @Override
    public void sendRemoteCommand(IWearableCallbacks callbacks, byte b) throws RemoteException {
        Log.d(TAG, "unimplemented Method: sendRemoteCommand: " + b);
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    @Override
    public void getLocalNode(IWearableCallbacks callbacks) throws RemoteException {
        postMain(callbacks, () -> {
            try {
                callbacks.onGetLocalNodeResponse(new GetLocalNodeResponse(0, new NodeParcelable(wearable.getLocalNodeId(), wearable.getLocalNodeId())));
            } catch (Exception e) {
                callbacks.onGetLocalNodeResponse(new GetLocalNodeResponse(8, null));
            }
        });
    }

    @Override
    public void getConnectedNodes(IWearableCallbacks callbacks) throws RemoteException {
        postMain(callbacks, () -> {
            callbacks.onGetConnectedNodesResponse(new GetConnectedNodesResponse(0, wearable.getConnectedNodesParcelableList()));
        });
    }

    /*
     * Capability
     */

    @Override
    public void getConnectedCapability(IWearableCallbacks callbacks, String capability, int nodeFilter) throws RemoteException {
        Log.d(TAG, "getConnectedCapability: " + capability + ", " + nodeFilter);
        postMain(callbacks, () -> callbacks.onGetCapabilityResponse(new GetCapabilityResponse(0, capabilities.getCapability(capability, nodeFilter))));
    }

    @Override
    public void getAllCapabilities(IWearableCallbacks callbacks, int nodeFilter) throws RemoteException {
        Log.d(TAG, "getAllCapabilities: " + nodeFilter);
        postMain(callbacks, () -> {
            GetAllCapabilitiesResponse response = new GetAllCapabilitiesResponse();
            response.capabilities = capabilities.getAllCapabilities(nodeFilter);
            callbacks.onGetAllCapabilitiesResponse(response);
        });
    }

    @Override
    public void addLocalCapability(IWearableCallbacks callbacks, String capability) throws RemoteException {
        Log.d(TAG, "addLocalCapability: " + capability);
        this.wearable.networkHandler.post(new CallbackRunnable(callbacks) {
            @Override
            public void run(IWearableCallbacks callbacks) throws RemoteException {
                callbacks.onAddLocalCapabilityResponse(new AddLocalCapabilityResponse(capabilities.add(capability)));
            }
        });
    }

    @Override
    public void removeLocalCapability(IWearableCallbacks callbacks, String capability) throws RemoteException {
        Log.d(TAG, "removeLocalCapability: " + capability);
        this.wearable.networkHandler.post(new CallbackRunnable(callbacks) {
            @Override
            public void run(IWearableCallbacks callbacks) throws RemoteException {
                callbacks.onRemoveLocalCapabilityResponse(new RemoveLocalCapabilityResponse(capabilities.remove(capability)));
            }
        });
    }

    @Override
    public void addListener(IWearableCallbacks callbacks, AddListenerRequest request) throws RemoteException {
        Log.d(TAG, "addListener: " + packageName);
        if (request.listener != null) {
            wearable.addListener(packageName, request.listener, request.intentFilters);
            // Clients keep their listeners across a restart of this service, so they need to learn the current state
            postNetwork(callbacks, () -> request.listener.onConnectedNodes(wearable.getConnectedNodesParcelableList()));
        }
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void removeListener(IWearableCallbacks callbacks, RemoveListenerRequest request) throws RemoteException {
        wearable.removeListener(request.listener);
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void getStorageInformation(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "unimplemented Method: getStorageInformation");
        callbacks.onStorageInfoResponse(new StorageInfoResponse(WearableStatusCodes.FEATURE_DISABLED));
    }

    @Override
    public void clearStorage(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "unimplemented Method: clearStorage");
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    @Override
    public void endCall(IWearableCallbacks callbacks) throws RemoteException {
        runCallControl(callbacks, "endCall", telecomManager -> {
            if (SDK_INT < 28) throw new UnsupportedOperationException();
            telecomManager.endCall();
        });
    }

    @Override
    public void acceptRingingCall(IWearableCallbacks callbacks) throws RemoteException {
        runCallControl(callbacks, "acceptRingingCall", TelecomManager::acceptRingingCall);
    }

    @Override
    public void silenceRinger(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "unimplemented Method: silenceRinger");
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    private void runCallControl(IWearableCallbacks callbacks, String name, CallControl control) {
        Log.d(TAG, name + ": " + packageName);
        if (!new ExtendedPackageInfo(context, packageName).isGooglePackage()) {
            throw new SecurityException(packageName + " may not control phone calls");
        }
        postMain(callbacks, () -> {
            TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager == null) {
                callbacks.onStatus(new Status(CommonStatusCodes.DEVELOPER_ERROR));
                return;
            }
            try {
                control.run(telecomManager);
                callbacks.onStatus(Status.SUCCESS);
            } catch (Exception | NoSuchMethodError e) {
                Log.w(TAG, name + " failed", e);
                if (e instanceof SecurityException) wearable.requestCallPermission();
                callbacks.onStatus(new Status(CommonStatusCodes.INTERNAL_ERROR));
            }
        });
    }

    private interface CallControl {
        void run(TelecomManager telecomManager);
    }

    /*
     * Apple Notification Center Service
     */

    @Override
    public void injectAncsNotificationForTesting(IWearableCallbacks callbacks, AncsNotificationParcelable notification) throws RemoteException {
        Log.d(TAG, "unimplemented Method: injectAncsNotificationForTesting: " + notification);
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    @Override
    public void doAncsPositiveAction(IWearableCallbacks callbacks, int i) throws RemoteException {
        Log.d(TAG, "unimplemented Method: doAncsPositiveAction: " + i);
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    @Override
    public void doAncsNegativeAction(IWearableCallbacks callbacks, int i) throws RemoteException {
        Log.d(TAG, "unimplemented Method: doAncsNegativeAction: " + i);
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    @Override
    public void openChannel(IWearableCallbacks callbacks, String s1, String s2) throws RemoteException {
        Log.d(TAG, "openChannel: " + s1 + ", " + s2);
        postNetwork(callbacks, () -> wearable.getChannelManager().openChannel(packageName, s1, s2, callbacks));
    }

    /*
     * Channels
     */

    @Override
    public void closeChannel(IWearableCallbacks callbacks, String s) throws RemoteException {
        Log.d(TAG, "closeChannel: " + s);
        postNetwork(callbacks, () -> wearable.getChannelManager().closeChannel(packageName, s, 0, false, callbacks));
    }

    @Override
    public void closeChannelWithError(IWearableCallbacks callbacks, String s, int errorCode) throws RemoteException {
        Log.d(TAG, "closeChannelWithError: " + s + ", " + errorCode);
        postNetwork(callbacks, () -> wearable.getChannelManager().closeChannel(packageName, s, errorCode, true, callbacks));
    }

    @Override
    public void getChannelInputStream(IWearableCallbacks callbacks, IChannelStreamCallbacks channelCallbacks, String s) throws RemoteException {
        Log.d(TAG, "getChannelInputStream: " + s);
        postNetwork(callbacks, () -> wearable.getChannelManager().getInputStream(packageName, s, channelCallbacks, callbacks));
    }

    @Override
    public void getChannelOutputStream(IWearableCallbacks callbacks, IChannelStreamCallbacks channelCallbacks, String s) throws RemoteException {
        Log.d(TAG, "getChannelOutputStream: " + s);
        postNetwork(callbacks, () -> wearable.getChannelManager().getOutputStream(packageName, s, channelCallbacks, callbacks));
    }

    @Override
    public void writeChannelInputToFd(IWearableCallbacks callbacks, String s, ParcelFileDescriptor fd) throws RemoteException {
        Log.d(TAG, "writeChannelInputToFd: " + s);
        postNetwork(callbacks, () -> wearable.getChannelManager().writeInputToFd(packageName, s, fd, callbacks));
    }

    @Override
    public void readChannelOutputFromFd(IWearableCallbacks callbacks, String s, ParcelFileDescriptor fd, long l1, long l2) throws RemoteException {
        Log.d(TAG, "readChannelOutputFromFd: " + s);
        postNetwork(callbacks, () -> wearable.getChannelManager().readOutputFromFd(packageName, s, fd, l1, l2, callbacks));
    }

    @Override
    public void syncWifiCredentials(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "unimplemented Method: syncWifiCredentials");
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    /*
     * Connection deprecated
     */

    @Override
    @Deprecated
    public void putConnection(IWearableCallbacks callbacks, ConnectionConfiguration config) throws RemoteException {
        Log.d(TAG, "unimplemented Method: putConnection");
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    @Override
    @Deprecated
    public void getConnection(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "getConfig");
        postMain(callbacks, () -> {
            ConnectionConfiguration[] configurations = wearable.getConfigurations();
            if (configurations == null || configurations.length == 0) {
                callbacks.onGetConfigResponse(new GetConfigResponse(1, new ConnectionConfiguration(null, null, 0, 0, false)));
            } else {
                callbacks.onGetConfigResponse(new GetConfigResponse(0, configurations[0]));
            }
        });
    }

    @Override
    @Deprecated
    public void enableConnection(IWearableCallbacks callbacks) throws RemoteException {
        postMain(callbacks, () -> {
            ConnectionConfiguration[] configurations = wearable.getConfigurations();
            if (configurations.length > 0) {
                enableConfig(callbacks, configurations[0].name);
            }
        });
    }

    @Override
    @Deprecated
    public void disableConnection(IWearableCallbacks callbacks) throws RemoteException {
        postMain(callbacks, () -> {
            ConnectionConfiguration[] configurations = wearable.getConfigurations();
            if (configurations.length > 0) {
                disableConfig(callbacks, configurations[0].name);
            }
        });
    }

    @Override
    public void sendRequest(IWearableCallbacks callbacks, String targetNodeId, String path, byte[] data) throws RemoteException {
        Log.d(TAG, "sendRequest: " + targetNodeId + " / " + path);
        postNetwork(callbacks, () -> wearable.getRpcManager().sendRequest(packageName, targetNodeId, path, data, false, callbacks));
    }

    @Override
    public void sendMessageWithOptions(IWearableCallbacks callbacks, String targetNodeId, String path, byte[] data, MessageOptions options) throws RemoteException {
        Log.d(TAG, "sendMessageWithOptions: " + targetNodeId + " / " + path);
        postNetwork(callbacks, () -> {
            SendMessageResponse sendMessageResponse = new SendMessageResponse();
            try {
                sendMessageResponse.requestId = wearable.sendMessage(packageName, targetNodeId, path, data, isHighPriority(options));
                if (sendMessageResponse.requestId == -1) {
                    sendMessageResponse.statusCode = WearableStatusCodes.TARGET_NODE_NOT_CONNECTED;
                }
            } catch (Exception e) {
                sendMessageResponse.statusCode = CommonStatusCodes.INTERNAL_ERROR;
            }
            callbacks.onSendMessageResponse(sendMessageResponse);
        });
    }

    @Override
    public void sendRequestWithOptions(IWearableCallbacks callbacks, String targetNodeId, String path, byte[] data, MessageOptions options) throws RemoteException {
        Log.d(TAG, "sendRequestWithOptions: " + targetNodeId + " / " + path);
        postNetwork(callbacks, () -> wearable.getRpcManager().sendRequest(packageName, targetNodeId, path, data, isHighPriority(options), callbacks));
    }

    @Override
    public void retryConnection(IWearableCallbacks callbacks, String configName, boolean arg) throws RemoteException {
        Log.d(TAG, "retryConnection: " + configName);
        wearable.retryBluetoothConnections();
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void updateConnectionStrategy(IWearableCallbacks callbacks, String configName, int strategy) throws RemoteException {
        Log.d(TAG, "updateConnectionStrategy: " + configName + ", " + strategy);
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void getRelatedConfigs(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "unimplemented Method: getRelatedConfigs");
        callbacks.onGetConfigsResponse(new GetConfigsResponse(CommonStatusCodes.SUCCESS, new ConnectionConfiguration[0]));
    }

    @Override
    public void updateConfig(IWearableCallbacks callbacks, ConnectionConfiguration config) throws RemoteException {
        Log.d(TAG, "updateConfig: " + config);
        postMain(callbacks, () -> {
            wearable.updateConfiguration(config);
            callbacks.onStatus(Status.SUCCESS);
        });
    }

    @Override
    public void cancelMigration(IWearableCallbacks callbacks, ConnectionConfiguration config) throws RemoteException {
        Log.d(TAG, "unimplemented Method: cancelMigration");
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    @Override
    public void isNodeConnectionMetered(IWearableCallbacks callbacks, String nodeId) throws RemoteException {
        Log.d(TAG, "isNodeConnectionMetered: " + nodeId);
        BooleanResponse response = new BooleanResponse();
        response.statusCode = CommonStatusCodes.SUCCESS;
        callbacks.onBooleanResponse(response);
    }

    @Override
    public void setConnectionDelayConfig(IWearableCallbacks callbacks, ConnectionDelayConfig config) throws RemoteException {
        Log.d(TAG, "unimplemented Method: setConnectionDelayConfig");
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void requestConnection(IWearableCallbacks callbacks, RequestConnectionParcelable request, IConnectionCallback connectionCallback) throws RemoteException {
        Log.d(TAG, "unimplemented Method: requestConnection");
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void releaseConnectionRequest(IWearableCallbacks callbacks, IConnectionCallback connectionCallback) throws RemoteException {
        Log.d(TAG, "unimplemented Method: releaseConnectionRequest");
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void getCompanionPackageForNode(IWearableCallbacks callbacks, String nodeId) throws RemoteException {
        Log.d(TAG, "unimplemented Method: getCompanionPackageForNode");
        GetCompanionPackageForNodeResponse response = new GetCompanionPackageForNodeResponse();
        response.statusCode = CommonStatusCodes.ERROR;
        callbacks.onGetCompanionPackageForNodeResponse(response);
    }

    @Override
    public void getNodeId(IWearableCallbacks callbacks, String arg) throws RemoteException {
        Log.d(TAG, "getNodeId: " + arg);
        postMain(callbacks, () -> {
            GetNodeIdResponse response = new GetNodeIdResponse();
            response.nodeId = wearable.getNodeIdForAddress(arg);
            response.statusCode = response.nodeId != null ? CommonStatusCodes.SUCCESS : CommonStatusCodes.ERROR;
            callbacks.onGetNodeIdResponse(response);
        });
    }

    @Override
    public void getLocalCapabilities(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "getLocalCapabilities");
        postMain(callbacks, () -> {
            GetLocalCapabilitiesResponse response = new GetLocalCapabilitiesResponse();
            response.statusCode = CommonStatusCodes.SUCCESS;
            response.field2 = capabilities.getLocalCapabilities();
            response.field3 = new ArrayList<>();
            callbacks.onGetLocalCapabilitiesResponse(response);
        });
    }

    @Override
    public void initiateDataSyncTracking(IWearableCallbacks callbacks, String peerNodeId) throws RemoteException {
        Log.d(TAG, "unimplemented Method: initiateDataSyncTracking");
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    @Override
    public void flushBatchedData(IWearableCallbacks callbacks, String nodeId) throws RemoteException {
        Log.d(TAG, "unimplemented Method: flushBatchedData");
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void notifyChannelFlushed(IWearableCallbacks callbacks, String token) throws RemoteException {
        Log.d(TAG, "notifyChannelFlushed: " + token);
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void syncWifiCredentialWithArg(IWearableCallbacks callbacks, String nodeId, String arg) throws RemoteException {
        Log.d(TAG, "unimplemented Method: syncWifiCredentialWithArg: " + nodeId);
        callbacks.onStatus(Status.INTERNAL_ERROR);
    }

    @Override
    public void syncWifiCredential(IWearableCallbacks callbacks, String nodeId) throws RemoteException {
        Log.d(TAG, "unimplemented Method: syncWifiCredential: " + nodeId);
        callbacks.onStatus(Status.INTERNAL_ERROR);
    }

    @Override
    public void getEapId(IWearableCallbacks callbacks, int subscriptionId) throws RemoteException {
        Log.d(TAG, "unimplemented Method: getEapId");
        GetEapIdResponse response = new GetEapIdResponse();
        response.statusCode = WearableStatusCodes.FEATURE_DISABLED;
        callbacks.onGetEapIdResponse(response);
    }

    @Override
    public void performEapAka(IWearableCallbacks callbacks, int subscriptionId, String arg) throws RemoteException {
        Log.d(TAG, "unimplemented Method: performEapAka");
        PerformEapAkaResponse response = new PerformEapAkaResponse();
        response.statusCode = WearableStatusCodes.FEATURE_DISABLED;
        callbacks.onPerformEapAkaResponse(response);
    }

    @Override
    public void setCloudSyncSettingByNode(IWearableCallbacks callbacks, String nodeId, boolean enable) throws RemoteException {
        Log.d(TAG, "unimplemented Method: setCloudSyncSettingByNode");
        callbacks.onStatus(enable ? new Status(WearableStatusCodes.FEATURE_DISABLED) : Status.SUCCESS);
    }

    @Override
    public void getUserConsentStatus(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "getUserConsentStatus");
        postMain(callbacks, () -> callbacks.onConsentResponse(getConsentResponse()));
    }

    @Override
    public void addAccountToConsent(IWearableCallbacks callbacks, AddAccountToConsentRequest request) throws RemoteException {
        Log.d(TAG, "addAccountToConsent");
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void addSupervisedAccount(IWearableCallbacks callbacks, AddSupervisedAccountRequest request) throws RemoteException {
        Log.d(TAG, "unimplemented Method: addSupervisedAccount");
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    @Override
    public void recordOptIn(IWearableCallbacks callbacks, PrivacyRecordOptinRequest request) throws RemoteException {
        Log.d(TAG, "recordOptIn");
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void recordSwaadlOptIn(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "recordSwaadlOptIn");
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void acceptTerms(IWearableCallbacks callbacks, AcceptTermsRequest request) throws RemoteException {
        Log.d(TAG, "acceptTerms");
        postNetwork(callbacks, () -> {
            wearable.putGmsDataItem(PATH_PRIVACY_SETTINGS, new PrivacySettings.Builder().loggingOptIn(false).cloudSyncOptIn(false).locationOptIn(false).build().encode());
            callbacks.onStatus(Status.SUCCESS);
        });
    }

    @Override
    public void recordTermConsent(IWearableCallbacks callbacks, RecordTermConsentRequest request) throws RemoteException {
        Log.d(TAG, "recordTermConsent: " + request);
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void getTerms(IWearableCallbacks callbacks, int termType) throws RemoteException {
        Log.d(TAG, "getTerms: " + termType);
        GetTermsResponse response = new GetTermsResponse();
        response.terms = new ArrayList<>();
        if (termType == 0) {
            Term term = new Term();
            term.termType = 0;
            term.text = context.getString(R.string.wearable_terms_text);
            term.title = context.getString(R.string.wearable_terms_title);
            response.terms.add(term);
            response.statusCode = CommonStatusCodes.SUCCESS;
        } else {
            response.statusCode = CommonStatusCodes.INTERNAL_ERROR;
        }
        callbacks.onGetTermsResponse(response);
    }

    @Override
    public void migrateToConsentPerWatch(IWearableCallbacks callbacks, String nodeId) throws RemoteException {
        Log.d(TAG, "migrateToConsentPerWatch: " + nodeId);
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void getConsentStatus(IWearableCallbacks callbacks, ConsentStatusRequest request) throws RemoteException {
        Log.d(TAG, "getConsentStatus");
        postMain(callbacks, () -> callbacks.onConsentResponse(getConsentResponse()));
    }

    @Override
    public void recordUntetheredSupervisedAccountTransfer(IWearableCallbacks callbacks, RecordUntetheredSupervisedAccountTransferRequest request) throws RemoteException {
        Log.d(TAG, "unimplemented Method: recordUntetheredSupervisedAccountTransfer");
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    @Override
    public void associateDeviceAndAccountWithFastPair(IWearableCallbacks callbacks, String nodeId, Account account, String s1, String s2) throws RemoteException {
        Log.d(TAG, "unimplemented Method: associateDeviceAndAccountWithFastPair");
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    @Override
    public void getFastpairAccountKeys(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "unimplemented Method: getFastpairAccountKeys");
        GetFastpairAccountKeysResponse response = new GetFastpairAccountKeysResponse();
        response.statusCode = WearableStatusCodes.FEATURE_DISABLED;
        callbacks.onGetFastpairAccountKeysResponse(response);
    }

    @Override
    public void getFastpairAccountKeyByAccount(IWearableCallbacks callbacks, Account account) throws RemoteException {
        Log.d(TAG, "unimplemented Method: getFastpairAccountKeyByAccount");
        GetFastpairAccountKeyByAccountResponse response = new GetFastpairAccountKeyByAccountResponse();
        response.statusCode = WearableStatusCodes.FEATURE_DISABLED;
        callbacks.onGetFastpairAccountKeyByAccountResponse(response);
    }

    @Override
    public void getBackupSettingsSupported(IWearableCallbacks callbacks, String nodeId) throws RemoteException {
        Log.d(TAG, "getBackupSettingsSupported: " + nodeId);
        GetBackupSettingsSupportedResponse response = new GetBackupSettingsSupportedResponse();
        response.supported = false;
        callbacks.onGetBackupSettingsSupportedResponse(response);
    }

    @Override
    public void getRestoreSupported(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "getRestoreSupported");
        GetRestoreSupportedResponse response = new GetRestoreSupportedResponse();
        response.supported = false;
        callbacks.onGetRestoreSupportedResponse(response);
    }

    @Override
    public void startRestoreSession(IWearableCallbacks callbacks, StartRestoreSessionRequest request) throws RemoteException {
        Log.d(TAG, "unimplemented Method: startRestoreSession");
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    @Override
    public void saveRestoreState(IWearableCallbacks callbacks, SaveRestoreStateRequest request) throws RemoteException {
        Log.d(TAG, "unimplemented Method: saveRestoreState");
        callbacks.onStatus(new Status(WearableStatusCodes.FEATURE_DISABLED));
    }

    @Override
    public void getRestoreState(IWearableCallbacks callbacks, GetRestoreStateRequest request) throws RemoteException {
        Log.d(TAG, "unimplemented Method: getRestoreState");
        GetRestoreStateResponse response = new GetRestoreStateResponse();
        response.statusCode = WearableStatusCodes.FEATURE_DISABLED;
        callbacks.onGetRestoreStateResponse(response);
    }

    @Override
    public void getBackupEnabled(IWearableCallbacks callbacks, String nodeId) throws RemoteException {
        Log.d(TAG, "getBackupEnabled");
        BooleanResponse response = new BooleanResponse();
        response.statusCode = WearableStatusCodes.FEATURE_DISABLED;
        callbacks.onBooleanResponse(response);
    }

    @Override
    public void isRestoreOngoing(IWearableCallbacks callbacks, String nodeId) throws RemoteException {
        Log.d(TAG, "isRestoreOngoing");
        BooleanResponse response = new BooleanResponse();
        response.statusCode = CommonStatusCodes.SUCCESS;
        callbacks.onBooleanResponse(response);
    }

    @Override
    public void hasAvailableBackups(IWearableCallbacks callbacks, FetchBackupRequest request) throws RemoteException {
        Log.d(TAG, "hasAvailableBackups");
        BooleanResponse response = new BooleanResponse();
        response.statusCode = WearableStatusCodes.FEATURE_DISABLED;
        callbacks.onBooleanResponse(response);
    }

    @Override
    public void getCachedBackups(IWearableCallbacks callbacks, FetchBackupRequest request) throws RemoteException {
        Log.d(TAG, "unimplemented Method: getCachedBackups");
        GetCachedBackupsResponse response = new GetCachedBackupsResponse();
        response.statusCode = WearableStatusCodes.FEATURE_DISABLED;
        callbacks.onGetCachedBackupsResponse(response);
    }

    @Override
    public void getAppRecommendations(IWearableCallbacks callbacks, AppRecommendationsRequest request) throws RemoteException {
        Log.d(TAG, "unimplemented Method: getAppRecommendations");
        AppRecommendationsResponse response = new AppRecommendationsResponse();
        response.statusCode = WearableStatusCodes.FEATURE_DISABLED;
        callbacks.onAppRecommendationsResponse(response);
    }

    @Override
    public void setThemeForApp(IWearableCallbacks callbacks, AppTheme theme) throws RemoteException {
        Log.d(TAG, "setThemeForApp: " + packageName);
        context.getSharedPreferences(APP_THEME_PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(packageName, Base64.encodeToString(SafeParcelableSerializer.serializeToBytes(getCanonicalTheme(theme)), Base64.NO_WRAP))
                .apply();
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void getAppTheme(IWearableCallbacks callbacks, String packageName) throws RemoteException {
        Log.d(TAG, "getAppTheme: " + packageName);
        String stored = context.getSharedPreferences(APP_THEME_PREFERENCES, Context.MODE_PRIVATE).getString(packageName, null);
        AppTheme theme = stored != null ? SafeParcelableSerializer.deserializeFromBytes(Base64.decode(stored, Base64.DEFAULT), AppTheme.CREATOR) : applyThemeDefaults(new AppTheme());
        GetAppThemeResponse response = new GetAppThemeResponse();
        response.statusCode = CommonStatusCodes.SUCCESS;
        response.appTheme = theme;
        callbacks.onGetAppThemeResponse(response);
    }

    @Override
    public void getCanonicalAppTheme(IWearableCallbacks callbacks, AppTheme theme) throws RemoteException {
        Log.d(TAG, "getCanonicalAppTheme");
        GetAppThemeResponse response = new GetAppThemeResponse();
        response.statusCode = CommonStatusCodes.SUCCESS;
        response.appTheme = getCanonicalTheme(theme);
        callbacks.onGetAppThemeResponse(response);
    }

    @Override
    public void logCounter(IWearableCallbacks callbacks, LogCounterRequest request) throws RemoteException {
        Log.d(TAG, "unimplemented Method: logCounter");
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void logEvent(IWearableCallbacks callbacks, LogEventRequest request) throws RemoteException {
        Log.d(TAG, "unimplemented Method: logEvent");
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void logTimer(IWearableCallbacks callbacks, LogTimerRequest request) throws RemoteException {
        Log.d(TAG, "unimplemented Method: logTimer");
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void flushLogs(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "unimplemented Method: flushLogs");
        callbacks.onStatus(Status.SUCCESS);
    }

    @Override
    public void unknown85(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "unimplemented Method: unknown85");
        BooleanResponse response = new BooleanResponse();
        response.statusCode = WearableStatusCodes.FEATURE_DISABLED;
        callbacks.onBooleanResponse(response);
    }

    @Override
    public void unknown118(IWearableCallbacks callbacks) throws RemoteException {
        Log.d(TAG, "unimplemented Method: unknown118");
        BooleanResponse response = new BooleanResponse();
        response.statusCode = WearableStatusCodes.FEATURE_DISABLED;
        callbacks.onBooleanResponse(response);
    }

    private ConsentResponse getConsentResponse() {
        ConsentResponse response = new ConsentResponse();
        response.statusCode = CommonStatusCodes.SUCCESS;
        response.accountConsentRecords = new ArrayList<>();
        DataItemRecord record = wearable.getGmsDataItem(PATH_PRIVACY_SETTINGS);
        if (record != null && !record.deleted && record.dataItem.data != null) {
            try {
                PrivacySettings settings = PrivacySettings.ADAPTER.decode(record.dataItem.data);
                response.hasTosConsent = true;
                response.hasLoggingConsent = Boolean.TRUE.equals(settings.loggingOptIn);
                response.hasCloudSyncConsent = Boolean.TRUE.equals(settings.cloudSyncOptIn);
                response.hasLocationConsent = Boolean.TRUE.equals(settings.locationOptIn);
            } catch (IOException e) {
                Log.w(TAG, "Failed to parse privacy settings", e);
            }
        }
        return response;
    }

    private static int inRange(int value, int max) {
        return value < 0 || value > max ? 0 : value;
    }

    private static AppTheme applyThemeDefaults(AppTheme theme) {
        if (theme.field1 == 0) theme.field1 = 1;
        if (theme.field2 == 0) theme.field2 = 1;
        if (theme.field3 == 0) theme.field3 = 1;
        if (theme.field4 == 0) theme.field4 = 3;
        return theme;
    }

    private static AppTheme getCanonicalTheme(AppTheme theme) {
        AppTheme result = new AppTheme();
        result.field1 = inRange(theme.field1, 3);
        result.field2 = inRange(theme.field2, 2);
        result.field3 = inRange(theme.field3, 2);
        result.field4 = inRange(theme.field4, 3);
        result.field5 = inRange(theme.field5, 1);
        result.icon = new AppTheme.Icon();
        if (theme.icon != null) result.icon.field1 = inRange(theme.icon.field1, 3);
        result.headline = new AppTheme.Headline();
        if (theme.headline != null) {
            result.headline.field1 = inRange(theme.headline.field1, 3);
            result.headline.field2 = inRange(theme.headline.field2, 2);
            result.headline.field3 = inRange(theme.headline.field3, 3);
        }
        result.description = new AppTheme.Description();
        if (theme.description != null) {
            result.description.field1 = inRange(theme.description.field1, 3);
            result.description.field2 = inRange(theme.description.field2, 2);
        }
        result.callToAction = new AppTheme.CallToAction();
        if (theme.callToAction != null) {
            result.callToAction.field1 = inRange(theme.callToAction.field1, 2);
            result.callToAction.field2 = inRange(theme.callToAction.field2, 3);
            result.callToAction.field3 = inRange(theme.callToAction.field3, 9);
        }
        return applyThemeDefaults(result);
    }

    private static boolean isHighPriority(MessageOptions options) {
        return options != null && options.priority == 1;
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (super.onTransact(code, data, reply, flags)) return true;
        Log.d(TAG, "onTransact [unknown]: " + code + ", " + data + ", " + flags);
        return false;
    }

    public abstract class CallbackRunnable implements Runnable {
        private IWearableCallbacks callbacks;

        public CallbackRunnable(IWearableCallbacks callbacks) {
            this.callbacks = callbacks;
        }

        @Override
        public void run() {
            try {
                run(callbacks);
            } catch (RemoteException e) {
                mainHandler.post(() -> {
                    try {
                        callbacks.onStatus(Status.CANCELED);
                    } catch (RemoteException e2) {
                        Log.w(TAG, e);
                    }
                });
            }
        }

        public abstract void run(IWearableCallbacks callbacks) throws RemoteException;
    }

    public interface RemoteExceptionRunnable {
        void run() throws RemoteException;
    }
}
