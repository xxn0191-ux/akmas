package com.example

import com.aistudio.akmessenger.shared.crypto.E2eeCryptoManager
import com.aistudio.akmessenger.shared.model.*
import com.aistudio.akmessenger.shared.repository.AkAdminRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AkMessengerUnitTest {

    @Test
    fun e2ee_roundtrip_bangla_and_english_succeeds() {
        val (alicePub, alicePriv) = E2eeCryptoManager.generateIdentityKeyPair()
        val (bobPub, bobPriv) = E2eeCryptoManager.generateIdentityKeyPair()

        val message = "তুমি কেমন আছো? Hello AK Messenger E2EE! 🔐"
        val encrypted = E2eeCryptoManager.encrypt(message, alicePriv, bobPub)
        assertNotNull(encrypted)
        assertNotEquals(message, encrypted!!.ciphertextBase64)

        val decryptedByBob = E2eeCryptoManager.decrypt(
            encrypted.ciphertextBase64,
            encrypted.ivBase64,
            bobPriv,
            alicePub
        )
        assertEquals(message, decryptedByBob)

        // Wrong key must fail decryption
        val (_, evePriv) = E2eeCryptoManager.generateIdentityKeyPair()
        val decryptedByEve = E2eeCryptoManager.decrypt(
            encrypted.ciphertextBase64,
            encrypted.ivBase64,
            evePriv,
            alicePub
        )
        assertNull(decryptedByEve)
    }

    @Test
    fun safety_number_is_symmetric() {
        val (pubA, _) = E2eeCryptoManager.generateIdentityKeyPair()
        val (pubB, _) = E2eeCryptoManager.generateIdentityKeyPair()
        assertEquals(
            E2eeCryptoManager.computeSafetyNumber(pubA, pubB),
            E2eeCryptoManager.computeSafetyNumber(pubB, pubA)
        )
    }
}
