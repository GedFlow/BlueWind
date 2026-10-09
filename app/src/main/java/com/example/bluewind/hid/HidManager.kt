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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    // HID 콜백을 받는 전용 스레드
    private val hidExecutor: ExecutorService = Executors.newSingleThreadExecutor()

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
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            Log.i(TAG, "onSetProtocol protocol=$protocol")
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            // 키보드 LED 출력 리포트(CapsLock 등)가 여기로 온다. 지금은 기록만 한다.
            Log.i(TAG, "onInterruptData reportId=$reportId data=${data.toHex()}")
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
