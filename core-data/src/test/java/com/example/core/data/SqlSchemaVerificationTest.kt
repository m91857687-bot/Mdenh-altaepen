package com.example.core.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlSchemaVerificationTest {

    private fun resolveSqlDir(): File {
        val candidates = listOf(
            File("supabase/sql"),
            File("../supabase/sql")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Could not locate supabase/sql directory from ${File(".").absolutePath}")
    }

    @Test
    fun `all 12 sql files exist and stay strictly under 500 lines`() {
        val sqlDir = resolveSqlDir()
        val expectedFiles = listOf(
            "part_01_extensions.sql",
            "part_02_profiles.sql",
            "part_03_city_layout.sql",
            "part_04_houses_and_privacy.sql",
            "part_05_house_requests.sql",
            "part_06_relationships_constraints.sql",
            "part_07_indexes.sql",
            "part_08_functions.sql",
            "part_09_rls_policies.sql",
            "part_10_realtime.sql",
            "part_11_seed_data.sql",
            "part_12_device_auth_functions.sql"
        )

        expectedFiles.forEach { fileName ->
            val file = File(sqlDir, fileName)
            assertTrue("Missing SQL file $fileName", file.exists())
            val lineCount = file.readLines().size
            assertTrue("$fileName has $lineCount lines (limit is 500)", lineCount <= 500)
        }
        assertTrue("Missing README.md in supabase/sql", File(sqlDir, "README.md").exists())
    }

    @Test
    fun `rls is enabled on all 9 tables including device auth tables`() {
        val sqlDir = resolveSqlDir()
        val rlsContent = File(sqlDir, "part_09_rls_policies.sql").readText()
        val tables = listOf(
            "profiles",
            "city_layout",
            "plots",
            "houses",
            "house_private_details",
            "house_public_details",
            "house_requests",
            "admin_activation_codes",
            "admin_devices"
        )
        tables.forEach { table ->
            assertTrue(
                "RLS not enabled for $table",
                rlsContent.contains("ALTER TABLE public.$table ENABLE ROW LEVEL SECURITY;")
            )
        }
        assertTrue(rlsContent.contains("admin_activation_codes_deny_all"))
        assertTrue(rlsContent.contains("admin_devices_deny_all"))
    }

    @Test
    fun `device auth functions lock server-only helpers away from client roles`() {
        val sqlDir = resolveSqlDir()
        val part12 = File(sqlDir, "part_12_device_auth_functions.sql").readText()
        assertTrue(
            part12.contains("REVOKE ALL ON FUNCTION public.create_admin_activation_code(text, integer) FROM PUBLIC, anon, authenticated;")
        )
        assertTrue(
            part12.contains("REVOKE ALL ON FUNCTION public.revoke_admin_device(text, text) FROM PUBLIC, anon, authenticated;")
        )
        assertTrue(
            part12.contains("REVOKE ALL ON FUNCTION public.revoke_admin_activation_code(uuid) FROM PUBLIC, anon, authenticated;")
        )
        assertTrue(part12.contains("GRANT EXECUTE ON FUNCTION public.activate_admin_device(text, text, text) TO anon, authenticated;"))
        assertTrue(part12.contains("GRANT EXECUTE ON FUNCTION public.verify_admin_device(text, text) TO anon, authenticated;"))
    }

    @Test
    fun `seed script contains all 12 plots and 12 houses idempotently`() {
        val sqlDir = resolveSqlDir()
        val seedContent = File(sqlDir, "part_11_seed_data.sql").readText()
        for (i in 1..12) {
            val suffix = i.toString().padStart(2, '0')
            assertTrue("Missing plot_$suffix", seedContent.contains("'plot_$suffix'"))
            assertTrue("Missing house_$suffix", seedContent.contains("'house_$suffix'"))
        }
        assertTrue(seedContent.contains("ON CONFLICT (id) DO NOTHING"))
        assertEquals(true, seedContent.contains("IF NOT EXISTS (SELECT 1 FROM public.houses LIMIT 1)"))
    }
}
