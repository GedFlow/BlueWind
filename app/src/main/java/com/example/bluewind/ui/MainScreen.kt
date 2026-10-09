package com.example.bluewind.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluewind.hid.ConsumerUsage
import com.example.bluewind.hid.HidManager
import com.example.bluewind.hid.HidManager.Status

private val MENU_WIDTH = 128.dp
private val VOLUME_BUTTON_HEIGHT = 56.dp

/**
 * 메인 화면: 트랙패드가 화면 전체.
 * - 좌상단: 키보드 버튼 (항상 표시)
 * - 우상단: 연결 상태 버튼. 누르면 아래로 볼륨 버튼·리모컨 모드, 왼쪽으로 연결 설정·프레젠테이션 버튼이 펼쳐진다.
 */
@Composable
fun MainScreen(
    onOpenConnection: () -> Unit,
    onOpenKeyboard: () -> Unit,
    onOpenPresentation: () -> Unit,
    onOpenRemote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by HidManager.state.collectAsStateWithLifecycle()
    val connected = state.status == Status.CONNECTED
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(SCREEN_PADDING),
    ) {
        if (connected) {
            Trackpad(modifier = Modifier.fillMaxSize())
        } else {
            NotConnected(onOpenConnection = onOpenConnection, modifier = Modifier.fillMaxSize())
        }

        CornerButton(label = "키보드", onClick = onOpenKeyboard, modifier = Modifier.align(Alignment.TopStart))

        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (menuOpen) {
                PanelButton(
                    label = "프레젠테이션",
                    onClick = {
                        menuOpen = false
                        onOpenPresentation()
                    },
                    modifier = Modifier.size(MENU_WIDTH, CORNER_BUTTON_HEIGHT),
                )
                PanelButton(
                    label = "연결 설정",
                    onClick = {
                        menuOpen = false
                        onOpenConnection()
                    },
                    modifier = Modifier.size(MENU_WIDTH, CORNER_BUTTON_HEIGHT),
                )
            }
            Column(
                modifier = Modifier.width(MENU_WIDTH),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConnectionStatusButton(
                    state = state,
                    expanded = menuOpen,
                    onClick = { menuOpen = !menuOpen },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CORNER_BUTTON_HEIGHT),
                )
                if (menuOpen) {
                    val volumeModifier = Modifier
                        .fillMaxWidth()
                        .height(VOLUME_BUTTON_HEIGHT)
                    HoldButton(
                        label = "볼륨 +",
                        onFire = { HidManager.sendConsumerClick(ConsumerUsage.VOLUME_UP) },
                        repeat = true,
                        enabled = connected,
                        modifier = volumeModifier,
                    )
                    HoldButton(
                        label = "볼륨 −",
                        onFire = { HidManager.sendConsumerClick(ConsumerUsage.VOLUME_DOWN) },
                        repeat = true,
                        enabled = connected,
                        modifier = volumeModifier,
                    )
                    HoldButton(
                        label = "음소거",
                        onFire = { HidManager.sendConsumerClick(ConsumerUsage.MUTE) },
                        enabled = connected,
                        modifier = volumeModifier,
                    )
                    PanelButton(
                        label = "리모컨 모드",
                        onClick = {
                            menuOpen = false
                            onOpenRemote()
                        },
                        modifier = volumeModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusButton(
    state: HidManager.State,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (color, label) = when (state.status) {
        Status.CONNECTED -> Color(0xFF81C784) to "연결됨"
        Status.CONNECTING, Status.REGISTERING, Status.DISCONNECTING -> Color(0xFFFFB74D) to "연결 중"
        Status.READY, Status.NOT_STARTED -> Color.Gray to "연결 안 됨"
        Status.BLUETOOTH_OFF, Status.NOT_SUPPORTED, Status.REGISTER_FAILED -> Color(0xFFE57373) to "오류"
    }
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            if (state.status == Status.CONNECTED && state.host != null) {
                Text(
                    state.host.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(if (expanded) "▴" else "▾", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun NotConnected(onOpenConnection: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PC에 연결되지 않았습니다", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onOpenConnection) { Text("연결 화면 열기") }
        }
    }
}
