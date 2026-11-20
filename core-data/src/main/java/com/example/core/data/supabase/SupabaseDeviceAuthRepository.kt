package com.example.core.data.supabase

import android.os.Build
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface AdminDeviceAuthState {
    data object Unconfigured : AdminDeviceAuthState
    data object CheckingDevice : AdminDeviceAuthState
    data class Unenrolled(val feedbackMessage: String? = null) : AdminDeviceAuthState
    data object Activating : AdminDeviceAuthState
    data class Authorized(
        val deviceId: String,
        val deviceName: String,
        val activatedAt: String? = null
    ) : AdminDeviceAuthState
    data class Revoked(val reason: String) : AdminDeviceAuthState
    data class OfflineVerificationBlocked(val message: String) : AdminDeviceAuthState
}

interface AdminDeviceAuthManager {
    val authState: StateFlow<AdminDeviceAuthState>
    fun isAuthorizedDevice(): Boolean
    suspend fun verifyDeviceOnStartup()
    suspend fun activateDevice(activationCode: String, deviceName: String)
    fun beginReEnrollmentFromRevoked()
}

/**
 * Manages server-verified one-time Admin device enrollment and Android Keystore-backed credentials.
 * Replaces Email/Password login: an already-enrolled and server-verified device enters the Admin
 * dashboard directly, while unenrolled, revoked, or offline-unverified devices remain locked.
 */
