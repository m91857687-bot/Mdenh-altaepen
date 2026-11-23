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

    @Test
    fun `forward migrations part_14 through part_16 exist, stay under 500 lines, preserve data, and enforce RLS + is_admin`() {
        val sqlDir = resolveSqlDir()
        val forwardFiles = listOf(
            "part_14_city_builder_schema.sql",
            "part_15_city_builder_constraints_indexes.sql",
            "part_16_city_builder_functions_rls_realtime.sql"
        )
        forwardFiles.forEach { fileName ->
            val file = File(sqlDir, fileName)
            assertTrue("Missing forward SQL migration $fileName", file.exists())
            val lines = file.readLines()
            assertTrue("$fileName has ${lines.size} lines (limit is 500)", lines.size <= 500)
            val content = lines.joinToString("\n")
            assertTrue("$fileName must never DROP TABLE existing data", !content.contains("DROP TABLE", ignoreCase = true))
            assertTrue("$fileName must never TRUNCATE existing tables", !content.contains("TRUNCATE", ignoreCase = true))
        }

        val part16 = File(sqlDir, "part_16_city_builder_functions_rls_realtime.sql").readText()
        val newTables = listOf("city_districts", "city_buildings", "city_environment_props", "city_lights")
        newTables.forEach { table ->
            assertTrue("RLS not enabled for $table", part16.contains("ALTER TABLE public.$table ENABLE ROW LEVEL SECURITY;"))
            assertTrue("Admin policy missing for $table", part16.contains("${table}_admin_write"))
        }
        assertTrue(part16.contains("IF NOT public.is_admin() THEN"))
        assertTrue(part16.contains("admin_expand_city_grid"))
        assertTrue(part16.contains("admin_upsert_plot"))
        assertTrue(part16.contains("admin_upsert_public_building"))
        assertTrue(part16.contains("admin_set_lighting_phase"))

        val readme = File(sqlDir, "README.md").readText()
        forwardFiles.forEach { fileName ->
            assertTrue("README.md should document $fileName", readme.contains(fileName))
        }
    }

    @Test
    fun `phase 3 forward migrations part_17 and part_18 exist, stay under 500 lines, preserve data, and secure RPCs`() {
        val sqlDir = resolveSqlDir()
        val phase3Files = listOf(
            "part_17_phase3_house_requests_upgrade.sql",
            "part_18_phase3_city_metadata_and_flags.sql"
        )
        phase3Files.forEach { fileName ->
            val file = File(sqlDir, fileName)
            assertTrue("Missing Phase 3 SQL migration $fileName", file.exists())
            val lines = file.readLines()
            assertTrue("$fileName has ${lines.size} lines (limit is 500)", lines.size <= 500)
            val content = lines.joinToString("\n")
            assertTrue("$fileName must never DROP TABLE existing data", !content.contains("DROP TABLE", ignoreCase = true))
            assertTrue("$fileName must never TRUNCATE existing tables", !content.contains("TRUNCATE", ignoreCase = true))
        }

        val part17 = File(sqlDir, "part_17_phase3_house_requests_upgrade.sql").readText()
        assertTrue(part17.contains("submit_house_request"))
        assertTrue(part17.contains("admin_update_house_request_status"))
        assertTrue(part17.contains("admin_assign_request_plot_and_house"))
        assertTrue(part17.contains("GRANT EXECUTE ON FUNCTION public.submit_house_request"))
        assertTrue(part17.contains("ALTER PUBLICATION supabase_realtime ADD TABLE public.house_requests"))
        listOf("NEW", "UNDER_REVIEW", "APPROVED", "PREPARING_PLOT", "BUILDING_HOUSE", "COMPLETED", "REJECTED").forEach { status ->
            assertTrue("Missing status $status in part_17", part17.contains("'$status'"))
        }

        val part18 = File(sqlDir, "part_18_phase3_city_metadata_and_flags.sql").readText()
        assertTrue(part18.contains("description_ar"))
        assertTrue(part18.contains("public_info_ar"))
        assertTrue(part18.contains("house_title"))
        assertTrue(part18.contains("public_status"))
        assertTrue(part18.contains("allow_owner_name_public"))
        assertTrue(part18.contains("admin_set_house_public_visibility"))
        assertTrue(part18.contains("IF NOT public.is_admin() THEN"))

        val readme = File(sqlDir, "README.md").readText()
        phase3Files.forEach { fileName ->
            assertTrue("README.md should document $fileName", readme.contains(fileName))
        }
    }
}
