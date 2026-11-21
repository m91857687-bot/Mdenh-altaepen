package com.example.core.data

import com.example.core.data.supabase.SupabaseConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseConfigTest {

    // Sample unsigned JWT payloads with {"role":"anon"} and {"role":"service_role"}
    private val anonJwt =
        "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlIn0.signature"
    private val serviceRoleJwt =
        "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoic2VydmljZV9yb2xlIiwiaXNzIjoic3VwYWJhc2UifQ.signature"

    @Test
    fun `blank or placeholder config is marked unconfigured with clear error`() {
        val blankConfig = SupabaseConfig("", "")
        assertFalse(blankConfig.isConfigured)
        assertNotNull(blankConfig.validationErrorOrNull())

        val placeholderConfig = SupabaseConfig(
            SupabaseConfig.DEFAULT_URL_PLACEHOLDER,
            SupabaseConfig.DEFAULT_ANON_KEY_PLACEHOLDER
        )
        assertFalse(placeholderConfig.isConfigured)
        assertNotNull(placeholderConfig.validationErrorOrNull())
    }

    @Test
    fun `non-https url is rejected`() {
        val config = SupabaseConfig("http://example.supabase.co", anonJwt)
        assertFalse(config.isConfigured)
        assertFalse(config.isUrlValid)
    }

    @Test
    fun `service_role jwt is strictly rejected`() {
        val config = SupabaseConfig("https://project.supabase.co", serviceRoleJwt)
        assertTrue(config.isPrivilegedServiceRoleKey)
        assertFalse(config.isConfigured)
        assertNotNull(config.validationErrorOrNull())
    }

    @Test
    fun `valid https url and anon key pass configuration check`() {
        val config = SupabaseConfig("https://project.supabase.co/", anonJwt)
        assertFalse(config.isPrivilegedServiceRoleKey)
        assertTrue(config.isConfigured)
        assertNull(config.validationErrorOrNull())
        assertTrue(config.normalizedUrl == "https://project.supabase.co")
    }

    @Test
    fun `publishable key passes and sb_secret key is rejected`() {
        val publishableConfig = SupabaseConfig(
            "https://project.supabase.co",
            "sb_publishable_example_public_key_12345"
        )
        assertFalse(publishableConfig.isPrivilegedServiceRoleKey)
        assertTrue(publishableConfig.isConfigured)
        assertNull(publishableConfig.validationErrorOrNull())

        val secretConfig = SupabaseConfig(
            "https://project.supabase.co",
            "sb_secret_example_privileged_key_12345"
        )
        assertTrue(secretConfig.isPrivilegedServiceRoleKey)
        assertFalse(secretConfig.isConfigured)
        assertNotNull(secretConfig.validationErrorOrNull())
    }
}
