package com.example.powernap.presentation

import android.bluetooth.BluetoothAdapter
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.powernap.data.ConnectionState
import com.example.powernap.presentation.permissions.PermissionUtils
import com.example.powernap.presentation.permissions.SystemBroadcastReceiver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import androidx.compose.ui.unit.sp
import com.example.powernap.presentation.permissions.BarChartScreen
import com.example.powernap.presentation.permissions.WaveChartScreen

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProcessScreen(
    onBluetoothStateChanged:()->Unit,
    viewModel: PowerNapViewModel = hiltViewModel()
) {

    SystemBroadcastReceiver(systemAction = BluetoothAdapter.ACTION_STATE_CHANGED){ bluetoothState ->
        val action = bluetoothState?.action ?: return@SystemBroadcastReceiver
        if(action == BluetoothAdapter.ACTION_STATE_CHANGED){
            onBluetoothStateChanged() // 舉例 : 若關閉藍芽, 系統會跳出通知請求開始藍芽調配器
        }
    }

    val permissionState = rememberMultiplePermissionsState(permissions = PermissionUtils.permissions)
    val lifecycleOwner = LocalLifecycleOwner.current
    val bleConnectionState = viewModel.connectionState

    DisposableEffect(
        key1 = lifecycleOwner,
        effect = {
            val observer = LifecycleEventObserver{_,event ->
                if(event == Lifecycle.Event.ON_START){
                    permissionState.launchMultiplePermissionRequest()
                    if(permissionState.allPermissionsGranted && bleConnectionState == ConnectionState.Disconnected){
                        viewModel.reconnect()
                    }
                }
                if(event == Lifecycle.Event.ON_STOP){
                    if (bleConnectionState == ConnectionState.Connected){
                        viewModel.disconnect()
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    )

    LaunchedEffect(key1 = permissionState.allPermissionsGranted){
        if(permissionState.allPermissionsGranted){
            if(bleConnectionState == ConnectionState.Uninitialized){
                viewModel.initializeConnection()
            }
        }
    }

    Column(modifier = Modifier.padding(1.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (bleConnectionState == ConnectionState.CurrentlyInitializing) {
                    if (viewModel.initializingMessage == "Connected") {
                        Button(onClick = {
                            viewModel.writeCharacteristic()
                        }) {
                            Text(text = "Start Record",fontSize = 19.sp)
                        }
                        Text(text = "\nConnected ...")
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            if (viewModel.initializingMessage != null) {
                                Text(text = viewModel.initializingMessage!!)
                            }
                        }
                    }
                } else if (!permissionState.allPermissionsGranted) {
                    Text(
                        text = "Go to the app setting and allow the missing permissions.",
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(10.dp),
                        textAlign = TextAlign.Center
                    )
                } else if (viewModel.errorMessage != null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = viewModel.errorMessage!!
                        )
                        Button(
                            onClick = {
                                if (permissionState.allPermissionsGranted) {
                                    viewModel.initializeConnection()
                                }
                            }
                        ) {
                            Text(
                                "Try again"
                            )
                        }
                    }
                } else if (bleConnectionState == ConnectionState.Connected) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        WaveChartScreen()
                        BarChartScreen()
                    }

                } else if (bleConnectionState == ConnectionState.Disconnected) {
                    Button(onClick = {
                        viewModel.initializeConnection()
                    }) {
                        Text("Initialize again")
                    }
                }
            }
        }
    }
}