/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.people

import android.os.Parcel
import android.util.Log
import com.google.android.gms.common.Feature
import com.google.android.gms.common.api.ApiMetadata
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.internal.ConnectionInfo
import com.google.android.gms.common.internal.GetServiceRequest
import com.google.android.gms.common.internal.IGmsCallbacks
import com.google.android.gms.people.contactssync.internal.DeviceContactsSyncSettingResponse
import com.google.android.gms.people.contactssync.internal.IContactsSyncServiceCallbacks
import com.google.android.gms.people.contactssync.internal.IContactsSyncThirdPartyService
import com.google.android.gms.people.contactssync.model.TetheredContactsSyncRequest
import org.microg.gms.BaseService
import org.microg.gms.common.GmsService
import org.microg.gms.utils.warnOnTransactionIssues

private const val TAG = "ContactsSyncService"
private const val SYNC_SETTING_OFF = 2

private val FEATURES = arrayOf(
    Feature("get_device_contacts_sync_setting_3p_api", 1),
    Feature("get_device_contacts_sync_setting_action_3p_api", 1),
    Feature("device_contacts_sync_setting_changed_listener_3p_api", 1),
    Feature("trigger_tethered_contacts_sync_2p_api", 1),
)

class ContactsSyncThirdPartyService : BaseService(TAG, GmsService.CONTACT_SYNC) {
    override fun handleServiceRequest(callback: IGmsCallbacks, request: GetServiceRequest, service: GmsService) {
        callback.onPostInitCompleteWithConnectionInfo(CommonStatusCodes.SUCCESS, ContactsSyncThirdPartyServiceImpl().asBinder(), ConnectionInfo().apply {
            features = FEATURES
        })
    }
}

class ContactsSyncThirdPartyServiceImpl : IContactsSyncThirdPartyService.Stub() {
    override fun getDeviceContactsSyncSetting(callbacks: IContactsSyncServiceCallbacks, apiMetadata: ApiMetadata?) {
        Log.d(TAG, "getDeviceContactsSyncSetting")
        callbacks.onDeviceContactsSyncSetting(Status.SUCCESS, DeviceContactsSyncSettingResponse().apply { state = SYNC_SETTING_OFF }, null)
    }

    override fun registerSyncSettingUpdatedListener(callbacks: IContactsSyncServiceCallbacks, apiMetadata: ApiMetadata?) {
        Log.d(TAG, "Not yet implemented: registerSyncSettingUpdatedListener")
        callbacks.onStatus(Status.SUCCESS, null)
    }

    override fun unregisterSyncSettingUpdatedListener(callbacks: IContactsSyncServiceCallbacks, apiMetadata: ApiMetadata?) {
        Log.d(TAG, "Not yet implemented: unregisterSyncSettingUpdatedListener")
        callbacks.onStatus(Status.SUCCESS, null)
    }

    override fun getDeviceContactsSyncSettingAction(callbacks: IContactsSyncServiceCallbacks, apiMetadata: ApiMetadata?) {
        Log.d(TAG, "Not yet implemented: getDeviceContactsSyncSettingAction")
        callbacks.onSyncSettingAction(Status.INTERNAL_ERROR, null, null)
    }

    override fun triggerTetheredContactsSync(callbacks: IContactsSyncServiceCallbacks, request: TetheredContactsSyncRequest?, apiMetadata: ApiMetadata?) {
        Log.d(TAG, "Not yet implemented: triggerTetheredContactsSync")
        callbacks.onTetheredSyncStatus(Status.INTERNAL_ERROR, null, null)
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean =
        warnOnTransactionIssues(code, reply, flags, TAG) { super.onTransact(code, data, reply, flags) }
}
