package com.example.bluewind.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bluewind.hid.HidManager
import com.example.bluewind.hid.MouseButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 트랙패드 설정값. 초기값은 실기기에서 튜닝한다. */
object TrackpadConfig {
    /** 커서 감도: 손가락 1dp 이동당 마우스 이동량 (v0.2: 2.0 → 2.2) */
    const val CURSOR_SENSITIVITY = 2.2f

    /** 스크롤 감도: 휠 1칸에 필요한 손가락 이동 dp (작을수록 빠르다) */
    const val SCROLL_DP_PER_NOTCH = 20f

    /** 스크롤 방향 반전. false = 노트북 기본값(콘텐츠가 손가락을 따라 움직임) */
    const val INVERT_SCROLL = false

    /** 이 시간 안에 손을 떼야 탭으로 본다 */
    const val TAP_TIMEOUT_MS = 250L

    /** 탭 후 이 시간 안에 다시 누르면 드래그(또는 더블클릭). 그래서 탭 클릭은 이만큼 늦게 보낸다. */
    const val TAP_DRAG_WINDOW_MS = 220L
}

private const val DEFAULT_HINT = "한 손가락: 이동 · 탭 클릭 · 탭 후 끌기\n두 손가락: 스크롤 · 탭 우클릭"

/**
 * @param movementOnly true면 커서 이동만 한다 (클릭·스크롤 없음). 프레젠테이션 레이저 포인터용.
 */
