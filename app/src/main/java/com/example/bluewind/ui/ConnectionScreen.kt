package com.example.bluewind.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluewind.BuildConfig
import com.example.bluewind.TAG
import com.example.bluewind.hid.HidManager
import com.example.bluewind.hid.HidManager.PairedDevice
import com.example.bluewind.hid.HidManager.Status

private const val DISCOVERABLE_SECONDS = 300

@Composable
fun ConnectionScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val state by HidManager.state.collectAsStateWithLifecycle()
    val devices by HidManager.pairedDevices.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val enableBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> Log.i(TAG, "enable bluetooth result=${result.resultCode}") }

    val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS)
    val discoverable = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> Log.i(TAG, "discoverable result=${result.resultCode}") }
    val advertisePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) discoverable.launch(discoverableIntent) }

    val makeDiscoverable = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            advertisePermission.launch(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            discoverable.launch(discoverableIntent)
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 왼쪽: 상태 + 최초 페어링 안내
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("← 돌아가기") }
                Text("연결", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "BlueWind v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusCard(
                state = state,
                onEnableBluetooth = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                onRetry = HidManager::retry,
                onDisconnect = HidManager::disconnect,
            )
            PairingGuide(
                phoneName = if (state.status.isRegistered) HidManager.phoneName() else null,
                canMakeDiscoverable = state.status.isRegistered,
                onMakeDiscoverable = makeDiscoverable,
            )
        }

        // 오른쪽: 페어링된 기기 목록
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("페어링된 기기", style = MaterialTheme.typography.titleMedium)
            if (devices.isEmpty()) {
                Text(
                    "페어링된 기기가 없습니다. 왼쪽 안내에 따라 PC에서 이 폰을 추가하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices, key = { it.address }) { device ->
                    DeviceRow(
                        device = device,
                        state = state,
                        onConnect = { HidManager.connect(device) },
                        onDisconnect = HidManager::disconnect,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    state: HidManager.State,
    onEnableBluetooth: () -> Unit,
    onRetry: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val hostName = state.host?.name.orEmpty()
    val (color, title) = when (state.status) {
        Status.NOT_STARTED -> Color.Gray to "준비 중"
        Status.BLUETOOTH_OFF -> Color(0xFFE57373) to "블루투스가 꺼져 있습니다"
        Status.NOT_SUPPORTED -> Color(0xFFE57373) to "블루투스 HID를 사용할 수 없습니다"
        Status.REGISTERING -> Color(0xFFFFB74D) to "HID 장치 등록 중…"
        Status.REGISTER_FAILED -> Color(0xFFE57373) to "HID 장치 등록 실패"
        Status.READY -> Color.Gray to "PC 연결 대기 중"
        Status.CONNECTING -> Color(0xFFFFB74D) to "연결 중: $hostName"
        Status.CONNECTED -> Color(0xFF4FC3F7) to "연결됨: $hostName"
        Status.DISCONNECTING -> Color(0xFFFFB74D) to "연결 끊는 중: $hostName"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, CircleShape)
                )
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (state.status) {
                Status.BLUETOOTH_OFF -> Button(onClick = onEnableBluetooth) { Text("블루투스 켜기") }
                Status.REGISTER_FAILED -> Button(onClick = onRetry) { Text("다시 시도") }
                Status.CONNECTED -> OutlinedButton(onClick = onDisconnect) { Text("연결 끊기") }
                else -> Unit
            }
        }
    }
}

@Composable
private fun PairingGuide(
    phoneName: String?,
    canMakeDiscoverable: Boolean,
    onMakeDiscoverable: () -> Unit,
) {
    val phone = phoneName?.let { "'$it'" } ?: "이 폰"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("처음 연결하는 PC", style = MaterialTheme.typography.titleMedium)
            val steps = listOf(
                "1. 다른 HID 앱(예: Bluetooth Keyboard & Mouse)은 강제 종료합니다.",
                "2. 예전에 페어링한 적이 있으면 양쪽 모두 지웁니다: 폰 블루투스 설정에서 PC '등록 해제', Windows에서 폰 '장치 제거'.",
                "3. 아래 버튼으로 폰을 검색 가능 상태로 만듭니다 (${DISCOVERABLE_SECONDS / 60}분).",
                "4. Windows '장치 추가' → 'Bluetooth' → $phone 선택 → 폰에 뜨는 PIN 확인 창(또는 알림)에서 먼저 수락한 뒤 Windows에서 '연결'.",
                "5. 페어링이 끝나면 위 상태가 '연결됨'으로 바뀝니다. 처음 페어링할 때는 오른쪽 '연결' 버튼을 누르지 않습니다.",
            )
            steps.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            FilledTonalButton(onClick = onMakeDiscoverable, enabled = canMakeDiscoverable) {
                Text("검색 가능하게 하기")
            }
            if (!canMakeDiscoverable) {
                // HID 등록 전에 페어링하면 Windows가 키보드·마우스 정보를 받지 못한다
                Text(
                    "HID 등록이 끝나야 누를 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: PairedDevice,
    state: HidManager.State,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val isHost = state.host?.address == device.address
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    (if (device.isComputer) "PC · " else "기타 기기 · ") + device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                isHost && state.status == Status.CONNECTED ->
                    OutlinedButton(onClick = onDisconnect) { Text("연결 끊기") }
                isHost && state.status == Status.CONNECTING -> Text("연결 중…")
                isHost && state.status == Status.DISCONNECTING -> Text("끊는 중…")
                else -> Button(onClick = onConnect, enabled = state.status == Status.READY) { Text("연결") }
            }
        }
    }
}
