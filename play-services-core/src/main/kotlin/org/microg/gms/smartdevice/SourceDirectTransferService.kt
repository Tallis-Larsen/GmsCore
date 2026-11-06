/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.smartdevice

import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.Feature
import com.google.android.gms.common.api.ApiMetadata
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.internal.ConnectionInfo
import com.google.android.gms.common.internal.GetServiceRequest
import com.google.android.gms.common.internal.IGmsCallbacks
import com.google.android.gms.smartdevice.d2d.internal.IDirectTransferListener
import com.google.android.gms.smartdevice.directtransfer.DirectTransferConfigurations
import com.google.android.gms.smartdevice.directtransfer.internal.IDirectTransferCallback
import com.google.android.gms.smartdevice.directtransfer.internal.ISourceDirectTransferService
import org.microg.gms.BaseService
import org.microg.gms.auth.AuthConstants
import org.microg.gms.common.GmsService
import org.microg.gms.common.GooglePackagePermission
import org.microg.gms.common.PackageUtils
import org.microg.gms.utils.warnOnTransactionIssues

private const val TAG = "SourceDirectTransferService"

class SourceDirectTransferService : BaseService(TAG, GmsService.SMARTDEVICE_SOURCE_DIRECT_TRANSFER) {
    override fun handleServiceRequest(callback: IGmsCallbacks, request: GetServiceRequest, service: GmsService) {
        val packageName = PackageUtils.getAndCheckCallingPackage(this, request.packageName)
            ?: throw IllegalArgumentException("Missing package name")
        if (!PackageUtils.callerHasGooglePackagePermission(this, GooglePackagePermission.AUTH)) {
            callback.onPostInitComplete(ConnectionResult.API_DISABLED, null, null)
            return
        }
        callback.onPostInitCompleteWithConnectionInfo(CommonStatusCodes.SUCCESS, SourceDirectTransferServiceImpl(this, packageName).asBinder(), ConnectionInfo().apply {
            features = arrayOf(Feature("source_direct_transfer_api", 1))
        })
    }
}

class SourceDirectTransferServiceImpl(private val context: Context, private val packageName: String) : ISourceDirectTransferService.Stub() {
    override fun startDirectTransfer(
        callback: IDirectTransferCallback,
        configurations: DirectTransferConfigurations,
        fileDescriptors: Array<ParcelFileDescriptor>,
        listener: IDirectTransferListener,
        apiMetadata: ApiMetadata?
    ) {
        PackageUtils.assertGooglePackagePermission(context, GooglePackagePermission.AUTH)
        Log.d(TAG, "startDirectTransfer: $packageName $configurations")
        val status = synchronized(SourceDirectTransferServiceImpl) {
            val deviceAccounts = AccountManager.get(context).getAccountsByType(AuthConstants.DEFAULT_ACCOUNT_TYPE).map { it.name }
            val accounts = configurations.bootstrapAccounts.orEmpty().filter { it.name in deviceAccounts }
            if (currentTransfer != null || accounts.isEmpty()) {
                fileDescriptors.forEach { runCatching { it.close() } }
                return@synchronized Status(if (currentTransfer != null) STATUS_TRANSFER_IN_PROGRESS else STATUS_NO_ACCOUNTS)
            }
            currentTransfer = SourceDirectTransfer(context, accounts, fileDescriptors[0], fileDescriptors[1], listener) {
                synchronized(SourceDirectTransferServiceImpl) { if (currentTransfer == it) currentTransfer = null }
            }.also { it.start() }
            Status.SUCCESS
        }
        callback.onResult(status)
    }

    override fun abortDirectTransfer(callback: IDirectTransferCallback, apiMetadata: ApiMetadata?) {
        PackageUtils.assertGooglePackagePermission(context, GooglePackagePermission.AUTH)
        Log.d(TAG, "abortDirectTransfer: $packageName")
        val transfer = synchronized(SourceDirectTransferServiceImpl) { currentTransfer }
        transfer?.cancel()
        callback.onResult(if (transfer != null) Status.SUCCESS else Status(STATUS_NO_TRANSFER_IN_PROGRESS))
    }

    override fun isTransferInProgress(callback: IDirectTransferCallback, bundle: Bundle?, apiMetadata: ApiMetadata?) {
        PackageUtils.assertGooglePackagePermission(context, GooglePackagePermission.AUTH)
        val inProgress = synchronized(SourceDirectTransferServiceImpl) { currentTransfer != null }
        callback.onResult(if (inProgress) Status.SUCCESS else Status(STATUS_NO_TRANSFER_IN_PROGRESS))
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean =
        warnOnTransactionIssues(code, reply, flags, TAG) { super.onTransact(code, data, reply, flags) }

    companion object {
        private var currentTransfer: SourceDirectTransfer? = null
    }
}
