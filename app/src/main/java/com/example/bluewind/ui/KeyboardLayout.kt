package com.example.bluewind.ui

import com.example.bluewind.hid.KeyUsage

/**
 * 키 하나.
 * @param label 기본 라벨 (영문/기호)
 * @param sub 보조 라벨 (두벌식 한글 또는 Shift 기호)
 * @param usage 보낼 HID usage. null이면 Fn(앱 내부 키)
 * @param fnLabel, fnUsage Fn 레이어에서 바뀌는 라벨과 usage. null이면 Fn 레이어에서도 그대로
 * @param isLetter 알파벳 키. sub는 두벌식 한글. 한/영·CapsLock·Shift 상태에 따라 라벨을 바꾼다
 * @param hangulShift Shift를 누르면 바뀌는 한글 (ㄲ, ㅆ, ㅒ 등)
 */
data class KeyDef(
    val label: String,
    val usage: Int?,
    val sub: String? = null,
    val weight: Float = 1f,
    val fnLabel: String? = null,
    val fnUsage: Int? = null,
    val isLetter: Boolean = false,
    val hangulShift: String? = null,
) {
    val isFn: Boolean get() = usage == null

    /** Shift를 누르고 있는 동안 라벨을 Shift 글자로 보여준다 */
    val isShift: Boolean get() = usage == KeyUsage.LEFT_SHIFT || usage == KeyUsage.RIGHT_SHIFT
}

private fun letter(c: Char, hangul: String, hangulShift: String? = null) =
    KeyDef(c.toString(), KeyUsage.letter(c), sub = hangul, isLetter = true, hangulShift = hangulShift)

private fun digit(c: Char, shifted: String, fn: Int) =
    KeyDef(c.toString(), KeyUsage.digit(c), sub = shifted, fnLabel = "F$fn", fnUsage = KeyUsage.f(fn))

// 상단 줄(닫기 버튼 옆)에 따로 두는 키 (v0.6)
val ESC_KEY = KeyDef("Esc", KeyUsage.ESC)
val FN_KEY = KeyDef("Fn", null)
val HANJA_KEY = KeyDef("한자", KeyUsage.RIGHT_CTRL)

