package com.example.bluewind.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// 누르고 있을 때 반복 전송 간격. 실기기에서 튜닝한다.
private const val REPEAT_INITIAL_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 150L

/**
 * 손가락이 닿는 순간 [onFire]를 한 번 호출한다.
 * [repeat]이면 누르고 있는 동안 일정 간격으로 계속 호출한다.
 */
@Composable
fun HoldButton(
    label: String,
    onFire: () -> Unit,
    modifier: Modifier = Modifier,
    repeat: Boolean = false,
    enabled: Boolean = true,
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
                waitForUpOrCancellation()
                repeatJob?.cancel()
                pressed = false
            }
        },
        shape = RoundedCornerShape(12.dp),
        color = background,
        contentColor = content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
