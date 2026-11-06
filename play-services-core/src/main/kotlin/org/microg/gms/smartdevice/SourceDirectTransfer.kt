/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.smartdevice

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import android.os.SystemClock
import android.util.Log
import androidx.core.content.getSystemService
import com.google.android.gms.R
import com.google.android.gms.smartdevice.d2d.AccountTransferResult
import com.google.android.gms.smartdevice.d2d.BootstrapAccount
import com.google.android.gms.smartdevice.d2d.internal.IDirectTransferListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.microg.gms.checkin.LastCheckinInfo
import org.microg.gms.common.Constants
import org.microg.gms.cryptauth.enrollAuthzenKeys
import org.microg.gms.cryptauth.isLockscreenConfigured
import org.microg.gms.profile.Build
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

private const val TAG = "SourceDirectTransfer"

const val STATUS_TRANSFER_IN_PROGRESS = 10561
const val STATUS_NO_ACCOUNTS = 10562
private const val STATUS_CANCELED = 10564
const val STATUS_NO_TRANSFER_IN_PROGRESS = 10565
private const val STATUS_BOOTSTRAP_INFO_FAILED = 10573
private const val STATUS_REMOTE_DEVICE_ERROR = 10575
private const val STATUS_PIPE_READ_ERROR = 10576
private const val STATUS_ENCRYPTION_FAILED = 10577
private const val STATUS_DECRYPTION_FAILED = 10587
private const val STATUS_PROTOCOL_ERROR = 10589
private const val STATUS_CANCELED_BY_USER = 10598
private const val STATUS_FALLBACK_CHALLENGE_FAILED = 10599
private const val STATUS_ASSERTIONS_FAILED = 10650

private const val MAX_FRAME_SIZE = 20 * 1024 * 1024
private const val FALLBACK_URL_PREFIX = "https://accounts.google.com/"

private const val PROTOCOL_VERSION = 3
private const val ENCRYPTION_UKEY2 = 8
private val UKEY2_HANDSHAKE_COMPLETE = byteArrayOf(1, 1, 2, 3, 5, 8)

private const val STATE_CANCELED = 1
private const val STATE_COMPLETED = 2
private const val STATE_ERROR = 3

private const val DEVICE_TYPE_PHONE = 1
private const val CREDENTIAL_TYPE_ACCOUNT = 3
private const val LOCK_SCREEN_AUTHENTICATION_CONFIRMED = 3
private const val LOCK_SCREEN_AUTHENTICATION_NONE = 4
private const val CLIENT_ID = "1070009224336-sdh77n7uot3oc99ais00jmuft6sk2fg9.apps.googleusercontent.com"

/**
 * Copies accounts of this device to a target device, communicating through the
 * pipes provided by the calling app.
 */
