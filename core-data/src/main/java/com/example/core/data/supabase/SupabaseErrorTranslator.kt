package com.example.core.data.supabase

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Translates Supabase/network/auth exceptions into clear, honest Arabic error messages
 * and distinguishes network/offline failures from authentication, session, or RLS failures.
 */
object SupabaseErrorTranslator {

    fun isNetworkError(throwable: Throwable): Boolean {
        var cur: Throwable? = throwable
        while (cur != null) {
            if (cur is HttpRequestException ||
                cur is IOException ||
                cur is UnknownHostException ||
                cur is ConnectException ||
                cur is SocketTimeoutException
            ) {
                return true
            }
            cur = cur.cause
        }
        return false
    }

    fun toArabicMessage(throwable: Throwable, fallback: String): String {
        if (isNetworkError(throwable)) {
            return "تعذر الاتصال بالخادم. تحقق من اتصال الإنترنت وأعد المحاولة."
        }
        val msg = throwable.message.orEmpty()
        if (throwable is RestException) {
            return when {
                msg.contains("Invalid login credentials", ignoreCase = true) ||
                    msg.contains("invalid_credentials", ignoreCase = true) ->
                    "البريد الإلكتروني أو كلمة المرور غير صحيحة."
                msg.contains("Email not confirmed", ignoreCase = true) ->
                    "الحساب غير مؤكّد بالبريد الإلكتروني بعد."
                msg.contains("JWT expired", ignoreCase = true) ||
                    msg.contains("invalid_grant", ignoreCase = true) ||
                    msg.contains("refresh_token_not_found", ignoreCase = true) ||
                    (msg.contains("session", ignoreCase = true) && msg.contains("expired", ignoreCase = true)) ||
                    msg.contains("401") ->
                    "انتهت صلاحية الجلسة. يرجى تسجيل الدخول مجددًا."
                msg.contains("42501") ||
                    msg.contains("permission denied", ignoreCase = true) ||
                    msg.contains("row-level security", ignoreCase = true) ||
                    msg.contains("صلاحيات المدير") ||
                    msg.contains("403") ->
                    "عملية مرفوضة: الحساب لا يمتلك صلاحيات المدير (ADMIN)."
                msg.contains("uq_houses_plot_id") || msg.contains("مشغولة") || msg.contains("AVAILABLE") ->
                    "قطعة الأرض المحددة مشغولة أو غير متاحة."
                msg.contains("uq_houses_grid_coord") ->
                    "يوجد بيت آخر على الإحداثي نفسه."
                else -> msg.ifBlank { fallback }
            }
        }
        return msg.ifBlank { fallback }
    }
}
