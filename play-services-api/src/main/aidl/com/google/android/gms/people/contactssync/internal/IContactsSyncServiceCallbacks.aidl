/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.people.contactssync.internal;

import com.google.android.gms.common.api.ApiMetadata;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.people.contactssync.internal.DeviceContactsSyncSettingResponse;
import com.google.android.gms.people.contactssync.model.TetheredSyncStatus;

interface IContactsSyncServiceCallbacks {
    void onDeviceContactsSyncSetting(in Status status, in DeviceContactsSyncSettingResponse response, in ApiMetadata apiMetadata) = 6;
    void onSyncSettingUpdated() = 17;
    void onStatus(in Status status, in ApiMetadata apiMetadata) = 18;
    void onSyncSettingAction(in Status status, String action, in ApiMetadata apiMetadata) = 20;
    void onTetheredSyncStatus(in Status status, in TetheredSyncStatus syncStatus, in ApiMetadata apiMetadata) = 39;
}
