/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.smartdevice

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.google.security.cryptauth.lib.securegcm.GcmMetadata
import com.google.security.cryptauth.lib.securegcm.IdentityAssertion
import com.google.security.cryptauth.lib.securegcm.Type
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import org.json.JSONStringer
import org.microg.gms.checkin.LastCheckinInfo
import org.microg.gms.common.Constants
import org.microg.gms.cryptauth.encodeGenericPublicKey
import org.microg.gms.cryptauth.getAndAdvanceOtpCounter
import org.microg.gms.cryptauth.isLockscreenConfigured
import org.microg.gms.cryptauth.loadAuthzenKeyPair
import org.microg.gms.cryptauth.signcryptWithAuthzenKeys
import org.microg.gms.profile.Build
import org.microg.gms.smartdevice.proto.LockScreenSignals
import org.microg.gms.smartdevice.proto.SourceDeviceSignals
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey

private const val TAG = "AccountBootstrap"

private const val ASSERTION_TYPE_IDENTITY = 1
private const val USER_APPROVED = 1

private fun ByteArray.toBase64() = Base64.encodeToString(this, Base64.NO_WRAP)
private fun ByteArray.toBase64Url() = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

fun Context.createUserBootstrapInfo(accountName: String): JSONObject? {
    val keyPair = loadAuthzenKeyPair(accountName) ?: return null
    return JSONObject()
        .put("accountIdentifier", accountName)
        .put("userPublicKey", encodeGenericPublicKey(keyPair.public as ECPublicKey).toBase64())
}

fun Context.createAssertion(challenge: JSONObject, lockScreenConfirmedAt: Long?): JSONObject? {
    val accountName = challenge.optString("accountIdentifier")
    if (challenge.optInt("status") != 0) {
        Log.w(TAG, "Challenge for $accountName has status ${challenge.optInt("status")}")
        return null
    }
    val challengeBytes = Base64.decode(challenge.optString("challenge"), Base64.DEFAULT)
    val lockScreenSecure = isLockscreenConfigured()
    val signals = SourceDeviceSignals(
        android_id = java.lang.Long.toHexString(LastCheckinInfo.read(this).androidId),
        device = Build.DEVICE,
        model = Build.MODEL,
        sdk_version = Build.VERSION.SDK_INT.toString(),
        gms_version = Constants.GMS_VERSION_CODE.toString(),
        lock_screen = LockScreenSignals(
            secure = lockScreenSecure,
            confirmed = lockScreenConfirmedAt != null,
            type = if (lockScreenSecure) 2 else 1,
            seconds_since_unlock = lockScreenConfirmedAt?.let { (SystemClock.elapsedRealtime() - it) / 1000 } ?: -1,
            seconds_since_setup = -1
        )
    )
    val clientData = JSONStringer().`object`()
        .key("typ").value("navigator.id.getAssertion")
        .key("challenge").value(challengeBytes.toBase64Url())
        .key("source_device_signals").value(signals.encode().toBase64Url())
        .key("account_identifier").value(accountName)
        .endObject().toString().toByteArray()
    val counter = getAndAdvanceOtpCounter(accountName) ?: return null.also { Log.w(TAG, "No authzen key for $accountName") }
    val body = IdentityAssertion(
        browser_data_hash = MessageDigest.getInstance("SHA-256").digest(clientData).toByteString(),
        counter = counter,
        user_approval = USER_APPROVED
    ).encode()
    val header = GcmMetadata(type = Type.GCMV1_IDENTITY_ASSERTION, version = 1).encode()
    val encryptedAssertion = signcryptWithAuthzenKeys(accountName, header, body) ?: return null
    return JSONObject()
        .put("accountIdentifier", accountName)
        .put("clientData", clientData.toBase64())
        .put("encryptedUserAssertion", encryptedAssertion.toBase64())
        .put("challengeSessionState", challenge.optString("challengeSessionState"))
        .put("challenge", challenge.optString("challenge"))
        .put("assertionType", ASSERTION_TYPE_IDENTITY)
}
