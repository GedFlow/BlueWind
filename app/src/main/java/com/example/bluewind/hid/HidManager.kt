package com.example.bluewind.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.example.bluewind.TAG
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 블루투스 HID 장치 등록·연결을 관리하는 앱 수준 싱글톤.
 * Activity가 다시 만들어져도 등록과 연결이 유지되도록 Activity 밖에 둔다.
 *
 * 권한: start()는 BLUETOOTH_CONNECT 권한을 확인한 뒤에만 호출한다.
 */
@SuppressLint("MissingPermission")
object HidManager {

    enum class Status {
        NOT_STARTED,
        BLUETOOTH_OFF,
        NOT_SUPPORTED,
        REGISTERING,
        REGISTER_FAILED,
        READY, // 등록됨, PC 미연결
        CONNECTING,
        CONNECTED,
        DISCONNECTING;

        val isRegistered: Boolean
            get() = this == READY || this == CONNECTING || this == CONNECTED || this == DISCONNECTING
    }

    data class PairedDevice(
        val device: BluetoothDevice,
        val name: String,
        val address: String,
        val isComputer: Boolean,
    )

    data class State(
        val status: Status = Status.NOT_STARTED,
        val host: PairedDevice? = null,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val pairedDevices: StateFlow<List<PairedDevice>> = _pairedDevices.asStateFlow()

    // PC의 CapsLock 상태. PC가 보내는 키보드 LED 리포트로 갱신한다.
    private val _capsLock = MutableStateFlow(false)
    val capsLock: StateFlow<Boolean> = _capsLock.asStateFlow()

    // HID 콜백 수신과 리포트 전송을 모두 이 한 스레드에서 순서대로 처리한다
    private val hidExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    // 마우스 이동·스크롤을 모아서 보내는 간격 (리포트 폭주 방지). 실기기에서 튜닝한다.
    private const val MOUSE_REPORT_INTERVAL_MS = 10L

    private lateinit var appContext: Context
    private var adapter: BluetoothAdapter? = null
    private var proxyRequested = false

    @Volatile private var hidDevice: BluetoothHidDevice? = null
    @Volatile private var started = false

    private val sdpSettings by lazy {
        BluetoothHidDeviceAppSdpSettings(
            "BlueWind",
            "BlueWind keyboard and mouse",
            "BlueWind",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptor.REPORT_MAP,
        )
    }

    @MainThread
    fun start(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        Log.i(TAG, "HidManager start")

        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            _state.value = State(Status.NOT_SUPPORTED, message = "이 기기에서 블루투스를 찾을 수 없습니다")
            return
        }
        this.adapter = adapter

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        // 블루투스 시스템 브로드캐스트는 블루투스 프로세스가 보낸다. NOT_EXPORTED면 못 받을 수 있어 EXPORTED로 등록한다.
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        refreshBondedDevices()
        if (adapter.isEnabled) {
            requestProxy()
        } else {
            _state.value = State(Status.BLUETOOTH_OFF)
        }
    }

    /** 앱 종료 시 호출. HID 등록을 해제한다. */
    @MainThread
    fun stop() {
        if (!started) return
        started = false
        Log.i(TAG, "HidManager stop → unregisterApp")
        hidDevice?.unregisterApp()
        closeProxy()
        runCatching { appContext.unregisterReceiver(receiver) }
        _state.value = State()
        _pairedDevices.value = emptyList()
    }

    /** 등록 실패 후 다시 시도 (예: 다른 HID 앱을 강제 종료한 뒤) */
    @MainThread
    fun retry() {
        if (!started) return
        Log.i(TAG, "retry")
        if (hidDevice != null) registerApp() else requestProxy()
    }

    @MainThread
    fun connect(target: PairedDevice) {
        val hid = hidDevice ?: return
        val ok = hid.connect(target.device)
        Log.i(TAG, "connect(${target.describe()}) returned $ok")
        if (!ok) _state.update { it.copy(message = "연결 요청 실패: ${target.name}") }
    }

    @MainThread
    fun disconnect() {
        val hid = hidDevice ?: return
        val host = _state.value.host ?: return
        val ok = hid.disconnect(host.device)
        Log.i(TAG, "disconnect(${host.describe()}) returned $ok")
    }

    @MainThread
    fun refreshBondedDevices() {
        val adapter = adapter ?: return
        if (!adapter.isEnabled) {
            _pairedDevices.value = emptyList()
            return
        }
        _pairedDevices.value = adapter.bondedDevices.orEmpty()
            .map { it.toPaired() }
            .sortedWith(compareByDescending<PairedDevice> { it.isComputer }.thenBy { it.name.lowercase() })
    }

    /** Windows에서 찾을 때 보이는 이 폰의 블루투스 이름 */
    fun phoneName(): String? = adapter?.name

