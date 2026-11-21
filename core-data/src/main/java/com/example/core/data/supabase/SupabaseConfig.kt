package com.example.core.data.supabase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Central Supabase client configuration.
 * Only public/publishable client credentials (SUPABASE_URL + SUPABASE_ANON_KEY) are permitted.
 * Never embeds or accepts privileged service_role keys.
 */
data class SupabaseConfig(
    val url: String,
    val anonKey: String
) {
    val normalizedUrl: String
        get() = url.trim()
            .takeUnless { it == DEFAULT_URL_PLACEHOLDER }
            ?.trimEnd('/')
            .orEmpty()

    val normalizedAnonKey: String
        get() = anonKey.trim()
            .takeUnless { it == DEFAULT_ANON_KEY_PLACEHOLDER }
            .orEmpty()

    val isUrlValid: Boolean
        get() = normalizedUrl.startsWith("https://") && normalizedUrl.length > "https://".length

    val isKeyPresent: Boolean
        get() = normalizedAnonKey.isNotEmpty()

    val isPrivilegedServiceRoleKey: Boolean
        get() = isPrivilegedKey(normalizedAnonKey)

    val isConfigured: Boolean
        get() = isUrlValid && isKeyPresent && !isPrivilegedServiceRoleKey

    fun validationErrorOrNull(): String? {
        if (normalizedUrl.isEmpty() || normalizedAnonKey.isEmpty()) {
            return "إعدادات Supabase غير مكتملة. أضف SUPABASE_URL و SUPABASE_ANON_KEY لتفعيل الاتصال الفعلي."
        }
        if (!isUrlValid) {
            return "رابط Supabase غير صالح؛ يجب أن يبدأ بـ https://"
        }
        if (isPrivilegedServiceRoleKey) {
            return "مرفوض أمنيًا: لا يجوز استخدام مفتاح service_role داخل تطبيق Android."
        }
        return null
    }

    companion object {
        const val DEFAULT_URL_PLACEHOLDER = "SUPABASE_URL_DEFAULT_VALUE"
        const val DEFAULT_ANON_KEY_PLACEHOLDER = "SUPABASE_ANON_KEY_DEFAULT_VALUE"

        internal fun isPrivilegedKey(token: String): Boolean =
            token.startsWith("sb_secret_", ignoreCase = true) || isServiceRoleJwt(token)

        internal fun isServiceRoleJwt(token: String): Boolean {
            val parts = token.split('.')
            if (parts.size != 3) return false
            return try {
                val payloadJson = decodeBase64UrlToString(parts[1]) ?: return false
                val obj = Json.parseToJsonElement(payloadJson).jsonObject
                val role = obj["role"]?.jsonPrimitive?.content
                role.equals("service_role", ignoreCase = true)
            } catch (_: Throwable) {
                false
            }
        }

        private fun decodeBase64UrlToString(input: String): String? {
            val clean = input.trim().replace('-', '+').replace('_', '/')
            val padded = when (clean.length % 4) {
                0 -> clean
                2 -> "$clean=="
                3 -> "$clean="
                else -> return null
            }
            val table = IntArray(256) { -1 }
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            alphabet.forEachIndexed { idx, ch -> table[ch.code] = idx }
            val out = ByteArray((padded.length * 3) / 4)
            var outLen = 0
            var i = 0
            while (i < padded.length) {
                val b0 = table[padded[i].code]
                val b1 = table[padded[i + 1].code]
                val c2 = padded[i + 2]
                val c3 = padded[i + 3]
                if (b0 < 0 || b1 < 0) return null
                val b2 = if (c2 == '=') 0 else table[c2.code].also { if (it < 0) return null }
                val b3 = if (c3 == '=') 0 else table[c3.code].also { if (it < 0) return null }
                val block = (b0 shl 18) or (b1 shl 12) or (b2 shl 6) or b3
                out[outLen++] = ((block ushr 16) and 0xFF).toByte()
                if (c2 != '=') out[outLen++] = ((block ushr 8) and 0xFF).toByte()
                if (c3 != '=') out[outLen++] = (block and 0xFF).toByte()
                i += 4
            }
            return out.decodeToString(0, outLen)
        }
    }
}
