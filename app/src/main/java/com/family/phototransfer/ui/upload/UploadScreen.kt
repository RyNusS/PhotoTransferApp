package com.family.phototransfer.ui.upload

import android.Manifest
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

// ── 컬러 상수 ─────────────────────────────────────────────────
private val BgDark        = Color(0xFF0D1117)
private val CardBg        = Color(0xFF1C2333)
private val CardBorder    = Color(0xFF2D3748)
private val PrimaryBlue   = Color(0xFF2D6BE4)
private val SuccessGreen  = Color(0xFF00C853)
private val WarningOrange = Color(0xFFFF6B35)
private val TextPrimary   = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF8B9BB4)

enum class MediaTab { ALL, PHOTO, VIDEO }

// ── 메인 화면 ─────────────────────────────────────────────────
@Composable
fun UploadScreen(viewModel: UploadViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) viewModel.loadMediaFiles(context)
    }

    LaunchedEffect(Unit) { permissionLauncher.launch(permissions) }

    // 전송 완료 다이얼로그
    if (uiState.isUploadDone) {
        UploadResultDialog(
            successCount   = uiState.successCount,
            duplicateCount = uiState.duplicateCount,
            failedCount    = uiState.failedCount,
            onDismiss      = { viewModel.dismissUploadResult() }
        )
    }

    // 전송 중 다이얼로그
    if (uiState.isUploading) {
        UploadProgressDialog(
            currentFile  = uiState.currentUploadFile,
            current      = uiState.uploadCurrent,
            total        = uiState.uploadTotal,
            progress     = uiState.uploadProgress
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BgDark)
    ) {
        // 1) 헤더
        UploadHeader(
            selectedCount = uiState.selectedFiles.size,
            selectedSize  = uiState.selectedSizeText,
            onSelectAll   = { viewModel.selectAll() },
            onClearAll    = { viewModel.clearSelection() }
        )

        // 2) 기기 연결 섹션
        DeviceConnectionSection(
            isScanning        = uiState.isScanning,
            discoveredDevices = uiState.discoveredDevices,
            selectedDevice    = uiState.selectedDevice,
            cloudSyncActive   = uiState.cloudSyncActive,
            onScanClick       = { viewModel.scanForDevices(context) },
            onSelectDevice    = { viewModel.selectDevice(it) }
        )

        // 3) 탭 필터 + 열 전환 버튼
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                MediaTabRow(
                    selectedTab   = uiState.selectedTab,
                    onTabSelected = { viewModel.setTab(it) }
                )
            }
            Spacer(Modifier.width(8.dp))
            // 열 전환 버튼 (3열 ↔ 5열)
            GridColumnToggle(
                columns   = uiState.gridColumns,
                onToggle  = { viewModel.toggleGridColumns() }
            )
        }

        // 4) 사진 그리드
        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center), color = PrimaryBlue
                )
                uiState.filteredFiles.isEmpty() -> EmptyState()
                else -> MediaGrid(
                    files          = uiState.filteredFiles,
                    selectedIds    = uiState.selectedFiles,
                    columns        = uiState.gridColumns,
                    onToggleSelect = { viewModel.toggleSelection(it) },
                    onDragSelect   = { viewModel.dragSelect(it) }
                )
            }
        }

        // 5) 하단 버튼
        UploadBottomBar(
            selectedCount  = uiState.selectedFiles.size,
            isUploading    = uiState.isUploading,
            hasDevice      = uiState.selectedDevice != null,
            onUpload       = { viewModel.startUpload(context) }
        )
    }
}

// ── 기기 연결 섹션 ─────────────────────────────────────────────
@Composable
fun DeviceConnectionSection(
    isScanning: Boolean,
    discoveredDevices: List<com.family.phototransfer.network.DiscoveredDevice>,
    selectedDevice: com.family.phototransfer.network.DiscoveredDevice?,
    cloudSyncActive: Boolean,
    onScanClick: () -> Unit,
    onSelectDevice: (com.family.phototransfer.network.DiscoveredDevice) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .border(0.5.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (cloudSyncActive) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (cloudSyncActive) PrimaryBlue else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (selectedDevice != null) selectedDevice.deviceName
                               else "수신 기기 탐색",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (selectedDevice != null) "${selectedDevice.ipAddress} · 연결 준비 완료"
                               else "같은 WiFi에서 수신 앱이 실행 중인 기기 탐색",
                        color = if (selectedDevice != null) SuccessGreen else TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // 탐색 버튼
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = PrimaryBlue,
                    strokeWidth = 2.dp
                )
            } else {
                OutlinedButton(
                    onClick = onScanClick,
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(
                        text = if (discoveredDevices.isEmpty()) "탐색" else "재탐색",
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 발견된 기기 목록
        if (discoveredDevices.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))
            Text("발견된 기기", color = TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(discoveredDevices) { device ->
                    val isSelected = device == selectedDevice
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) PrimaryBlue.copy(alpha = 0.15f)
                                else CardBorder.copy(alpha = 0.5f)
                            )
                            .border(
                                1.dp,
                                if (isSelected) PrimaryBlue else CardBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectDevice(device) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = if (isSelected) PrimaryBlue else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                device.deviceName,
                                color = if (isSelected) PrimaryBlue else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(device.ipAddress, color = TextSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── 전송 중 다이얼로그 ─────────────────────────────────────────
@Composable
fun UploadProgressDialog(
    currentFile: String,
    current: Int,
    total: Int,
    progress: Float
) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = PrimaryBlue)
                Spacer(Modifier.height(16.dp))
                Text("전송 중...", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "$current / $total",
                    color = PrimaryBlue,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentFile,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { if (total > 0) current.toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = PrimaryBlue,
                    trackColor = CardBorder
                )
            }
        }
    }
}

