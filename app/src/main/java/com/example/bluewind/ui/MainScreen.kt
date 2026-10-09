package com.example.bluewind.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluewind.hid.ConsumerUsage
import com.example.bluewind.hid.HidManager
import com.example.bluewind.hid.HidManager.Status

private val SIDE_COLUMN_WIDTH = 120.dp

/** 메인 화면: 트랙패드 영역 + 오른쪽 측면 버튼 열 */
@Composable
fun MainScreen(
    onOpenConnection: () -> Unit,
    onOpenKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by HidManager.state.collectAsStateWithLifecycle()
    val connected = state.status == Status.CONNECTED

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val trackpadModifier = Modifier
            .weight(1f)
            .fillMaxHeight()
        if (connected) {
            Trackpad(modifier = trackpadModifier)
        } else {
            NotConnected(onOpenConnection = onOpenConnection, modifier = trackpadModifier)
        }
        Column(
            modifier = Modifier
                .width(SIDE_COLUMN_WIDTH)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConnectionStatusButton(
                state = state,
                onClick = onOpenConnection,
                modifier = Modifier.fillMaxWidth(),
            )
            HoldButton(
                label = "볼륨 +",
                onFire = { HidManager.sendConsumerClick(ConsumerUsage.VOLUME_UP) },
                repeat = true,
                enabled = connected,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            HoldButton(
                label = "볼륨 −",
                onFire = { HidManager.sendConsumerClick(ConsumerUsage.VOLUME_DOWN) },
                repeat = true,
                enabled = connected,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            HoldButton(
                label = "음소거",
                onFire = { HidManager.sendConsumerClick(ConsumerUsage.MUTE) },
                enabled = connected,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            SideButton(
                label = "키보드",
                onClick = onOpenKeyboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun ConnectionStatusButton(
    state: HidManager.State,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (color, label) = when (state.status) {
        Status.CONNECTED -> Color(0xFF81C784) to "연결됨"
        Status.CONNECTING, Status.REGISTERING, Status.DISCONNECTING -> Color(0xFFFFB74D) to "연결 중"
        Status.READY, Status.NOT_STARTED -> Color.Gray to "연결 안 됨"
        Status.BLUETOOTH_OFF, Status.NOT_SUPPORTED, Status.REGISTER_FAILED -> Color(0xFFE57373) to "오류"
    }
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
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
}

@Composable
private fun SideButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun NotConnected(onOpenConnection: () -> Unit, modifier: Modifier = Modifier) {
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
