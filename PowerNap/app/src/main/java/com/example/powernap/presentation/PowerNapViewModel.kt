package com.example.powernap.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.yml.charts.common.model.Point
import com.example.powernap.data.ConnectionState
import com.example.powernap.data.PowerNapReceiveManager
import com.example.powernap.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PowerNapViewModel @Inject constructor(
    private val powerNapReceiveManager: PowerNapReceiveManager
) : ViewModel(){

    var initializingMessage by mutableStateOf<String?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var connectionState by mutableStateOf<ConnectionState>(ConnectionState.Uninitialized)


    private val _pointsData1 = mutableStateListOf<Point>()
    private val _pointsData2 = mutableStateListOf<Point>()
    val pointsData1: List<Point> = _pointsData1
    val pointsData2: List<Point> = _pointsData2

    var dData by mutableStateOf(0f)
    var tData by mutableStateOf(0f)
    var aData by mutableStateOf(0f)
    var bData by mutableStateOf(0f)

    private fun subscribeToChanges(){
        viewModelScope.launch {
            powerNapReceiveManager.data.collect{ result ->
                when(result){
                    is Resource.Success -> {
                        connectionState = result.data.connectionState

                        val signals = result.data.fSignal

                        dData = result.data.dData
                        tData = result.data.tData
                        aData = result.data.aData
                        bData = result.data.bData

                        if (signals.isNotEmpty()) {
                            // channel 1
                            val signal1 = signals[0]
                                var x1 = if (_pointsData1.isNotEmpty()) _pointsData1.last().x + 1 else 0f
                            for (value in signal1) {
                                _pointsData1.add(Point(x1++, value))
                                if (_pointsData1.size > 64) _pointsData1.removeFirst()
                            }
                            // channel 2
                            if (signals.size > 1) {
                                val signal2 = signals[1]
                                var x2 = if (_pointsData2.isNotEmpty()) _pointsData2.last().x + 1 else 0f
                                for (value in signal2) {
                                    _pointsData2.add(Point(x2++, value))
                                    if (_pointsData2.size > 64) _pointsData2.removeFirst()
                                }
                            }
                        }
                    }

                    is Resource.Loading -> {
                        initializingMessage = result.message
                        connectionState = ConnectionState.CurrentlyInitializing
                    }

                    is Resource.Error -> {
                        errorMessage = result.errorMessage
                        connectionState = ConnectionState.Uninitialized
                    }
                }
            }
        }
    }

    fun disconnect(){
        powerNapReceiveManager.disconnect()
    }

    fun reconnect(){
        powerNapReceiveManager.reconnect()
    }

    fun initializeConnection(){
        errorMessage = null
        subscribeToChanges()
        powerNapReceiveManager.startReceiving()
    }

    override fun onCleared() {
        super.onCleared()
        powerNapReceiveManager.closeConnection()
    }

    fun writeCharacteristic() {
        powerNapReceiveManager.writeCharacteristic()
    }

}