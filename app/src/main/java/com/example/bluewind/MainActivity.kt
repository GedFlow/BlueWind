package com.example.bluewind

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import com.example.bluewind.hid.HidManager
import com.example.bluewind.ui.BlueWindApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity onCreate")
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BlueWindApp()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 앱 종료 시에만 HID 등록 해제. 화면 재생성(설정 변경)일 때는 유지한다.
        if (isFinishing) {
            Log.i(TAG, "MainActivity finishing")
            HidManager.stop()
        }
    }
}
