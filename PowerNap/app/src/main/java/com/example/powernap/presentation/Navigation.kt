package com.example.powernap.presentation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun Navigation(
    onBluetoothStateChanged:()->Unit
) {

    val navController = rememberNavController() // 確保橫豎切換屏幕等配置更改時保留導航控制器的狀態

    NavHost(navController = navController, startDestination = Screen.StartScreen.route){
        composable(Screen.StartScreen.route){
            StartScreen(navController = navController)
        }

        composable(Screen.ProcessScreen.route){
            ProcessScreen(
                onBluetoothStateChanged
            )
        }
    }
}

sealed class Screen(val route:String){
    object StartScreen:Screen("start_screen")
    object ProcessScreen:Screen("process_screen")
}