/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.auth.aang

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Parcel
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.aang.AppRestriction
import com.google.android.gms.auth.aang.AppRestrictionState
import com.google.android.gms.auth.aang.FetchAppRestrictionRequest
import com.google.android.gms.auth.aang.GetAccountsRequest
import com.google.android.gms.auth.aang.GetAccountsResponse
import com.google.android.gms.auth.aang.GetTokenRequest
import com.google.android.gms.auth.aang.GetTokenResponse
import com.google.android.gms.auth.aang.GoogleAccount
import com.google.android.gms.auth.aang.HasCapabilitiesRequest
import com.google.android.gms.auth.aang.internal.IGoogleAuthAangCallbacks
import com.google.android.gms.auth.aang.internal.IGoogleAuthAangService
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.Feature
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.android.gms.common.api.internal.IStatusCallback
import com.google.android.gms.common.internal.ConnectionInfo
import com.google.android.gms.common.internal.GetServiceRequest
import com.google.android.gms.common.internal.IGmsCallbacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.microg.gms.BaseService
import org.microg.gms.auth.AuthConstants
import org.microg.gms.auth.AuthManager
import org.microg.gms.auth.capabilities.HasCapabilitiesHandler
import org.microg.gms.auth.capabilities.UserDataKeys
import org.microg.gms.common.GmsService
import org.microg.gms.common.GooglePackagePermission
import org.microg.gms.common.PackageUtils
import org.microg.gms.utils.warnOnTransactionIssues
import java.io.IOException

private const val TAG = "GoogleAuthAangService"

private val FEATURES = arrayOf(
    Feature("google_auth_api", 1),
)

class GoogleAuthAangService : BaseService(TAG, GmsService.GOOGLE_AUTH_AANG) {
    override fun handleServiceRequest(callback: IGmsCallbacks, request: GetServiceRequest, service: GmsService) {
        val packageName = PackageUtils.getAndCheckCallingPackage(this, request.packageName)
            ?: throw IllegalArgumentException("Missing package name")
        if (!PackageUtils.callerHasGooglePackagePermission(this, GooglePackagePermission.ACCOUNT)) {
            callback.onPostInitComplete(ConnectionResult.API_DISABLED, null, null)
            return
        }
        callback.onPostInitCompleteWithConnectionInfo(CommonStatusCodes.SUCCESS, GoogleAuthAangServiceImpl(this, packageName, lifecycle).asBinder(), ConnectionInfo().apply {
            features = FEATURES
        })
    }
}

class GoogleAuthAangServiceImpl(
    private val context: Context,
    private val packageName: String,
    override val lifecycle: Lifecycle
) : IGoogleAuthAangService.Stub(), LifecycleOwner {
    private val accountManager = AccountManager.get(context)

    private fun GoogleAccount.toAccount() = Account(name, type ?: AuthConstants.DEFAULT_ACCOUNT_TYPE)

    private fun launchSafely(block: () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, e)
            }
        }
    }

    override fun getAccounts(callbacks: IGoogleAuthAangCallbacks, request: GetAccountsRequest) {
        PackageUtils.assertGooglePackagePermission(context, GooglePackagePermission.ACCOUNT)
        Log.d(TAG, "getAccounts: $packageName ${request.accountType}")
        val accounts = accountManager.getAccountsByType(request.accountType ?: AuthConstants.DEFAULT_ACCOUNT_TYPE).map { account ->
            GoogleAccount().apply {
                obfuscatedGaiaId = accountManager.getUserData(account, UserDataKeys.GOOGLE_USER_ID)
                type = account.type
                name = account.name
            }
        }
        callbacks.onGetAccounts(Status.SUCCESS, GetAccountsResponse().apply { this.accounts = ArrayList(accounts) })
    }

    override fun getToken(callbacks: IGoogleAuthAangCallbacks, request: GetTokenRequest) {
        PackageUtils.assertGooglePackagePermission(context, GooglePackagePermission.AUTH)
        val service = when {
            !request.oauth2Scopes.isNullOrEmpty() -> "oauth2:" + request.oauth2Scopes.joinToString(" ")
            !request.webLoginScopes.isNullOrEmpty() -> "weblogin:" + request.webLoginScopes.joinToString(" ")
            !request.audiences.isNullOrEmpty() -> "audience:server:client_id:" + request.audiences.first()
            else -> request.services?.firstOrNull()
        }
        val account = request.account
        if (service == null || account == null) return callbacks.onGetToken(Status(CommonStatusCodes.DEVELOPER_ERROR), null)
        launchSafely {
            val status = try {
                val response = AuthManager(context, account.name, packageName, service).requestAuthWithBackgroundResolution(false)
                if (response.auth != null) {
                    callbacks.onGetToken(Status.SUCCESS, GetTokenResponse().apply { token = response.auth })
                    return@launchSafely
                }
                Status.INTERNAL_ERROR
            } catch (e: IOException) {
                Log.w(TAG, e)
                Status(CommonStatusCodes.NETWORK_ERROR)
            } catch (e: RuntimeException) {
                Log.w(TAG, e)
                Status.INTERNAL_ERROR
            }
            callbacks.onGetToken(status, null)
        }
    }

    override fun clearToken(callback: IStatusCallback, token: String) {
        PackageUtils.assertGooglePackagePermission(context, GooglePackagePermission.AUTH)
        accountManager.invalidateAuthToken(AuthConstants.DEFAULT_ACCOUNT_TYPE, token)
        callback.onResult(Status.SUCCESS)
    }

    override fun hasCapabilities(callbacks: IGoogleAuthAangCallbacks, request: HasCapabilitiesRequest) {
        PackageUtils.assertGooglePackagePermission(context, GooglePackagePermission.ACCOUNT)
        launchSafely {
            val result = HasCapabilitiesHandler(context).handle(com.google.android.gms.auth.HasCapabilitiesRequest().apply {
                account = request.account.toAccount()
                capabilities = request.capabilities?.toTypedArray()
            })
            Log.d(TAG, "hasCapabilities: ${request.capabilities} = $result")
            callbacks.onHasCapabilities(Status.SUCCESS, result)
        }
    }

    override fun fetchAppRestriction(callbacks: IGoogleAuthAangCallbacks, request: FetchAppRestrictionRequest) {
        PackageUtils.assertGooglePackagePermission(context, GooglePackagePermission.ACCOUNT)
        callbacks.onFetchAppRestriction(Status.SUCCESS, AppRestriction().apply { state = AppRestrictionState() })
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean =
        warnOnTransactionIssues(code, reply, flags, TAG) { super.onTransact(code, data, reply, flags) }
}
