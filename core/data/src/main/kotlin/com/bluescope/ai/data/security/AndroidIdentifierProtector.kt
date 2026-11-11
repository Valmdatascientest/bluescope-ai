package com.bluescope.ai.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Protects reusable Bluetooth identifiers with non-exportable Android Keystore keys. */
class AndroidIdentifierProtector : IdentifierProtector {
    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val envelope = byteArrayOf(FORMAT_VERSION) + cipher.iv + encrypted
        return Base64.getEncoder().encodeToString(envelope)
    }

    override fun decrypt(value: String): String {
        val envelope = Base64.getDecoder().decode(value)
        require(envelope.size > 1 + IV_SIZE_BYTES) { "Identifiant chiffré invalide" }
        require(envelope[0] == FORMAT_VERSION) { "Version de chiffrement inconnue" }
        val iv = envelope.copyOfRange(1, 1 + IV_SIZE_BYTES)
        val payload = envelope.copyOfRange(1 + IV_SIZE_BYTES, envelope.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(payload).toString(Charsets.UTF_8)
    }

    override fun lookupHash(value: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(hashKey())
        val normalized = value.trim().uppercase().toByteArray(Charsets.UTF_8)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(normalized))
    }

    @Synchronized
    private fun encryptionKey(): SecretKey = keyStore.getKey(ENCRYPTION_ALIAS, null) as? SecretKey
        ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    ENCRYPTION_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }

    @Synchronized
    private fun hashKey(): SecretKey = keyStore.getKey(HASH_ALIAS, null) as? SecretKey
        ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    HASH_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            generateKey()
        }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ENCRYPTION_ALIAS = "bluescope_identifiers_v1"
        const val HASH_ALIAS = "bluescope_identifier_lookup_v1"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val IV_SIZE_BYTES = 12
        const val FORMAT_VERSION: Byte = 1
    }
}
