package org.microg.gms.cryptauth

import android.accounts.Account
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.util.Log
import com.google.android.gms.BuildConfig
import com.google.android.gms.common.Scopes
import com.google.security.cryptauth.lib.securemessage.SigScheme
import cryptauthv2.ApplicationSpecificMetadata
import cryptauthv2.ClientAppMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import org.microg.gms.auth.AuthConstants
import org.microg.gms.auth.AuthManager
import org.microg.gms.checkin.LastCheckinInfo
import org.microg.gms.common.Constants
import org.microg.gms.common.DeviceConfiguration
import org.microg.gms.common.Utils
import org.microg.gms.gcm.GcmConstants
import org.microg.gms.gcm.GcmDatabase
import org.microg.gms.gcm.RegisterRequest
import org.microg.gms.gcm.completeRegisterRequest
import org.microg.gms.profile.Build
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt

private const val TAG = "CryptAuthFlow"

private const val GCM_REGISTER_SENDER = "16502139086"
private const val GCM_REGISTER_SUBTYPE = "16502139086"
private const val GCM_REGISTER_SUBSCRIPTION = "16502139086"
private const val GCM_REGISTER_SCOPE = "GCM"
private const val GCM_REGISTER_CLIV = "iid-202414000"
private const val GCM_REGISTER_INFO = "0wqs6iYsl_URQEb1aBJ6XhzCbHSr-hg"

private const val RSA_KEY_SIZE = 2048

private const val AFTER_REQUEST_DELAY = 5000L

private const val AUTHZEN_PUBLIC_KEY_NAME = "PublicKey"
private val AUTHZEN_PUBLIC_KEY_HANDLE = "device_key".toByteArray()
private const val AUTHZEN_SYMMETRIC_KEY_NAME = "authzen"
private val ENROLLMENT_SALT = "CryptAuth Enrollment".toByteArray()
private val KEY_PROOF_SALT = "CryptAuth Key Proof".toByteArray()
private const val AUTHZEN_PREFERENCES = "cryptauth_authzen"
private val otpCounterLock = Any()
private val OTP_SALT = MessageDigest.getInstance("SHA-256").digest("GoogleOfflineOTP".toByteArray())

suspend fun Context.sendDeviceScreenlockState(account: Account): Boolean = sendDeviceScreenlockState(account.name)

suspend fun Context.sendDeviceScreenlockState(accountName: String): Boolean {

    // Ensure that device is checked in
    val checkinInfo = LastCheckinInfo.read(this)
    if (checkinInfo.androidId == 0L) {
        Log.w(TAG, "Device is not checked in, as it doesn't have an Android ID. Cannot perform cryptauth flow.")
        return false
    }

    // Instance ID token for use in CryptAuth query
    val instanceId = generateAppId()
    val instanceToken = registerForCryptAuth(checkinInfo, instanceId).getString(GcmConstants.EXTRA_REGISTRATION_ID)
    if (instanceToken == null) {
        Log.w(TAG, "No instance ID was gathered. Is GCM enabled, has there been a checkin?")
        return false
    }

    // Auth token for use in CryptAuth query
    val authToken = authenticateForCryptAuth(accountName)
    if (authToken == null) {
        Log.w(TAG, "Authentication failed. Cannot perform cryptauth flow.")
        return false
    }

    val cryptAuthSyncKeysResult = cryptAuthSyncKeys(authToken, instanceId, instanceToken, checkinInfo.androidId)
    if (cryptAuthSyncKeysResult == null
        || !cryptAuthSyncKeysResult.has(CRYPTAUTH_FIELD_SESSION_ID)
        || cryptAuthSyncKeysResult.get(CRYPTAUTH_FIELD_SESSION_ID) !is String
    ) {
        Log.w(TAG, "CryptAuth syncKeys failed. Cannot complete flow.")
        return false
    }

    val session: String = cryptAuthSyncKeysResult.get(CRYPTAUTH_FIELD_SESSION_ID) as String
    val cryptAuthEnrollKeysResult = cryptAuthEnrollKeys(authToken, session)

    return if (cryptAuthEnrollKeysResult != null) {
        /* Give Google server some time to process the new information.
         * This leads to higher success rate compared to sending
         * the next query immediately after this one. Tests show that it
         * makes sense to wait multiple seconds.
         */
        delay(AFTER_REQUEST_DELAY)
        true
    } else {
        false
    }
}

