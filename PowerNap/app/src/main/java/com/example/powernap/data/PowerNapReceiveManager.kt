package com.example.powernap.data

import com.example.powernap.util.Resource
import kotlinx.coroutines.flow.MutableSharedFlow

interface PowerNapReceiveManager {

    val data: MutableSharedFlow<Resource<PowerNapResult>>

    fun reconnect()

    fun disconnect()

    fun startReceiving()

    fun closeConnection()

    fun writeCharacteristic(): ByteArray?
}