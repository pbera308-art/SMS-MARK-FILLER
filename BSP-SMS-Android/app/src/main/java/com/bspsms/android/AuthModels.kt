package com.bspsms.android

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

enum class AuthTab {
    REGISTER,
    GENERAL_LOGIN,
    ADMIN_LOGIN
}

data class UserRecord(
    val userId: String,
    val phone: String,
    val status: String,
    val expiryDate: String,
    val activeDeviceId: String,
    val lastLogin: String,
    val lastActivity: String,
    val createdAt: String
) {
    val isActive: Boolean
        get() = status.equals("active", ignoreCase = true)

    val isExpired: Boolean
        get() = isExpiryDatePassed(expiryDate)
}

data class AuthSession(
    val isAdmin: Boolean,
    val userId: String,
    val phone: String,
    val expiryDate: String,
    val deviceId: String
)

data class AuthResponse(
    val ok: Boolean,
    val code: String,
    val message: String,
    val user: UserRecord? = null,
    val users: List<UserRecord> = emptyList()
)

fun isExpiryDatePassed(expiryDate: String): Boolean {
    val trimmed = expiryDate.trim()
    if (trimmed.isEmpty()) return true
    return try {
        val expiry = LocalDate.parse(trimmed.take(10))
        LocalDate.now().isAfter(expiry)
    } catch (_: Exception) {
        true
    }
}

fun generateNewUserId(): String {
    val num = Random.nextInt(100000, 999999)
    return "PB$num"
}

@SuppressLint("HardwareIds")
fun resolveStableDeviceId(context: Context): String {
    val androidId = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    } catch (_: Exception) {
        null
    }
    if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
        return androidId
    }
    val prefs = context.getSharedPreferences("bsp_sms_auth_prefs", Context.MODE_PRIVATE)
    val saved = prefs.getString("fallback_device_uuid", null)
    if (!saved.isNullOrBlank()) return saved
    val generated = UUID.randomUUID().toString()
    prefs.edit().putString("fallback_device_uuid", generated).apply()
    return generated
}
