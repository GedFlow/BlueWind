package com.example.bluewind.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.bluewind.TAG
import com.example.bluewind.hid.HidManager

// Android 12+: 둘 다 "근처 기기" 권한 그룹이라 한 번의 허용 창으로 받는다.
// BLUETOOTH_ADVERTISE는 폰을 검색 가능 상태로 만들 때 쓴다.
private val bluetoothPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
        emptyArray()
    }

private fun hasConnectPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

private enum class Screen { MAIN, CONNECTION, KEYBOARD }

@Composable
fun BlueWindApp() {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasConnectPermission(context)) }
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        Log.i(TAG, "permission result $result")
        hasPermission = hasConnectPermission(context)
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(bluetoothPermissions)
    }

    // 설정 화면에서 권한을 바꾸고 돌아온 경우, 다른 곳에서 페어링하고 돌아온 경우 반영
    LifecycleResumeEffect(Unit) {
        hasPermission = hasConnectPermission(context)
        if (hasPermission) HidManager.refreshBondedDevices()
        onPauseOrDispose { }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) HidManager.start(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        if (hasPermission) {
            when (screen) {
                Screen.MAIN -> MainScreen(
                    onOpenConnection = { screen = Screen.CONNECTION },
                    onOpenKeyboard = { screen = Screen.KEYBOARD },
                )
                Screen.CONNECTION -> ConnectionScreen(onBack = { screen = Screen.MAIN })
                Screen.KEYBOARD -> KeyboardPanel(onClose = { screen = Screen.MAIN })
            }
        } else {
            PermissionScreen(
                onRequest = { permissionLauncher.launch(bluetoothPermissions) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                },
            )
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("블루투스 권한이 필요합니다", style = MaterialTheme.typography.titleLarge)
            Text(
                "PC에 키보드·마우스로 연결하려면 '근처 기기' 권한을 허용해야 합니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRequest) { Text("권한 허용") }
                OutlinedButton(onClick = onOpenSettings) { Text("앱 설정 열기") }
            }
        }
    }
}
