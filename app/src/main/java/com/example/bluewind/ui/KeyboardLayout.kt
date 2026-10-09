package com.example.bluewind.ui

import com.example.bluewind.hid.KeyUsage

/**
 * 키 하나.
 * @param label 기본 라벨 (영문/기호)
 * @param sub 보조 라벨 (두벌식 한글 또는 Shift 기호)
 * @param usage 보낼 HID usage. null이면 Fn(앱 내부 키)
 * @param fnLabel, fnUsage Fn 레이어에서 바뀌는 라벨과 usage. null이면 Fn 레이어에서도 그대로
 */
data class KeyDef(
    val label: String,
    val usage: Int?,
    val sub: String? = null,
    val weight: Float = 1f,
    val fnLabel: String? = null,
    val fnUsage: Int? = null,
) {
    val isFn: Boolean get() = usage == null
}

private fun letter(c: Char, hangul: String) = KeyDef(c.toString(), KeyUsage.letter(c), sub = hangul)

private fun digit(c: Char, shifted: String, fn: Int) =
    KeyDef(c.toString(), KeyUsage.digit(c), sub = shifted, fnLabel = "F$fn", fnUsage = KeyUsage.f(fn))

// 각 행 weight 합계는 15.5로 맞춘다 (행 끝이 나란하게)
val KEYBOARD_ROWS: List<List<KeyDef>> = listOf(
    listOf(
        KeyDef("Esc", KeyUsage.ESC),
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
        KeyDef("⌫", KeyUsage.BACKSPACE, weight = 1.5f, fnLabel = "Del", fnUsage = KeyUsage.DELETE),
    ),
    listOf(
        KeyDef("Tab", KeyUsage.TAB, weight = 1.75f),
        letter('Q', "ㅂ"),
        letter('W', "ㅈ"),
        letter('E', "ㄷ"),
        letter('R', "ㄱ"),
        letter('T', "ㅅ"),
        letter('Y', "ㅛ"),
        letter('U', "ㅕ"),
        letter('I', "ㅑ"),
        letter('O', "ㅐ"),
        letter('P', "ㅔ"),
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
        KeyDef("Fn", null),
        KeyDef("Ctrl", KeyUsage.LEFT_CTRL, weight = 1.25f),
        KeyDef("Win", KeyUsage.LEFT_GUI, weight = 1.25f),
        KeyDef("Alt", KeyUsage.LEFT_ALT, weight = 1.25f),
        KeyDef("Space", KeyUsage.SPACE, weight = 4.25f),
        KeyDef("한/영", KeyUsage.RIGHT_ALT, weight = 1.25f),
        KeyDef("한자", KeyUsage.RIGHT_CTRL, weight = 1.25f),
        KeyDef("←", KeyUsage.LEFT, fnLabel = "Home", fnUsage = KeyUsage.HOME),
        KeyDef("↑", KeyUsage.UP, fnLabel = "PgUp", fnUsage = KeyUsage.PAGE_UP),
        KeyDef("↓", KeyUsage.DOWN, fnLabel = "PgDn", fnUsage = KeyUsage.PAGE_DOWN),
        KeyDef("→", KeyUsage.RIGHT, fnLabel = "End", fnUsage = KeyUsage.END),
    ),
)