    // ---- 리포트 전송 ----
    // 모든 전송은 hidExecutor 한 스레드에서 순서대로 처리한다.

    /** Consumer 키(볼륨 등) 한 번: 누름 → 뗌을 한 묶음으로 보낸다. */
    fun sendConsumerClick(usage: Int) {
        hidExecutor.execute {
            val host = connectedHost() ?: return@execute
            val press = byteArrayOf((usage and 0xFF).toByte(), ((usage shr 8) and 0xFF).toByte())
            sendReport(host, HidDescriptor.REPORT_ID_CONSUMER, press)
            sendReport(host, HidDescriptor.REPORT_ID_CONSUMER, ByteArray(HidDescriptor.CONSUMER_REPORT_SIZE))
        }
    }

    // ---- 키보드 ----
    // usage별 누름 횟수 (hidExecutor 스레드에서만 접근). 누른 순서를 유지해 앞의 6개를 보낸다.
    private val keyPressCounts = LinkedHashMap<Int, Int>()

    fun keyDown(usage: Int) {
        hidExecutor.execute {
            keyPressCounts[usage] = (keyPressCounts[usage] ?: 0) + 1
            val sent = sendKeyboardState()
            // LED 리포트가 오기 전에 라벨을 먼저 바꾼다. LED 리포트가 오면 그 값으로 맞춰진다.
            if (sent && usage == KeyUsage.CAPS_LOCK) _capsLock.value = !_capsLock.value
        }
    }

    /** 단축키 한 번: 모두 누른 뒤 역순으로 뗀다. 예) keyTap(LEFT_SHIFT, F5) */
    fun keyTap(vararg usages: Int) {
        hidExecutor.execute {
            for (usage in usages) {
                keyPressCounts[usage] = (keyPressCounts[usage] ?: 0) + 1
                sendKeyboardState()
            }
            for (usage in usages.reversed()) {
                val count = keyPressCounts[usage] ?: continue
                if (count <= 1) keyPressCounts.remove(usage) else keyPressCounts[usage] = count - 1
                sendKeyboardState()
            }
        }
    }

    fun keyUp(usage: Int) {
        hidExecutor.execute {
            val count = keyPressCounts[usage] ?: return@execute
            if (count <= 1) keyPressCounts.remove(usage) else keyPressCounts[usage] = count - 1
            sendKeyboardState()
        }
    }

    /** 키보드 패널을 닫을 때 등: 눌린 키가 PC에 남지 않도록 모두 뗀다. */
    fun releaseAllKeys() {
        hidExecutor.execute {
            if (keyPressCounts.isEmpty()) return@execute
            keyPressCounts.clear()
            sendKeyboardState()
        }
    }

    private fun sendKeyboardState(): Boolean {
        val host = connectedHost() ?: return false
        val report = ByteArray(HidDescriptor.KEYBOARD_REPORT_SIZE)
        var modifiers = 0
        var slot = 2 // [0]=modifier, [1]=reserved, [2..7]=key1~key6
        for (usage in keyPressCounts.keys) {
            if (KeyUsage.isModifier(usage)) {
                modifiers = modifiers or KeyUsage.modifierBit(usage)
            } else if (slot < report.size) {
                report[slot++] = usage.toByte()
            }
        }
        report[0] = modifiers.toByte()
        return sendReport(host, HidDescriptor.REPORT_ID_KEYBOARD, report)
    }

    /** 키보드 LED 출력 리포트: bit0 NumLock, bit1 CapsLock, bit2 ScrollLock */
    private fun handleOutputReport(reportId: Byte, data: ByteArray) {
        if (reportId != HidDescriptor.REPORT_ID_KEYBOARD && reportId.toInt() != 0) return
        // 스택에 따라 data 앞에 Report ID가 붙어 올 수 있다
        val leds = when {
            data.isEmpty() -> return
            data.size >= 2 && data[0] == HidDescriptor.REPORT_ID_KEYBOARD -> data[1]
            else -> data[0]
        }.toInt()
        val caps = (leds and 0x02) != 0
        Log.i(TAG, "keyboard LED=0x%02X capsLock=%s".format(leds, caps))
        _capsLock.value = caps
    }

    // ---- 마우스 ----
    private val mouseLock = Any()
    private var pendingX = 0f
    private var pendingY = 0f
    private var pendingWheel = 0f
    private var pendingPan = 0f
    private var mouseFlushScheduled = false

    // 눌린 마우스 버튼 비트 (hidExecutor 스레드에서만 접근)
    private var mouseButtons = 0

    /** 커서 이동량(마우스 카운트)을 누적한다. 소수점 이하는 다음 리포트로 넘긴다. */
    fun moveMouse(dx: Float, dy: Float) {
        accumulateMouse { pendingX += dx; pendingY += dy }
    }