@Composable
fun Trackpad(
    modifier: Modifier = Modifier,
    movementOnly: Boolean = false,
    hint: String = DEFAULT_HINT,
) {
    val scope = rememberCoroutineScope()
    // 드래그 중에 화면이 바뀌어도 왼쪽 버튼이 눌린 채 남지 않게 한다
    DisposableEffect(Unit) {
        onDispose { HidManager.releaseMouseButtons() }
    }
    Box(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .pointerInput(movementOnly) {
                if (movementOnly) movementOnlyGestures() else trackpadGestures(scope)
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
    }
}

private enum class Mode {
    UNDECIDED, // 아직 움직이지 않음 (탭 후보)
    MOVE, // 한 손가락 커서 이동
    DRAG, // 탭 직후 다시 눌러 이동: 왼쪽 버튼 누른 채 이동
    TWO_FINGER, // 두 손가락, 아직 움직이지 않음 (우클릭 후보)
    SCROLL_VERTICAL,
    SCROLL_HORIZONTAL,
    IGNORE, // 세 손가락 이상
}

private suspend fun PointerInputScope.trackpadGestures(scope: CoroutineScope) {
    val slop = viewConfiguration.touchSlop
    val countsPerPx = TrackpadConfig.CURSOR_SENSITIVITY / density
    val notchesPerPx = 1f / (TrackpadConfig.SCROLL_DP_PER_NOTCH * density)
    val scrollSign = if (TrackpadConfig.INVERT_SCROLL) -1f else 1f

    // 탭 클릭 대기 작업. 다음 제스처가 이 시간 안에 시작되면 취소하고 드래그/더블클릭으로 처리한다.
    var pendingClick: Job? = null

    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        val downTime = firstDown.uptimeMillis
        var afterTap = pendingClick?.isActive == true
        pendingClick?.cancel()
        pendingClick = null

        var mode = Mode.UNDECIDED
        var maxPointers = 1
        var moved = false
        var travel = Offset.Zero
        var upTime = downTime

        // 앞 탭의 클릭을 미뤄둔 상태에서 드래그가 아닌 동작이 되면 그 클릭을 바로 보낸다
        fun flushTapClick() {
            if (afterTap) {
                HidManager.mouseClick(MouseButton.LEFT)
                afterTap = false
            }
        }

        while (true) {
            val event = awaitPointerEvent()
            val changes = event.changes
            val pressedCount = changes.count { it.pressed }
            if (pressedCount == 0) {
                upTime = changes.maxOf { it.uptimeMillis }
                changes.forEach { it.consume() }
                break
            }
            maxPointers = maxOf(maxPointers, pressedCount)

            // 이번 이벤트 전후로 계속 눌려 있던 손가락들의 평균 이동량
            val moving = changes.filter { it.pressed && it.previousPressed }
            val delta = if (moving.isEmpty()) {
                Offset.Zero
            } else {
                moving.fold(Offset.Zero) { acc, c -> acc + (c.position - c.previousPosition) } / moving.size.toFloat()
            }

            if (maxPointers >= 3 && mode != Mode.DRAG && mode != Mode.IGNORE) {
                flushTapClick()
                mode = Mode.IGNORE
            }

            when (mode) {
                Mode.UNDECIDED -> {
                    if (pressedCount >= 2) {
                        flushTapClick()
                        mode = Mode.TWO_FINGER
                        travel = Offset.Zero
                    } else {
                        travel += delta
                        if (travel.getDistance() > slop) {
                            moved = true
                            if (afterTap) {
                                afterTap = false
                                mode = Mode.DRAG
                                HidManager.mouseButtonDown(MouseButton.LEFT)
                            } else {
                                mode = Mode.MOVE
                            }
                        }
                    }
                }
                Mode.MOVE -> {
                    if (pressedCount >= 2) {
                        // 한 손가락으로 움직이다 두 번째 손가락을 대면 스크롤로 전환
                        mode = Mode.TWO_FINGER
                        travel = Offset.Zero
                    } else {
                        HidManager.moveMouse(delta.x * countsPerPx, delta.y * countsPerPx)
                    }
                }
                Mode.DRAG -> HidManager.moveMouse(delta.x * countsPerPx, delta.y * countsPerPx)
                Mode.TWO_FINGER -> {
                    if (pressedCount >= 2) {
                        travel += delta
                        if (travel.getDistance() > slop) {
                            moved = true
                            mode = if (abs(travel.x) > abs(travel.y)) Mode.SCROLL_HORIZONTAL else Mode.SCROLL_VERTICAL
                        }
                    }
                }
                // 두 손가락 중 하나를 떼면 남은 손가락으로는 아무것도 하지 않는다
                Mode.SCROLL_VERTICAL -> if (pressedCount >= 2) {
                    // 자연 스크롤: 손가락을 아래로 → 콘텐츠가 아래로 → 위로 스크롤(+)
                    HidManager.scrollMouse(wheel = delta.y * notchesPerPx * scrollSign, pan = 0f)
                }
                Mode.SCROLL_HORIZONTAL -> if (pressedCount >= 2) {
                    // 자연 스크롤: 손가락을 왼쪽으로 → 콘텐츠가 왼쪽으로 → 오른쪽으로 스크롤(+)
                    HidManager.scrollMouse(wheel = 0f, pan = -delta.x * notchesPerPx * scrollSign)
                }
                Mode.IGNORE -> Unit
            }
            changes.forEach { it.consume() }
        }

        // 모든 손가락을 뗌
        val quick = upTime - downTime <= TrackpadConfig.TAP_TIMEOUT_MS
        when (mode) {
            Mode.DRAG -> HidManager.mouseButtonUp(MouseButton.LEFT)
            Mode.UNDECIDED -> {
                if (afterTap) {
                    // 탭 → 다시 탭: 미뤄둔 클릭 + 이번 클릭 = 더블클릭
                    flushTapClick()
                    if (quick) HidManager.mouseClick(MouseButton.LEFT)
                } else if (quick) {
                    pendingClick = scope.launch {
                        delay(TrackpadConfig.TAP_DRAG_WINDOW_MS)
                        HidManager.mouseClick(MouseButton.LEFT)
                    }
                }
            }
            Mode.TWO_FINGER -> if (quick && !moved && maxPointers == 2) HidManager.mouseClick(MouseButton.RIGHT)
            else -> flushTapClick()
        }
    }
}

/** 한 손가락 이동만 커서 이동으로 보낸다. 탭·두 손가락은 무시 (슬라이드쇼에서 클릭하면 슬라이드가 넘어가므로). */
private suspend fun PointerInputScope.movementOnlyGestures() {
    val countsPerPx = TrackpadConfig.CURSOR_SENSITIVITY / density
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            val finger = pressed.singleOrNull()
            if (finger != null && finger.previousPressed) {
                val delta = finger.position - finger.previousPosition
                HidManager.moveMouse(delta.x * countsPerPx, delta.y * countsPerPx)
            }
            event.changes.forEach { it.consume() }
        }
    }
}
