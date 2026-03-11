package com.family.phototransfer.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── 컬러 상수 (Figma 디자인 기반) ──────────────────────────────
private val BgDark        = Color(0xFF0D1117)
private val CardBg        = Color(0xFF1C2333)
private val CardBorder    = Color(0xFF2D3748)
private val PrimaryBlue   = Color(0xFF2D6BE4)
private val SuccessGreen  = Color(0xFF00C853)
private val WarningOrange = Color(0xFFFF6B35)
private val TextPrimary   = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF8B9BB4)

// ── 메인 화면 ─────────────────────────────────────────────────
@Composable
fun SenderDashboardScreen() {
    var syncEnabled by remember { mutableStateOf(true) }
    val transferProgress = 0.78f

    val recentActivities = listOf(
        RecentActivity("IMG_20240315_1234.jpg", ActivityStatus.SUCCESS,   "3.2 MB", "2분 전"),
        RecentActivity("IMG_20240315_1235.jpg", ActivityStatus.SUCCESS,   "4.1 MB", "3분 전"),
        RecentActivity("VID_20240314_0921.mp4", ActivityStatus.DUPLICATE, "128 MB", "5분 전"),
        RecentActivity("IMG_20240314_0055.jpg", ActivityStatus.FAILED,    "2.8 MB", "1시간 전"),
        RecentActivity("IMG_20240313_1847.jpg", ActivityStatus.SUCCESS,   "5.6 MB", "2시간 전"),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1) 상단 헤더
        item { HeaderSection(syncEnabled = syncEnabled, onSyncToggle = { syncEnabled = it }) }

        // 2) 연결 상태 카드
        item { ConnectionStatusCard(isConnected = syncEnabled) }

        // 3) 전송 진행률
        item { TransferProgressCard(progress = transferProgress) }

        // 4) 통계 카드 3개
        item { StatsRow() }

        // 5) 동기화 설정
        item { SyncConfigCard() }

        // 6) 최근 활동 헤더
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("최근 활동", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = {}) {
                    Text("전체 보기", color = PrimaryBlue, fontSize = 12.sp)
                }
            }
        }

        // 7) 최근 활동 목록
        items(recentActivities) { activity ->
            ActivityItem(activity = activity)
        }
    }
}

// ── 헤더 섹션 ─────────────────────────────────────────────────
@Composable
fun HeaderSection(syncEnabled: Boolean, onSyncToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Sender Dashboard",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (syncEnabled) "동기화 활성화됨" else "동기화 비활성화",
                color = if (syncEnabled) SuccessGreen else TextSecondary,
                fontSize = 12.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (syncEnabled) "ON" else "OFF",
                color = if (syncEnabled) SuccessGreen else TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
            Switch(
                checked = syncEnabled,
                onCheckedChange = onSyncToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PrimaryBlue,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = CardBorder
                )
            )
        }
    }
}

// ── 연결 상태 카드 ─────────────────────────────────────────────
@Composable
fun ConnectionStatusCard(isConnected: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 연결 상태 아이콘
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) SuccessGreen.copy(alpha = 0.2f) else WarningOrange.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = if (isConnected) SuccessGreen else WarningOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isConnected) "Pixel 1에 연결됨" else "기기 연결 안됨",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isConnected) "192.168.1.100 · Wi-Fi" else "기기를 찾는 중...",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            // 연결 상태 뱃지
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isConnected) SuccessGreen.copy(alpha = 0.15f) else WarningOrange.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isConnected) "연결됨" else "연결 안됨",
                    color = if (isConnected) SuccessGreen else WarningOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── 전송 진행률 카드 ───────────────────────────────────────────
@Composable
fun TransferProgressCard(progress: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("현재 전송", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = PrimaryBlue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = PrimaryBlue,
                trackColor = CardBorder,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("IMG_20240315_1236.jpg", color = TextSecondary, fontSize = 11.sp)
                Text("9.8 MB / 12.4 MB", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

// ── 통계 카드 3개 ─────────────────────────────────────────────
@Composable
fun StatsRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.PhotoLibrary,
            iconTint = PrimaryBlue,
            value = "1,402",
            label = "전송된 사진"
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Storage,
            iconTint = SuccessGreen,
            value = "12.4 GB",
            label = "전송 용량"
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Block,
            iconTint = WarningOrange,
            value = "98",
            label = "중복 제거"
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(text = label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

// ── 동기화 설정 카드 ───────────────────────────────────────────
@Composable
fun SyncConfigCard() {
    var verifyMode by remember { mutableStateOf(true) }
    var autoTransfer by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("동기화 설정", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = {}) {
                    Text("더보기", color = PrimaryBlue, fontSize = 12.sp)
                }
            }

            Divider(color = CardBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // 전송 시작 날짜
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("전송 시작 날짜", color = TextPrimary, fontSize = 13.sp)
                        Text("07/01/2023", color = TextSecondary, fontSize = 11.sp)
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 중복 확인 토글
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FindReplace, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("중복 확인", color = TextPrimary, fontSize = 13.sp)
                }
                Switch(
                    checked = verifyMode,
                    onCheckedChange = { verifyMode = it },
                    modifier = Modifier.size(40.dp, 24.dp),
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = PrimaryBlue,
                        uncheckedTrackColor = CardBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 자동 전송 토글
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoMode, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("자동 전송", color = TextPrimary, fontSize = 13.sp)
                }
                Switch(
                    checked = autoTransfer,
                    onCheckedChange = { autoTransfer = it },
                    modifier = Modifier.size(40.dp, 24.dp),
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = PrimaryBlue,
                        uncheckedTrackColor = CardBorder
                    )
                )
            }
        }
    }
}

// ── 최근 활동 아이템 ───────────────────────────────────────────
@Composable
fun ActivityItem(activity: RecentActivity) {
    val (iconRes, iconTint, statusText) = when (activity.status) {
        ActivityStatus.SUCCESS   -> Triple(Icons.Default.CheckCircle, SuccessGreen, "전송 완료")
        ActivityStatus.FAILED    -> Triple(Icons.Default.Error, Color(0xFFFF4444), "전송 실패")
        ActivityStatus.DUPLICATE -> Triple(Icons.Default.ContentCopy, WarningOrange, "중복 건너뜀")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .border(0.5.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 상태 아이콘
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = iconRes, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 파일 정보
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.fileName,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = "$statusText · ${activity.fileSize}",
                color = iconTint,
                fontSize = 11.sp
            )
        }

        // 시간
        Text(text = activity.timeAgo, color = TextSecondary, fontSize = 11.sp)
    }
}
