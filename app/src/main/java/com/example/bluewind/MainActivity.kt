package com.example.bluewind

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.bluewind.hid.HidManager
import com.example.bluewind.ui.BlueWindApp
import com.example.bluewind.ui.BlueWindTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity onCreate")
        enableEdgeToEdge()
        hideSystemBars()
        // 알림의 "종료"를 누르면 화면도 닫는다
        lifecycleScope.launch {
            HidManager.quitRequests.collect { finishAndRemoveTask() }
        }
        setContent {
            BlueWindTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BlueWindApp()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 권한 창 등으로 포커스를 잃었다 돌아오면 다시 숨긴다
        if (hasFocus) hideSystemBars()
    }

    /**
     * 앱 사용 중 상단 상태 표시줄(시간·알림·배터리)과 하단 내비게이션 바(홈·최근 앱 제스처 바)를 숨긴다 (v0.8).
     * 화면 위·아래 끝에서 쓸면 잠깐 보인다. 하단 바가 보일 때 한 번 더 쓸어올리면 홈으로 나간다 (게임과 같은 방식).
     */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
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
