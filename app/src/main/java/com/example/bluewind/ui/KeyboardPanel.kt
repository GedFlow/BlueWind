package com.example.bluewind.ui

import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluewind.hid.HidManager
import com.example.bluewind.hid.KeyUsage

// Shift를 이 시간 안에 두 번 탭하면 고정
private const val SHIFT_DOUBLE_TAP_MS = 400L

private enum class ShiftMode { OFF, ONE_SHOT, LOCKED }

/**
 * 키보드 패널을 닫았다 열어도 유지되는 표시 상태.
 * PC의 한/영 상태는 알 수 없으므로, 앱의 한/영 키를 누를 때마다 바꾸는 추정값이다.
 * 어긋나면 사용자가 상단 "표시" 버튼으로 맞춘다 (PC로는 아무것도 보내지 않음).
 */
private object KeyboardDisplay {
    var hangul by mutableStateOf(false)
}

/**
 * 앱이 직접 그린 키보드. 터치 DOWN = 키 누름, 터치 UP = 키 뗌.
 * 각 키가 손가락을 따로 받으므로 여러 키를 동시에 누를 수 있다 (Ctrl+C, Alt+Tab).
 * 반복 입력은 PC가 처리하므로 앱은 반복 전송하지 않는다.
 *
 * Shift는 토글: 탭 = 다음 키 한 번만, 두 번 탭 = 고정, 다시 탭 = 해제. 누른 채로 다른 키를 눌러도 된다.
 * Shift 자체는 PC로 바로 보내지 않고, 다른 키를 누를 때 Shift+키로 함께 보낸다.
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
    val capsLock by HidManager.capsLock.collectAsStateWithLifecycle()
    val hangul = KeyboardDisplay.hangul

    // 키를 누를 때 아주 약한 진동
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    val keyTick = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } else {
            VibrationEffect.createOneShot(10, 40)
        }
    }

    // Fn: 누르고 있는 동안 레이어 전환. 다른 키 없이 탭만 하면 고정/해제.
    var fnHeld by remember { mutableStateOf(false) }
    var fnLatched by remember { mutableStateOf(false) }
    var usedWhileFnHeld by remember { mutableStateOf(false) }
    val fnActive = fnHeld || fnLatched

    var shiftMode by remember { mutableStateOf(ShiftMode.OFF) }
    var shiftHeldCount by remember { mutableIntStateOf(0) }
    var usedWhileShiftHeld by remember { mutableStateOf(false) }
    var lastShiftTapTime by remember { mutableLongStateOf(0L) }
    val shiftActive = shiftHeldCount > 0 || shiftMode != ShiftMode.OFF

    /** 키를 누름. PC로 누른 usage 목록을 돌려준다 (뗄 때 역순으로 뗀다). */
    fun press(def: KeyDef): List<Int> {
        vibrator?.vibrate(keyTick)
        if (def.isFn) {
            fnHeld = true
            usedWhileFnHeld = false
            return emptyList()
        }
        if (def.isShift) {
            if (shiftHeldCount == 0) usedWhileShiftHeld = false
            shiftHeldCount++
            return emptyList()
        }
        if (fnHeld) usedWhileFnHeld = true
        if (shiftHeldCount > 0) usedWhileShiftHeld = true

        // 누를 때 정한 usage를 뗄 때도 쓴다 (도중에 Fn·Shift 상태가 바뀌어도 같은 키를 뗀다)
        val usage = if (fnActive && def.fnUsage != null) def.fnUsage else def.usage!!
        val withShift = shiftActive && !KeyUsage.isModifier(usage)
        val pressed = if (withShift) listOf(KeyUsage.LEFT_SHIFT, usage) else listOf(usage)
        pressed.forEach { HidManager.keyDown(it) }

        // 한 번만 Shift는 이 키에 쓰고 해제
        if (withShift && shiftMode == ShiftMode.ONE_SHOT) shiftMode = ShiftMode.OFF
        // 한/영 키를 누르면 표시 언어를 바꾼다 (PC 상태 추정)
        if (usage == KeyUsage.RIGHT_ALT && connected) KeyboardDisplay.hangul = !KeyboardDisplay.hangul
        return pressed
    }

    fun release(def: KeyDef, pressed: List<Int>) {
        when {
            def.isFn -> {
                fnHeld = false
                if (!usedWhileFnHeld) fnLatched = !fnLatched
            }
            def.isShift -> {
                shiftHeldCount = (shiftHeldCount - 1).coerceAtLeast(0)
                if (shiftHeldCount == 0 && !usedWhileShiftHeld) {
                    // 다른 키 없이 탭만 함: 토글
                    val now = SystemClock.uptimeMillis()
                    shiftMode = when (shiftMode) {
                        ShiftMode.OFF -> ShiftMode.ONE_SHOT
                        ShiftMode.ONE_SHOT ->
                            if (now - lastShiftTapTime <= SHIFT_DOUBLE_TAP_MS) ShiftMode.LOCKED else ShiftMode.OFF
                        ShiftMode.LOCKED -> ShiftMode.OFF
                    }
                    lastShiftTapTime = now
                }
            }
            else -> pressed.asReversed().forEach { HidManager.keyUp(it) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // 닫기 버튼은 메인 화면의 키보드 버튼과 같은 자리
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CornerButton(label = "닫기", onClick = onClose)
            PanelButton(
                label = if (hangul) "표시: 한글" else "표시: 영문",
                sub = "PC와 다르면 탭",
                onClick = { KeyboardDisplay.hangul = !KeyboardDisplay.hangul },
                modifier = Modifier
                    .width(120.dp)
                    .height(CORNER_BUTTON_HEIGHT),
                labelStyle = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!connected) {
                StatusText("PC에 연결되지 않았습니다", Color(0xFFE57373))
            }
            if (shiftMode == ShiftMode.LOCKED) StatusText("Shift 고정", MaterialTheme.colorScheme.tertiary)
            if (fnLatched) StatusText("Fn 고정", MaterialTheme.colorScheme.tertiary)
            if (capsLock) StatusText("Caps Lock", MaterialTheme.colorScheme.primary)
        }
        KEYBOARD_ROWS.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { def ->
                    val showFnLayer = fnActive && def.fnLabel != null
                    val (main, sub) = keyLabels(def, showFnLayer, shiftActive, capsLock, hangul)
                    val highlight = when {
                        def.isFn -> if (fnLatched) Highlight.STRONG else if (fnActive) Highlight.SOFT else Highlight.NONE
                        def.isShift -> when {
                            shiftMode == ShiftMode.LOCKED -> Highlight.STRONG
                            shiftActive -> Highlight.SOFT
                            else -> Highlight.NONE
                        }
                        else -> Highlight.NONE
                    }
                    Key(
                        def = def,
                        mainLabel = if (def.isShift && shiftMode == ShiftMode.LOCKED) "Shift 고정" else main,
                        subLabel = sub,
                        highlight = highlight,
                        fnLayerLabel = showFnLayer,
                        onPress = ::press,
                        onRelease = ::release,
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
private fun StatusText(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = color)
}

/**
 * 키에 표시할 (큰 라벨, 작은 라벨).
 * - 알파벳 키: 현재 표시 언어(한/영)를 크게, 다른 언어를 작게. 영문은 CapsLock·Shift에 따라 대소문자.
 * - 기호 키: Shift가 켜져 있으면 Shift 기호를 크게.
 */
private fun keyLabels(
    def: KeyDef,
    showFnLayer: Boolean,
    shiftActive: Boolean,
    capsLock: Boolean,
    hangul: Boolean,
): Pair<String, String?> {
    if (showFnLayer) return def.fnLabel!! to null
    if (def.isLetter) {
        val upper = capsLock != shiftActive // Windows: CapsLock 상태에서 Shift를 누르면 소문자
        val latin = if (upper) def.label.uppercase() else def.label.lowercase()
        val jamo = if (shiftActive && def.hangulShift != null) def.hangulShift else def.sub!!
        return if (hangul) jamo to latin else latin to jamo
    }
    if (def.sub != null) {
        return if (shiftActive) def.sub to def.label else def.label to def.sub
    }
    return def.label to null
}

private enum class Highlight { NONE, SOFT, STRONG }

@Composable
private fun Key(
    def: KeyDef,
    mainLabel: String,
    subLabel: String?,
    highlight: Highlight,
    fnLayerLabel: Boolean,
    onPress: (KeyDef) -> List<Int>,
    onRelease: (KeyDef, List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)

    val colors = MaterialTheme.colorScheme
    val background = when {
        pressed -> colors.primary
        highlight == Highlight.STRONG -> colors.tertiary
        highlight == Highlight.SOFT -> colors.tertiaryContainer
        else -> colors.surfaceVariant
    }
    val content = when {
        pressed -> colors.onPrimary
        highlight == Highlight.STRONG -> colors.onTertiary
        highlight == Highlight.SOFT -> colors.onTertiaryContainer
        fnLayerLabel -> colors.tertiary
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
                    val sent = currentOnPress(def)
                    try {
                        // 실물 키보드처럼 손가락을 뗄 때까지 누른 상태 유지 (키 밖으로 조금 밀려도 유지)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                        }
                    } finally {
                        pressed = false
                        currentOnRelease(def, sent)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                mainLabel,
                color = content,
                fontSize = if (mainLabel.length > 2) 14.sp else 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            if (subLabel != null) {
                // 현재 쓰지 않는 쪽은 작고 흐리게
                Text(subLabel, color = content.copy(alpha = 0.4f), fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}
