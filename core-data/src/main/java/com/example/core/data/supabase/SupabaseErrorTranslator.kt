package com.example.core.data.supabase

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Translates Supabase/network exceptions into clear, honest Arabic error messages
 * and distinguishes network/offline failures from authorization or validation failures.
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
            val firstLine = msg.lineSequence().firstOrNull()?.trim().orEmpty()
            return when {
                msg.contains("رمز التفعيل") ->
                    firstLine.ifBlank { fallback }
                msg.contains("إلغاء اعتماد هذا الجهاز") || msg.contains("غير معتمد") ->
                    firstLine.ifBlank { fallback }
                msg.contains("JWT expired", ignoreCase = true) ||
                    (msg.contains("session", ignoreCase = true) && msg.contains("expired", ignoreCase = true)) ->
                    "انتهت صلاحية اعتماد الجلسة. يرجى إعادة التحقق من الجهاز."
                msg.contains("42501") ||
                    msg.contains("permission denied", ignoreCase = true) ||
                    msg.contains("row-level security", ignoreCase = true) ||
                    msg.contains("صلاحيات المدير") ->
                    "عملية مرفوضة: الجهاز غير معتمد أو تم إلغاء صلاحيته من الخادم."
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
