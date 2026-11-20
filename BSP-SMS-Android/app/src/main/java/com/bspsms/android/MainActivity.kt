package com.bspsms.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.launch

enum class AppLocale { EN, BN }

enum class ToolDestination(
    val id: String,
    val titleEn: String,
    val titleBn: String,
    val descEn: String,
    val descBn: String,
    val entryPath: String,
    val contentScript: String? = null,
    val icon: ImageVector
) {
    FORMATIVE(
        id = "formative",
        titleEn = "Formative Filler",
        titleBn = "ফরমেটিভ ফিলার",
        descEn = "Automatic marks filling",
        descBn = "স্বয়ংক্রিয় নম্বর পূরণ",
        entryPath = "formative/popup.html",
        icon = Icons.Outlined.Edit
    ),
    TALK(
        id = "talk",
        titleEn = "Talk to Filler",
        titleBn = "টক টু ফিলার",
        descEn = "Voice-controlled marks entry",
        descBn = "ভয়েস-নিয়ন্ত্রিত নম্বর এন্ট্রি",
        entryPath = "talk/popup.html",
        icon = Icons.Outlined.Mic
    ),
    LPCD(
        id = "lpcd",
        titleEn = "LPCD Filler",
        titleBn = "এলপিসিডি ফিলার",
        descEn = "Qualitative data auto & manual",
        descBn = "গুণগত তথ্য স্বয়ংক্রিয় ও ম্যানুয়াল",
        entryPath = "lpcd/popup.html",
        contentScript = "lpcd/content.js",
        icon = Icons.Outlined.Layers
    ),
    BCO(
        id = "bco",
        titleEn = "BCO Filler",
        titleBn = "বিসিও ফিলার",
        descEn = "A/B form group filler",
        descBn = "এ/বি ফর্ম গ্রুপ ফিলার",
        entryPath = "bco/ab_form_filler_v2/popup.html",
        contentScript = "bco/ab_form_filler_v2/content.js",
        icon = Icons.Outlined.GridView
    ),
    EXCEL(
        id = "excel",
        titleEn = "Excel Filler",
        titleBn = "এক্সেল ফিলার",
        descEn = "Excel / CSV to webpage",
        descBn = "এক্সেল / সিএসভি থেকে ওয়েবপেজ",
        entryPath = "excel/popup.html",
        icon = Icons.Outlined.TableChart
    )
}

sealed interface NavScreen {
    data object Portal : NavScreen
    data object Hub : NavScreen
    data object AdminPanel : NavScreen
    data class Tool(val tool: ToolDestination) : NavScreen
}

class MainActivity : ComponentActivity() {
    companion object {
        const val PORTAL_URL = "https://www.school.banglarshiksha.gov.in/sms/"
        private const val ASSET_ORIGIN = "https://appassets.androidplatform.net"
        private const val ASSET_BASE = "$ASSET_ORIGIN/assets/extension/"
    }

