package com.example.bluewind.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluewind.hid.HidManager

/**
 * 앱이 직접 그린 키보드. 터치 DOWN = 키 누름, 터치 UP = 키 뗌.
 * 각 키가 손가락을 따로 받으므로 여러 키를 동시에 누를 수 있다 (Shift+문자, Ctrl+C, Alt+Tab).
 * 반복 입력은 PC가 처리하므로 앱은 반복 전송하지 않는다.
 */
@Composable
fun KeyboardPanel(onClose: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onClose)
    // 패널이 닫힐 때 눌린 키가 PC에 남지 않게 한다
    DisposableEffect(Unit) {
        onDispose { HidManager.releaseAllKeys() }
    }

    val state by HidManager.state.collectAsStateWithLifecycle()
    val connected = state.status == HidManager.Status.CONNECTED

    // Fn: 누르고 있는 동안 레이어 전환. 다른 키 없이 탭만 하면 고정/해제.
    var fnHeld by remember { mutableStateOf(false) }
    var fnLatched by remember { mutableStateOf(false) }
    var usedWhileFnHeld by remember { mutableStateOf(false) }
    val fnActive = fnHeld || fnLatched

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!connected) {
                Text(
                    "PC에 연결되지 않았습니다",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFE57373),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (fnLatched) {
                Text(
                    "Fn 고정",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("닫기") }
        }
        KEYBOARD_ROWS.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { def ->
                    Key(
                        def = def,
                        fnActive = fnActive,
                        fnLatched = fnLatched,
                        onPress = { usage ->
                            if (fnHeld) usedWhileFnHeld = true
                            HidManager.keyDown(usage)
                        },
                        onRelease = { usage -> HidManager.keyUp(usage) },
                        onFnDown = {
                            fnHeld = true
                            usedWhileFnHeld = false
                        },
                        onFnUp = {
                            fnHeld = false
                            if (!usedWhileFnHeld) fnLatched = !fnLatched
                        },
                        modifier = Modifier
                            .weight(def.weight)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Key(
    def: KeyDef,
    fnActive: Boolean,
    fnLatched: Boolean,
    onPress: (usage: Int) -> Unit,
    onRelease: (usage: Int) -> Unit,
    onFnDown: () -> Unit,
    onFnUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentFnActive by rememberUpdatedState(fnActive)
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnFnDown by rememberUpdatedState(onFnDown)
    val currentOnFnUp by rememberUpdatedState(onFnUp)

    val showFnLayer = fnActive && def.fnLabel != null
    val colors = MaterialTheme.colorScheme
    val background = when {
        pressed -> colors.primary
        def.isFn && fnLatched -> colors.tertiary
        def.isFn && fnActive -> colors.tertiaryContainer
        else -> colors.surfaceVariant
    }
    val content = when {
        pressed -> colors.onPrimary
        def.isFn && fnLatched -> colors.onTertiary
        showFnLayer -> colors.tertiary
        else -> colors.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(6.dp))
            .pointerInput(def) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    // 누를 때 정한 usage를 뗄 때도 쓴다 (도중에 Fn 레이어가 바뀌어도 같은 키를 뗀다)
                    val usage = if (currentFnActive && def.fnUsage != null) def.fnUsage else def.usage
                    if (usage == null) currentOnFnDown() else currentOnPress(usage)
                    try {
                        // 실물 키보드처럼 손가락을 뗄 때까지 누른 상태 유지 (키 밖으로 조금 밀려도 유지)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                        }
                    } finally {
                        pressed = false
                        if (usage == null) currentOnFnUp() else currentOnRelease(usage)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (showFnLayer) def.fnLabel!! else def.label,
                color = content,
                fontSize = if (def.label.length > 2 || showFnLayer) 14.sp else 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            if (def.sub != null && !showFnLayer) {
                Text(def.sub, color = content.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
