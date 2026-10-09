package com.example.bluewind.hid

/** Consumer Page (0x0C) usage */
object ConsumerUsage {
    const val VOLUME_UP = 0xE9
    const val VOLUME_DOWN = 0xEA
    const val MUTE = 0xE2

    // 화면 밝기. Windows가 밝기를 조절할 수 있는 내장 디스플레이(노트북)에서만 동작할 수 있다.
    const val BRIGHTNESS_UP = 0x6F
    const val BRIGHTNESS_DOWN = 0x70
}

/** 마우스 리포트 버튼 비트 */
object MouseButton {
    const val LEFT = 0x01
    const val RIGHT = 0x02
    const val MIDDLE = 0x04
}

/** Keyboard/Keypad Page (0x07) usage */
object KeyUsage {
    fun letter(c: Char): Int = 0x04 + (c.uppercaseChar() - 'A') // A=0x04 … Z=0x1D

    /** '1'..'9' = 0x1E..0x26, '0' = 0x27 */
    fun digit(c: Char): Int = if (c == '0') 0x27 else 0x1E + (c - '1')

    /** n = 1..12 */
    fun f(n: Int): Int = 0x3A + (n - 1)

    const val ENTER = 0x28
    const val ESC = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val MINUS = 0x2D
    const val EQUAL = 0x2E
    const val LEFT_BRACKET = 0x2F
    const val RIGHT_BRACKET = 0x30
    const val BACKSLASH = 0x31
    const val SEMICOLON = 0x33
    const val APOSTROPHE = 0x34
    const val GRAVE = 0x35
    const val COMMA = 0x36
    const val PERIOD = 0x37
    const val SLASH = 0x38
    const val CAPS_LOCK = 0x39

    const val PRINT_SCREEN = 0x46
    const val INSERT = 0x49
    const val HOME = 0x4A
    const val PAGE_UP = 0x4B
    const val DELETE = 0x4C
    const val END = 0x4D
    const val PAGE_DOWN = 0x4E
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52

    // 키패드 연산자·Enter (NumLock과 무관하게 동작)
    const val KP_SLASH = 0x54
    const val KP_ASTERISK = 0x55
    const val KP_MINUS = 0x56
    const val KP_PLUS = 0x57
    const val KP_ENTER = 0x58

    // Modifier (0xE0~0xE7). 리포트에서는 첫 바이트의 비트로 보낸다.
    const val LEFT_CTRL = 0xE0
    const val LEFT_SHIFT = 0xE1
    const val LEFT_ALT = 0xE2
    const val LEFT_GUI = 0xE3
    const val RIGHT_CTRL = 0xE4 // 한자
    const val RIGHT_SHIFT = 0xE5
    const val RIGHT_ALT = 0xE6 // 한/영
    const val RIGHT_GUI = 0xE7

    fun isModifier(usage: Int): Boolean = usage in LEFT_CTRL..RIGHT_GUI

    /** LCtrl 0x01, LShift 0x02, LAlt 0x04, LGUI 0x08, RCtrl 0x10, RShift 0x20, RAlt 0x40, RGUI 0x80 */
    fun modifierBit(usage: Int): Int = 1 shl (usage - LEFT_CTRL)
}
