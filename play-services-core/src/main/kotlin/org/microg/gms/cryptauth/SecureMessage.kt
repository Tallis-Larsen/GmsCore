/*
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.cryptauth

import com.google.security.cryptauth.lib.securemessage.EcP256PublicKey
import com.google.security.cryptauth.lib.securemessage.EncScheme
import com.google.security.cryptauth.lib.securemessage.GenericPublicKey
import com.google.security.cryptauth.lib.securemessage.Header
import com.google.security.cryptauth.lib.securemessage.HeaderAndBodyInternal
import com.google.security.cryptauth.lib.securemessage.PublicKeyType
import com.google.security.cryptauth.lib.securemessage.SecureMessage
import com.google.security.cryptauth.lib.securemessage.SigScheme
import okio.ByteString.Companion.toByteString
import java.math.BigInteger
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.SignatureException
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private val SECURE_MESSAGE_SALT = MessageDigest.getInstance("SHA-256").digest("SecureMessage".toByteArray())
private const val TAG_LENGTH = 20

internal fun hkdf(inputKey: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(salt, "HmacSHA256"))
    val pseudoRandomKey = mac.doFinal(inputKey)
    mac.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
    var block = ByteArray(0)
    var output = ByteArray(0)
    var counter = 1
    while (output.size < length) {
        mac.update(block)
        mac.update(info)
        mac.update(counter++.toByte())
        block = mac.doFinal()
        output += block
    }
    return output.copyOf(length)
}

internal fun generateEcP256KeyPair(): KeyPair =
    KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

internal fun encodeGenericPublicKey(key: ECPublicKey): ByteArray = GenericPublicKey(
    type = PublicKeyType.EC_P256,
    ec_p256_public_key = EcP256PublicKey(
        x = key.w.affineX.toByteArray().toByteString(),
        y = key.w.affineY.toByteArray().toByteString()
    )
).encode()

internal fun decodeGenericPublicKey(bytes: ByteArray): ECPublicKey {
    val key = GenericPublicKey.ADAPTER.decode(bytes).ec_p256_public_key
        ?: throw IllegalArgumentException("Not an EC P-256 public key")
    val params = (generateEcP256KeyPair().public as ECPublicKey).params
    val point = ECPoint(BigInteger(key.x.toByteArray()), BigInteger(key.y.toByteArray()))
    return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params)) as ECPublicKey
}

private fun deriveKey(key: Key, purpose: String) = hkdf(key.encoded, SECURE_MESSAGE_SALT, purpose.toByteArray())

private fun sign(signingKey: Key, sigScheme: SigScheme, data: ByteArray): ByteArray = when (sigScheme) {
    SigScheme.HMAC_SHA256 -> Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(deriveKey(signingKey, "SIG:${sigScheme.value}"), "HmacSHA256"))
        doFinal(data)
    }
    SigScheme.ECDSA_P256_SHA256 -> Signature.getInstance("SHA256withECDSA").run {
        initSign(signingKey as PrivateKey)
        update(SECURE_MESSAGE_SALT)
        update(data)
        sign()
    }
    else -> throw IllegalArgumentException("Unsupported signature scheme $sigScheme")
}

private fun verify(verificationKey: Key, sigScheme: SigScheme, signature: ByteArray, data: ByteArray): Boolean = when (sigScheme) {
    SigScheme.HMAC_SHA256 -> MessageDigest.isEqual(sign(verificationKey, sigScheme, data), signature)
    SigScheme.ECDSA_P256_SHA256 -> Signature.getInstance("SHA256withECDSA").run {
        initVerify(verificationKey as PublicKey)
        update(SECURE_MESSAGE_SALT)
        update(data)
        verify(signature)
    }
    else -> false
}

private fun aes(mode: Int, key: SecretKey, iv: ByteArray, data: ByteArray): ByteArray = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
    init(mode, SecretKeySpec(deriveKey(key, "ENC:${EncScheme.AES_256_CBC.value}"), "AES"), IvParameterSpec(iv))
    doFinal(data)
}

internal fun buildSignCryptedMessage(
    signingKey: Key,
    sigScheme: SigScheme,
    encryptionKey: SecretKey,
    body: ByteArray,
    publicMetadata: ByteArray? = null,
    verificationKeyId: ByteArray? = null,
    decryptionKeyId: ByteArray? = null
): SecureMessage {
    val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val header = Header(
        signature_scheme = sigScheme,
        encryption_scheme = EncScheme.AES_256_CBC,
        verification_key_id = verificationKeyId?.toByteString(),
        decryption_key_id = decryptionKeyId?.toByteString(),
        iv = iv.toByteString(),
        public_metadata = publicMetadata?.toByteString()
    ).encode()
    val taggedBody = if (isTagRequired(signingKey, sigScheme, encryptionKey)) {
        headerTag(header) + body
    } else {
        body
    }
    val headerAndBody = HeaderAndBodyInternal(
        header_ = header.toByteString(),
        body = aes(Cipher.ENCRYPT_MODE, encryptionKey, iv, taggedBody).toByteString()
    ).encode()
    return SecureMessage(
        header_and_body = headerAndBody.toByteString(),
        signature = sign(signingKey, sigScheme, headerAndBody).toByteString()
    )
}

internal fun parseSignCryptedMessage(
    message: SecureMessage,
    verificationKey: Key,
    sigScheme: SigScheme,
    decryptionKey: SecretKey
): Pair<Header, ByteArray> {
    val headerAndBodyBytes = message.header_and_body.toByteArray()
    if (!verify(verificationKey, sigScheme, message.signature.toByteArray(), headerAndBodyBytes)) {
        throw SignatureException("Signature verification failed")
    }
    val headerAndBody = HeaderAndBodyInternal.ADAPTER.decode(headerAndBodyBytes)
    val headerBytes = headerAndBody.header_.toByteArray()
    val header = Header.ADAPTER.decode(headerBytes)
    if (header.signature_scheme != sigScheme || header.encryption_scheme != EncScheme.AES_256_CBC) {
        throw SignatureException("Unexpected header schemes")
    }
    val iv = header.iv?.toByteArray() ?: throw SignatureException("Missing IV")
    val decrypted = aes(Cipher.DECRYPT_MODE, decryptionKey, iv, headerAndBody.body.toByteArray())
    if (!isTagRequired(verificationKey, sigScheme, decryptionKey)) return header to decrypted
    val tag = headerTag(headerBytes)
    if (decrypted.size < tag.size || !MessageDigest.isEqual(decrypted.copyOf(tag.size), tag)) {
        throw SignatureException("Header tag mismatch")
    }
    return header to decrypted.copyOfRange(tag.size, decrypted.size)
}

private fun isTagRequired(signingKey: Key, sigScheme: SigScheme, encryptionKey: SecretKey) =
    sigScheme != SigScheme.HMAC_SHA256 || !signingKey.encoded.contentEquals(encryptionKey.encoded)

private fun headerTag(header: ByteArray) = MessageDigest.getInstance("SHA-256").digest(header).copyOf(TAG_LENGTH)
