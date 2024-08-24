package com.example.powernap.presentation.permissions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
fun SystemBroadcastReceiver(
    systemAction:String,
    onSystemEvent:(intent: Intent?)->Unit
) {

    val context = LocalContext.current

    val currentOnSystemEvent by rememberUpdatedState(onSystemEvent)

    DisposableEffect(context, systemAction){
        val intentFilter = IntentFilter(systemAction)
        val broadcast = object : BroadcastReceiver(){ // 透過監聽服務的事件，活動就可以根據目前的 BLE 裝置連線狀態更新使用者介面。
            override fun onReceive(p0: Context?, intent: Intent?) { // 在設定活動時註冊這個接收器
                currentOnSystemEvent(intent)
            }
        }

        context.registerReceiver(broadcast, intentFilter)

        onDispose { // 在活動離開螢幕時取消註冊
            context.unregisterReceiver(broadcast)
        }
    }
}