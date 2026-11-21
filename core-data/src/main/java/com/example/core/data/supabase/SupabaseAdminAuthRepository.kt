package com.example.core.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

sealed interface AdminAuthState {
    data object Unconfigured : AdminAuthState
    data object CheckingSession : AdminAuthState
    data class Unauthenticated(val feedbackMessage: String? = null) : AdminAuthState
    data object SigningIn : AdminAuthState
    data class AuthenticatedAdmin(
        val userId: String,
        val email: String,
        val displayName: String
    ) : AdminAuthState
    data class AccessDenied(val email: String, val message: String) : AdminAuthState
    data class SessionExpired(val message: String) : AdminAuthState
}

interface AdminAuthManager {
    val authState: StateFlow<AdminAuthState>
    fun isAuthenticatedAdmin(): Boolean
    suspend fun restoreAndVerifySession()
    suspend fun signIn(email: String, password: String)
    suspend fun signOut()

    suspend fun ensureAuthenticatedAdminForMutation(): String? =
        if (isAuthenticatedAdmin()) null else "عملية مرفوضة: يجب تسجيل الدخول بحساب الإدارة."

    fun isAuthorizationOrSessionError(throwable: Throwable): Boolean = false

    suspend fun handleAuthorizationOrSessionFailure(
        throwable: Throwable,
        translatedMessage: String
    ) {
        // Default no-op for test fakes unless overridden
    }
}

/**
 * Manages traditional Phase 4 Supabase Auth (Email + Password) for :app-admin,
 * enforcing public.profiles.role = 'ADMIN', safe session restoration/refresh, and sign-out.
 */