// 각 행 weight 합계는 15.5로 맞춘다 (행 끝이 나란하게)
val KEYBOARD_ROWS: List<List<KeyDef>> = listOf(
    listOf(
        KeyDef("`", KeyUsage.GRAVE, sub = "~", fnLabel = "PrtSc", fnUsage = KeyUsage.PRINT_SCREEN),
        digit('1', "!", 1),
        digit('2', "@", 2),
        digit('3', "#", 3),
        digit('4', "$", 4),
        digit('5', "%", 5),
        digit('6', "^", 6),
        digit('7', "&", 7),
        digit('8', "*", 8),
        digit('9', "(", 9),
        digit('0', ")", 10),
        KeyDef("-", KeyUsage.MINUS, sub = "_", fnLabel = "F11", fnUsage = KeyUsage.f(11)),
        KeyDef("=", KeyUsage.EQUAL, sub = "+", fnLabel = "F12", fnUsage = KeyUsage.f(12)),
        KeyDef("⌫", KeyUsage.BACKSPACE, weight = 2.5f, fnLabel = "Del", fnUsage = KeyUsage.DELETE),
    ),
    listOf(
        KeyDef("Tab", KeyUsage.TAB, weight = 1.75f),
        letter('Q', "ㅂ", "ㅃ"),
        letter('W', "ㅈ", "ㅉ"),
        letter('E', "ㄷ", "ㄸ"),
        letter('R', "ㄱ", "ㄲ"),
        letter('T', "ㅅ", "ㅆ"),
        letter('Y', "ㅛ"),
        letter('U', "ㅕ"),
        letter('I', "ㅑ"),
        letter('O', "ㅐ", "ㅒ"),
        letter('P', "ㅔ", "ㅖ"),
        KeyDef("[", KeyUsage.LEFT_BRACKET, sub = "{"),
        KeyDef("]", KeyUsage.RIGHT_BRACKET, sub = "}"),
        KeyDef("\\", KeyUsage.BACKSLASH, sub = "|", weight = 1.75f, fnLabel = "Ins", fnUsage = KeyUsage.INSERT),
    ),
    listOf(
        KeyDef("Caps", KeyUsage.CAPS_LOCK, weight = 2f),
        letter('A', "ㅁ"),
        letter('S', "ㄴ"),
        letter('D', "ㅇ"),
        letter('F', "ㄹ"),
        letter('G', "ㅎ"),
        letter('H', "ㅗ"),
        letter('J', "ㅓ"),
        letter('K', "ㅏ"),
        letter('L', "ㅣ"),
        KeyDef(";", KeyUsage.SEMICOLON, sub = ":"),
        KeyDef("'", KeyUsage.APOSTROPHE, sub = "\""),
        KeyDef("Enter", KeyUsage.ENTER, weight = 2.5f),
    ),
    listOf(
        KeyDef("Shift", KeyUsage.LEFT_SHIFT, weight = 2.5f),
        letter('Z', "ㅋ"),
        letter('X', "ㅌ"),
        letter('C', "ㅊ"),
        letter('V', "ㅍ"),
        letter('B', "ㅠ"),
        letter('N', "ㅜ"),
        letter('M', "ㅡ"),
        KeyDef(",", KeyUsage.COMMA, sub = "<"),
        KeyDef(".", KeyUsage.PERIOD, sub = ">"),
        KeyDef("/", KeyUsage.SLASH, sub = "?"),
        KeyDef("Shift", KeyUsage.RIGHT_SHIFT, weight = 3f),
    ),
    listOf(
        KeyDef("Ctrl", KeyUsage.LEFT_CTRL, weight = 1.5f),
        KeyDef("Win", KeyUsage.LEFT_GUI, weight = 1.25f),
        KeyDef("Alt", KeyUsage.LEFT_ALT, weight = 1.25f),
        KeyDef("Space", KeyUsage.SPACE, weight = 6f),
        KeyDef("한/영", KeyUsage.RIGHT_ALT, weight = 1.5f),
        KeyDef("←", KeyUsage.LEFT, fnLabel = "Home", fnUsage = KeyUsage.HOME),
        KeyDef("↑", KeyUsage.UP, fnLabel = "PgUp", fnUsage = KeyUsage.PAGE_UP),
        KeyDef("↓", KeyUsage.DOWN, fnLabel = "PgDn", fnUsage = KeyUsage.PAGE_DOWN),
        KeyDef("→", KeyUsage.RIGHT, fnLabel = "End", fnUsage = KeyUsage.END),
    ),
)

/**
 * 텐키 (Nums 모드). 왼쪽 3열 × 5행 + 오른쪽 1열(⌫, +, Enter).
 * 숫자와 '.'은 PC의 NumLock 상태와 무관하게 숫자가 나오도록 위쪽 숫자줄 키를 보낸다.
 */
val NUMPAD_LEFT_ROWS: List<List<KeyDef>> = listOf(
    listOf(KeyDef("/", KeyUsage.KP_SLASH), KeyDef("*", KeyUsage.KP_ASTERISK), KeyDef("-", KeyUsage.KP_MINUS)),
    listOf(numKey('7'), numKey('8'), numKey('9')),
    listOf(numKey('4'), numKey('5'), numKey('6')),
    listOf(numKey('1'), numKey('2'), numKey('3')),
    listOf(KeyDef("0", KeyUsage.digit('0'), weight = 2f), KeyDef(".", KeyUsage.PERIOD)),
)

/** 오른쪽 열. weight는 세로 비율 */
val NUMPAD_RIGHT_COLUMN: List<KeyDef> = listOf(
    KeyDef("⌫", KeyUsage.BACKSPACE, weight = 1f),
    KeyDef("+", KeyUsage.KP_PLUS, weight = 2f),
    KeyDef("Enter", KeyUsage.KP_ENTER, weight = 2f),
)

private fun numKey(c: Char) = KeyDef(c.toString(), KeyUsage.digit(c))