// ── 전송 완료 다이얼로그 ───────────────────────────────────────
@Composable
fun UploadResultDialog(
    successCount: Int,
    duplicateCount: Int,
    failedCount: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Text("전송 완료", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultRow(Icons.Default.CheckCircle, SuccessGreen,  "전송 성공",  "$successCount 개")
                ResultRow(Icons.Default.ContentCopy, WarningOrange, "중복 건너뜀", "$duplicateCount 개")
                if (failedCount > 0) {
                    ResultRow(Icons.Default.Error, Color(0xFFFF4444), "전송 실패", "$failedCount 개")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인", color = PrimaryBlue)
            }
        }
    )
}

@Composable
fun ResultRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ── 상단 헤더 ─────────────────────────────────────────────────
@Composable
fun UploadHeader(
    selectedCount: Int,
    selectedSize: String,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("사진 선택", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (selectedCount > 0) {
                Text("${selectedCount}개 선택 · $selectedSize", color = PrimaryBlue, fontSize = 12.sp)
            } else {
                Text("전송할 사진/동영상을 선택하세요", color = TextSecondary, fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onSelectAll) { Text("전체 선택", color = PrimaryBlue, fontSize = 12.sp) }
            if (selectedCount > 0) {
                TextButton(onClick = onClearAll) { Text("해제", color = TextSecondary, fontSize = 12.sp) }
            }
        }
    }
}

// ── 탭 필터 ───────────────────────────────────────────────────
@Composable
fun MediaTabRow(selectedTab: MediaTab, onTabSelected: (MediaTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
    ) {
        MediaTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) PrimaryBlue else Color.Transparent)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (tab) { MediaTab.ALL -> "전체"; MediaTab.PHOTO -> "사진"; MediaTab.VIDEO -> "동영상" },
                    color = if (isSelected) TextPrimary else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ── 열 전환 버튼 ─────────────────────────────────────────────
@Composable
fun GridColumnToggle(columns: Int, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(0.5.dp, CardBorder, RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = "열 전환",
                tint = PrimaryBlue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (columns == 3) "5열" else "3열",
                color = PrimaryBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── 미디어 그리드 (드래그 선택 지원) ─────────────────────────
@Composable
fun MediaGrid(
    files: List<MediaFileUi>,
    selectedIds: Set<Long>,
    columns: Int,
    onToggleSelect: (Long) -> Unit,
    onDragSelect: (Set<Long>) -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val spacing = if (columns == 5) 2.dp else 3.dp
    val padding = 16.dp

    // 그리드 너비 측정용
    var gridWidthPx by remember { mutableStateOf(0) }

    // 드래그 시작 시 선택 스냅샷
    var dragStartIndex     by remember { mutableStateOf(-1) }
    var dragCurrentIndex   by remember { mutableStateOf(-1) }
    var dragStartSelected  by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // 드래그 범위(시작~현재) 인덱스 → 선택 ID 계산
    fun indicesInRange(a: Int, b: Int): Set<Long> {
        if (a < 0 || b < 0) return emptySet()
        val lo = minOf(a, b)
        val hi = maxOf(a, b)
        return (lo..hi).mapNotNull { files.getOrNull(it)?.id }.toSet()
    }

    // 터치 좌표 → 그리드 인덱스 계산
    fun posToIndex(x: Float, y: Float, scrollOffset: Float): Int {
        if (gridWidthPx == 0) return -1
        val paddingPx = with(density) { padding.toPx() }
        val spacingPx = with(density) { spacing.toPx() }
        val availWidth = gridWidthPx - paddingPx * 2
        val cellW = (availWidth - spacingPx * (columns - 1)) / columns

        val col = ((x - paddingPx) / (cellW + spacingPx)).toInt().coerceIn(0, columns - 1)
        val row = ((y + scrollOffset - with(density) { 4.dp.toPx() }) / (cellW + spacingPx)).toInt()
        if (row < 0) return -1
        val idx = row * columns + col
        return if (idx in files.indices) idx else -1
    }

    val lazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    LazyVerticalGrid(
        state   = lazyGridState,
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { gridWidthPx = it.size.width }
            .pointerInput(files, columns) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val scrollOffset = lazyGridState.firstVisibleItemScrollOffset.toFloat() +
                            lazyGridState.firstVisibleItemIndex * (size.width / columns).toFloat()
                        dragStartIndex    = posToIndex(offset.x, offset.y, scrollOffset)
                        dragCurrentIndex  = dragStartIndex
                        dragStartSelected = selectedIds.toSet()
                        if (dragStartIndex >= 0) {
                            onDragSelect(dragStartSelected + indicesInRange(dragStartIndex, dragStartIndex))
                        }
                    },
                    onDrag = { change, _ ->
                        val scrollOffset = lazyGridState.firstVisibleItemScrollOffset.toFloat() +
                            lazyGridState.firstVisibleItemIndex * (size.width / columns).toFloat()
                        val newIndex = posToIndex(change.position.x, change.position.y, scrollOffset)
                        if (newIndex >= 0 && newIndex != dragCurrentIndex) {
                            dragCurrentIndex = newIndex
                            onDragSelect(dragStartSelected + indicesInRange(dragStartIndex, dragCurrentIndex))
                        }
                    },
                    onDragEnd = {
                        dragStartIndex   = -1
                        dragCurrentIndex = -1
                    },
                    onDragCancel = {
                        dragStartIndex   = -1
                        dragCurrentIndex = -1
                    }
                )
            },
        contentPadding = PaddingValues(horizontal = padding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement   = Arrangement.spacedBy(spacing)
    ) {
        itemsIndexed(files, key = { _, file -> file.id }) { _, file ->
            MediaGridItem(
                file       = file,
                isSelected = file.id in selectedIds,
                onToggle   = { onToggleSelect(file.id) }
            )
        }
    }
}