    /** 스크롤량(휠 칸 수)을 누적한다. wheel: +위, pan: +오른쪽 */
    fun scrollMouse(wheel: Float, pan: Float) {
        accumulateMouse { pendingWheel += wheel; pendingPan += pan }
    }

    fun mouseClick(button: Int) {
        hidExecutor.execute {
            flushMouse()
            setMouseButtons(mouseButtons or button)
            setMouseButtons(mouseButtons and button.inv())
        }
    }

    fun mouseButtonDown(button: Int) {
        hidExecutor.execute {
            flushMouse()
            setMouseButtons(mouseButtons or button)
        }
    }

    fun mouseButtonUp(button: Int) {
        hidExecutor.execute {
            flushMouse()
            setMouseButtons(mouseButtons and button.inv())
        }
    }

    /** 트랙패드 화면을 벗어날 때 등: 눌린 버튼을 모두 뗀다. */
    fun releaseMouseButtons() {
        hidExecutor.execute {
            flushMouse()
            if (mouseButtons != 0) setMouseButtons(0)
        }
    }

    private inline fun accumulateMouse(block: () -> Unit) {
        val schedule: Boolean
        synchronized(mouseLock) {
            block()
            schedule = !mouseFlushScheduled
            mouseFlushScheduled = true
        }
        if (schedule) hidExecutor.schedule(::flushMouse, MOUSE_REPORT_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun setMouseButtons(buttons: Int) {
        mouseButtons = buttons
        val host = connectedHost() ?: return
        sendMouseReport(host, 0, 0, 0, 0)
    }

    private fun flushMouse() {
        var x: Int
        var y: Int
        var wheel: Int
        var pan: Int
        synchronized(mouseLock) {
            mouseFlushScheduled = false
            x = pendingX.toInt(); pendingX -= x
            y = pendingY.toInt(); pendingY -= y
            wheel = pendingWheel.toInt(); pendingWheel -= wheel
            pan = pendingPan.toInt(); pendingPan -= pan
        }
        val host = connectedHost() ?: return
        // X/Y/Wheel/Pan은 int8(-127~127). 큰 값은 여러 리포트로 나눈다.
        while (x != 0 || y != 0 || wheel != 0 || pan != 0) {
            val cx = x.coerceIn(-127, 127)
            val cy = y.coerceIn(-127, 127)
            val cw = wheel.coerceIn(-127, 127)
            val cp = pan.coerceIn(-127, 127)
            sendMouseReport(host, cx, cy, cw, cp)
            x -= cx; y -= cy; wheel -= cw; pan -= cp
        }
    }

    private fun sendMouseReport(host: BluetoothDevice, x: Int, y: Int, wheel: Int, pan: Int) {
        val report = byteArrayOf(mouseButtons.toByte(), x.toByte(), y.toByte(), wheel.toByte(), pan.toByte())
        sendReport(host, HidDescriptor.REPORT_ID_MOUSE, report)
    }

    /** 연결이 바뀌면 이전 입력 상태를 버린다 (새 연결에 눌린 키·버튼이 넘어가지 않게). hidExecutor 스레드. */
    private fun resetInputState() {
        keyPressCounts.clear()
        mouseButtons = 0
        synchronized(mouseLock) {
            pendingX = 0f; pendingY = 0f; pendingWheel = 0f; pendingPan = 0f
        }
    }

    private fun connectedHost(): BluetoothDevice? =
        _state.value.takeIf { it.status == Status.CONNECTED }?.host?.device

    private fun sendReport(host: BluetoothDevice, reportId: Byte, data: ByteArray): Boolean {
        val ok = hidDevice?.sendReport(host, reportId.toInt(), data) ?: false
        if (!ok) Log.w(TAG, "sendReport failed id=$reportId data=${data.toHex()}")
        return ok
    }

    // ---- 프록시 / 등록 ----

    private fun requestProxy() {
        val adapter = adapter ?: return
        if (proxyRequested) return
        proxyRequested = true
        _state.value = State(Status.REGISTERING)
        val ok = adapter.getProfileProxy(appContext, profileListener, BluetoothProfile.HID_DEVICE)
        Log.i(TAG, "getProfileProxy(HID_DEVICE) returned $ok")
        if (!ok) {
            proxyRequested = false
            _state.value = State(Status.NOT_SUPPORTED, message = "HID_DEVICE 프로필을 가져오지 못했습니다")
        }
    }

    private fun closeProxy() {
        val hid = hidDevice
        hidDevice = null
        if (hid != null) adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        proxyRequested = false
    }

    private fun registerApp() {
        val hid = hidDevice ?: return
        _state.value = State(Status.REGISTERING)
        val ok = hid.registerApp(sdpSettings, null, null, hidExecutor, hidCallback)
        Log.i(TAG, "registerApp() returned $ok")
        if (!ok) {
            _state.value = State(
                Status.REGISTER_FAILED,
                message = "다른 HID 앱(예: Bluetooth Keyboard & Mouse)이 실행 중이면 강제 종료한 뒤 다시 시도하세요",
            )
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            if (!started || !proxyRequested) {
                adapter?.closeProfileProxy(profile, proxy)
                return
            }
            Log.i(TAG, "HID_DEVICE proxy connected")
            hidDevice = proxy as BluetoothHidDevice
            registerApp()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            Log.w(TAG, "HID_DEVICE proxy disconnected")
            hidDevice = null
            proxyRequested = false
            if (started) {
                val off = adapter?.isEnabled != true
                _state.value = State(if (off) Status.BLUETOOTH_OFF else Status.REGISTER_FAILED)
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val s = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    Log.i(TAG, "adapter state ${adapterStateName(s)}")
                    when (s) {
                        BluetoothAdapter.STATE_ON -> {
                            refreshBondedDevices()
                            requestProxy()
                        }
                        BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                            closeProxy()
                            _state.value = State(Status.BLUETOOTH_OFF)
                            _pairedDevices.value = emptyList()
                        }
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    val bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    Log.i(TAG, "bond state ${device?.toPaired()?.describe()} → ${bondStateName(bond)}")
                    refreshBondedDevices()
                }
            }
        }
    }

    // ---- HID 콜백 (hidExecutor 스레드) ----

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.i(TAG, "onAppStatusChanged registered=$registered plugged=${pluggedDevice?.toPaired()?.describe()}")
            if (!started) return
            if (registered) {
                // 등록 시점에 이미 연결된 PC가 있을 수 있다
                val connected = hidDevice?.connectedDevices?.firstOrNull()
                _state.value = if (connected != null) {
                    State(Status.CONNECTED, host = connected.toPaired())
                } else {
                    State(Status.READY)
                }
            } else {
                _state.value = State(Status.REGISTER_FAILED, message = "HID 등록이 해제되었습니다")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            val paired = device.toPaired()
            Log.i(TAG, "onConnectionStateChanged ${paired.describe()} → ${connectionStateName(state)}")
            if (!started) return
            if (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_DISCONNECTED) {
                resetInputState()
            }
            _state.update { current ->
                when (state) {
                    BluetoothProfile.STATE_CONNECTING -> State(Status.CONNECTING, host = paired)
                    BluetoothProfile.STATE_CONNECTED -> State(Status.CONNECTED, host = paired)
                    BluetoothProfile.STATE_DISCONNECTING -> State(Status.DISCONNECTING, host = paired)
                    BluetoothProfile.STATE_DISCONNECTED ->
                        if (current.host == null || current.host.address == paired.address) State(Status.READY) else current
                    else -> current
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            Log.i(TAG, "onGetReport type=$type id=$id bufferSize=$bufferSize")
            val hid = hidDevice ?: return
            val size = HidDescriptor.inputReportSize(id)
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT && size != null) {
                hid.replyReport(device, type, id, ByteArray(size))
            } else {
                hid.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            Log.i(TAG, "onSetReport type=$type id=$id data=${data.toHex()}")
            if (type == BluetoothHidDevice.REPORT_TYPE_OUTPUT) handleOutputReport(id, data)
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            Log.i(TAG, "onSetProtocol protocol=$protocol")
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            // 키보드 LED 출력 리포트(CapsLock 등)가 여기로 온다
            Log.i(TAG, "onInterruptData reportId=$reportId data=${data.toHex()}")
            handleOutputReport(reportId, data)
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            Log.i(TAG, "onVirtualCableUnplug ${device.toPaired().describe()}")
        }
    }

    // ---- 유틸 ----

    private fun BluetoothDevice.toPaired() = PairedDevice(
        device = this,
        name = name ?: address,
        address = address,
        isComputer = bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER,
    )

    private fun PairedDevice.describe() = "$name ($address)"

    private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }

    private fun connectionStateName(state: Int) = when (state) {
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN($state)"
    }

    private fun bondStateName(state: Int) = when (state) {
        BluetoothDevice.BOND_NONE -> "NONE"
        BluetoothDevice.BOND_BONDING -> "BONDING"
        BluetoothDevice.BOND_BONDED -> "BONDED"
        else -> "UNKNOWN($state)"
    }

    private fun adapterStateName(state: Int) = when (state) {
        BluetoothAdapter.STATE_OFF -> "OFF"
        BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"
        BluetoothAdapter.STATE_ON -> "ON"
        BluetoothAdapter.STATE_TURNING_OFF -> "TURNING_OFF"
        else -> "UNKNOWN($state)"
    }
}
