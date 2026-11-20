package com.example.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.supabase.AesGcmKeyProvider
import com.example.core.data.supabase.KeystoreAdminDeviceCredentialStore
import com.example.core.data.supabase.StoredDeviceCredential
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KeystoreDeviceCredentialStoreTest {

    private class SoftwareAesGcmKeyProvider : AesGcmKeyProvider {
        private val key: SecretKey by lazy {
            val generator = KeyGenerator.getInstance("AES")
            generator.init(256)
            generator.generateKey()
        }

        override fun getOrCreateSecretKey(): SecretKey = key
    }

    @Test
    fun `saves and loads device credentials encrypted with AES-GCM without plaintext leakage`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = KeystoreAdminDeviceCredentialStore(context, SoftwareAesGcmKeyProvider())
        store.clearCredential()

        val deviceId = store.getOrCreateDeviceId()
        assertTrue(deviceId.startsWith("dev_"))
        assertEquals(deviceId, store.getOrCreateDeviceId())

        val secretToken = "tok_super_secret_device_session_token_256bit"
        val cred = StoredDeviceCredential(
            deviceId = deviceId,
            deviceToken = secretToken,
            deviceName = "Pixel 9 Owner",
            activatedAt = "2026-11-20T07:00:00Z"
        )
        store.saveCredential(cred)

        // Verify SharedPreferences stores ONLY encrypted base64 iv.ciphertext blobs, never plaintext
        val rawPrefs = context.getSharedPreferences("madinat_admin_device_keystore", Context.MODE_PRIVATE).all
        assertFalse(rawPrefs.isEmpty())
        rawPrefs.values.forEach { rawValue ->
            val asText = rawValue.toString()
            assertFalse("Plaintext token leaked in prefs!", asText.contains(secretToken))
            assertFalse("Plaintext deviceId leaked in prefs!", asText.contains(deviceId))
            assertFalse("Plaintext deviceName leaked in prefs!", asText.contains("Pixel 9 Owner"))
            assertTrue("Expected IV.ciphertext format", asText.contains('.'))
        }

        val loaded = store.loadCredential()
        assertNotNull(loaded)
        assertEquals(cred, loaded)

        store.clearCredential()
        assertNull(store.loadCredential())
    }

    @Test
    fun `tampered encrypted credential is rejected and wiped safely`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = KeystoreAdminDeviceCredentialStore(context, SoftwareAesGcmKeyProvider())

        val cred = StoredDeviceCredential(
            deviceId = "dev_tamper_check_01",
            deviceToken = "tok_secret_value_abc123",
            deviceName = "Admin Tablet"
        )
        store.saveCredential(cred)

        // Tamper with stored ciphertext in SharedPreferences
        val prefs = context.getSharedPreferences("madinat_admin_device_keystore", Context.MODE_PRIVATE)
        prefs.edit().putString("enc_device_cred", "AAAAAAAAAAAAAAAA.BBBBBBBBBBBBBBBBBBBB").apply()

        assertNull(store.loadCredential())
        assertFalse(prefs.contains("enc_device_cred"))
    }
}
