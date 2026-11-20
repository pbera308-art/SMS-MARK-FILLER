package com.bspsms.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private enum class UserFilter { ALL, ACTIVE, INACTIVE, EXPIRED }

@Composable
fun AdminPanelScreen(
    repo: AuthRepository,
    locale: AppLocale
) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<UserRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var isErrorBanner by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(UserFilter.ALL) }
    var scriptUrlInput by remember { mutableStateOf(repo.getEffectiveScriptUrl()) }
    var showUrlConfig by remember { mutableStateOf(!repo.isScriptUrlConfigured()) }

    fun loadUsers() {
        if (!repo.isScriptUrlConfigured()) {
            showUrlConfig = true
            statusBanner = "Configure your Google Apps Script Web App (/exec) URL below to load & manage users."
            isErrorBanner = true
            return
        }
        isLoading = true
        scope.launch {
            val res = repo.adminListUsers()
            isLoading = false
            if (res.ok) {
                users = res.users
                statusBanner = res.message
                isErrorBanner = false
            } else {
                statusBanner = res.message
                isErrorBanner = true
            }
        }
    }

    LaunchedEffect(Unit) {
        if (repo.isScriptUrlConfigured()) {
            loadUsers()
        }
    }

    val filteredUsers = remember(users, searchQuery, filter) {
        val q = searchQuery.trim().lowercase()
        users.filter { user ->
            val matchesFilter = when (filter) {
                UserFilter.ALL -> true
                UserFilter.ACTIVE -> user.isActive && !user.isExpired
                UserFilter.INACTIVE -> !user.isActive
                UserFilter.EXPIRED -> user.isActive && user.isExpired
            }
            val matchesSearch = q.isEmpty() ||
                user.userId.lowercase().contains(q) ||
                user.phone.lowercase().contains(q) ||
                user.activeDeviceId.lowercase().contains(q)
            matchesFilter && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (locale == AppLocale.BN) "অ্যাডমিন মনিটরিং প্যানেল" else "Admin User Monitor",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Total: ${users.size} · Active: ${users.count { it.isActive && !it.isExpired }} · Inactive: ${users.count { !it.isActive }}",
                    fontSize = 12.sp,
                    color = Color(0xFF475569)
                )
            }
            Row {
                IconButton(onClick = { showUrlConfig = !showUrlConfig }) {
                    Icon(Icons.Outlined.Link, contentDescription = "Configure Apps Script URL", tint = Color(0xFF123B70))
                }
                IconButton(onClick = { loadUsers() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh users", tint = Color(0xFF123B70))
                }
            }
        }

        if (showUrlConfig) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Google Apps Script Web App URL (/exec)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = scriptUrlInput,
                        onValueChange = { scriptUrlInput = it },
                        placeholder = { Text("https://script.google.com/macros/s/.../exec", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            repo.saveCustomScriptUrl(scriptUrlInput)
                            showUrlConfig = false
                            loadUsers()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF123B70)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save URL & Load Users")
                    }
                }
            }
        }

        if (!statusBanner.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = if (isErrorBanner) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isErrorBanner) Color(0xFFFECACA) else Color(0xFFBBF7D0),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Text(
                    text = statusBanner!!,
                    color = if (isErrorBanner) Color(0xFF991B1B) else Color(0xFF166534),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search User ID, phone, or device ID...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == UserFilter.ALL,
                onClick = { filter = UserFilter.ALL },
                label = { Text("All", fontSize = 12.sp) }
            )
            FilterChip(
                selected = filter == UserFilter.ACTIVE,
                onClick = { filter = UserFilter.ACTIVE },
                label = { Text("Active", fontSize = 12.sp) }
            )
            FilterChip(
                selected = filter == UserFilter.INACTIVE,
                onClick = { filter = UserFilter.INACTIVE },
                label = { Text("Inactive", fontSize = 12.sp) }
            )
            FilterChip(
                selected = filter == UserFilter.EXPIRED,
                onClick = { filter = UserFilter.EXPIRED },
                label = { Text("Expired", fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color(0xFF123B70))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading users from Google Sheet...", fontSize = 12.sp, color = Color(0xFF475569))
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredUsers, key = { it.userId.ifBlank { it.phone } }) { user ->
                    val statusColor = when {
                        !user.isActive -> Color(0xFFB45309)
                        user.isExpired -> Color(0xFFDC2626)
                        else -> Color(0xFF15803D)
                    }
                    val statusText = when {
                        !user.isActive -> "Inactive"
                        user.isExpired -> "Expired (${user.expiryDate})"
                        else -> "Active until ${user.expiryDate}"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${user.userId} · ${user.phone}",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = statusText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Device ID: ${user.activeDeviceId.ifBlank { "Unlocked (none)" }}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = "Last Login: ${user.lastLogin.ifBlank { "Never" }}",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = "Activity: ${user.lastActivity.ifBlank { "-" }}",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val res = repo.adminActivateUser(user.userId)
                                            statusBanner = res.message
                                            isErrorBanner = !res.ok
                                            if (res.ok && res.user != null) {
                                                users = users.map { if (it.userId == user.userId) res.user else it }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Activate 30d", fontSize = 11.sp, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val res = repo.adminResetDevice(user.userId)
                                            statusBanner = res.message
                                            isErrorBanner = !res.ok
                                            if (res.ok && res.user != null) {
                                                users = users.map { if (it.userId == user.userId) res.user else it }
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reset Device", fontSize = 11.sp, maxLines = 1)
                                }
                            }

                            if (user.isActive) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val res = repo.adminDeactivateUser(user.userId)
                                            statusBanner = res.message
                                            isErrorBanner = !res.ok
                                            if (res.ok && res.user != null) {
                                                users = users.map { if (it.userId == user.userId) res.user else it }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Deactivate Account", fontSize = 11.sp, color = Color(0xFFDC2626))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