@Composable
fun MediaGridItem(
    file: MediaFileUi,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current

    // 동영상 썸네일 비동기 로드 (IO 스레드에서 처리)
    val videoBitmap: Bitmap? by if (file.isVideo) {
        produceState<Bitmap?>(initialValue = null, key1 = file.id) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val uri = android.net.Uri.parse(file.uri)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.contentResolver.loadThumbnail(uri, Size(300, 300), null)
                    } else {
                        val path = context.contentResolver.query(
                            uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToFirst())
                                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
                            else null
                        }
                        if (path != null) {
                            @Suppress("DEPRECATION")
                            ThumbnailUtils.createVideoThumbnail(
                                path, MediaStore.Images.Thumbnails.MINI_KIND
                            )
                        } else null
                    }
                } catch (e: Exception) { null }
            }
        }
    } else remember { mutableStateOf<Bitmap?>(null) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable { onToggle() }
    ) {
        // delegated property는 스마트캐스트 불가 → 로컬 변수로 캡처
        val bitmap = videoBitmap
        if (file.isVideo && bitmap != null) {
            // 동영상: 로드된 썸네일 비트맵 표시
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (file.isVideo) {
            // 동영상: 썸네일 로드 실패 시 아이콘 표시
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF1C2333)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.VideoFile, null,
                    tint = Color(0xFF8B9BB4),
                    modifier = Modifier.size(32.dp)
                )
            }
        } else {
            // 사진: AsyncImage로 바로 로드
            AsyncImage(
                model = android.net.Uri.parse(file.uri),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (file.isVideo) {
            Box(
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(file.duration ?: "", color = Color.White, fontSize = 9.sp)
                }
            }
        }
        if (isSelected) {
            Box(modifier = Modifier.fillMaxSize().background(PrimaryBlue.copy(alpha = 0.35f)))
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp)
                    .clip(CircleShape).background(PrimaryBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        } else {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp)
                .clip(CircleShape).border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape))
        }
    }
}

// ── 빈 상태 ───────────────────────────────────────────────────
@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.PhotoLibrary, null, tint = TextSecondary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("사진이 없습니다", color = TextSecondary, fontSize = 16.sp)
        Text("갤러리 접근 권한을 확인해주세요", color = TextSecondary, fontSize = 12.sp)
    }
}

// ── 하단 업로드 버튼 ───────────────────────────────────────────
@Composable
fun UploadBottomBar(
    selectedCount: Int,
    isUploading: Boolean,
    hasDevice: Boolean,
    onUpload: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(CardBg).padding(16.dp)
    ) {
        if (!hasDevice && selectedCount > 0) {
            Text(
                "⚠️ 수신 기기를 먼저 탐색해주세요",
                color = WarningOrange,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Button(
            onClick = onUpload,
            enabled = selectedCount > 0 && !isUploading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryBlue,
                disabledContainerColor = CardBorder
            )
        ) {
            if (isUploading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("전송 중...", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Default.CloudUpload, null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (selectedCount > 0) "Pixel Hub에 ${selectedCount}개 전송" else "사진을 선택하세요",
                    color = if (selectedCount > 0) Color.White else TextSecondary,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
