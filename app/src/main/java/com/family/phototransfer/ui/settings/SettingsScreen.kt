package com.family.phototransfer.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.Calendar

private val BgDark        = Color(0xFF0D1117)
private val CardBg        = Color(0xFF1C2333)
private val CardBorder    = Color(0xFF2D3748)
private val PrimaryBlue   = Color(0xFF2D6BE4)
private val SuccessGreen  = Color(0xFF00C853)
private val WarningOrange = Color(0xFFFF6B35)
private val ErrorRed      = Color(0xFFFF4444)
private val TextPrimary   = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF8B9BB4)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    // 날짜 선택 다이얼로그
    if (uiState.showDatePicker) {
        SyncDatePickerDialog(
            current   = uiState.syncFromDate,
            onSelect  = { viewModel.setSyncFromDate(it) },
            onAll     = { viewModel.setDateAll() },
            onDismiss = { viewModel.hideDatePicker() }
        )
    }

    // 폴더 추가 바텀시트
    if (uiState.showFolderPicker) {
        FolderPickerDialog(
            availableFolders = uiState.availableFolders,
            currentFolders   = uiState.syncFolders,
            onAddFolders     = { viewModel.addFolders(it) },
            onDismiss        = { viewModel.hideFolderPicker() }
        )
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize().background(BgDark),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("설정", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        // ── 1. 자동 동기화 ────────────────────────────────────
        item {
            SettingsSection(title = "자동 동기화") {

                // ON/OFF 토글
                SettingsToggleRow(
                    icon    = Icons.Default.Autorenew,
                    title   = "자동 동기화",
                    subtitle = if (uiState.autoSyncEnabled) "활성화됨" else "비활성화",
                    checked = uiState.autoSyncEnabled,
                    onCheckedChange = { viewModel.setAutoSync(it) }
                )
                SectionDivider()

                // ✅ 6번 - 동기화 시작 시각 (시간 0~23, 분 0/30)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("동기화 시작 시각", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(uiState.syncStartTimeText, color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    // 시간 슬라이더 (0~23)
                    Text("시각: ${uiState.syncStartHour}시", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 32.dp))
                    Slider(
                        value         = uiState.syncStartHour.toFloat(),
                        onValueChange = { viewModel.setSyncStartHour(it.toInt()) },
                        valueRange    = 0f..23f,
                        steps         = 22,
                        modifier      = Modifier.padding(start = 32.dp),
                        colors        = SliderDefaults.colors(thumbColor = PrimaryBlue, activeTrackColor = PrimaryBlue, inactiveTrackColor = CardBorder)
                    )
                    // 분 선택 (0분 / 30분)
                    Row(
                        modifier = Modifier.padding(start = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0, 30).forEach { min ->
                            val isSelected = uiState.syncStartMinute == min
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PrimaryBlue else CardBorder)
                                    .clickable { viewModel.setSyncStartMinute(min) }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (min == 0) "00분" else "30분",
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                SectionDivider()

                // ✅ 7번 - 동기화 주기 (6개 옵션 버튼)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Repeat, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("동기화 주기", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(uiState.syncIntervalText, color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(10.dp))
                    // 6개 옵션을 2행 3열로 표시
                    val options = SyncIntervalOption.values().toList()
                    Column(
                        modifier = Modifier.padding(start = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        options.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { option ->
                                    val isSelected = uiState.syncInterval == option
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) PrimaryBlue else CardBorder)
                                            .border(
                                                width = if (isSelected) 0.dp else 0.5.dp,
                                                color = CardBorder,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.setSyncInterval(option) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = option.label,
                                            color = if (isSelected) Color.White else TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 설명 텍스트
                if (uiState.autoSyncEnabled) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "⏰ ${uiState.syncStartTimeText}부터 ${uiState.syncIntervalText}마다 동기화",
                        color = PrimaryBlue.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
                    )
                }
            }
        }

        // ── 2. 동기화 범위 ────────────────────────────────────
        item {
            SettingsSection(title = "동기화 범위") {

                // ✅ 5번 - 동기화 범위 날짜 선택 (빠른 옵션 + 직접 선택)
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("동기화 시작 날짜", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(uiState.syncFromDateText, color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(10.dp))
                    // 빠른 선택 버튼 (전체 / 최근3일 / 최근7일 / 직접선택)
                    Row(
                        modifier = Modifier.padding(start = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        data class DateOption(val key: String, val label: String)
                        listOf(
                            DateOption("ALL",      "전체"),
                            DateOption("RECENT_3", "최근 3일"),
                            DateOption("RECENT_7", "최근 7일"),
                            DateOption("CUSTOM",   "직접 선택")
                        ).forEach { opt ->
                            val isSelected = when (opt.key) {
                                "CUSTOM" -> uiState.syncFromDate != "ALL" && uiState.syncFromDate != "RECENT_3" && uiState.syncFromDate != "RECENT_7"
                                else     -> uiState.syncFromDate == opt.key
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PrimaryBlue else CardBorder)
                                    .clickable {
                                        if (opt.key == "CUSTOM") viewModel.showDatePicker()
                                        else viewModel.setSyncFromDate(opt.key)
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt.label,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                    // 직접 선택된 날짜 표시
                    val isCustomDate = uiState.syncFromDate != "ALL" && uiState.syncFromDate != "RECENT_3" && uiState.syncFromDate != "RECENT_7"
                    if (isCustomDate) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "📅 ${uiState.syncFromDate} 이후 파일만 동기화",
                            color = PrimaryBlue.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 32.dp)
                        )
                    }
                }
                SectionDivider()

                // WiFi 전용 안내
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Wifi, null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("WiFi 전용 동작", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "자동 동기화는 항상 WiFi 연결 상태에서만 실행됩니다",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Icon(Icons.Default.Lock, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                }
                SectionDivider()

                // 중복 파일 건너뛰기
                SettingsToggleRow(
                    icon     = Icons.Default.ContentCopy,
                    title    = "중복 파일 건너뛰기",
                    subtitle = "이미 전송된 파일은 제외",
                    checked  = uiState.skipDuplicates,
                    onCheckedChange = { viewModel.setSkipDuplicates(it) }
                )
            }
        }

        // ── 3. 동기화 폴더 (6번 요구사항) ─────────────────────
        item {
            SettingsSection(title = "동기화 폴더") {
                // 폴더 목록
                if (uiState.syncFolders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "추가된 폴더가 없습니다\n아래 버튼으로 폴더를 추가하세요",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    uiState.syncFolders.forEachIndexed { index, folder ->
                        SyncFolderRow(
                            folder    = folder,
                            onToggle  = { viewModel.toggleFolderEnabled(folder.path) },
                            onRemove  = { viewModel.removeFolder(folder.path) }
                        )
                        if (index < uiState.syncFolders.lastIndex) SectionDivider()
                    }
                }

                SectionDivider()

                // 폴더 추가 버튼
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.showFolderPicker() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("폴더 추가", color = PrimaryBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // ── 4. 알림 설정 (2번 요구사항) ──────────────────────
        item {
            SettingsSection(title = "알림 설정") {
                SettingsToggleRow(
                    icon     = Icons.Default.Upload,
                    title    = "송신 완료 알림",
                    subtitle = "파일 전송 완료 시 알림",
                    checked  = uiState.notifyOnSend,
                    onCheckedChange = { viewModel.setNotifyOnSend(it) }
                )
                SectionDivider()
                SettingsToggleRow(
                    icon     = Icons.Default.Download,
                    title    = "수신 완료 알림",
                    subtitle = "파일 수신 완료 시 알림",
                    checked  = uiState.notifyOnReceive,
                    onCheckedChange = { viewModel.setNotifyOnReceive(it) }
                )
            }
        }

        // ── 5. 수신 기기 IP ───────────────────────────────────
        item {
            SettingsSection(title = "수신 기기") {
                var ipText by remember { mutableStateOf(uiState.pixelIpAddress) }
                LaunchedEffect(uiState.pixelIpAddress) { ipText = uiState.pixelIpAddress }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhoneAndroid, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value         = ipText,
                        onValueChange = { ipText = it; viewModel.setPixelIp(it) },
                        label         = { Text("Pixel IP 주소", fontSize = 12.sp) },
                        placeholder   = { Text("192.168.x.x", color = TextSecondary, fontSize = 12.sp) },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedTextColor      = TextPrimary,
                            unfocusedTextColor    = TextPrimary,
                            focusedBorderColor    = PrimaryBlue,
                            unfocusedBorderColor  = CardBorder,
                            focusedLabelColor     = PrimaryBlue,
                            unfocusedLabelColor   = TextSecondary,
                            cursorColor           = PrimaryBlue
                        )
                    )
                }
            }
        }

        // ── 6. 즉시 동기화 ────────────────────────────────────
        item {
            Button(
                onClick  = { viewModel.runSyncNow() },
                enabled  = !uiState.isSyncRunning && uiState.pixelIpAddress.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = PrimaryBlue,
                    disabledContainerColor = CardBg
                )
            ) {
                if (uiState.isSyncRunning) {
                    CircularProgressIndicator(
                        modifier  = Modifier.size(18.dp),
                        color     = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("동기화 중...", color = Color.White, fontSize = 15.sp)
                } else {
                    Icon(Icons.Default.Sync, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("지금 동기화", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ── 섹션 컨테이너 ──────────────────────────────────────────────
@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text     = title,
            color    = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(14.dp),
            colors   = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SectionDivider() {
    HorizontalDivider(
        color     = CardBorder,
        thickness = 0.5.dp,
        modifier  = Modifier.padding(horizontal = 16.dp)
    )
}

// ── 토글 행 ──────────────────────────────────────────────────
@Composable
fun SettingsToggleRow(
    icon:            ImageVector,
    title:           String,
    subtitle:        String,
    checked:         Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,    color = TextPrimary,   fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = PrimaryBlue,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = CardBorder
            )
        )
    }
}

// ── 슬라이더 행 ───────────────────────────────────────────────
@Composable
fun SettingsSliderRow(
    icon:         ImageVector,
    title:        String,
    valueText:    String,
    value:        Float,
    valueRange:   ClosedFloatingPointRange<Float>,
    steps:        Int,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                text       = valueText,
                color      = PrimaryBlue,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value         = value,
            onValueChange = onValueChange,
            valueRange    = valueRange,
            steps         = steps,
            modifier      = Modifier.padding(start = 32.dp),
            colors        = SliderDefaults.colors(
                thumbColor       = PrimaryBlue,
                activeTrackColor = PrimaryBlue,
                inactiveTrackColor = CardBorder
            )
        )
    }
}

// ── 클릭 행 ───────────────────────────────────────────────────
@Composable
fun SettingsClickableRow(
    icon:     ImageVector,
    title:    String,
    subtitle: String,
    onClick:  () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,    color = TextPrimary,   fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
}

// ── 동기화 폴더 행 ────────────────────────────────────────────
@Composable
fun SyncFolderRow(
    folder:   SyncFolder,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 폴더 아이콘 (체크 여부 반영)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (folder.enabled) PrimaryBlue.copy(alpha = 0.15f)
                    else CardBorder
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (folder.enabled) {
                Icon(Icons.Default.CheckCircle, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
            } else {
                Icon(Icons.Default.CheckBoxOutlineBlank, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.width(12.dp))

        // 폴더명 + 경로
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = folder.name,
                color      = if (folder.enabled) TextPrimary else TextSecondary,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            if (folder.itemCount > 0) {
                Text(
                    "${folder.itemCount}개 항목",
                    color    = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        // 제거 버튼
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Remove, null, tint = ErrorRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
    }
}

// ── 날짜 선택 다이얼로그 ──────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDatePickerDialog(
    current:  String,
    onSelect: (String) -> Unit,
    onAll:    () -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (current == "ALL") System.currentTimeMillis()
        else runCatching {
            val parts = current.split("-")
            val cal   = Calendar.getInstance()
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
            cal.timeInMillis
        }.getOrDefault(System.currentTimeMillis())
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onAll) {
                    Text("전체", color = WarningOrange)
                }
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val cal = Calendar.getInstance().apply { timeInMillis = millis }
                            val dateStr = "%04d-%02d-%02d".format(
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH) + 1,
                                cal.get(Calendar.DAY_OF_MONTH)
                            )
                            onSelect(dateStr)
                            onDismiss()
                        }
                    }
                ) {
                    Text("확인", color = PrimaryBlue)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = TextSecondary) }
        },
        colors = DatePickerDefaults.colors(containerColor = CardBg)
    ) {
        DatePicker(
            state  = datePickerState,
            colors = DatePickerDefaults.colors(
                containerColor               = CardBg,
                titleContentColor            = TextPrimary,
                headlineContentColor         = TextPrimary,
                weekdayContentColor          = TextSecondary,
                subheadContentColor          = TextSecondary,
                navigationContentColor       = TextPrimary,
                yearContentColor             = TextPrimary,
                currentYearContentColor      = PrimaryBlue,
                selectedYearContentColor     = Color.White,
                selectedYearContainerColor   = PrimaryBlue,
                dayContentColor              = TextPrimary,
                todayContentColor            = PrimaryBlue,
                todayDateBorderColor         = PrimaryBlue,
                selectedDayContentColor      = Color.White,
                selectedDayContainerColor    = PrimaryBlue
            )
        )
    }
}