class SupabaseAdminAuthRepository(
    private val config: SupabaseConfig,
    clientProvider: (() -> SupabaseClient?)? = null,
    private val connectivityChecker: ConnectivityChecker = AlwaysOnlineChecker
) : AdminAuthManager {

    private val resolveClient: () -> SupabaseClient? = clientProvider ?: {
        SupabaseClientProvider.getOrCreateAdminClient(config)
    }

    private val _authState = MutableStateFlow<AdminAuthState>(
        if (config.isConfigured) AdminAuthState.CheckingSession else AdminAuthState.Unconfigured
    )
    override val authState: StateFlow<AdminAuthState> = _authState.asStateFlow()

    override fun isAuthenticatedAdmin(): Boolean =
        _authState.value is AdminAuthState.AuthenticatedAdmin

    override suspend fun restoreAndVerifySession() {
        if (config.validationErrorOrNull() != null) {
            _authState.value = AdminAuthState.Unconfigured
            return
        }

        _authState.value = AdminAuthState.CheckingSession
        val client = resolveClient() ?: run {
            _authState.value = AdminAuthState.Unconfigured
            return
        }

        withTimeoutOrNull(3_000L) {
            client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        }

        val existingSession = client.auth.currentSessionOrNull()
        if (existingSession == null) {
            _authState.value = AdminAuthState.Unauthenticated()
            return
        }

        if (!connectivityChecker.isOnline()) {
            _authState.value = AdminAuthState.Unauthenticated(
                "لا يوجد اتصال بالإنترنت للتحقق من الجلسة وصلاحيات الإدارة."
            )
            return
        }

        try {
            val nowEpochSec = System.currentTimeMillis() / 1000L
            if (existingSession.expiresAt.epochSeconds <= nowEpochSec + SESSION_REFRESH_LEEWAY_SECONDS) {
                client.auth.refreshCurrentSession()
            }

            val currentUser = client.auth.currentUserOrNull()
                ?: client.auth.currentSessionOrNull()?.user
                ?: run {
                    safeLocalSignOut(client)
                    _authState.value = AdminAuthState.SessionExpired(DEFAULT_SESSION_EXPIRED_MESSAGE)
                    return
                }

            val profile = fetchProfileWithAutoRefresh(client, currentUser.id)
            applyProfileAuthorization(client, currentUser.id, currentUser.email.orEmpty(), profile)
        } catch (t: Throwable) {
            if (!connectivityChecker.isOnline() || SupabaseErrorTranslator.isNetworkError(t)) {
                _authState.value = AdminAuthState.Unauthenticated(
                    "تعذر الاتصال بالخادم للتحقق من الجلسة. تحقق من الإنترنت وأعد المحاولة."
                )
            } else if (isSessionExpiredError(t)) {
                safeLocalSignOut(client)
                _authState.value = AdminAuthState.SessionExpired(DEFAULT_SESSION_EXPIRED_MESSAGE)
            } else if (isPermissionDeniedError(t)) {
                val email = client.auth.currentUserOrNull()?.email.orEmpty()
                safeSignOut(client)
                _authState.value = AdminAuthState.AccessDenied(
                    email = email,
                    message = SupabaseErrorTranslator.toArabicMessage(
                        t,
                        "تم رفض الوصول: الحساب لا يمتلك صلاحيات الإدارة (ADMIN)."
                    )
                )
            } else {
                safeLocalSignOut(client)
                _authState.value = AdminAuthState.Unauthenticated(
                    SupabaseErrorTranslator.toArabicMessage(t, "تعذر التحقق من جلسة الإدارة.")
                )
            }
        }
    }

    override suspend fun signIn(email: String, password: String) {
        if (config.validationErrorOrNull() != null) {
            _authState.value = AdminAuthState.Unconfigured
            return
        }

        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@") || password.isEmpty()) {
            _authState.value = AdminAuthState.Unauthenticated(
                "يرجى إدخال بريد إلكتروني صحيح وكلمة مرور صالحة."
            )
            return
        }

        if (!connectivityChecker.isOnline()) {
            _authState.value = AdminAuthState.Unauthenticated(
                "لا يوجد اتصال بالإنترنت لتسجيل الدخول."
            )
            return
        }

        val client = resolveClient() ?: run {
            _authState.value = AdminAuthState.Unconfigured
            return
        }

        _authState.value = AdminAuthState.SigningIn

        try {
            client.auth.signInWith(Email) {
                this.email = cleanEmail
                this.password = password
            }

            val user = client.auth.currentUserOrNull()
                ?: client.auth.currentSessionOrNull()?.user
                ?: run {
                    safeLocalSignOut(client)
                    _authState.value = AdminAuthState.Unauthenticated(
                        "تعذر استرداد بيانات الحساب بعد تسجيل الدخول."
                    )
                    return
                }

            val profile = fetchProfileWithAutoRefresh(client, user.id)
            applyProfileAuthorization(client, user.id, user.email ?: cleanEmail, profile)
        } catch (t: Throwable) {
            safeLocalSignOut(client)
            if (!connectivityChecker.isOnline() || SupabaseErrorTranslator.isNetworkError(t)) {
                _authState.value = AdminAuthState.Unauthenticated(
                    "تعذر الاتصال بالخادم لتسجيل الدخول. تحقق من الإنترنت وأعد المحاولة."
                )
            } else if (isPermissionDeniedError(t)) {
                _authState.value = AdminAuthState.AccessDenied(
                    email = cleanEmail,
                    message = SupabaseErrorTranslator.toArabicMessage(
                        t,
                        "تم رفض الدخول: هذا الحساب لا يمتلك صلاحية الإدارة (ADMIN)."
                    )
                )
            } else {
                _authState.value = AdminAuthState.Unauthenticated(
                    SupabaseErrorTranslator.toArabicMessage(
                        t,
                        "فشل تسجيل الدخول. تأكد من صحة البريد الإلكتروني وكلمة المرور."
                    )
                )
            }
        }
    }

    override suspend fun signOut() {
        val client = resolveClient()
        if (client != null) {
            safeSignOut(client)
        }
        _authState.value = if (config.isConfigured) {
            AdminAuthState.Unauthenticated()
        } else {
            AdminAuthState.Unconfigured
        }
    }

    override suspend fun ensureAuthenticatedAdminForMutation(): String? {
        val configErr = config.validationErrorOrNull()
        if (configErr != null) return configErr

        if (_authState.value !is AdminAuthState.AuthenticatedAdmin) {
            return "عملية مرفوضة: يجب تسجيل الدخول بحساب الإدارة أولًا."
        }

        if (!connectivityChecker.isOnline()) {
            return "لا يوجد اتصال بالإنترنت؛ لا يمكن تنفيذ العملية دون اتصال بالخادم."
        }

        val client = resolveClient() ?: return "اتصال Supabase غير مهيأ."
        val session = client.auth.currentSessionOrNull()
        if (session == null) {
            _authState.value = AdminAuthState.SessionExpired(DEFAULT_SESSION_EXPIRED_MESSAGE)
            return DEFAULT_SESSION_EXPIRED_MESSAGE
        }

        val nowEpochSec = System.currentTimeMillis() / 1000L
        if (session.expiresAt.epochSeconds <= nowEpochSec + SESSION_REFRESH_LEEWAY_SECONDS) {
            try {
                client.auth.refreshCurrentSession()
            } catch (t: Throwable) {
                return if (!connectivityChecker.isOnline() || SupabaseErrorTranslator.isNetworkError(t)) {
                    "تعذر تجديد جلسة الإدارة لعدم توفر الاتصال بالخادم."
                } else {
                    safeLocalSignOut(client)
                    _authState.value = AdminAuthState.SessionExpired(DEFAULT_SESSION_EXPIRED_MESSAGE)
                    DEFAULT_SESSION_EXPIRED_MESSAGE
                }
            }
        }

        return null
    }

    override fun isAuthorizationOrSessionError(throwable: Throwable): Boolean =
        isSessionExpiredError(throwable) || isPermissionDeniedError(throwable)

    override suspend fun handleAuthorizationOrSessionFailure(
        throwable: Throwable,
        translatedMessage: String
    ) {
        val client = resolveClient()
        if (isSessionExpiredError(throwable)) {
            if (client != null) safeLocalSignOut(client)
            _authState.value = AdminAuthState.SessionExpired(
                translatedMessage.ifBlank { DEFAULT_SESSION_EXPIRED_MESSAGE }
            )
        } else if (isPermissionDeniedError(throwable)) {
            val email = (_authState.value as? AdminAuthState.AuthenticatedAdmin)?.email.orEmpty()
            if (client != null) safeSignOut(client)
            _authState.value = AdminAuthState.AccessDenied(
                email = email,
                message = translatedMessage.ifBlank { DEFAULT_ACCESS_DENIED_MESSAGE }
            )
        }
    }

    private suspend fun fetchProfileWithAutoRefresh(
        client: SupabaseClient,
        userId: String
    ): ProfileDto? {
        return try {
            client.from("profiles").select {
                filter { eq("id", userId) }
            }.decodeSingleOrNull<ProfileDto>()
        } catch (t: Throwable) {
            if (isSessionExpiredError(t)) {
                client.auth.refreshCurrentSession()
                client.from("profiles").select {
                    filter { eq("id", userId) }
                }.decodeSingleOrNull<ProfileDto>()
            } else {
                throw t
            }
        }
    }

    private suspend fun applyProfileAuthorization(
        client: SupabaseClient,
        userId: String,
        email: String,
        profile: ProfileDto?
    ) {
        if (profile != null && profile.role.equals(ROLE_ADMIN, ignoreCase = true)) {
            _authState.value = AdminAuthState.AuthenticatedAdmin(
                userId = userId,
                email = email,
                displayName = profile.displayName.ifBlank { email }
            )
        } else {
            val actualRole = profile?.role ?: "VIEWER"
            safeSignOut(client)
            _authState.value = AdminAuthState.AccessDenied(
                email = email,
                message = "تم رفض الدخول: هذا الحساب ($actualRole) لا يمتلك صلاحية الإدارة ($ROLE_ADMIN)."
            )
        }
    }

    private suspend fun safeSignOut(client: SupabaseClient) {
        runCatching { client.auth.signOut() }
        safeLocalSignOut(client)
    }

    private suspend fun safeLocalSignOut(client: SupabaseClient) {
        runCatching { client.auth.signOut(SignOutScope.LOCAL) }
        runCatching { client.auth.clearSession() }
    }

    internal fun isSessionExpiredError(throwable: Throwable): Boolean {
        if (throwable !is RestException) return false
        val msg = throwable.message.orEmpty()
        return msg.contains("JWT expired", ignoreCase = true) ||
            msg.contains("invalid_grant", ignoreCase = true) ||
            msg.contains("refresh_token_not_found", ignoreCase = true) ||
            (msg.contains("session", ignoreCase = true) && msg.contains("expired", ignoreCase = true)) ||
            msg.contains("401")
    }

    internal fun isPermissionDeniedError(throwable: Throwable): Boolean {
        if (throwable !is RestException) return false
        val msg = throwable.message.orEmpty()
        return msg.contains("42501") ||
            msg.contains("permission denied", ignoreCase = true) ||
            msg.contains("row-level security", ignoreCase = true) ||
            msg.contains("صلاحيات المدير") ||
            msg.contains("403")
    }

    companion object {
        const val ROLE_ADMIN = "ADMIN"
        private const val SESSION_REFRESH_LEEWAY_SECONDS = 60L
        const val DEFAULT_SESSION_EXPIRED_MESSAGE =
            "انتهت صلاحية جلسة الإدارة. يرجى تسجيل الدخول مجددًا."
        const val DEFAULT_ACCESS_DENIED_MESSAGE =
            "تم رفض الوصول: الحساب لا يمتلك صلاحيات الإدارة (ADMIN)."

        fun create(
            config: SupabaseConfig,
            connectivityChecker: ConnectivityChecker = AlwaysOnlineChecker
        ): AdminAuthManager = SupabaseAdminAuthRepository(
            config = config,
            clientProvider = null,
            connectivityChecker = connectivityChecker
        )
    }
}
