package com.example.bluewind.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.bluewind.hid.HidManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// 누르고 있을 때 반복 전송 간격. 실기기에서 튜닝한다.
private const val REPEAT_INITIAL_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 150L

private val ICON_SIZE = 32.dp

/**
 * 손가락이 닿는 순간 [onFire]를 한 번 호출한다.
 * [repeat]이면 누르고 있는 동안 일정 간격으로 계속 호출한다.
 * @param icon 있으면 글자 대신 아이콘을 표시한다. 이때 [label]은 접근성 설명으로 쓴다.
 */
@Composable
fun HoldButton(
    label: String,
    onFire: () -> Unit,
    modifier: Modifier = Modifier,
    repeat: Boolean = false,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    val scope = rememberCoroutineScope()
    val currentOnFire by rememberUpdatedState(onFire)
    var pressed by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val background = when {
        !enabled -> colors.surfaceVariant.copy(alpha = 0.4f)
        pressed -> colors.primary
        else -> colors.surfaceVariant
    }
    val content = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.4f)
        pressed -> colors.onPrimary
        else -> colors.onSurfaceVariant
    }

    Surface(
        modifier = modifier.pointerInput(enabled, repeat) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                currentOnFire()
                val repeatJob = if (repeat) {
                    scope.launch {
                        delay(REPEAT_INITIAL_DELAY_MS)
                        while (isActive) {
                            currentOnFire()
                            delay(REPEAT_INTERVAL_MS)
                        }
                    }
                } else {
                    null
                }
                try {
                    waitForUpOrCancellation()
                } finally {
                    // 누르고 있는 중에 연결 상태가 바뀌면(enabled 변경) 이 제스처가 취소된다.
                    // 그때도 반복을 반드시 멈춘다. 안 멈추면 다시 연결됐을 때 볼륨이 끝까지 올라간다 (v0.7까지의 버그).
                    repeatJob?.cancel()
                    pressed = false
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        color = background,
        contentColor = content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(ICON_SIZE))
            } else {
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/**
 * 키보드 키 하나처럼 동작하는 버튼: 손가락이 닿으면 키 누름, 떼면 키 뗌.
 * 누르고 있을 때의 반복 입력은 PC가 처리한다 (방향키를 누르고 있으면 계속 이동).
 */
@Composable
fun KeyButton(
    label: String,
    usage: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var pressed by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.pointerInput(enabled, usage) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                pressed = true
                HidManager.keyDown(usage)
                try {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                    }
                } finally {
                    pressed = false
                    HidManager.keyUp(usage)
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        color = when {
            !enabled -> color.copy(alpha = 0.4f)
            pressed -> colors.primary
            else -> color
        },
        contentColor = when {
            !enabled -> contentColor.copy(alpha = 0.4f)
            pressed -> colors.onPrimary
            else -> contentColor
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
    }
}
