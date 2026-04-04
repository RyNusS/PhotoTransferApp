package com.family.phototransfer.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

// ── 컬러 상수 ──────────────────────────────────────────────────
private val BgDark        = Color(0xFF0D1117)
private val CardBg        = Color(0xFF1C2333)
private val CardBorder    = Color(0xFF2D3748)
private val PrimaryBlue   = Color(0xFF2D6BE4)
private val SuccessGreen  = Color(0xFF00C853)
private val TextPrimary   = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF8B9BB4)

// ── 메인 화면 ─────────────────────────────────────────────────
@Composable
fun SenderDashboardScreen(
    selectedMode:     String            = "upload",
    onModeSelected:   (String) -> Unit  = {},
    onNavigateToMode: (String) -> Unit  = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // 헤더
        Text(
            "Choose your role",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Select how you want to use the app today",
            color = TextSecondary,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(20.dp))

        // Upload Mode 카드 (위쪽 절반)
        RoleModeCard(
            modifier      = Modifier.weight(1f),
            tagLabel      = "SENDER",
            tagColor      = PrimaryBlue,
            title         = "Upload Mode",
            description   = "Push Photos / 사진, 동영상을 전송합니다",
            icon          = Icons.Default.Upload,
            accentColor   = PrimaryBlue,
            buttonLabel   = "Continue as Sender",
            isSelected    = selectedMode == "upload",
            onCardClick   = { onModeSelected("upload") },
            onButtonClick = { onNavigateToMode("upload") }
        )

        Spacer(Modifier.height(12.dp))

        // Receiver Mode 카드 (아래쪽 절반)
        RoleModeCard(
            modifier      = Modifier.weight(1f),
            tagLabel      = "RECEIVER (PIXEL)",
            tagColor      = TextSecondary,
            title         = "Receiver Mode",
            description   = "Pull Photos / 사진, 동영상을 수신합니다",
            icon          = Icons.Default.Download,
            accentColor   = PrimaryBlue,
            buttonLabel   = "Switch to Receiver",
            isSelected    = selectedMode == "receiver",
            onCardClick   = { onModeSelected("receiver") },
            onButtonClick = { onNavigateToMode("receiver") }
        )
    }
}

// ── 역할 선택 카드 ────────────────────────────────────────────
@Composable
fun RoleModeCard(
    modifier:      Modifier            = Modifier,
    tagLabel:      String,
    tagColor:      Color,
    title:         String,
    description:   String,
    icon:          ImageVector,
    accentColor:   Color,
    buttonLabel:   String,
    isSelected:    Boolean,
    onCardClick:   () -> Unit,
    onButtonClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) Color(0xFF151D2E) else CardBg)
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) accentColor else CardBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onCardClick() }
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 상단: 아이콘
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) accentColor.copy(alpha = 0.25f)
                        else Color(0xFF0D1117)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = if (isSelected) accentColor else TextSecondary,
                    modifier           = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // 태그 + 체크 인디케이터
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text          = tagLabel,
                    color         = if (isSelected) accentColor else tagColor,
                    fontSize      = 11.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                if (isSelected) {
                    Box(
                        modifier         = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint     = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // 타이틀
            Text(
                text       = title,
                color      = TextPrimary,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(2.dp))

            // 설명
            Text(
                text     = description,
                color    = TextSecondary,
                fontSize = 12.sp
            )

            // 버튼을 항상 하단에 붙이기
            Spacer(Modifier.weight(1f))

            Button(
                onClick  = onButtonClick,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) accentColor else Color(0xFF2A3347)
                )
            ) {
                Text(
                    text       = buttonLabel,
                    color      = if (isSelected) Color.White else TextSecondary,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
