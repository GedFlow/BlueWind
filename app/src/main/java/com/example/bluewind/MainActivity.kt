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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.bluewind.hid.HidManager
import com.example.bluewind.ui.BlueWindApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity onCreate")
        enableEdgeToEdge()
        hideStatusBar()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BlueWindApp()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 권한 창 등으로 포커스를 잃었다 돌아오면 다시 숨긴다
        if (hasFocus) hideStatusBar()
    }

    /** 앱 사용 중 상단 상태 표시줄(시간·알림·배터리)을 숨긴다. 위에서 쓸어내리면 잠깐 보인다. */
    private fun hideStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
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
