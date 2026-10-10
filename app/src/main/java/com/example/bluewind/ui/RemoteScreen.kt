package com.example.bluewind.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluewind.R
import com.example.bluewind.hid.ConsumerUsage
import com.example.bluewind.hid.HidManager
import com.example.bluewind.hid.KeyUsage

/**
 * 리모컨 모드 (세로 화면). v0.8: 버튼을 위로, 트랙패드를 아래로 (하단 내비게이션 바를 숨기므로 아래 끝까지 쓴다).
 * - 위 40%: 닫기 + 버튼 4행 × 3열. 볼륨 버튼은 스피커 아이콘.
 * - 아래 60%: 트랙패드 (가로 화면보다 30% 빠르게)
 *
 *   [닫기]
 *   [밝기 +] [Space] [볼륨 +]
 *   [밝기 −] [Enter] [볼륨 −]
 *   [  ⌫  ] [  ↑  ] [음소거]
 *   [  ←  ] [  ↓  ] [  →  ]
 *   [        트랙패드        ]
 */
@Composable
fun RemoteScreen(
    onClose: () -> Unit,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val state by HidManager.state.collectAsStateWithLifecycle()
    val connected = state.status == HidManager.Status.CONNECTED

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 위 40%: 닫기(다른 화면과 같이 좌상단) + 버튼 4행 × 3열
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CornerButton(label = "닫기", onClick = onClose)
            ButtonRow {
                ConsumerButton("밝기 +", ConsumerUsage.BRIGHTNESS_UP, repeat = true, enabled = connected)
                RemoteKey("Space", KeyUsage.SPACE, connected)
                ConsumerButton("볼륨 +", ConsumerUsage.VOLUME_UP, repeat = true, enabled = connected, icon = R.drawable.ic_volume_up)
            }
            ButtonRow {
                ConsumerButton("밝기 −", ConsumerUsage.BRIGHTNESS_DOWN, repeat = true, enabled = connected)
                RemoteKey("Enter", KeyUsage.ENTER, connected, primary = true)
                ConsumerButton("볼륨 −", ConsumerUsage.VOLUME_DOWN, repeat = true, enabled = connected, icon = R.drawable.ic_volume_down)
            }
            ButtonRow {
                RemoteKey("⌫", KeyUsage.BACKSPACE, connected)
                RemoteKey("↑", KeyUsage.UP, connected)
                ConsumerButton("음소거", ConsumerUsage.MUTE, repeat = false, enabled = connected, icon = R.drawable.ic_volume_mute)
            }
            ButtonRow {
                RemoteKey("←", KeyUsage.LEFT, connected)
                RemoteKey("↓", KeyUsage.DOWN, connected)
                RemoteKey("→", KeyUsage.RIGHT, connected)
            }
        }

        // 아래 60%: 트랙패드
        val padModifier = Modifier
            .fillMaxWidth()
            .weight(0.6f)
        if (connected) {
            Trackpad(
                modifier = padModifier,
                sensitivityScale = TrackpadConfig.PORTRAIT_SENSITIVITY_SCALE,
                hint = "리모컨 모드\n\n한 손가락: 이동 · 탭 클릭\n두 손가락: 스크롤 · 탭 우클릭",
            )
        } else {
            NotConnected(onOpenConnection = onOpenConnection, modifier = padModifier)
        }
    }
}

@Composable
private fun ColumnScope.ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun RowScope.ConsumerButton(
    label: String,
    usage: Int,
    repeat: Boolean,
    enabled: Boolean,
    @DrawableRes icon: Int? = null,
) {
    HoldButton(
        label = label,
        onFire = { HidManager.sendConsumerClick(usage) },
        repeat = repeat,
        enabled = enabled,
        icon = icon?.let { painterResource(it) },
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
    )
}

@Composable
private fun RowScope.RemoteKey(label: String, usage: Int, enabled: Boolean, primary: Boolean = false) {
    KeyButton(
        label = label,
        usage = usage,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        color = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (primary) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    )
}
