package com.aistudio.akmessenger.shared.crypto

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String
)

/**
 * Real End-to-End Encryption manager using ECDH (secp256r1) + SHA-256 + AES-256-GCM.
 */
object E2eeCryptoManager {
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    fun generateIdentityKeyPair(): Pair<String, String> {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val pair: KeyPair = kpg.generateKeyPair()
        val pubB64 = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
        val privB64 = Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP)
        return pubB64 to privB64
    }

    fun deriveSharedAesKey(myPrivateKeyB64: String, peerPublicKeyB64: String): SecretKey? {
        return try {
            val kf = KeyFactory.getInstance("EC")
            val privBytes = Base64.decode(myPrivateKeyB64, Base64.NO_WRAP)
            val pubBytes = Base64.decode(peerPublicKeyB64, Base64.NO_WRAP)
            val privKey: PrivateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
            val pubKey: PublicKey = kf.generatePublic(X509EncodedKeySpec(pubBytes))

            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(privKey)
            ka.doPhase(pubKey, true)
            val sharedSecret = ka.generateSecret()

            val digest = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
            SecretKeySpec(digest, "AES")
        } catch (_: Exception) {
            null
        }
    }

    fun encrypt(plaintext: String, myPrivateKeyB64: String, peerPublicKeyB64: String): EncryptedPayload? {
        val aesKey = deriveSharedAesKey(myPrivateKeyB64, peerPublicKeyB64) ?: return null
        return try {
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            EncryptedPayload(
                ciphertextBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP),
                ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun decrypt(ciphertextB64: String, ivB64: String, myPrivateKeyB64: String, peerPublicKeyB64: String): String? {
        val aesKey = deriveSharedAesKey(myPrivateKeyB64, peerPublicKeyB64) ?: return null
        return try {
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ciphertextB64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun computeSafetyNumber(myPublicKeyB64: String, peerPublicKeyB64: String): String {
        if (myPublicKeyB64.isBlank() || peerPublicKeyB64.isBlank()) return "Pending key exchange"
        val ordered = listOf(myPublicKeyB64, peerPublicKeyB64).sorted().joinToString("|")
        val hash = MessageDigest.getInstance("SHA-256").digest(ordered.toByteArray(Charsets.UTF_8))
        return hash.take(15).joinToString(" ") { byte ->
            ((byte.toInt() and 0xFF)).toString().padStart(3, '0')
        }
    }

    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