class SourceDirectTransfer(
    private val context: Context,
    private val accounts: List<BootstrapAccount>,
    input: ParcelFileDescriptor,
    output: ParcelFileDescriptor,
    private val listener: IDirectTransferListener,
    private val onFinished: (SourceDirectTransfer) -> Unit
) {
    private sealed class Event {
        class Frame(val data: ByteArray) : Event()
        class ReadFailed(val message: String?) : Event()
        class UserConfirmation(val confirmed: Boolean, val lockScreenConfirmed: Boolean) : Event()
        object Canceled : Event()
        class ChallengesCompleted(val completed: Boolean, val accounts: List<String>, val sessionCheckpoints: List<String>) : Event()
    }

    private val inputStream = DataInputStream(ParcelFileDescriptor.AutoCloseInputStream(input))
    private val outputStream = DataOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(output))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private lateinit var enrollment: Deferred<Boolean>

    private var handshake: Ukey2ServerHandshake? = null
    private var connection: D2DConnection? = null
    private var lockScreenConfirmedAt: Long? = null
    private var transferStarted = false
    private val results = arrayListOf<AccountTransferResult>()

    @Volatile
    private var closed = false

    fun start() {
        enrollment = scope.async { accounts.all { context.enrollAuthzenKeys(it.name) } }
        scope.launch {
            try {
                send(byteArrayOf(ENCRYPTION_UKEY2.toByte()))
            } catch (e: IOException) {
                return@launch finishWithError(STATUS_PIPE_READ_ERROR, "Error writing to pipe: $e")
            }
            for (event in events) {
                try {
                    handleEvent(event)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to handle $event", e)
                    abort(STATUS_PROTOCOL_ERROR, e.toString())
                }
                if (closed) break
            }
        }
        Thread({
            try {
                while (!closed) {
                    val size = inputStream.readInt()
                    if (size !in 0..MAX_FRAME_SIZE) throw IOException("Invalid frame size $size")
                    val data = ByteArray(size)
                    inputStream.readFully(data)
                    events.trySendBlocking(Event.Frame(data))
                }
            } catch (e: IOException) {
                if (!closed) events.trySendBlocking(Event.ReadFailed(e.message))
            }
        }, TAG).start()
    }

    fun cancel() {
        events.trySend(Event.Canceled)
    }

    private suspend fun handleEvent(event: Event) {
        when (event) {
            is Event.Frame -> handleFrame(event.data)
            is Event.ReadFailed -> finishWithError(STATUS_PIPE_READ_ERROR, "Error thrown in the reading pipe: ${event.message}")
            is Event.UserConfirmation -> handleUserConfirmation(event.confirmed, event.lockScreenConfirmed)
            is Event.ChallengesCompleted -> handleChallengesCompleted(event.completed, event.accounts, event.sessionCheckpoints)
            is Event.Canceled -> {
                Log.d(TAG, "Transfer canceled")
                if (connection != null) sendPayload(JSONObject().put("bootstrapState", STATE_CANCELED))
                close()
            }
        }
    }

    private suspend fun handleFrame(data: ByteArray) {
        val handshake = handshake
        val connection = connection
        when {
            handshake == null -> {
                if (data.size != 1 || Integer.highestOneBit(data[0].toInt() and ENCRYPTION_UKEY2) != ENCRYPTION_UKEY2) {
                    return finishWithError(STATUS_ENCRYPTION_FAILED, "Unsupported encryption: ${data.contentToString()}")
                }
                this.handshake = Ukey2ServerHandshake()
            }

            connection == null -> try {
                if (handshake.hasSentServerInit) {
                    this.connection = handshake.handleClientFinished(data)
                    send(UKEY2_HANDSHAKE_COMPLETE)
                    Log.d(TAG, "Encryption negotiation has completed")
                } else {
                    send(handshake.handleClientInit(data))
                }
            } catch (e: Exception) {
                Log.w(TAG, "UKEY2 handshake failed", e)
                finishWithError(STATUS_ENCRYPTION_FAILED, "Error establishing encrypted connection: $e")
            }

            else -> {
                val payload = try {
                    JSONObject(connection.decode(data).decodeToString().replace("\n", ""))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode message", e)
                    return finishWithError(STATUS_DECRYPTION_FAILED, e.toString())
                }
                Log.d(TAG, "Received payload: ${payload.keys().asSequence().toList()}")
                handlePayload(payload)
            }
        }
    }

    private suspend fun handlePayload(payload: JSONObject) {
        when (val state = payload.optInt("bootstrapState")) {
            0, 5, 6 -> Unit
            STATE_COMPLETED -> return handleCompleted()
            STATE_CANCELED -> return finishWithError(payload.remoteErrorCode() ?: STATUS_CANCELED, "Bootstrap canceled")
            STATE_ERROR -> return finishWithError(payload.remoteErrorCode() ?: STATUS_REMOTE_DEVICE_ERROR, "Remote device error")
            else -> return finishWithError(STATUS_REMOTE_DEVICE_ERROR, "Unknown state from remote device: $state")
        }
        payload.optJSONObject("bootstrapOptions")?.let { handleBootstrapOptions(it) }
        payload.optJSONObject("accountBootstrapPayload")?.optJSONArray("challenges")?.let { handleChallenges(it) }
        payload.optJSONObject("accountBootstrapPayload")?.optJSONArray("userCredentials")?.let { handleUserCredentials(it) }
        payload.optJSONArray("accountTransferResults")?.let { handleAccountTransferResults(it) }
    }

    private fun JSONObject.remoteErrorCode() = optJSONObject("deviceStatus")?.optInt("errorCode")?.takeIf { it != 0 }

    private fun handleBootstrapOptions(options: JSONObject) {
        val deviceDetails = JSONObject()
            .put("androidId", LastCheckinInfo.read(context).androidId)
            .put("gmsVersion", Constants.GMS_VERSION_CODE)
            .put("hasConnectivity", context.getSystemService<ConnectivityManager>()?.activeNetworkInfo?.isConnected == true)
            .put("hasLockscreen", context.isLockscreenConfigured())
            .put("model", Build.MODEL)
            .put("deviceType", DEVICE_TYPE_PHONE)
            .put("deviceFingerprint", Build.FINGERPRINT)
        val configurations = JSONObject()
            .put("isLockScreenShown", false)
            .put("hasUserConfirmed", false)
            .put("supportsUnencryptedCommunication", false)
            .put("maxPacketSize", 0)
            .put("optionFlags", 0)
            .put("optionFlagSetIndicators", 1 shl 1)
            .put("deviceDetails", deviceDetails)
            .put("hideSkipAccount", false)
            .put("authenticatingUser", JSONObject.NULL)
            .put("unicornChallengeDeduplicationEnabled", false)
        sendPayload(JSONObject().put("bootstrapConfigurations", configurations))

        val resultReceiver = object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                val confirmed = resultCode == Activity.RESULT_OK
                val lockScreenConfirmed = resultData?.getBoolean(ConfirmTransferActivity.EXTRA_LOCK_SCREEN_CONFIRMED) == true
                events.trySend(Event.UserConfirmation(confirmed, lockScreenConfirmed))
            }
        }
        val intent = Intent(context, ConfirmTransferActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(ConfirmTransferActivity.EXTRA_ACCOUNTS, accounts.map { it.name }.toTypedArray())
            .putExtra(ConfirmTransferActivity.EXTRA_DEVICE_NAME, options.optString("deviceName"))
            .putExtra(ConfirmTransferActivity.EXTRA_RESULT_RECEIVER, resultReceiver)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        listener.onShowUi(pendingIntent)
    }

    private suspend fun handleUserConfirmation(confirmed: Boolean, lockScreenConfirmed: Boolean) {
        Log.d(TAG, "User confirmation: confirmed=$confirmed lockScreenConfirmed=$lockScreenConfirmed")
        if (!confirmed) return abort(STATUS_CANCELED_BY_USER, "Bootstrap canceled by user.")
        if (lockScreenConfirmed) lockScreenConfirmedAt = SystemClock.elapsedRealtime()
        transferStarted = true
        if (!enrollment.await()) Log.w(TAG, "Authzen enrollment failed")
        val bootstrapInfos = accounts.mapNotNull { context.createUserBootstrapInfo(it.name) }
        if (bootstrapInfos.isEmpty()) return abort(STATUS_BOOTSTRAP_INFO_FAILED, "Error trying to fetch user bootstrap info")
        Log.d(TAG, "Sending BootstrapInfos to target")
        sendPayload(JSONObject()
            .put("accountBootstrapPayload", JSONObject().put("userBootstrapInfos", JSONArray(bootstrapInfos)))
            .put("displayText", JSONObject().put("text", context.resources.getQuantityString(R.plurals.smartdevice_copying_accounts, accounts.size))))
    }

    private fun handleChallenges(challenges: JSONArray) {
        val assertions = (0 until challenges.length()).mapNotNull { index ->
            try {
                context.createAssertion(challenges.getJSONObject(index), lockScreenConfirmedAt)
            } catch (e: Exception) {
                Log.w(TAG, "Could not create assertion", e)
                null
            }
        }
        if (assertions.isEmpty()) return abort(STATUS_ASSERTIONS_FAILED, "Assertions are empty.")
        val request = JSONObject()
            .put("assertions", JSONArray(assertions))
            .put("targetDeviceSignals", JSONObject.NULL)
            .put("credentialType", CREDENTIAL_TYPE_ACCOUNT)
            .put("clientId", CLIENT_ID)
            .put("locale", JSONObject.NULL)
            .put("sourceBackupAccountId", "")
            .put("sourceAndroidDeviceId", java.lang.Long.toHexString(LastCheckinInfo.read(context).androidId))
            .put("deferCredentialsAfterFallback", false)
        Log.d(TAG, "Sending ExchangeAssertionsForUserCredentialsRequest to target")
        sendPayload(JSONObject().put("accountBootstrapPayload", JSONObject().put("exchangeAssertionsForUserCredentialsRequest", request)))
    }

    private fun handleUserCredentials(userCredentials: JSONArray) {
        val challenges = (0 until userCredentials.length()).map { userCredentials.getJSONObject(it) }
            .filter { it.optString("fallbackUrl").isNotEmpty() }
        if (challenges.isEmpty()) return
        if (challenges.any { !it.optString("fallbackUrl").startsWith(FALLBACK_URL_PREFIX) }) {
            return abort(STATUS_FALLBACK_CHALLENGE_FAILED, "Unexpected fallback challenge URL")
        }
        Log.d(TAG, "Server requested fallback challenges for ${challenges.map { it.optString("accountIdentifier") }}")
        val resultReceiver = object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                events.trySend(Event.ChallengesCompleted(
                    resultCode == Activity.RESULT_OK,
                    resultData?.getStringArray(AccountChallengeActivity.EXTRA_ACCOUNTS).orEmpty().toList(),
                    resultData?.getStringArray(AccountChallengeActivity.EXTRA_SESSION_CHECKPOINTS).orEmpty().toList()
                ))
            }
        }
        val intent = Intent(context, AccountChallengeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(AccountChallengeActivity.EXTRA_ACCOUNTS, challenges.map { it.optString("accountIdentifier") }.toTypedArray())
            .putExtra(AccountChallengeActivity.EXTRA_URLS, challenges.map { it.optString("fallbackUrl") }.toTypedArray())
            .putExtra(AccountChallengeActivity.EXTRA_RESULT_RECEIVER, resultReceiver)
        val pendingIntent = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        listener.onShowUi(pendingIntent)
    }

    private fun handleChallengesCompleted(completed: Boolean, accounts: List<String>, sessionCheckpoints: List<String>) {
        if (!completed || sessionCheckpoints.isEmpty()) return abort(STATUS_FALLBACK_CHALLENGE_FAILED, "Error happened during fallback challenge webview.")
        val checkpoints = accounts.zip(sessionCheckpoints).map { (account, checkpoint) ->
            JSONObject().put("accountIdentifier", account).put("sessionCheckpoint", checkpoint)
        }
        Log.d(TAG, "Sending ExchangeSessionCheckpointsForUserCredentialsRequest to target")
        sendPayload(JSONObject().put("accountBootstrapPayload", JSONObject()
            .put("exchangeSessionCheckpointsForUserCredentialsRequest", JSONObject().put("sessionCheckpoints", JSONArray(checkpoints)))))
    }

    private fun handleAccountTransferResults(transferResults: JSONArray) {
        for (index in 0 until transferResults.length()) {
            val result = transferResults.getJSONObject(index)
            val account = result.optJSONObject("bootstrapAccount")
            results.add(AccountTransferResult(
                BootstrapAccount(account?.optString("name"), account?.optString("type")),
                result.optInt("RESULT"),
                if (lockScreenConfirmedAt != null) LOCK_SCREEN_AUTHENTICATION_CONFIRMED else LOCK_SCREEN_AUTHENTICATION_NONE
            ))
        }
    }

    private fun handleCompleted() {
        if (!transferStarted) return finishWithError(STATUS_PROTOCOL_ERROR, "handleOnCompleted called before transfer started.")
        sendPayload(JSONObject().put("bootstrapState", STATE_COMPLETED))
        Log.d(TAG, "Transfer completed: $results")
        try {
            listener.onAccountTransferResults(results.toTypedArray())
        } catch (e: Exception) {
            Log.w(TAG, e)
        }
        close()
    }

    private fun abort(statusCode: Int, message: String) {
        if (connection != null) {
            val state = if (statusCode == STATUS_CANCELED || statusCode == STATUS_CANCELED_BY_USER) STATE_CANCELED else STATE_ERROR
            try {
                sendPayload(JSONObject().put("bootstrapState", state).put("deviceStatus", JSONObject().put("errorCode", statusCode)))
            } catch (e: IOException) {
                Log.w(TAG, e)
            }
        }
        finishWithError(statusCode, message)
    }

    private fun finishWithError(statusCode: Int, message: String) {
        if (closed) return
        Log.w(TAG, "Transfer failed: $statusCode $message")
        try {
            listener.onError(statusCode, message)
        } catch (e: Exception) {
            Log.w(TAG, e)
        }
        close()
    }

    private fun sendPayload(payload: JSONObject) {
        payload.put("protocolVersion", PROTOCOL_VERSION)
        Log.d(TAG, "Sending payload: ${payload.keys().asSequence().toList()}")
        send(connection!!.encode(payload.toString().toByteArray()))
    }

    private fun send(data: ByteArray) = synchronized(outputStream) {
        outputStream.writeInt(data.size)
        outputStream.write(data)
        outputStream.flush()
    }

    private fun close() {
        if (closed) return
        closed = true
        events.close()
        runCatching { inputStream.close() }
        runCatching { outputStream.close() }
        scope.cancel()
        onFinished(this)
    }
}