suspend fun Context.enrollAuthzenKeys(accountName: String): Boolean {
    val checkinInfo = LastCheckinInfo.read(this)
    if (checkinInfo.androidId == 0L) {
        Log.w(TAG, "Device is not checked in, as it doesn't have an Android ID. Cannot perform cryptauth flow.")
        return false
    }
    val preferences = getSharedPreferences(AUTHZEN_PREFERENCES, Context.MODE_PRIVATE)
    val instanceId = preferences.getString("instance_id", null)
        ?: generateAppId().also { preferences.edit().putString("instance_id", it).apply() }
    val instanceToken = registerForCryptAuth(checkinInfo, instanceId).getString(GcmConstants.EXTRA_REGISTRATION_ID)
    if (instanceToken == null) {
        Log.w(TAG, "No instance ID was gathered. Is GCM enabled, has there been a checkin?")
        return false
    }
    val authToken = authenticateForCryptAuth(accountName)
    if (authToken == null) {
        Log.w(TAG, "Authentication failed. Cannot perform cryptauth flow.")
        return false
    }

    val publicKeyHandle = Base64.encodeToString(AUTHZEN_PUBLIC_KEY_HANDLE, Base64.NO_WRAP)
    val symmetricKeyHandle = preferences.getString("symmetric_key_handle_$accountName", null)
    val syncResult = cryptAuthSyncKeys(authToken, instanceId, instanceToken, checkinInfo.androidId, jsonArrayOf(
        jsonObjectOf(
            "keyName" to AUTHZEN_PUBLIC_KEY_NAME,
            "keyHandles" to if (preferences.contains("private_key_$accountName")) jsonArrayOf(publicKeyHandle) else JSONArray()
        ),
        jsonObjectOf(
            "keyName" to AUTHZEN_SYMMETRIC_KEY_NAME,
            "keyHandles" to if (symmetricKeyHandle != null) jsonArrayOf(symmetricKeyHandle) else JSONArray()
        ),
    ))
    val session = syncResult?.optString(CRYPTAUTH_FIELD_SESSION_ID)
    if (session.isNullOrEmpty()) {
        Log.w(TAG, "CryptAuth syncKeys failed. Cannot complete flow.")
        return false
    }
    val sessionId = decodeBase64(session)
    val responses = syncResult.optJSONArray("syncSingleKeyResponses")

    val enrollRequests = JSONArray()
    val editor = preferences.edit()
    var clientEphemeralDh = ""
    if (responses?.optJSONObject(0)?.optString("keyCreation") == "ACTIVE") {
        val keyPair = loadAuthzenKeyPair(accountName) ?: generateEcP256KeyPair()
        val keyProof = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(KEY_PROOF_SALT)
            update(sessionId)
            sign()
        }
        enrollRequests.put(jsonObjectOf(
            "keyName" to AUTHZEN_PUBLIC_KEY_NAME,
            "newKeyHandle" to publicKeyHandle,
            "keyMaterial" to Base64.encodeToString(encodeGenericPublicKey(keyPair.public as ECPublicKey), Base64.NO_WRAP),
            "keyProof" to Base64.encodeToString(keyProof, Base64.NO_WRAP),
        ))
        editor.putString("private_key_$accountName", Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP))
        editor.putString("public_key_$accountName", Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP))
    }
    if (responses?.optJSONObject(1)?.optString("keyCreation") == "ACTIVE") {
        val serverDh = decodeGenericPublicKey(decodeBase64(syncResult.optString("serverEphemeralDh")))
        val clientKeyPair = generateEcP256KeyPair()
        val sharedSecret = MessageDigest.getInstance("SHA-256").digest(KeyAgreement.getInstance("ECDH").run {
            init(clientKeyPair.private)
            doPhase(serverDh, true)
            generateSecret()
        })
        val key = hkdf(sharedSecret, ENROLLMENT_SALT, AUTHZEN_SYMMETRIC_KEY_NAME.toByteArray())
        val keyProof = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(hkdf(key, KEY_PROOF_SALT, AUTHZEN_SYMMETRIC_KEY_NAME.toByteArray()), "HmacSHA256"))
            doFinal(sessionId)
        }
        val keyHandle = ByteArray(32).also { SecureRandom().nextBytes(it) }
        keyHandle[0] = (keyHandle[0].toInt() and 0x7f).toByte()
        val newKeyHandle = Base64.encodeToString(keyHandle, Base64.NO_WRAP)
        enrollRequests.put(jsonObjectOf(
            "keyName" to AUTHZEN_SYMMETRIC_KEY_NAME,
            "newKeyHandle" to newKeyHandle,
            "keyMaterial" to Base64.encodeToString(key, Base64.NO_WRAP),
            "keyProof" to Base64.encodeToString(keyProof, Base64.NO_WRAP),
        ))
        clientEphemeralDh = Base64.encodeToString(encodeGenericPublicKey(clientKeyPair.public as ECPublicKey), Base64.NO_WRAP)
        editor.putString("symmetric_key_handle_$accountName", newKeyHandle)
        editor.putString("symmetric_key_$accountName", Base64.encodeToString(key, Base64.NO_WRAP))
        editor.remove("otp_counter_$accountName")
    }
    if (enrollRequests.length() == 0) {
        Log.d(TAG, "No new authzen keys needed for $accountName")
        return true
    }
    if (cryptAuthEnrollKeys(authToken, session, enrollRequests, clientEphemeralDh) == null) {
        Log.w(TAG, "CryptAuth enrollKeys failed.")
        return false
    }
    editor.apply()
    Log.d(TAG, "Enrolled ${enrollRequests.length()} authzen keys for $accountName")
    return true
}

