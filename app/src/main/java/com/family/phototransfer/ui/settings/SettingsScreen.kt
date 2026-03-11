package com.family.phototransfer.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// ── 컬러 상수 ─────────────────────────────────────────────────
private val BgDark        = Color(0xFF0D1117)
private val CardBg        = Color(0xFF1C2333)
private val CardBorder    = Color(0xFF2D3748)
private val PrimaryBlue   = Color(0xFF2D6BE4)
private val SuccessGreen  = Color(0xFF00C853)
private val WarningOrange = Color(0xFFFF6B35)
private val TextPrimary   = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF8B9BB4)

// ── 메인 설정 화면 ─────────────────────────────────────────────
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 헤더
        item {
            Text(
                "설정",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 1) 자동 동기화 섹션
        item {
            SettingsSection(title = "자동 동기화") {
                // 자동 동기화 ON/OFF
                SettingsToggleRow(
                    icon        = Icons.Default.Autorenew,
                    title       = "자동 동기화",
                    subtitle    = if (uiState.autoSyncEnabled) "활성화됨" else "비활성화",
                    checked     = uiState.autoSyncEnabled,
                    onCheckedChange = {
                        viewModel.setAutoSync(it, context)
                    }
                )

                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                // 동기화 주기
                SettingsDropdownRow(
                    icon     = Icons.Default.Schedule,
                    title    = "동기화 주기",
                    subtitle = uiState.syncIntervalText,
                    enabled  = uiState.autoSyncEnabled,
                    options  = listOf("1시간마다", "6시간마다", "12시간마다", "매일"),
                    selected = uiState.syncIntervalIndex,
                    onSelect = { viewModel.setSyncInterval(it, context) }
                )

                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                // 동기화 범위 (며칠 전까지)
                SettingsDropdownRow(
                    icon     = Icons.Default.CalendarToday,
                    title    = "동기화 범위",
                    subtitle = uiState.syncRangeText,
                    enabled  = uiState.autoSyncEnabled,
                    options  = listOf("7일 이내", "30일 이내", "90일 이내", "전체"),
                    selected = uiState.syncRangeIndex,
                    onSelect = { viewModel.setSyncRange(it) }
                )

                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                // WiFi 전용 토글
                SettingsToggleRow(
                    icon        = Icons.Default.Wifi,
                    title       = "WiFi 전용",
                    subtitle    = "모바일 데이터 사용 안 함",
                    checked     = uiState.wifiOnly,
                    onCheckedChange = { viewModel.setWifiOnly(it, context) }
                )
            }
        }

        // 2) 수신 기기 설정
        item {
            SettingsSection(title = "수신 기기") {
                SettingsInputRow(
                    icon     = Icons.Default.PhoneAndroid,
                    title    = "Pixel 1 IP 주소",
                    subtitle = uiState.pixelIpAddress.ifEmpty { "자동 탐색 (미설정 시)" },
                    value    = uiState.pixelIpAddress,
                    hint     = "예: 192.168.1.100",
                    onSave   = { viewModel.setPixelIp(it) }
                )

                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                // 지금 즉시 동기화 버튼
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.runSyncNow(context) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("지금 동기화", color = PrimaryBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("즉시 1회 동기화 실행", color = TextSecondary, fontSize = 12.sp)
                    }
                    if (uiState.isSyncRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = PrimaryBlue,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 3) 전송 설정
        item {
            SettingsSection(title = "전송 설정") {
                SettingsToggleRow(
                    icon     = Icons.Default.FindReplace,
                    title    = "중복 파일 건너뜀",
                    subtitle = "이미 전송된 파일은 다시 보내지 않음",
                    checked  = uiState.skipDuplicates,
                    onCheckedChange = { viewModel.setSkipDuplicates(it) }
                )

                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)

                SettingsToggleRow(
                    icon     = Icons.Default.NotificationsActive,
                    title    = "전송 완료 알림",
                    subtitle = "동기화 완료 시 알림 표시",
                    checked  = uiState.showNotification,
                    onCheckedChange = { viewModel.setShowNotification(it) }
                )
            }
        }

        // 4) 앱 정보
        item {
            SettingsSection(title = "앱 정보") {
                SettingsInfoRow(
                    icon  = Icons.Default.Info,
                    title = "버전",
                    value = "1.0.0"
                )
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                SettingsInfoRow(
                    icon  = Icons.Default.Storage,
                    title = "전송 기록 DB",
                    value = "Room DB"
                )
            }
        }

        // 하단 여백
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── 설정 섹션 컨테이너 ─────────────────────────────────────────
@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
                .border(0.5.dp, CardBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp)
        ) {
            content()
        }
    }
}

// ── 토글 설정 행 ───────────────────────────────────────────────
@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, contentDescription = null,
            tint = if (enabled) PrimaryBlue else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) TextPrimary else TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor   = PrimaryBlue,
                uncheckedTrackColor = CardBorder
            )
        )
    }
}

// ── 드롭다운 설정 행 ───────────────────────────────────────────
@Composable
fun SettingsDropdownRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { expanded = true }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (enabled) PrimaryBlue else TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (enabled) TextPrimary else TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = if (enabled) PrimaryBlue else TextSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.background(CardBg)
    ) {
        options.forEachIndexed { index, option ->
            DropdownMenuItem(
                text = {
                    Text(
                        option,
                        color = if (index == selected) PrimaryBlue else TextPrimary,
                        fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                onClick = {
                    onSelect(index)
                    expanded = false
                },
                trailingIcon = {
                    if (index == selected) {
                        Icon(Icons.Default.Check, null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    }
                }
            )
        }
    }
}

// ── IP 입력 설정 행 ────────────────────────────────────────────
@Composable
fun SettingsInputRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    hint: String,
    onSave: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var inputText  by remember { mutableStateOf(value) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true; inputText = value }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = if (value.isNotEmpty()) PrimaryBlue else TextSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Default.Edit, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = CardBg,
            title = { Text(title, color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text(hint, color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor    = TextPrimary,
                        unfocusedTextColor  = TextPrimary,
                        focusedBorderColor  = PrimaryBlue,
                        unfocusedBorderColor = CardBorder
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { onSave(inputText); showDialog = false }) {
                    Text("저장", color = PrimaryBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("취소", color = TextSecondary)
                }
            }
        )
    }
}

// ── 정보 표시 행 ───────────────────────────────────────────────
@Composable
fun SettingsInfoRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextSecondary, fontSize = 13.sp)
    }
}
