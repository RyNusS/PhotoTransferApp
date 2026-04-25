package com.family.phototransfer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.family.phototransfer.network.ReceiverStateHolder
import com.family.phototransfer.ui.dashboard.SenderDashboardScreen
import com.family.phototransfer.ui.history.HistoryScreen
import com.family.phototransfer.ui.receiver.ReceiverScreen
import com.family.phototransfer.ui.settings.SettingsScreen
import com.family.phototransfer.ui.theme.PhotoTransferTheme
import com.family.phototransfer.ui.upload.UploadScreen
import com.family.phototransfer.ui.upload.UploadViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

// ── 네비게이션 라우트 ──────────────────────────────────────────
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Home",     Icons.Default.Home)
    object Upload    : Screen("upload",    "Upload",   Icons.Default.Upload)
    object Receiver  : Screen("receiver",  "Receiver", Icons.Default.PhoneAndroid)
    object History   : Screen("history",   "History",  Icons.Default.History)
    object Settings  : Screen("settings",  "Settings", Icons.Default.Settings)
}

// ── 컬러 ──────────────────────────────────────────────────────
private val NavBarBg      = Color(0xFF1C2333)
private val NavSelected   = Color(0xFF2D6BE4)
private val NavUnselected = Color(0xFF8B9BB4)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var receiverStateHolder: ReceiverStateHolder

    // 딥링크로 전달된 URL (QR 스캔 → Upload 탭 자동 입력용)
    private val _deepLinkUrl = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    val deepLinkUrl = _deepLinkUrl.asSharedFlow()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val uri: Uri = intent.data ?: return
        if (uri.scheme == "phototransfer" && uri.host == "send") {
            val url = uri.getQueryParameter("url") ?: return
            _deepLinkUrl.tryEmit(url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        setContent {
            PhotoTransferTheme {
                PhotoTransferNavHost(
                    receiverStateHolder = receiverStateHolder,
                    deepLinkUrl = deepLinkUrl
                )
            }
        }
    }
}

@Composable
fun PhotoTransferNavHost(
    receiverStateHolder: ReceiverStateHolder,
    deepLinkUrl: kotlinx.coroutines.flow.SharedFlow<String> = kotlinx.coroutines.flow.MutableSharedFlow()
) {
    val navController        = rememberNavController()
    val navBackStackEntry    by navController.currentBackStackEntryAsState()
    val currentRoute          = navBackStackEntry?.destination?.route

    // ── 딥링크 URL 수신 → Upload 탭 이동 + ViewModel에 전달 ──
    var pendingDeepLinkUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        deepLinkUrl.collect { url ->
            pendingDeepLinkUrl = url
            selectedMode = "upload"
            navController.navigate(Screen.Upload.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState    = true
            }
        }
    }

    // ── 선택된 모드 상태 (upload = 기본값) ────────────────────
    var selectedMode by remember { mutableStateOf("upload") }

    // ── 전송 중 상태 (UploadViewModel에서 읽기 위한 참조) ─────
    // NavHost 내부에서만 hiltViewModel() 사용 가능하므로
    // isUploading 플래그를 여기서 mutableState로 관리
    var isUploading  by remember { mutableStateOf(false) }

    // ── ReceiverStateHolder에서 수신 중 상태 구독 ─────────────
    val isReceiving by receiverStateHolder.isReceiving.collectAsState(initial = false)

    // ── 차단 다이얼로그 ───────────────────────────────────────
    var blockDialogMessage by remember { mutableStateOf<String?>(null) }
    blockDialogMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { blockDialogMessage = null },
            containerColor   = Color(0xFF1C2333),
            title  = { Text("이동 불가", color = Color.White) },
            text   = { Text(msg, color = Color(0xFF8B9BB4)) },
            confirmButton = {
                TextButton(onClick = { blockDialogMessage = null }) {
                    Text("확인", color = Color(0xFF2D6BE4))
                }
            }
        )
    }

    // ── 하단 탭 (모드에 따라 4개) ─────────────────────────────
    val bottomNavItems = remember(selectedMode) {
        listOf(
            Screen.Dashboard,
            if (selectedMode == "upload") Screen.Upload else Screen.Receiver,
            Screen.History,
            Screen.Settings
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = NavBarBg) {
                bottomNavItems.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        icon  = { Icon(screen.icon, screen.label, tint = if (isSelected) NavSelected else NavUnselected) },
                        label = { Text(screen.label, color = if (isSelected) NavSelected else NavUnselected) },
                        selected = isSelected,
                        onClick  = {
                            // ── Home 탭 클릭 차단 ──────────────────────
                            if (screen.route == Screen.Dashboard.route) {
                                when {
                                    isReceiving -> {
                                        blockDialogMessage = "수신을 중지해주세요."
                                        return@NavigationBarItem
                                    }
                                    isUploading -> {
                                        blockDialogMessage = "전송을 중지해주세요."
                                        return@NavigationBarItem
                                    }
                                }
                            }
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    // Home 탭은 saveState = false → 항상 재진입 가능
                                    saveState = screen.route != Screen.Dashboard.route
                                }
                                launchSingleTop = true
                                restoreState    = screen.route != Screen.Dashboard.route
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = NavSelected.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Dashboard.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            // ── Home 탭 ───────────────────────────────────────
            composable(Screen.Dashboard.route) {
                SenderDashboardScreen(
                    selectedMode     = selectedMode,
                    onModeSelected   = { newMode -> selectedMode = newMode },
                    onNavigateToMode = { mode ->
                        selectedMode = mode
                        val target = if (mode == "upload") Screen.Upload else Screen.Receiver
                        // Receiver 탭으로 이동할 때 자동 수신 시작 플래그 설정
                        if (mode == "receiver") {
                            navController.currentBackStackEntry
                                ?.savedStateHandle?.set("auto_start_receiver", true)
                        }
                        navController.navigate(target.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }

            // ── Upload 탭 ─────────────────────────────────────
            composable(Screen.Upload.route) {
                val uploadVm: UploadViewModel = hiltViewModel()
                val uploadState by uploadVm.uiState.collectAsState()
                LaunchedEffect(uploadState.isUploading) {
                    isUploading = uploadState.isUploading
                }
                // 딥링크로 받은 URL이 있으면 ViewModel에 전달
                LaunchedEffect(pendingDeepLinkUrl) {
                    pendingDeepLinkUrl?.let { url ->
                        uploadVm.setRemoteUrl(url)
                        pendingDeepLinkUrl = null
                    }
                }
                UploadScreen(viewModel = uploadVm)
            }

            // ── Receiver 탭 ───────────────────────────────────
            composable(Screen.Receiver.route) {
                // Home 탭 → Switch to Receiver 버튼으로 넘어온 경우 자동 수신 시작
                val autoStart = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<Boolean>("auto_start_receiver") ?: false
                // 한 번 읽은 후 초기화
                LaunchedEffect(Unit) {
                    navController.currentBackStackEntry
                        ?.savedStateHandle?.remove<Boolean>("auto_start_receiver")
                }
                ReceiverScreen(autoStart = autoStart)
            }

            composable(Screen.History.route)  { HistoryScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