    private var portalWebView: WebView? = null
    private var toolWebView: WebView? = null
    private var activeBridge: ChromeCompatBridge? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingAudioRequest: PermissionRequest? = null
    private var pendingSpeechCallback: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)

        setContent {
            MaterialTheme {
                BspSmsRoot()
            }
        }
    }

    override fun onDestroy() {
        activeBridge?.destroy()
        super.onDestroy()
    }

    @Composable
    private fun BspSmsRoot() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val repo = remember { AuthRepository(context) }
        val deviceId = remember { resolveStableDeviceId(context) }

        var locale by remember { mutableStateOf(AppLocale.EN) }
        var session by remember { mutableStateOf(repo.loadSavedSession()) }
        var blockedReason by remember { mutableStateOf<String?>(null) }
        var isCheckingSession by remember { mutableStateOf(false) }

        fun verifyActiveSession(current: AuthSession) {
            if (current.isAdmin) {
                blockedReason = null
                return
            }
            if (isExpiryDatePassed(current.expiryDate)) {
                blockedReason = if (locale == AppLocale.BN) {
                    "আপনার ১ মাসের (৩০ দিনের) সাবস্ক্রিপশনের মেয়াদ (${current.expiryDate}) শেষ হয়েছে। রিনিউ করতে অ্যাডমিনের সাথে যোগাযোগ করুন।"
                } else {
                    "Your 1-month (30-day) subscription expired on ${current.expiryDate}. Please contact Admin to renew/activate your account."
                }
            }
            if (repo.isScriptUrlConfigured()) {
                isCheckingSession = true
                scope.launch {
                    val res = repo.checkSession(current.userId, deviceId)
                    isCheckingSession = false
                    if (res.ok) {
                        val updatedUser = res.user
                        if (updatedUser != null && updatedUser.expiryDate != current.expiryDate) {
                            val updatedSession = current.copy(
                                expiryDate = updatedUser.expiryDate,
                                phone = updatedUser.phone.ifBlank { current.phone }
                            )
                            repo.saveSession(updatedSession)
                            session = updatedSession
                        }
                        blockedReason = null
                    } else if (res.code in setOf("INACTIVE", "EXPIRED", "DEVICE_LOCKED", "NOT_FOUND")) {
                        blockedReason = res.message
                    }
                }
            }
        }

        LaunchedEffect(session?.userId, session?.isAdmin) {
            val current = session
            if (current != null) {
                verifyActiveSession(current)
            } else {
                blockedReason = null
            }
        }

        DisposableEffect(session?.userId, session?.isAdmin) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val current = session
                    if (current != null && !current.isAdmin) {
                        verifyActiveSession(current)
                    }
                }
            }
            this@MainActivity.lifecycle.addObserver(observer)
            onDispose {
                this@MainActivity.lifecycle.removeObserver(observer)
            }
        }

        val currentSession = session
        when {
            currentSession == null -> {
                AuthGateScreen(
                    repo = repo,
                    deviceId = deviceId,
                    locale = locale,
                    onChangeLocale = { locale = it },
                    onAuthenticated = { newSession ->
                        blockedReason = null
                        session = newSession
                    }
                )
            }
            !currentSession.isAdmin && blockedReason != null -> {
                SubscriptionBlockedScreen(
                    session = currentSession,
                    reasonMessage = blockedReason!!,
                    locale = locale,
                    isChecking = isCheckingSession,
                    onRecheck = { verifyActiveSession(currentSession) },
                    onSignOut = {
                        repo.clearSession()
                        blockedReason = null
                        session = null
                    }
                )
            }
            else -> {
                BspSmsApp(
                    session = currentSession,
                    repo = repo,
                    deviceId = deviceId,
                    locale = locale,
                    onChangeLocale = { locale = it },
                    onSignOut = {
                        repo.clearSession()
                        blockedReason = null
                        portalWebView = null
                        toolWebView = null
                        session = null
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun BspSmsApp(
        session: AuthSession,
        repo: AuthRepository,
        deviceId: String,
        locale: AppLocale,
        onChangeLocale: (AppLocale) -> Unit,
        onSignOut: () -> Unit
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

        var currentScreen by remember { mutableStateOf<NavScreen>(NavScreen.Portal) }
        var portalProgress by remember { mutableIntStateOf(100) }
        var portalPageTitle by remember { mutableStateOf("Banglarshiksha SMS") }

        LaunchedEffect(currentScreen, session.userId) {
            if (!session.isAdmin) {
                val activityLabel = when (val s = currentScreen) {
                    NavScreen.Portal -> "Opened SMS Portal"
                    NavScreen.Hub -> "Opened Tools Hub"
                    NavScreen.AdminPanel -> "Admin Panel"
                    is NavScreen.Tool -> "Opened ${s.tool.titleEn}"
                }
                repo.updateActivity(session.userId, deviceId, activityLabel)
            }
        }

        var audioPermissionLauncherRef: androidx.activity.result.ActivityResultLauncher<String>? = null

        val bridge = remember {
            ChromeCompatBridge(
                context = context,
                portalWebViewProvider = { portalWebView },
                toolWebViewProvider = { toolWebView },
                onRequestRecordAudio = { cb ->
                    pendingSpeechCallback = cb
                    audioPermissionLauncherRef?.launch(Manifest.permission.RECORD_AUDIO)
                },
                onHubAction = { action ->
                    if (action == "BSP_MINIMIZE" || action == "BSP_CLOSE") {
                        currentScreen = NavScreen.Portal
                    }
                }
            ).also { activeBridge = it }
        }

        val assetLoader = remember {
            WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
        }

        val fileChooserLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val callback = filePathCallback
            filePathCallback = null
            if (callback == null) return@rememberLauncherForActivityResult

            if (result.resultCode == RESULT_OK) {
                val uris = mutableListOf<Uri>()
                val data = result.data
                val clipData = data?.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let { uris.add(it) }
                    }
                } else {
                    data?.data?.let { uris.add(it) }
                }
                callback.onReceiveValue(uris.toTypedArray())
            } else {
                callback.onReceiveValue(null)
            }
        }

        val audioPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            val req = pendingAudioRequest
            pendingAudioRequest = null
            if (req != null) {
                if (granted) {
                    req.grant(req.resources)
                } else {
                    req.deny()
                }
            }
            val speechCb = pendingSpeechCallback
            pendingSpeechCallback = null
            speechCb?.invoke(granted)
        }
        audioPermissionLauncherRef = audioPermissionLauncher

        BackHandler(enabled = drawerState.isOpen || currentScreen != NavScreen.Portal || (portalWebView?.canGoBack() == true)) {
            when {
                drawerState.isOpen -> scope.launch { drawerState.close() }
                currentScreen != NavScreen.Portal -> currentScreen = NavScreen.Portal
                portalWebView?.canGoBack() == true -> portalWebView?.goBack()
            }
        }

        val primaryBlue = Color(0xFF123B70)
        val saffronStart = Color(0xFFFF9933)
        val saffronEnd = Color(0xFFF57C00)
        val screenTitle = when (val s = currentScreen) {
            NavScreen.Portal -> if (locale == AppLocale.BN) "এসএমএস পোর্টাল" else "SMS Portal"
            NavScreen.Hub -> if (locale == AppLocale.BN) "টুলস হাব" else "Tools Hub"
            NavScreen.AdminPanel -> if (locale == AppLocale.BN) "অ্যাডমিন মনিটর" else "Admin Monitor"
            is NavScreen.Tool -> if (locale == AppLocale.BN) s.tool.titleBn else s.tool.titleEn
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = currentScreen != NavScreen.Portal || drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(296.dp),
                    drawerContainerColor = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(primaryBlue)
                                .padding(horizontal = 18.dp, vertical = 20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            brush = Brush.linearGradient(listOf(saffronStart, saffronEnd)),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "PB",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            lineHeight = 14.sp
                                        )
                                        Text(
                                            text = "SMS",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            lineHeight = 10.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "BSP-SMS",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    Text(
                                        text = if (session.isAdmin) {
                                            "Admin (${session.userId}) · No Expiry"
                                        } else {
                                            "${session.userId} · Exp: ${session.expiryDate}"
                                        },
                                        color = Color(0xFFD6E6FA),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (locale == AppLocale.BN) "পোর্টাল ও হাব" else "Portal & Workspace",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                        )

                        NavigationDrawerItem(
                            selected = currentScreen == NavScreen.Portal,
                            onClick = {
                                currentScreen = NavScreen.Portal
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                            label = {
                                Text(if (locale == AppLocale.BN) "বাংলারশিক্ষা এসএমএস পোর্টাল" else "Banglarshiksha SMS Portal")
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color(0xFFE8F0FE),
                                selectedIconColor = primaryBlue,
                                selectedTextColor = primaryBlue
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        NavigationDrawerItem(
                            selected = currentScreen == NavScreen.Hub,
                            onClick = {
                                currentScreen = NavScreen.Hub
                                scope.launch { drawerState.close() }
                            },
                            icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            label = {
                                Text(if (locale == AppLocale.BN) "সকল অটোমেশন টুল (হাব)" else "All Automation Tools (Hub)")
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color(0xFFE8F0FE),
                                selectedIconColor = primaryBlue,
                                selectedTextColor = primaryBlue
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        Text(
                            text = if (locale == AppLocale.BN) "৫টি অটোমেশন টুলস" else "5 Automation Tools",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                        )

                        ToolDestination.entries.forEach { tool ->
                            val selected = (currentScreen as? NavScreen.Tool)?.tool == tool
                            NavigationDrawerItem(
                                selected = selected,
                                onClick = {
                                    currentScreen = NavScreen.Tool(tool)
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(tool.icon, contentDescription = null) },
                                label = {
                                    Column {
                                        Text(
                                            text = if (locale == AppLocale.BN) tool.titleBn else tool.titleEn,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (locale == AppLocale.BN) tool.descBn else tool.descEn,
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Color(0xFFE8F0FE),
                                    selectedIconColor = primaryBlue,
                                    selectedTextColor = primaryBlue
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                            )
                        }

                        if (session.isAdmin) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            Text(
                                text = if (locale == AppLocale.BN) "অ্যাডমিন কন্ট্রোল" else "Administration",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                            )
                            NavigationDrawerItem(
                                selected = currentScreen == NavScreen.AdminPanel,
                                onClick = {
                                    currentScreen = NavScreen.AdminPanel
                                    scope.launch { drawerState.close() }
                                },
                                icon = { Icon(Icons.Outlined.AdminPanelSettings, contentDescription = null) },
                                label = {
                                    Text(if (locale == AppLocale.BN) "অ্যাডমিন ইউজার মনিটর" else "Admin User Monitor")
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Color(0xFFFFF7ED),
                                    selectedIconColor = saffronEnd,
                                    selectedTextColor = saffronEnd
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        HorizontalDivider()

                        NavigationDrawerItem(
                            selected = false,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    onSignOut()
                                }
                            },
                            icon = { Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, tint = Color(0xFFDC2626)) },
                            label = {
                                Text(
                                    text = if (locale == AppLocale.BN) "সাইন আউট" else "Sign Out",
                                    color = Color(0xFFDC2626)
                                )
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 6.dp)) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = locale == AppLocale.EN,
                                    onClick = { onChangeLocale(AppLocale.EN) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) {
                                    Text("English")
                                }
                                SegmentedButton(
                                    selected = locale == AppLocale.BN,
                                    onClick = { onChangeLocale(AppLocale.BN) },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) {
                                    Text("বাংলা")
                                }
                            }
                        }
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = primaryBlue,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White
                        ),
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Open navigation drawer")
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = screenTitle,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (currentScreen == NavScreen.Portal) {
                                    Text(
                                        text = portalPageTitle,
                                        fontSize = 11.sp,
                                        color = Color(0xFFD6E6FA),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        actions = {
                            if (currentScreen == NavScreen.Portal) {
                                IconButton(onClick = { portalWebView?.reload() }) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = "Reload portal")
                                }
                            } else {
                                IconButton(onClick = { currentScreen = NavScreen.Portal }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Return to portal")
                                }
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Keep Portal WebView mounted so login session and active portal page stay intact
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (currentScreen == NavScreen.Portal && portalProgress in 1..99) {
                            LinearProgressIndicator(
                                progress = { portalProgress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF1766A7)
                            )
                        }

                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                createPortalWebView(
                                    ctx = ctx,
                                    bridge = bridge,
                                    onProgress = { portalProgress = it },
                                    onTitle = { portalPageTitle = it },
                                    onOpenFileChooser = { callback, intent ->
                                        filePathCallback?.onReceiveValue(null)
                                        filePathCallback = callback
                                        fileChooserLauncher.launch(intent)
                                    },
                                    onAudioPermission = { request ->
                                        val granted = ContextCompat.checkSelfPermission(
                                            ctx,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            request.grant(request.resources)
                                        } else {
                                            pendingAudioRequest = request
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                ).also { portalWebView = it }
                            }
                        )
                    }

                    // Overlay the active Tool / Hub screen cleanly while preserving Portal WebView state underneath
                    if (currentScreen != NavScreen.Portal) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color(0xFFF8FAFC)
                        ) {
                            when (val screen = currentScreen) {
                                NavScreen.Hub -> {
                                    ToolWebViewPane(
                                        entryPath = "hub.html",
                                        bridge = bridge,
                                        assetLoader = assetLoader,
                                        locale = locale,
                                        onOpenFileChooser = { callback, intent ->
                                            filePathCallback?.onReceiveValue(null)
                                            filePathCallback = callback
                                            fileChooserLauncher.launch(intent)
                                        },
                                        onAudioPermission = { request ->
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                request.grant(request.resources)
                                            } else {
                                                pendingAudioRequest = request
                                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        onBackToPortal = { currentScreen = NavScreen.Portal }
                                    )
                                }
                                is NavScreen.Tool -> {
                                    val exists = remember(screen.tool) {
                                        bridge.assetExists(screen.tool.entryPath)
                                    }
                                    if (exists) {
                                        ToolWebViewPane(
                                            entryPath = screen.tool.entryPath,
                                            bridge = bridge,
                                            assetLoader = assetLoader,
                                            locale = locale,
                                            onOpenFileChooser = { callback, intent ->
                                                filePathCallback?.onReceiveValue(null)
                                                filePathCallback = callback
                                                fileChooserLauncher.launch(intent)
                                            },
                                            onAudioPermission = { request ->
                                                val granted = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (granted) {
                                                    request.grant(request.resources)
                                                } else {
                                                    pendingAudioRequest = request
                                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            },
                                            onBackToPortal = { currentScreen = NavScreen.Portal }
                                        )
                                    } else {
                                        MissingToolAssetNotice(
                                            tool = screen.tool,
                                            locale = locale,
                                            onReturnToPortal = { currentScreen = NavScreen.Portal }
                                        )
                                    }
                                }
                                NavScreen.AdminPanel -> {
                                    AdminPanelScreen(
                                        repo = repo,
                                        locale = locale
                                    )
                                }
                                NavScreen.Portal -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ToolWebViewPane(
        entryPath: String,
        bridge: ChromeCompatBridge,
        assetLoader: WebViewAssetLoader,
        locale: AppLocale,
        onOpenFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Unit,
        onAudioPermission: (PermissionRequest) -> Unit,
        onBackToPortal: () -> Unit
    ) {
        val url = "$ASSET_BASE${entryPath.trimStart('/')}"
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            update = { webView ->
                if (webView.url != url) {
                    webView.loadUrl(url)
                }
            },
            factory = { ctx ->
                createToolWebView(
                    ctx = ctx,
                    initialUrl = url,
                    bridge = bridge,
                    assetLoader = assetLoader,
                    onOpenFileChooser = onOpenFileChooser,
                    onAudioPermission = onAudioPermission
                ).also { toolWebView = it }
            }
        )
    }

    @Composable
    private fun MissingToolAssetNotice(
        tool: ToolDestination,
        locale: AppLocale,
        onReturnToPortal: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (locale == AppLocale.BN) tool.titleBn else tool.titleEn,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (locale == AppLocale.BN) tool.descBn else tool.descEn,
                fontSize = 14.sp,
                color = Color(0xFF475569)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (locale == AppLocale.BN) {
                            "এই টুলটির এক্সটেনশন ফোল্ডার এখনো যোগ করা হয়নি:"
                        } else {
                            "This tool's extension subfolder has not been added to assets yet:"
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "app/src/main/assets/extension/${tool.entryPath}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF123B70)
                    )
                    if (tool.contentScript != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "app/src/main/assets/extension/${tool.contentScript}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF123B70)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(onClick = onReturnToPortal) {
                Text(if (locale == AppLocale.BN) "পোর্টালে ফিরে যান" else "Back to Portal")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createPortalWebView(
        ctx: android.content.Context,
        bridge: ChromeCompatBridge,
        onProgress: (Int) -> Unit,
        onTitle: (String) -> Unit,
        onOpenFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Unit,
        onAudioPermission: (PermissionRequest) -> Unit
    ): WebView {
        return WebView(ctx).apply {
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
            }

            addJavascriptInterface(bridge, "BspAndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()
                    val compatJs = bridge.readExtensionAssetText("chrome-compat.js")
                    if (!compatJs.isNullOrBlank()) {
                        view?.evaluateJavascript(compatJs, null)
                    }
                    // Inject manifest.json content_scripts if present in assets/extension/
                    listOf("lpcd/content.js", "bco/ab_form_filler_v2/content.js").forEach { path ->
                        val script = bridge.readExtensionAssetText(path)
                        if (!script.isNullOrBlank()) {
                            view?.evaluateJavascript(script, null)
                        }
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    onProgress(newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) {
                        onTitle(title)
                    }
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        onAudioPermission(request)
                    } else {
                        request.deny()
                    }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (filePathCallback == null) return false
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(
                            Intent.EXTRA_MIME_TYPES,
                            arrayOf(
                                "text/csv",
                                "text/plain",
                                "text/comma-separated-values",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            )
                        )
                    }
                    onOpenFileChooser(filePathCallback, Intent.createChooser(intent, "Select Excel / CSV file"))
                    return true
                }
            }

            loadUrl(PORTAL_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createToolWebView(
        ctx: android.content.Context,
        initialUrl: String,
        bridge: ChromeCompatBridge,
        assetLoader: WebViewAssetLoader,
        onOpenFileChooser: (ValueCallback<Array<Uri>>, Intent) -> Unit,
        onAudioPermission: (PermissionRequest) -> Unit
    ): WebView {
        return WebView(ctx).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
            }

            addJavascriptInterface(bridge, "BspAndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val uri = request?.url ?: return null
                    val response = assetLoader.shouldInterceptRequest(uri) ?: return null
                    val path = uri.path ?: ""
                    if (path.endsWith(".html", ignoreCase = true) && !path.endsWith("/hub.html", ignoreCase = true)) {
                        return try {
                            val html = response.data.bufferedReader().use { it.readText() }
                            val compatTag = "<script src=\"/assets/extension/chrome-compat.js\"></script>"
                            val injectedHtml = if (!html.contains("chrome-compat.js")) {
                                if (html.contains("<head>", ignoreCase = true)) {
                                    html.replaceFirst("<head>", "<head>$compatTag", ignoreCase = true)
                                } else {
                                    "$compatTag$html"
                                }
                            } else {
                                html
                            }
                            WebResourceResponse(
                                "text/html",
                                "UTF-8",
                                injectedHtml.byteInputStream()
                            )
                        } catch (_: Exception) {
                            response
                        }
                    }
                    return response
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val compatJs = bridge.readExtensionAssetText("chrome-compat.js")
                    if (!compatJs.isNullOrBlank()) {
                        view?.evaluateJavascript(compatJs, null)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        onAudioPermission(request)
                    } else {
                        request.deny()
                    }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (filePathCallback == null) return false
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(
                            Intent.EXTRA_MIME_TYPES,
                            arrayOf(
                                "text/csv",
                                "text/plain",
                                "text/comma-separated-values",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            )
                        )
                    }
                    onOpenFileChooser(filePathCallback, Intent.createChooser(intent, "Select Excel / CSV file"))
                    return true
                }
            }

            loadUrl(initialUrl)
        }
    }
}
