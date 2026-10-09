package com.example.bluewind.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluewind.hid.HidManager
import com.example.bluewind.hid.KeyUsage

private val SMALL_BUTTON_HEIGHT = 56.dp

/**
 * 프레젠테이션 모드 (PowerPoint 슬라이드쇼 단축키). 세로 화면 (v0.6).
 * 키 입력은 활성 창으로 가므로 PowerPoint 창이 앞에 있어야 한다.
 *
 *   [닫기]   [처음부터 F5] [현재부터 Shift+F5]
 *   [검은 화면 .] [레이저 Ctrl+L] [종료 Esc]
 *   [      레이저 포인터 이동 영역       ]
 *   [   ◀ 이전   ] [      다음 ▶      ]
 */
@Composable
fun PresentationScreen(
    onClose: () -> Unit,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val state by HidManager.state.collectAsStateWithLifecycle()
    val connected = state.status == HidManager.Status.CONNECTED
    val smallStyle = MaterialTheme.typography.titleSmall

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 1줄: 닫기(다른 화면과 같은 자리) + 시작 버튼
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SMALL_BUTTON_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CornerButton(label = "닫기", onClick = onClose)
            val small = Modifier
                .weight(1f)
                .fillMaxHeight()
            PanelButton("처음부터", { HidManager.keyTap(KeyUsage.f(5)) }, small, sub = "F5", enabled = connected, labelStyle = smallStyle)
            PanelButton(
                "현재부터",
                { HidManager.keyTap(KeyUsage.LEFT_SHIFT, KeyUsage.f(5)) },
                small,
                sub = "Shift+F5",
                enabled = connected,
                labelStyle = smallStyle,
            )
        }

        // 2줄: 보조 버튼
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SMALL_BUTTON_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val small = Modifier
                .weight(1f)
                .fillMaxHeight()
            // PowerPoint 검은 화면은 B 또는 마침표(.). B는 Windows 입력기가 한글 상태면 'ㅠ'로 먹혀서 동작하지 않는다.
            PanelButton("검은 화면", { HidManager.keyTap(KeyUsage.PERIOD) }, small, sub = ".", enabled = connected, labelStyle = smallStyle)
            PanelButton(
                "레이저",
                { HidManager.keyTap(KeyUsage.LEFT_CTRL, KeyUsage.letter('L')) },
                small,
                sub = "Ctrl+L",
                enabled = connected,
                labelStyle = smallStyle,
            )
            PanelButton("종료", { HidManager.keyTap(KeyUsage.ESC) }, small, sub = "Esc", enabled = connected, labelStyle = smallStyle)
        }

        // 레이저 포인터 이동 영역
        val padModifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        if (connected) {
            Trackpad(
                modifier = padModifier,
                movementOnly = true,
                sensitivityScale = TrackpadConfig.PORTRAIT_SENSITIVITY_SCALE,
                hint = "레이저 포인터 이동\n(레이저 버튼으로 켜고 끄기)\n\nPowerPoint 창이 앞에 있어야 합니다",
            )
        } else {
            NotConnected(onOpenConnection = onOpenConnection, modifier = padModifier)
        }

        // 큰 이전/다음 버튼 (엄지가 닿는 아래쪽)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PanelButton(
                label = "◀ 이전",
                onClick = { HidManager.keyTap(KeyUsage.LEFT) },
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxHeight(),
                enabled = connected,
                labelStyle = MaterialTheme.typography.headlineSmall,
            )
            PanelButton(
                label = "다음 ▶",
                onClick = { HidManager.keyTap(KeyUsage.RIGHT) },
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                enabled = connected,
                labelStyle = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
