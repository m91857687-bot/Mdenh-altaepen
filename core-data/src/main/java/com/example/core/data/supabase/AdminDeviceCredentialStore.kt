package com.example.core.data.supabase

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class StoredDeviceCredential(
    val deviceId: String,
    val deviceToken: String,
    val deviceName: String,
    val activatedAt: String? = null
)

interface AesGcmKeyProvider {
    fun getOrCreateSecretKey(): SecretKey
}

/**
 * Hardware-backed / OS-managed AES-256-GCM key provider stored inside Android Keystore.
 * Raw key material never leaves Android Keystore.
 */
class AndroidKeystoreKeyProvider(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : AesGcmKeyProvider {

    override fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_KEY_ALIAS = "madinat_admin_device_aes_key"
    }
}

interface AdminDeviceCredentialStore {
    suspend fun getOrCreateDeviceId(): String
    suspend fun loadCredential(): StoredDeviceCredential?
    suspend fun saveCredential(credential: StoredDeviceCredential)
    suspend fun clearCredential()
}

/**
 * Encrypts both the device identifier and the server-issued device session token using
 * AES-GCM via [AesGcmKeyProvider] (Android Keystore in production).
 * Never persists sensitive tokens or keys in plaintext.
 */
class KeystoreAdminDeviceCredentialStore(
    context: Context,
    private val keyProvider: AesGcmKeyProvider = AndroidKeystoreKeyProvider()
) : AdminDeviceCredentialStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val secureRandom = SecureRandom()

    override suspend fun getOrCreateDeviceId(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val existingCred = readEncryptedJson<StoredDeviceCredential>(KEY_ENCRYPTED_CREDENTIAL)
            if (existingCred != null && existingCred.deviceId.isNotBlank()) {
                return@withLock existingCred.deviceId
            }
            val existingId = readEncryptedString(KEY_ENCRYPTED_DEVICE_ID)
            if (!existingId.isNullOrBlank()) {
                return@withLock existingId
            }
            val generated = "dev_${UUID.randomUUID().toString().replace("-", "")}"
            writeEncryptedString(KEY_ENCRYPTED_DEVICE_ID, generated)
            generated
        }
    }

    override suspend fun loadCredential(): StoredDeviceCredential? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readEncryptedJson<StoredDeviceCredential>(KEY_ENCRYPTED_CREDENTIAL)
        }
    }

    override suspend fun saveCredential(credential: StoredDeviceCredential) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val encoded = SupabaseClientProvider.json.encodeToString(StoredDeviceCredential.serializer(), credential)
            writeEncryptedString(KEY_ENCRYPTED_CREDENTIAL, encoded)
            writeEncryptedString(KEY_ENCRYPTED_DEVICE_ID, credential.deviceId)
        }
    }

    override suspend fun clearCredential() = withContext(Dispatchers.IO) {
        mutex.withLock {
            prefs.edit().remove(KEY_ENCRYPTED_CREDENTIAL).apply()
        }
    }

    private inline fun <reified T> readEncryptedJson(key: String): T? {
        val plaintext = readEncryptedString(key) ?: return null
        return try {
            SupabaseClientProvider.json.decodeFromString<T>(plaintext)
        } catch (_: Throwable) {
            prefs.edit().remove(key).apply()
            null
        }
    }

    private fun readEncryptedString(key: String): String? {
        val packed = prefs.getString(key, null) ?: return null
        val dotIndex = packed.indexOf('.')
        if (dotIndex <= 0 || dotIndex >= packed.length - 1) {
            prefs.edit().remove(key).apply()
            return null
        }
        return try {
            val iv = Base64.decode(packed.substring(0, dotIndex), Base64.NO_WRAP)
            val cipherText = Base64.decode(packed.substring(dotIndex + 1), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyProvider.getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(cipherText).decodeToString()
        } catch (_: Throwable) {
            // Tampered, corrupted, or invalidated Keystore key -> wipe stored blob and require re-enrollment.
            prefs.edit().remove(key).apply()
            null
        }
    }

    private fun writeEncryptedString(key: String, plaintext: String) {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val secretKey = keyProvider.getOrCreateSecretKey()
        val iv = ByteArray(GCM_IV_BYTES)
        // Note: AndroidKeyStore with randomizedEncryptionRequired=true prefers provider-generated IV on ENCRYPT_MODE
        // when no explicit GCMParameterSpec is passed. Let Cipher generate IV safely!
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val cipherBytes = cipher.doFinal(plaintext.encodeToByteArray())
        val actualIv = cipher.iv ?: iv.also { secureRandom.nextBytes(it) }
        val packed = "${Base64.encodeToString(actualIv, Base64.NO_WRAP)}.${Base64.encodeToString(cipherBytes, Base64.NO_WRAP)}"
        prefs.edit().putString(key, packed).apply()
    }

    companion object {
        private const val PREFS_NAME = "madinat_admin_device_keystore"
        private const val KEY_ENCRYPTED_CREDENTIAL = "enc_device_cred"
        private const val KEY_ENCRYPTED_DEVICE_ID = "enc_device_id"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}

class InMemoryAdminDeviceCredentialStore(
    initialCredential: StoredDeviceCredential? = null,
    initialDeviceId: String = "dev_test_${UUID.randomUUID().toString().take(12)}"
) : AdminDeviceCredentialStore {
    private var deviceId: String = initialCredential?.deviceId ?: initialDeviceId
    private var credential: StoredDeviceCredential? = initialCredential

    override suspend fun getOrCreateDeviceId(): String = deviceId

    override suspend fun loadCredential(): StoredDeviceCredential? = credential

    override suspend fun saveCredential(credential: StoredDeviceCredential) {
        this.deviceId = credential.deviceId
        this.credential = credential
    }

    override suspend fun clearCredential() {
        this.credential = null
    }
}