fun Context.loadAuthzenKeyPair(accountName: String): KeyPair? {
    val preferences = getSharedPreferences(AUTHZEN_PREFERENCES, Context.MODE_PRIVATE)
    val privateKey = preferences.getString("private_key_$accountName", null) ?: return null
    val publicKey = preferences.getString("public_key_$accountName", null) ?: return null
    val keyFactory = KeyFactory.getInstance("EC")
    return KeyPair(
        keyFactory.generatePublic(X509EncodedKeySpec(Base64.decode(publicKey, Base64.DEFAULT))),
        keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privateKey, Base64.DEFAULT)))
    )
}

fun Context.loadAuthzenSymmetricKey(accountName: String): SecretKey? {
    val preferences = getSharedPreferences(AUTHZEN_PREFERENCES, Context.MODE_PRIVATE)
    val key = preferences.getString("symmetric_key_$accountName", null) ?: return null
    return SecretKeySpec(Base64.decode(key, Base64.DEFAULT), "AES")
}

/**
 * Returns the current counter for assertions of the account and advances it. The initial value is derived from the
 * authzen key.
 */
fun Context.getAndAdvanceOtpCounter(accountName: String): Long? {
    val symmetricKey = loadAuthzenSymmetricKey(accountName) ?: return null
    val preferences = getSharedPreferences(AUTHZEN_PREFERENCES, Context.MODE_PRIVATE)
    synchronized(otpCounterLock) {
        val counter = if (preferences.contains("otp_counter_$accountName")) {
            preferences.getLong("otp_counter_$accountName", 0)
        } else {
            val secret = hkdf(symmetricKey.encoded, OTP_SALT, "THOTP".toByteArray())
            ByteBuffer.wrap(hkdf(secret, OTP_SALT, "THOTP-Counter".toByteArray())).order(ByteOrder.LITTLE_ENDIAN).long and Long.MAX_VALUE
        }
        preferences.edit().putLong("otp_counter_$accountName", if (counter == Long.MAX_VALUE) 0 else counter + 1).commit()
        return counter
    }
}

