package com.example.bluewind.hid

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.bluewind.MainActivity
import com.example.bluewind.R
import com.example.bluewind.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * HID 등록을 유지하기 위한 Foreground Service.
 *
 * Android 블루투스 HID 서비스는 앱이 화면에서 벗어나면(홈, 앱 전환, 화면 꺼짐) 등록을 강제로 해제한다
 * (AOSP HidDeviceService: 중요도가 IMPORTANCE_VISIBLE보다 낮아지면 unregister).
 * Foreground Service가 돌고 있으면 앱 중요도가 유지되어 연결이 끊기지 않는다.
 *
 * HID 로직은 [HidManager]에 있고, 이 서비스는 중요도 유지와 상단 알림만 맡는다.
 */
class HidService : Service() {

    companion object {
        private const val CHANNEL_ID = "connection"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_QUIT = "com.example.bluewind.action.QUIT"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, HidService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HidService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // 상태가 바뀌면 알림 문구를 갱신한다
        scope.launch {
            HidManager.state.collect { updateNotification(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_QUIT) {
            Log.i(TAG, "HidService: 알림에서 종료")
            HidManager.quit()
            stopSelf()
            return START_NOT_STICKY
        }
        // startForegroundService()로 시작할 때마다 바로 포그라운드로 올려야 한다
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(HidManager.state.value),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else {
                    0
                },
            )
            Log.i(TAG, "HidService: foreground started")
        } catch (e: Exception) {
            Log.e(TAG, "HidService: startForeground failed", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** 최근 앱 목록에서 앱을 지우면 종료로 본다 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "HidService: task removed → 종료")
        HidManager.quit()
        stopSelf()
    }

    override fun onDestroy() {
        Log.i(TAG, "HidService: destroyed")
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "PC 연결 상태", NotificationManager.IMPORTANCE_LOW).apply {
            description = "BlueWind가 PC에 연결되어 있는 동안 표시됩니다"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: HidManager.State) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("BlueWind")
        .setContentText(statusText(state))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .addAction(
            0,
            "종료",
            PendingIntent.getService(
                this,
                1,
                Intent(this, HidService::class.java).setAction(ACTION_QUIT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private fun updateNotification(state: HidManager.State) {
        // 알림 권한이 없어도 서비스는 동작한다. 알림 문구만 갱신하지 못한다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun statusText(state: HidManager.State): String = when (state.status) {
        HidManager.Status.CONNECTED -> "연결됨: ${state.host?.name.orEmpty()}"
        HidManager.Status.CONNECTING -> "연결 중: ${state.host?.name.orEmpty()}"
        HidManager.Status.READY -> "PC 연결 대기 중"
        HidManager.Status.BLUETOOTH_OFF -> "블루투스가 꺼져 있습니다"
        HidManager.Status.REGISTER_FAILED -> "HID 장치 등록 실패"
        else -> "준비 중"
    }
}
