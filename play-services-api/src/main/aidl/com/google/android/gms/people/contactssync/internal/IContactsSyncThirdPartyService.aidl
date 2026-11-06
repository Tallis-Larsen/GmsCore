/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.people.contactssync.internal;

import com.google.android.gms.common.api.ApiMetadata;
import com.google.android.gms.people.contactssync.internal.IContactsSyncServiceCallbacks;
import com.google.android.gms.people.contactssync.model.TetheredContactsSyncRequest;

interface IContactsSyncThirdPartyService {
    void getDeviceContactsSyncSetting(IContactsSyncServiceCallbacks callbacks, in ApiMetadata apiMetadata) = 224701;
    void registerSyncSettingUpdatedListener(IContactsSyncServiceCallbacks callbacks, in ApiMetadata apiMetadata) = 224702;
    void unregisterSyncSettingUpdatedListener(IContactsSyncServiceCallbacks callbacks, in ApiMetadata apiMetadata) = 224703;
    void getDeviceContactsSyncSettingAction(IContactsSyncServiceCallbacks callbacks, in ApiMetadata apiMetadata) = 224704;
    void triggerTetheredContactsSync(IContactsSyncServiceCallbacks callbacks, in TetheredContactsSyncRequest request, in ApiMetadata apiMetadata) = 262100;
}
