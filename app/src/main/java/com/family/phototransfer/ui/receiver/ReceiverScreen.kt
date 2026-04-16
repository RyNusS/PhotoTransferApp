package com.family.phototransfer.ui.receiver

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val BgDark        = Color(0xFF0D1117)
private val CardBg        = Color(0xFF1C2333)
private val CardBorder    = Color(0xFF2D3748)
private val PrimaryBlue   = Color(0xFF2D6BE4)
private val SuccessGreen  = Color(0xFF00C853)
private val WarningOrange = Color(0xFFFF6B35)
private val TextPrimary   = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF8B9BB4)

@Composable
fun ReceiverScreen(
    viewModel: ReceiverViewModel = hiltViewModel(),
    autoStart: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        if (autoStart && !uiState.isListening) {
            viewModel.startListening()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ReceiverHeader() }

        item {
            ReceiverStatusCard(
                isRunning         = uiState.isListening,
                myIp              = uiState.myIpAddress,
                statusMessage     = uiState.statusMessage,
                autoStartOnBoot   = uiState.autoStartOnBoot,
                boreTunnelStatus  = uiState.boreTunnelStatus,
                boreTunnelPort    = uiState.boreTunnelPort,
                onStart           = { viewModel.startListening() },
                onStop            = { viewModel.stopListening() },
                onToggleAutoStart = { viewModel.toggleAutoStartOnBoot(it) }
            )
        }

        item {
            StorageCard(
                receivedCount    = uiState.receivedFiles.size,
                receivedSizeText = uiState.receivedFiles
                    .sumOf { parseSizeText(it.sizeText) }
                    .let { formatBytes(it) }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("수신된 파일", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("${uiState.receivedFiles.size}개", color = TextSecondary, fontSize = 13.sp)
            }
        }

        if (uiState.receivedFiles.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, null, tint = TextSecondary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("아직 수신된 파일이 없습니다", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(uiState.receivedFiles) { file ->
                ReceivedFileItem(file = file)
            }
        }
    }
}

@Composable
fun ReceiverHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Pixel Receiver", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("가족 사진을 받아 구글포토에 업로드", color = TextSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Default.PhoneAndroid, null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
    }
}


@Composable
fun ReceiverStatusCard(
    isRunning:         Boolean,
    myIp:              String,
    statusMessage:     String,
    autoStartOnBoot:   Boolean,
    boreTunnelStatus:  String  = "",
    boreTunnelPort:    Int?    = null,
    onStart:           () -> Unit,
    onStop:            () -> Unit,
    onToggleAutoStart: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRunning) SuccessGreen.copy(alpha = 0.15f) else CardBorder
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (isRunning) SuccessGreen else TextSecondary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (isRunning) "수신 대기 중" else "수신 서버 꺼짐",
                color = if (isRunning) SuccessGreen else TextSecondary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))
            Text(statusMessage, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))

            if (isRunning && myIp.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryBlue.copy(alpha = 0.1f))
                        .border(0.5.dp, PrimaryBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Router, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("내 IP: $myIp", color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Text("같은 WiFi에 연결된 폰에서 자동으로 탐색됩니다", color = TextSecondary, fontSize = 11.sp)
            }

            if (isRunning && boreTunnelStatus.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val isBoreConnected = boreTunnelPort != null
                val isErrorState = boreTunnelStatus.startsWith("오류")
                val boreColor = when {
                    isBoreConnected -> SuccessGreen
                    isErrorState    -> Color(0xFFFF4444)
                    else            -> WarningOrange
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(boreColor.copy(alpha = 0.08f))
                        .border(0.5.dp, boreColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isBoreConnected) Icons.Default.Cloud
                                      else if (isErrorState) Icons.Default.CloudOff
                                      else Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = boreColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isBoreConnected) "원격 터널 연결됨"
                                   else if (isErrorState) "원격 터널 오류"
                                   else "원격 터널 연결 중...",
                            color = boreColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = boreTunnelStatus,
                            color = boreColor.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick  = if (isRunning) onStop else onStart,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) WarningOrange else PrimaryBlue
                )
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "수신 중지" else "수신 시작",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (autoStartOnBoot) PrimaryBlue.copy(alpha = 0.08f) else Color(0xFF0D1117))
                    .border(
                        0.5.dp,
                        if (autoStartOnBoot) PrimaryBlue.copy(alpha = 0.3f) else CardBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = if (autoStartOnBoot) PrimaryBlue else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "앱 시작 시 수신 자동 시작",
                        color = if (autoStartOnBoot) TextPrimary else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "폰 재시작 후 자동으로 수신 대기",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = autoStartOnBoot,
                    onCheckedChange = onToggleAutoStart,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = Color.White,
                        checkedTrackColor   = PrimaryBlue,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = CardBorder
                    )
                )
            }
        }
    }
}

@Composable
fun StorageCard(receivedCount: Int, receivedSizeText: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$receivedCount", color = PrimaryBlue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("수신된 파일", color = TextSecondary, fontSize = 11.sp)
            }
            Box(modifier = Modifier.width(1.dp).height(40.dp).background(CardBorder))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(receivedSizeText, color = SuccessGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("수신된 용량", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ReceivedFileItem(file: ReceivedFileUi) {
    val isVideo = file.fileName.lowercase().let {
        it.endsWith(".mp4") || it.endsWith(".mov") || it.endsWith(".avi") || it.endsWith(".mkv")
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
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isVideo) Icons.Default.VideoFile else Icons.Default.Image,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = file.fileName,
                color      = TextPrimary,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1
            )
            Text("${file.sizeText} · ${file.timeText}", color = TextSecondary, fontSize = 11.sp)
        }

        Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
    }
}

private fun parseSizeText(sizeText: String): Long {
    return try {
        val parts = sizeText.trim().split(" ")
        val value = parts[0].toDouble()
        when (parts.getOrNull(1)?.uppercase()) {
            "GB" -> (value * 1_073_741_824).toLong()
            "MB" -> (value * 1_048_576).toLong()
            "KB" -> (value * 1_024).toLong()
            else -> value.toLong()
        }
    } catch (e: Exception) { 0L }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024         -> "%.1f KB".format(bytes / 1_024.0)
        else                   -> "$bytes B"
    }
}
