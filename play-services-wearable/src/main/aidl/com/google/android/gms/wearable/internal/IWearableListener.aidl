package com.google.android.gms.wearable.internal;

import com.google.android.gms.common.data.DataHolder;
import com.google.android.gms.wearable.internal.AmsEntityUpdateParcelable;
import com.google.android.gms.wearable.internal.AncsNotificationParcelable;
import com.google.android.gms.wearable.internal.CapabilityInfoParcelable;
import com.google.android.gms.wearable.internal.ChannelEventParcelable;
import com.google.android.gms.wearable.internal.MessageEventParcelable;
import com.google.android.gms.wearable.internal.NodeParcelable;

import com.google.android.gms.wearable.internal.ConnectionStateEventParcelable;
import com.google.android.gms.wearable.internal.ConsentResponse;
import com.google.android.gms.wearable.internal.GetListenerServiceRequest;
import com.google.android.gms.wearable.internal.IRpcResponseCallback;
import com.google.android.gms.wearable.internal.IServiceResponseCallback;
import com.google.android.gms.wearable.internal.NodeMigratedEventParcelable;
import com.google.android.gms.wearable.internal.RestoreCompletedEventParcelable;

interface IWearableListener {
    void onDataChanged(in DataHolder data) = 0;
    void onMessageReceived(in MessageEventParcelable messageEvent) = 1;
    void onPeerConnected(in NodeParcelable node) = 2;
    void onPeerDisconnected(in NodeParcelable node) = 3;
    void onConnectedNodes(in List<NodeParcelable> nodes) = 4;
    void onNotificationReceived(in AncsNotificationParcelable notification) = 5;
    void onChannelEvent(in ChannelEventParcelable channelEvent) = 6;
    void onConnectedCapabilityChanged(in CapabilityInfoParcelable capabilityInfo) = 7;
    void onEntityUpdate(in AmsEntityUpdateParcelable update) = 8;

    void onRequestReceived(in MessageEventParcelable request, IRpcResponseCallback callback) = 12;
    void onConsentChanged(in ConsentResponse consent) = 13;
    void onConnectionStateChanged(in ConnectionStateEventParcelable event) = 14;
    void onNodeMigrated(in NodeMigratedEventParcelable event) = 15;
    void onRestoreCompleted(in RestoreCompletedEventParcelable event) = 16;
    boolean getListenerService(in GetListenerServiceRequest request, IServiceResponseCallback callback) = 17;
}