// ── 폴더 선택 다이얼로그 ──────────────────────────────────────
@Composable
fun FolderPickerDialog(
    availableFolders: List<SyncFolder>,
    currentFolders:  List<SyncFolder>,
    onAddFolders:    (List<SyncFolder>) -> Unit,
    onDismiss:       () -> Unit
) {
    // ✅ mutableStateOf(setOf())로 교체 - mutableStateSetOf 충돌 방지
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f),
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 헤더
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "동기화할 폴더 선택",
                            color      = TextPrimary,
                            fontSize   = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "자동으로 동기화할 폴더를 선택하세요",
                            color    = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    if (selectedPaths.isNotEmpty()) {
                        Text(
                            "${selectedPaths.size}개 선택",
                            color      = PrimaryBlue,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider(color = CardBorder)

                // 폴더 목록
                LazyColumn(
                    modifier       = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    if (availableFolders.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("폴더를 불러오는 중...", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    } else {
                        items(availableFolders, key = { it.path }) { folder ->
                            val alreadyAdded = currentFolders.any { it.path == folder.path }
                            val isSelected   = folder.path in selectedPaths

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !alreadyAdded) {
                                        selectedPaths = if (isSelected)
                                            selectedPaths - folder.path
                                        else
                                            selectedPaths + folder.path
                                    }
                                    .background(
                                        if (isSelected) PrimaryBlue.copy(alpha = 0.08f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 폴더 아이콘
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                alreadyAdded -> CardBorder
                                                isSelected   -> PrimaryBlue.copy(alpha = 0.2f)
                                                else         -> PrimaryBlue.copy(alpha = 0.1f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        null,
                                        tint = when {
                                            alreadyAdded -> TextSecondary
                                            isSelected   -> PrimaryBlue
                                            else         -> PrimaryBlue.copy(alpha = 0.7f)
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                // 폴더명 + 아이템 수
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text       = folder.name,
                                        color      = if (alreadyAdded) TextSecondary else TextPrimary,
                                        fontSize   = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines   = 1,
                                        overflow   = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text     = if (alreadyAdded) "이미 추가됨" else "${folder.itemCount}개 항목",
                                        color    = if (alreadyAdded) WarningOrange.copy(alpha = 0.7f) else TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                // 체크박스
                                if (alreadyAdded) {
                                    Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                                } else {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedPaths = if (checked)
                                                selectedPaths + folder.path
                                            else
                                                selectedPaths - folder.path
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor   = PrimaryBlue,
                                            uncheckedColor = TextSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = CardBorder)

                // 버튼 영역
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick   = onDismiss,
                        modifier  = Modifier.weight(1f),
                        shape     = RoundedCornerShape(10.dp),
                        border    = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Text("취소", color = TextSecondary)
                    }

                    Button(
                        onClick  = {
                            val toAdd = availableFolders.filter { it.path in selectedPaths }
                            if (toAdd.isNotEmpty()) onAddFolders(toAdd)
                            else onDismiss()
                        },
                        modifier  = Modifier.weight(1f),
                        shape     = RoundedCornerShape(10.dp),
                        colors    = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        enabled   = selectedPaths.isNotEmpty()
                    ) {
                        Text(
                            if (selectedPaths.isEmpty()) "추가"
                            else "${selectedPaths.size}개 추가",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
