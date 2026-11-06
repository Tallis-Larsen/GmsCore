/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.smartdevice

import com.google.security.cryptauth.lib.securegcm.DeviceToDeviceMessage
import com.google.security.cryptauth.lib.securegcm.GcmMetadata
import com.google.security.cryptauth.lib.securegcm.Type
import com.google.security.cryptauth.lib.securegcm.Ukey2ClientFinished
import com.google.security.cryptauth.lib.securegcm.Ukey2ClientInit
import com.google.security.cryptauth.lib.securegcm.Ukey2HandshakeCipher
import com.google.security.cryptauth.lib.securegcm.Ukey2Message
import com.google.security.cryptauth.lib.securegcm.Ukey2ServerInit
import com.google.security.cryptauth.lib.securemessage.SecureMessage
import com.google.security.cryptauth.lib.securemessage.SigScheme
import okio.ByteString.Companion.toByteString
import org.microg.gms.cryptauth.buildSignCryptedMessage
import org.microg.gms.cryptauth.decodeGenericPublicKey
import org.microg.gms.cryptauth.encodeGenericPublicKey
import org.microg.gms.cryptauth.generateEcP256KeyPair
import org.microg.gms.cryptauth.hkdf
import org.microg.gms.cryptauth.parseSignCryptedMessage
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.SignatureException
import java.security.interfaces.ECPublicKey
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

private const val UKEY2_VERSION = 1
private const val UKEY2_NEXT_PROTOCOL = "AES_256_CBC-HMAC_SHA256"
private const val SECURE_GCM_VERSION = 1
private val D2D_SALT = MessageDigest.getInstance("SHA-256").digest("D2D".toByteArray())

class Ukey2Exception(message: String) : Exception(message)

class Ukey2ServerHandshake {
    private val keyPair = generateEcP256KeyPair()
    private var clientInit: ByteArray? = null
    private var serverInit: ByteArray? = null
    private var commitment: ByteArray? = null

    val hasSentServerInit: Boolean
        get() = serverInit != null

    fun handleClientInit(message: ByteArray): ByteArray {
        val ukey2Message = Ukey2Message.ADAPTER.decode(message)
        if (ukey2Message.message_type != Ukey2Message.Type.CLIENT_INIT) throw Ukey2Exception("Expected ClientInit, got ${ukey2Message.message_type}")
        val init = Ukey2ClientInit.ADAPTER.decode(ukey2Message.message_data ?: throw Ukey2Exception("Missing ClientInit data"))
        if (init.version != UKEY2_VERSION) throw Ukey2Exception("Unsupported version ${init.version}")
        if (init.random?.size != 32) throw Ukey2Exception("Invalid random")
        if (init.next_protocol != UKEY2_NEXT_PROTOCOL) throw Ukey2Exception("Unsupported next protocol ${init.next_protocol}")
        commitment = init.cipher_commitments.firstOrNull { it.handshake_cipher == Ukey2HandshakeCipher.P256_SHA512 }?.commitment?.toByteArray()
            ?: throw Ukey2Exception("No supported cipher")
        clientInit = message
        val response = Ukey2Message(
            message_type = Ukey2Message.Type.SERVER_INIT,
            message_data = Ukey2ServerInit(
                version = UKEY2_VERSION,
                random = ByteArray(32).also { SecureRandom().nextBytes(it) }.toByteString(),
                handshake_cipher = Ukey2HandshakeCipher.P256_SHA512,
                public_key = encodeGenericPublicKey(keyPair.public as ECPublicKey).toByteString()
            ).encode().toByteString()
        ).encode()
        serverInit = response
        return response
    }

    fun handleClientFinished(message: ByteArray): D2DConnection {
        val clientInit = clientInit ?: throw Ukey2Exception("ClientInit not received")
        val serverInit = serverInit ?: throw Ukey2Exception("ServerInit not sent")
        if (!MessageDigest.isEqual(MessageDigest.getInstance("SHA-512").digest(message), commitment)) throw Ukey2Exception("Commitment mismatch")
        val ukey2Message = Ukey2Message.ADAPTER.decode(message)
        if (ukey2Message.message_type != Ukey2Message.Type.CLIENT_FINISH) throw Ukey2Exception("Expected ClientFinished, got ${ukey2Message.message_type}")
        val finished = Ukey2ClientFinished.ADAPTER.decode(ukey2Message.message_data ?: throw Ukey2Exception("Missing ClientFinished data"))
        val clientKey = decodeGenericPublicKey(finished.public_key?.toByteArray() ?: throw Ukey2Exception("Missing public key"))
        val sharedSecret = MessageDigest.getInstance("SHA-256").digest(KeyAgreement.getInstance("ECDH").run {
            init(keyPair.private)
            doPhase(clientKey, true)
            generateSecret()
        })
        val nextProtocolKey = hkdf(sharedSecret, "UKEY2 v1 next".toByteArray(), clientInit + serverInit)
        return D2DConnection(
            encodeKey = SecretKeySpec(hkdf(nextProtocolKey, D2D_SALT, "server".toByteArray()), "AES"),
            decodeKey = SecretKeySpec(hkdf(nextProtocolKey, D2D_SALT, "client".toByteArray()), "AES")
        )
    }
}

class D2DConnection(private val encodeKey: SecretKey, private val decodeKey: SecretKey) {
    private var encodeSequenceNumber = 0
    private var decodeSequenceNumber = 0

    @Synchronized
    fun encode(payload: ByteArray): ByteArray {
        val message = DeviceToDeviceMessage(message = payload.toByteString(), sequence_number = ++encodeSequenceNumber)
        return buildSignCryptedMessage(
            signingKey = encodeKey,
            sigScheme = SigScheme.HMAC_SHA256,
            encryptionKey = encodeKey,
            body = message.encode(),
            publicMetadata = GcmMetadata(type = Type.DEVICE_TO_DEVICE_MESSAGE, version = SECURE_GCM_VERSION).encode()
        ).encode()
    }

    @Synchronized
    fun decode(data: ByteArray): ByteArray {
        val (header, body) = parseSignCryptedMessage(SecureMessage.ADAPTER.decode(data), decodeKey, SigScheme.HMAC_SHA256, decodeKey)
        val metadata = GcmMetadata.ADAPTER.decode(header.public_metadata ?: throw SignatureException("Missing metadata"))
        if (metadata.type != Type.DEVICE_TO_DEVICE_MESSAGE) throw SignatureException("Unexpected message type ${metadata.type}")
        val message = DeviceToDeviceMessage.ADAPTER.decode(body)
        if (message.sequence_number != ++decodeSequenceNumber) throw SignatureException("Unexpected sequence number ${message.sequence_number}")
        return message.message?.toByteArray() ?: ByteArray(0)
    }
}
