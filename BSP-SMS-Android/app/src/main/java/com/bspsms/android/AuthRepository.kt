package com.bspsms.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AuthRepository(private val context: Context) {
    companion object {
        const val ADMIN_ID = "9679843774"
        const val ADMIN_PASS = "9933115114"
        private const val PREFS_NAME = "bsp_sms_auth_prefs"
        private const val KEY_SCRIPT_URL = "custom_apps_script_url"
        private const val KEY_IS_ADMIN = "session_is_admin"
        private const val KEY_USER_ID = "session_user_id"
        private const val KEY_PHONE = "session_phone"
        private const val KEY_EXPIRY = "session_expiry"
        private const val KEY_DEVICE_ID = "session_device_id"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getEffectiveScriptUrl(): String {
        val custom = prefs.getString(KEY_SCRIPT_URL, null)?.trim()
        if (!custom.isNullOrBlank()) return custom
        return BuildConfig.APPS_SCRIPT_URL
    }

    fun saveCustomScriptUrl(url: String) {
        prefs.edit().putString(KEY_SCRIPT_URL, url.trim()).apply()
    }

    fun isScriptUrlConfigured(): Boolean {
        val url = getEffectiveScriptUrl()
        return url.startsWith("https://") && !url.contains("REPLACE_WITH_YOUR_DEPLOYMENT_ID")
    }

    fun loadSavedSession(): AuthSession? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false)
        val phone = prefs.getString(KEY_PHONE, "") ?: ""
        val expiry = prefs.getString(KEY_EXPIRY, "") ?: ""
        val deviceId = prefs.getString(KEY_DEVICE_ID, "") ?: resolveStableDeviceId(context)
        return AuthSession(
            isAdmin = isAdmin,
            userId = userId,
            phone = phone,
            expiryDate = expiry,
            deviceId = deviceId
        )
    }

    fun saveSession(session: AuthSession) {
        prefs.edit()
            .putBoolean(KEY_IS_ADMIN, session.isAdmin)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_PHONE, session.phone)
            .putString(KEY_EXPIRY, session.expiryDate)
            .putString(KEY_DEVICE_ID, session.deviceId)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_IS_ADMIN)
            .remove(KEY_USER_ID)
            .remove(KEY_PHONE)
            .remove(KEY_EXPIRY)
            .remove(KEY_DEVICE_ID)
            .apply()
    }

    suspend fun register(
        userId: String,
        phone: String,
        password: String,
        deviceId: String
    ): AuthResponse {
        val payload = JSONObject()
            .put("action", "register")
            .put("userId", userId.trim())
            .put("phone", phone.trim())
            .put("password", password)
            .put("deviceId", deviceId)
        return postToAppsScript(payload)
    }

    suspend fun login(
        identifier: String,
        password: String,
        deviceId: String
    ): AuthResponse {
        val payload = JSONObject()
            .put("action", "login")
            .put("identifier", identifier.trim())
            .put("password", password)
            .put("deviceId", deviceId)
        return postToAppsScript(payload)
    }

    suspend fun checkSession(userId: String, deviceId: String): AuthResponse {
        val payload = JSONObject()
            .put("action", "check_session")
            .put("userId", userId)
            .put("deviceId", deviceId)
        return postToAppsScript(payload)
    }

    suspend fun updateActivity(userId: String, deviceId: String, activity: String) {
        if (!isScriptUrlConfigured()) return
        val payload = JSONObject()
            .put("action", "update_activity")
            .put("userId", userId)
            .put("deviceId", deviceId)
            .put("activity", activity)
        try {
            postToAppsScript(payload)
        } catch (_: Exception) {
        }
    }

    fun verifyAdminCredentials(adminId: String, adminPassword: String): Boolean {
        return adminId.trim() == ADMIN_ID && adminPassword == ADMIN_PASS
    }

    suspend fun adminListUsers(): AuthResponse {
        val payload = JSONObject()
            .put("action", "admin_list_users")
            .put("adminId", ADMIN_ID)
            .put("adminPassword", ADMIN_PASS)
        return postToAppsScript(payload)
    }

    suspend fun adminActivateUser(targetUserId: String): AuthResponse {
        val payload = JSONObject()
            .put("action", "admin_activate_user")
            .put("adminId", ADMIN_ID)
            .put("adminPassword", ADMIN_PASS)
            .put("targetUserId", targetUserId)
        return postToAppsScript(payload)
    }

    suspend fun adminDeactivateUser(targetUserId: String): AuthResponse {
        val payload = JSONObject()
            .put("action", "admin_deactivate_user")
            .put("adminId", ADMIN_ID)
            .put("adminPassword", ADMIN_PASS)
            .put("targetUserId", targetUserId)
        return postToAppsScript(payload)
    }

    suspend fun adminResetDevice(targetUserId: String): AuthResponse {
        val payload = JSONObject()
            .put("action", "admin_reset_device")
            .put("adminId", ADMIN_ID)
            .put("adminPassword", ADMIN_PASS)
            .put("targetUserId", targetUserId)
        return postToAppsScript(payload)
    }

    private suspend fun postToAppsScript(payload: JSONObject): AuthResponse = withContext(Dispatchers.IO) {
        if (!isScriptUrlConfigured()) {
            return@withContext AuthResponse(
                ok = false,
                code = "NOT_CONFIGURED",
                message = "Google Apps Script URL is not configured yet. Sign in as Admin to set the Web App URL."
            )
        }

        val initialUrl = getEffectiveScriptUrl()
        val bodyBytes = payload.toString().toByteArray(Charsets.UTF_8)

        var currentUrl = initialUrl
        var method = "POST"
        var redirectCount = 0

        while (redirectCount < 5) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = method
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Accept", "application/json")
                if (method == "POST") {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setFixedLengthStreamingMode(bodyBytes.size)
                }
            }

            try {
                if (method == "POST") {
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }
                }

                val status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    val nextLocation = conn.getHeaderField("Location")
                    if (nextLocation.isNullOrBlank()) {
                        return@withContext AuthResponse(
                            ok = false,
                            code = "NETWORK_ERROR",
                            message = "Redirect missing Location header (HTTP $status)."
                        )
                    }
                    currentUrl = nextLocation
                    // Apps Script POST returns 302 pointing to a GET echo URL
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                        status == HttpURLConnection.HTTP_MOVED_PERM ||
                        status == HttpURLConnection.HTTP_SEE_OTHER
                    ) {
                        method = "GET"
                    }
                    redirectCount++
                    continue
                }

                val stream = if (status in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
                val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                return@withContext parseResponse(responseText, status)
            } catch (e: Exception) {
                return@withContext AuthResponse(
                    ok = false,
                    code = "NETWORK_ERROR",
                    message = "Connection failed: ${e.message ?: "Check internet connection"}"
                )
            } finally {
                conn.disconnect()
            }
        }

        AuthResponse(
            ok = false,
            code = "NETWORK_ERROR",
            message = "Too many redirects from Google Apps Script."
        )
    }

    private fun parseResponse(rawText: String, httpStatus: Int): AuthResponse {
        return try {
            val json = JSONObject(rawText)
            val userObj = json.optJSONObject("user")?.let(::parseUser)
            val usersArr = json.optJSONArray("users")?.let(::parseUsers) ?: emptyList()
            AuthResponse(
                ok = json.optBoolean("ok", false),
                code = json.optString("code", "UNKNOWN"),
                message = json.optString("message", if (httpStatus in 200..299) "Done" else "HTTP $httpStatus"),
                user = userObj,
                users = usersArr
            )
        } catch (_: Exception) {
            AuthResponse(
                ok = false,
                code = "PARSE_ERROR",
                message = "Unexpected server response (HTTP $httpStatus). Ensure Apps Script is deployed as Web App with access 'Anyone'."
            )
        }
    }

    private fun parseUser(obj: JSONObject): UserRecord {
        return UserRecord(
            userId = obj.optString("userId", ""),
            phone = obj.optString("phone", ""),
            status = obj.optString("status", "inactive"),
            expiryDate = obj.optString("expiryDate", ""),
            activeDeviceId = obj.optString("activeDeviceId", ""),
            lastLogin = obj.optString("lastLogin", ""),
            lastActivity = obj.optString("lastActivity", ""),
            createdAt = obj.optString("createdAt", "")
        )
    }

    private fun parseUsers(arr: JSONArray): List<UserRecord> {
        val list = mutableListOf<UserRecord>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { list.add(parseUser(it)) }
        }
        return list
    }
}
