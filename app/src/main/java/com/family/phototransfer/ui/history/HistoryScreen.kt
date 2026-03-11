package com.family.phototransfer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.hilt.navigation.compose.hiltViewModel

private val BgDark        = Color(0xFF0D1117)
private val CardBg        = Color(0xFF1C2333)
private val CardBorder    = Color(0xFF2D3748)
private val PrimaryBlue   = Color(0xFF2D6BE4)
private val SuccessGreen  = Color(0xFF00C853)
private val WarningOrange = Color(0xFFFF6B35)
private val ErrorRed      = Color(0xFFFF4444)
private val PurpleAccent  = Color(0xFF9C6FE4)
private val TextPrimary   = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF8B9BB4)

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor   = CardBg,
            title = { Text("기록 전체 삭제", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text  = { Text("모든 전송/수신 기록이 삭제됩니다.\n(DB 데이터 포함)", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearDialog = false }) {
                    Text("삭제", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("취소", color = TextSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize().background(BgDark),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 헤더
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("전송 기록", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("최근 7일 기록", color = TextSecondary, fontSize = 12.sp)
                }
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(Icons.Default.DeleteSweep, null, tint = TextSecondary)
                }
            }
        }

        // 통계 카드
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    modifier    = Modifier.weight(1f),
                    label       = "전체",
                    count       = uiState.totalCount,
                    color       = PrimaryBlue,
                    icon        = Icons.Default.List
                )
                StatChip(
                    modifier    = Modifier.weight(1f),
                    label       = "송신",
                    count       = uiState.sendCount,
                    color       = SuccessGreen,
                    icon        = Icons.Default.Upload
                )
                StatChip(
                    modifier    = Modifier.weight(1f),
                    label       = "수신",
                    count       = uiState.receiveCount,
                    color       = PurpleAccent,
                    icon        = Icons.Default.Download
                )
                StatChip(
                    modifier    = Modifier.weight(1f),
                    label       = "실패",
                    count       = uiState.failedCount,
                    color       = ErrorRed,
                    icon        = Icons.Default.ErrorOutline
                )
            }
        }

        // 필터 탭
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardBg)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    HistoryFilter.ALL     to "전체",
                    HistoryFilter.SEND    to "송신",
                    HistoryFilter.RECEIVE to "수신",
                    HistoryFilter.FAILED  to "실패"
                ).forEach { (filter, label) ->
                    val selected = uiState.selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) PrimaryBlue else Color.Transparent)
                            .then(
                                Modifier.padding(vertical = 8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = label,
                            color      = if (selected) Color.White else TextSecondary,
                            fontSize   = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier   = Modifier.noRippleClickable { viewModel.setFilter(filter) }
                        )
                    }
                }
            }
        }

        // 날짜별 그룹 기록
        if (uiState.filteredRecords.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            null,
                            tint = TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("최근 7일 기록이 없습니다", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }
        } else {
            uiState.groupedRecords.forEach { (dateGroup, records) ->
                // 날짜 헤더
                item(key = "header_$dateGroup") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            text       = dateGroup,
                            color      = TextSecondary,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        HorizontalDivider(color = CardBorder, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text("${records.size}건", color = TextSecondary, fontSize = 11.sp)
                    }
                }

                // 해당 날짜 기록
                items(records, key = { it.id }) { record ->
                    HistoryRecordItem(record = record)
                }
            }
        }

        // 하단 여백
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ── 통계 칩 ───────────────────────────────────────────────────
@Composable
fun StatChip(
    modifier: Modifier = Modifier,
    label:    String,
    count:    Int,
    color:    Color,
    icon:     ImageVector
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier            = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(4.dp))
            Text("$count", color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

// ── 기록 아이템 ───────────────────────────────────────────────
@Composable
fun HistoryRecordItem(record: HistoryRecordUi) {
    // 방향 및 상태에 따른 색상/아이콘
    val (statusColor, statusIcon) = when {
        record.direction == "RECEIVE" && record.status == "RECEIVED" ->
            PurpleAccent to Icons.Default.Download
        record.status == "SUCCESS"           -> SuccessGreen  to Icons.Default.CheckCircle
        record.status == "SKIPPED_DUPLICATE" -> WarningOrange to Icons.Default.ContentCopy
        record.status == "FAILED"            -> ErrorRed      to Icons.Default.Error
        else                                 -> TextSecondary  to Icons.Default.HelpOutline
    }

    val directionIcon = if (record.direction == "RECEIVE") Icons.Default.Download
                        else Icons.Default.Upload

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .border(0.5.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 방향 아이콘
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(directionIcon, null, tint = statusColor, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.width(12.dp))

        // 파일명 + 기기명
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = record.fileName,
                color      = TextPrimary,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text     = "${record.sourceDevice} · ${record.sizeText}",
                color    = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        // 상태 + 시간
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    text       = record.statusLabel,
                    color      = statusColor,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(record.timeText, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

// clickable without ripple helper
@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.then(
        Modifier.clickable(
            indication        = null,
            interactionSource = interactionSource,
            onClick           = onClick
        )
    )
}