class SupabaseDeviceAuthRepository(
    private val config: SupabaseConfig,
    private val credentialStore: AdminDeviceCredentialStore,
    clientProvider: (() -> SupabaseClient?)? = null,
    private val connectivityChecker: ConnectivityChecker = AlwaysOnlineChecker,
    private val defaultDeviceName: String = resolveDefaultDeviceName()
) : AdminDeviceAuthManager {
    private val resolveClient: () -> SupabaseClient? = clientProvider ?: {
        SupabaseClientProvider.getOrCreateAdminClient(config) { currentDeviceHeadersOrNull() }
    }

    @Volatile
    private var activeCredential: StoredDeviceCredential? = null

    private val _authState = MutableStateFlow<AdminDeviceAuthState>(
        if (config.isConfigured) AdminDeviceAuthState.CheckingDevice else AdminDeviceAuthState.Unconfigured
    )
    override val authState: StateFlow<AdminDeviceAuthState> = _authState.asStateFlow()

    override fun isAuthorizedDevice(): Boolean =
        _authState.value is AdminDeviceAuthState.Authorized && activeCredential != null

    fun currentDeviceHeadersOrNull(): Pair<String, String>? {
        if (_authState.value !is AdminDeviceAuthState.Authorized) return null
        val cred = activeCredential ?: return null
        return cred.deviceId to cred.deviceToken
    }

    override suspend fun verifyDeviceOnStartup() {
        if (config.validationErrorOrNull() != null) {
            activeCredential = null
            _authState.value = AdminDeviceAuthState.Unconfigured
            return
        }

        _authState.value = AdminDeviceAuthState.CheckingDevice
        val stored = credentialStore.loadCredential()
        if (stored == null) {
            activeCredential = null
            _authState.value = AdminDeviceAuthState.Unenrolled()
            return
        }

        if (!connectivityChecker.isOnline()) {
            activeCredential = null
            _authState.value = AdminDeviceAuthState.OfflineVerificationBlocked(
                "تعذر التحقق من اعتماد الجهاز لعدم توفر الاتصال بالإنترنت. لا يمكن فتح لوحة الإدارة دون تحقق خادمي صالح."
            )
            return
        }

        val client = resolveClient() ?: run {
            activeCredential = null
            _authState.value = AdminDeviceAuthState.Unconfigured
            return
        }

        try {
            val result = verifyStoredCredentialWithServer(client, stored)
            if (result.authorized && result.status.equals("ACTIVE", ignoreCase = true)) {
                val refreshed = stored.copy(
                    deviceName = result.deviceName?.ifBlank { stored.deviceName } ?: stored.deviceName,
                    activatedAt = result.activatedAt ?: stored.activatedAt
                )
                activeCredential = refreshed
                _authState.value = AdminDeviceAuthState.Authorized(
                    deviceId = refreshed.deviceId,
                    deviceName = refreshed.deviceName,
                    activatedAt = refreshed.activatedAt
                )
            } else if (result.status.equals("UNENROLLED", ignoreCase = true)) {
                credentialStore.clearCredential()
                activeCredential = null
                _authState.value = AdminDeviceAuthState.Unenrolled(result.reason)
            } else {
                credentialStore.clearCredential()
                activeCredential = null
                _authState.value = AdminDeviceAuthState.Revoked(
                    result.reason?.ifBlank { null } ?: DEFAULT_REVOKED_REASON
                )
            }
        } catch (t: Throwable) {
            activeCredential = null
            if (!connectivityChecker.isOnline() || SupabaseErrorTranslator.isNetworkError(t)) {
                _authState.value = AdminDeviceAuthState.OfflineVerificationBlocked(
                    "تعذر التحقق من اعتماد الجهاز مع الخادم لعدم توفر الاتصال بالإنترنت."
                )
            } else if (isAuthorizationOrRevocationError(t)) {
                credentialStore.clearCredential()
                _authState.value = AdminDeviceAuthState.Revoked(
                    SupabaseErrorTranslator.toArabicMessage(t, DEFAULT_REVOKED_REASON)
                )
            } else {
                _authState.value = AdminDeviceAuthState.OfflineVerificationBlocked(
                    SupabaseErrorTranslator.toArabicMessage(t, "تعذر التحقق من اعتماد الجهاز على الخادم.")
                )
            }
        }
    }

    override suspend fun activateDevice(activationCode: String, deviceName: String) {
        if (config.validationErrorOrNull() != null) {
            activeCredential = null
            _authState.value = AdminDeviceAuthState.Unconfigured
            return
        }

        val cleanCode = activationCode.trim()
        if (cleanCode.length < 6) {
            _authState.value = AdminDeviceAuthState.Unenrolled("يرجى إدخال رمز التفعيل الصحيح الصادر من الخادم.")
            return
        }

        if (!connectivityChecker.isOnline()) {
            _authState.value = AdminDeviceAuthState.Unenrolled("لا يوجد اتصال بالإنترنت لتفعيل الجهاز عبر الخادم.")
            return
        }

        val client = resolveClient() ?: run {
            _authState.value = AdminDeviceAuthState.Unconfigured
            return
        }

        _authState.value = AdminDeviceAuthState.Activating

        try {
            val deviceId = credentialStore.getOrCreateDeviceId()
            val cleanDeviceName = deviceName.trim().ifBlank { defaultDeviceName }
            val params = buildJsonObject {
                put("p_activation_code", cleanCode)
                put("p_device_id", deviceId)
                put("p_device_name", cleanDeviceName)
            }
            val res = client.postgrest.rpc("activate_admin_device", params)
                .decodeAs<DeviceActivationResultDto>()

            val stored = StoredDeviceCredential(
                deviceId = res.deviceId,
                deviceToken = res.deviceToken,
                deviceName = res.deviceName.ifBlank { cleanDeviceName },
                activatedAt = res.activatedAt
            )
            credentialStore.saveCredential(stored)
            activeCredential = stored
            _authState.value = AdminDeviceAuthState.Authorized(
                deviceId = stored.deviceId,
                deviceName = stored.deviceName,
                activatedAt = stored.activatedAt
            )
        } catch (t: Throwable) {
            activeCredential = null
            if (!connectivityChecker.isOnline() || SupabaseErrorTranslator.isNetworkError(t)) {
                _authState.value = AdminDeviceAuthState.Unenrolled(
                    "تعذر الاتصال بالخادم لتفعيل الجهاز. تحقق من الإنترنت وأعد المحاولة."
                )
            } else {
                _authState.value = AdminDeviceAuthState.Unenrolled(
                    SupabaseErrorTranslator.toArabicMessage(
                        t,
                        "فشل تفعيل الجهاز. تأكد من صحة رمز التفعيل وعدم استخدامه مسبقًا."
                    )
                )
            }
        }
    }

    /**
     * Switches from a Revoked notice back to the empty initial activation form
     * so the owner can enroll with a freshly issued server activation code.
     * Does NOT grant access until server activation succeeds.
     */
    override fun beginReEnrollmentFromRevoked() {
        if (_authState.value is AdminDeviceAuthState.Revoked) {
            activeCredential = null
            _authState.value = AdminDeviceAuthState.Unenrolled()
        }
    }

    /**
     * Enforces fresh server-side verification immediately before sensitive Admin operations.
     * Returns null if verified ACTIVE; returns a descriptive Arabic denial message otherwise
     * and revokes local credentials if the server indicates revocation.
     */
    suspend fun verifyActiveWithServerForMutation(): String? {
        val configErr = config.validationErrorOrNull()
        if (configErr != null) return configErr

        val cred = activeCredential ?: credentialStore.loadCredential()
        if (_authState.value !is AdminDeviceAuthState.Authorized || cred == null) {
            return "عملية مرفوضة: هذا الجهاز غير معتمد للإدارة."
        }

        if (!connectivityChecker.isOnline()) {
            return "لا يوجد اتصال بالإنترنت؛ لا يمكن تنفيذ العملية دون تحقق خادمي صالح."
        }

        val client = resolveClient() ?: return "اتصال Supabase غير مهيأ."

        return try {
            val result = verifyStoredCredentialWithServer(client, cred)
            if (result.authorized && result.status.equals("ACTIVE", ignoreCase = true)) {
                null
            } else {
                val reason = result.reason?.ifBlank { null } ?: DEFAULT_REVOKED_REASON
                revokeLocally(reason)
                reason
            }
        } catch (t: Throwable) {
            if (!connectivityChecker.isOnline() || SupabaseErrorTranslator.isNetworkError(t)) {
                "لا يوجد اتصال بالإنترنت؛ تعذر التحقق من اعتماد الجهاز قبل تنفيذ العملية."
            } else if (isAuthorizationOrRevocationError(t)) {
                val reason = SupabaseErrorTranslator.toArabicMessage(t, DEFAULT_REVOKED_REASON)
                revokeLocally(reason)
                reason
            } else {
                SupabaseErrorTranslator.toArabicMessage(t, "تعذر التحقق من صلاحية الجهاز مع الخادم.")
            }
        }
    }

    suspend fun handleServerUnauthorizedOrRevoked(reason: String = DEFAULT_REVOKED_REASON) {
        revokeLocally(reason)
    }

    private suspend fun verifyStoredCredentialWithServer(
        client: SupabaseClient,
        stored: StoredDeviceCredential
    ): DeviceVerificationResultDto {
        val params = buildJsonObject {
            put("p_device_id", stored.deviceId)
            put("p_device_token", stored.deviceToken)
        }
        return client.postgrest.rpc("verify_admin_device", params)
            .decodeAs<DeviceVerificationResultDto>()
    }

    private suspend fun revokeLocally(reason: String) {
        credentialStore.clearCredential()
        activeCredential = null
        _authState.value = AdminDeviceAuthState.Revoked(reason)
    }

    internal fun isAuthorizationOrRevocationError(throwable: Throwable): Boolean {
        if (throwable !is RestException) return false
        val msg = throwable.message.orEmpty()
        return msg.contains("42501") ||
            msg.contains("REVOKED", ignoreCase = true) ||
            msg.contains("permission denied", ignoreCase = true) ||
            msg.contains("row-level security", ignoreCase = true) ||
            msg.contains("إلغاء اعتماد", ignoreCase = true)
    }

    companion object {
        const val DEFAULT_REVOKED_REASON = "تم إلغاء اعتماد هذا الجهاز من الخادم."

        fun create(
            config: SupabaseConfig,
            credentialStore: AdminDeviceCredentialStore,
            connectivityChecker: ConnectivityChecker = AlwaysOnlineChecker
        ): AdminDeviceAuthManager = SupabaseDeviceAuthRepository(
            config = config,
            credentialStore = credentialStore,
            clientProvider = null,
            connectivityChecker = connectivityChecker
        )

        private fun resolveDefaultDeviceName(): String {
            val combined = listOf(Build.MANUFACTURER, Build.MODEL)
                .map { it.orEmpty().trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(" ")
            return combined.ifBlank { "جهاز الإدارة" }
        }
    }
}