fun Context.signcryptWithAuthzenKeys(accountName: String, publicMetadata: ByteArray, body: ByteArray): ByteArray? {
    val keyPair = loadAuthzenKeyPair(accountName) ?: return null
    val symmetricKey = loadAuthzenSymmetricKey(accountName) ?: return null
    return buildSignCryptedMessage(
        signingKey = keyPair.private,
        sigScheme = SigScheme.ECDSA_P256_SHA256,
        encryptionKey = symmetricKey,
        body = body,
        publicMetadata = publicMetadata,
        verificationKeyId = encodeGenericPublicKey(keyPair.public as ECPublicKey)
    ).encode()
}

private fun decodeBase64(value: String): ByteArray = Base64.decode(value.replace('-', '+').replace('_', '/'), Base64.DEFAULT)

private suspend fun Context.registerForCryptAuth(checkinInfo: LastCheckinInfo, instanceId: String): Bundle = completeRegisterRequest(
    context = this,
    database = GcmDatabase(this),
    request = RegisterRequest().build(this)
        .checkin(checkinInfo)
        .app("com.google.android.gms", CERTIFICATE.lowercase(), BuildConfig.VERSION_CODE)
        .sender(GCM_REGISTER_SENDER)
        .extraParam("subscription", GCM_REGISTER_SUBSCRIPTION)
        .extraParam("X-subscription", GCM_REGISTER_SUBSCRIPTION)
        .extraParam("subtype", GCM_REGISTER_SUBTYPE)
        .extraParam("X-subtype", GCM_REGISTER_SUBTYPE)
        .extraParam("app_ver", BuildConfig.VERSION_CODE.toString())
        .extraParam("osv", "29")
        .extraParam("cliv", GCM_REGISTER_CLIV)
        .extraParam("gmsv", BuildConfig.VERSION_CODE.toString())
        .extraParam("appid", instanceId)
        .extraParam("scope", GCM_REGISTER_SCOPE)
        .extraParam("app_ver_name","%09d".format(BuildConfig.VERSION_CODE).let {
            "${it.substring(0, 2)}.${it.substring(2, 4)}.${it.substring(4, 6)} (190800-{{cl}})"
        })
        .info(GCM_REGISTER_INFO)
)

private suspend fun Context.authenticateForCryptAuth(accountName: String): String? {
    val cryptAuthServiceOauth2 = AuthConstants.SCOPE_OAUTH2 + Scopes.CRYPTAUTH
    val authManager = AuthManager(this, accountName, Constants.GMS_PACKAGE_NAME, cryptAuthServiceOauth2)
    return withContext(Dispatchers.IO) { authManager.requestAuth(false).auth }
}

/**
 * Generates an app / instance ID based on the hash of the public key of an RSA keypair.
 * The key itself is never used.
 */
fun generateAppId(): String {
    val rsaGenerator = KeyPairGenerator.getInstance("RSA")
    rsaGenerator.initialize(RSA_KEY_SIZE)
    val keyPair = rsaGenerator.generateKeyPair()

    val digest = MessageDigest.getInstance("SHA1").digest(keyPair.public.encoded)
    digest[0] = ((112 + (0xF and digest[0].toInt())) and 0xFF).toByte()
    return Base64.encodeToString(
        digest, 0, 8, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
}

fun Context.isLockscreenConfigured(): Boolean {
    val service: KeyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    return if (android.os.Build.VERSION.SDK_INT >= 23) {
        service.isDeviceSecure
    } else {
        service.isKeyguardSecure
    }
}
