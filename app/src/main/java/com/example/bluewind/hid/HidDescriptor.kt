package com.example.bluewind.hid

/**
 * 키보드 + 마우스 + Consumer(미디어키)를 하나의 Report Descriptor에 Report ID로 나눠 담는다.
 *
 * Windows는 페어링 시점의 descriptor를 기억한다. 여기를 바꾸면 Windows에서 장치를 제거하고 다시 페어링해야 한다.
 */
object HidDescriptor {
    const val REPORT_ID_KEYBOARD: Byte = 1
    const val REPORT_ID_MOUSE: Byte = 2
    const val REPORT_ID_CONSUMER: Byte = 3

    // Report ID를 뺀 페이로드 크기
    const val KEYBOARD_REPORT_SIZE = 8 // modifier, reserved, key1~key6
    const val MOUSE_REPORT_SIZE = 5 // buttons, X, Y, Wheel, AC Pan
    const val CONSUMER_REPORT_SIZE = 2 // 16비트 usage 1개

    fun inputReportSize(reportId: Byte): Int? = when (reportId) {
        REPORT_ID_KEYBOARD -> KEYBOARD_REPORT_SIZE
        REPORT_ID_MOUSE -> MOUSE_REPORT_SIZE
        REPORT_ID_CONSUMER -> CONSUMER_REPORT_SIZE
        else -> null
    }

    val REPORT_MAP: ByteArray = bytes(
        // ---- Report ID 1: 키보드 ----
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x06,       // Usage (Keyboard)
        0xA1, 0x01,       // Collection (Application)
        0x85, 0x01,       //   Report ID (1)
        0x05, 0x07,       //   Usage Page (Keyboard/Keypad)
        0x19, 0xE0,       //   Usage Minimum (Left Control)
        0x29, 0xE7,       //   Usage Maximum (Right GUI)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x01,       //   Logical Maximum (1)
        0x75, 0x01,       //   Report Size (1)
        0x95, 0x08,       //   Report Count (8)
        0x81, 0x02,       //   Input (Data, Var, Abs)        modifier 8비트
        0x75, 0x08,       //   Report Size (8)
        0x95, 0x01,       //   Report Count (1)
        0x81, 0x01,       //   Input (Const)                 reserved
        0x05, 0x08,       //   Usage Page (LEDs)
        0x19, 0x01,       //   Usage Minimum (Num Lock)
        0x29, 0x05,       //   Usage Maximum (Kana)
        0x75, 0x01,       //   Report Size (1)
        0x95, 0x05,       //   Report Count (5)
        0x91, 0x02,       //   Output (Data, Var, Abs)       LED 5비트
        0x75, 0x03,       //   Report Size (3)
        0x95, 0x01,       //   Report Count (1)
        0x91, 0x01,       //   Output (Const)                패딩 3비트
        // 키 배열은 0x00~0xFF 전체를 허용한다. 지금은 0x65 이하만 쓰지만
        // 나중에 LANG1(0x90) 같은 키가 필요해져도 descriptor를 바꾸지 않아도 된다.
        0x05, 0x07,       //   Usage Page (Keyboard/Keypad)
        0x19, 0x00,       //   Usage Minimum (0)
        0x2A, 0xFF, 0x00, //   Usage Maximum (255)
        0x15, 0x00,       //   Logical Minimum (0)
        0x26, 0xFF, 0x00, //   Logical Maximum (255)
        0x75, 0x08,       //   Report Size (8)
        0x95, 0x06,       //   Report Count (6)
        0x81, 0x00,       //   Input (Data, Array, Abs)      key1~key6
        0xC0,             // End Collection

        // ---- Report ID 2: 마우스 ----
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x02,       // Usage (Mouse)
        0xA1, 0x01,       // Collection (Application)
        0x85, 0x02,       //   Report ID (2)
        0x09, 0x01,       //   Usage (Pointer)
        0xA1, 0x00,       //   Collection (Physical)
        0x05, 0x09,       //     Usage Page (Button)
        0x19, 0x01,       //     Usage Minimum (Button 1)
        0x29, 0x03,       //     Usage Maximum (Button 3)
        0x15, 0x00,       //     Logical Minimum (0)
        0x25, 0x01,       //     Logical Maximum (1)
        0x75, 0x01,       //     Report Size (1)
        0x95, 0x03,       //     Report Count (3)
        0x81, 0x02,       //     Input (Data, Var, Abs)      버튼 3비트
        0x75, 0x05,       //     Report Size (5)
        0x95, 0x01,       //     Report Count (1)
        0x81, 0x01,       //     Input (Const)               패딩 5비트
        0x05, 0x01,       //     Usage Page (Generic Desktop)
        0x09, 0x30,       //     Usage (X)
        0x09, 0x31,       //     Usage (Y)
        0x09, 0x38,       //     Usage (Wheel)
        0x15, 0x81,       //     Logical Minimum (-127)
        0x25, 0x7F,       //     Logical Maximum (127)
        0x75, 0x08,       //     Report Size (8)
        0x95, 0x03,       //     Report Count (3)
        0x81, 0x06,       //     Input (Data, Var, Rel)      X, Y, Wheel
        0x05, 0x0C,       //     Usage Page (Consumer)
        0x0A, 0x38, 0x02, //     Usage (AC Pan)
        0x95, 0x01,       //     Report Count (1)
        0x81, 0x06,       //     Input (Data, Var, Rel)      가로 스크롤
        0xC0,             //   End Collection
        0xC0,             // End Collection

        // ---- Report ID 3: Consumer Control (볼륨 등) ----
        0x05, 0x0C,       // Usage Page (Consumer)
        0x09, 0x01,       // Usage (Consumer Control)
        0xA1, 0x01,       // Collection (Application)
        0x85, 0x03,       //   Report ID (3)
        0x19, 0x00,       //   Usage Minimum (0)
        0x2A, 0xFF, 0x03, //   Usage Maximum (0x03FF)
        0x15, 0x00,       //   Logical Minimum (0)
        0x26, 0xFF, 0x03, //   Logical Maximum (0x03FF)
        0x75, 0x10,       //   Report Size (16)
        0x95, 0x01,       //   Report Count (1)
        0x81, 0x00,       //   Input (Data, Array, Abs)    usage 1개 (뗌 = 0)
        0xC0,             // End Collection
    )

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
