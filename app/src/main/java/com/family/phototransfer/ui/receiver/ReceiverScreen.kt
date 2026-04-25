package com.family.phototransfer.ui.receiver

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
                tunnelStatus      = uiState.tunnelStatus,
                tunnelUrl         = uiState.tunnelUrl,
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
    tunnelStatus:      String  = "",
    tunnelUrl:         String? = null,
    onStart:           () -> Unit,
    onStop:            () -> Unit,
    onToggleAutoStart: (Boolean) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
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

            if (isRunning && tunnelStatus.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val isConnected  = tunnelUrl != null
                val isErrorState = tunnelStatus.startsWith("오류")
                val tunnelColor  = when {
                    isConnected  -> SuccessGreen
                    isErrorState -> Color(0xFFFF4444)
                    else         -> WarningOrange
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(tunnelColor.copy(alpha = 0.08f))
                        .border(0.5.dp, tunnelColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when {
                                isConnected  -> Icons.Default.Cloud
                                isErrorState -> Icons.Default.CloudOff
                                else         -> Icons.Default.CloudSync
                            },
                            contentDescription = null,
                            tint = tunnelColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when {
                                isConnected  -> "Cloudflare 터널 연결됨"
                                isErrorState -> "Cloudflare 터널 오류"
                                else         -> "Cloudflare 터널 연결 중..."
                            },
                            color = tunnelColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (tunnelUrl != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Samsung 폰에서 QR코드를 스캔하세요:",
                            color = tunnelColor.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        // QR코드 표시
                        val deepLinkUrl = "phototransfer://send?url=$tunnelUrl"
                        val qrBitmap = remember(deepLinkUrl) { generateQrBitmap(deepLinkUrl, 300) }
                        if (qrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .padding(8.dp)
                                    .align(Alignment.CenterHorizontally)
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR코드",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // URL 텍스트 + 복사 버튼
                        Text(
                            text = "또는 URL을 직접 입력:",
                            color = tunnelColor.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0D1117))
                                .border(0.5.dp, tunnelColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = tunnelUrl,
                                color = SuccessGreen,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = { clipboardManager.setText(AnnotatedString(tunnelUrl)) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "URL 복사",
                                    tint = tunnelColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else if (!isErrorState) {
                        Text(
                            text = tunnelStatus,
                            color = tunnelColor.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = tunnelStatus,
                            color = tunnelColor.copy(alpha = 0.8f),
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
                    imageVector = Icons.Default.Autorenew,
                    contentDescription = null,
                    tint = if (autoStartOnBoot) PrimaryBlue else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "자동 시작",
                        fontSize = 13.sp,
                        color = if (autoStartOnBoot) PrimaryBlue else TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text("앱 시작 시 자동으로 수신 시작", fontSize = 11.sp, color = TextSecondary)
                }
                Switch(
                    checked = autoStartOnBoot,
                    onCheckedChange = onToggleAutoStart,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = Color.White,
                        checkedTrackColor   = PrimaryBlue,
                        uncheckedTrackColor = CardBorder
                    )
                )
            }
        }
    }
}

@Composable
fun StorageCard(
    receivedCount:    Int,
    receivedSizeText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$receivedCount", color = PrimaryBlue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("수신 파일", color = TextSecondary, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(receivedSizeText, color = PrimaryBlue, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("총 용량", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ReceivedFileItem(file: ReceivedFileUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .border(0.5.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = file.fileName,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = "${file.sizeText} • ${file.timeText}",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

private fun parseSizeText(sizeText: String): Long {
    return try {
        when {
            sizeText.endsWith(" MB") -> (sizeText.removeSuffix(" MB").toDouble() * 1_048_576).toLong()
            sizeText.endsWith(" KB") -> (sizeText.removeSuffix(" KB").toDouble() * 1_024).toLong()
            sizeText.endsWith(" B")  -> sizeText.removeSuffix(" B").toLong()
            else -> 0L
        }
    } catch (e: Exception) { 0L }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024         -> "%.1f KB".format(bytes / 1_024.0)
    else                   -> "$bytes B"
}

// ── QR코드 생성 헬퍼 ───────────────────────────────────────────
fun generateQrBitmap(content: String, sizePx: Int): Bitmap? {
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) { null }
}
