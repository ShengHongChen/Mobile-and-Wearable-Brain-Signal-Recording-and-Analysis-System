package com.example.powernap.data.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.example.powernap.data.ConnectionState
import com.example.powernap.data.PowerNapReceiveManager
import com.example.powernap.data.PowerNapResult
import com.example.powernap.util.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.*
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt


@SuppressLint("MissingPermission")
class PowerNapBLEReceiveManager @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter,
    private val context: Context
) : PowerNapReceiveManager {

    // DEVICE NAME (裝置名稱)
    private val DEVICE_NAME = "EXG_A4D288C6"

    // BLE DEVICE UUID (notify)
    private val UART_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    private val UART_TX_CHARACTERISTICS_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

    // BLE DEVICE UUID (send)
    private val UART_RX_CHARACTERISTICS_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"

    override val data: MutableSharedFlow<Resource<PowerNapResult>> = MutableSharedFlow()

    // Signal part
    private val packageLength = 152
    private val bufferMiss = ByteArray(packageLength)
    private val readBuffer = ByteArray(packageLength)

    private val tempCH1 = FloatArray(126)
    private val tempCH2 = FloatArray(126)

    private val tempEpochCH1 = DoubleArray(7500)
    private val tempEpochCH2 = DoubleArray(7500)

    private var drawNftCH1 = DoubleArray(125)
    private var drawNftCH2 = DoubleArray(125)

    private var alphaCh1 = 0f
    private var alphaCh2 = 0f
    private var totalAlpha = 0f

    private var deltaCh1 = 0f
    private var deltaCh2 = 0f
    private var totalDelta = 0f

    private var thetaCh1 = 0f
    private var thetaCh2 = 0f
    private var totalTheta = 0f

    private var betaCh1 = 0f
    private var betaCh2 = 0f
    private var totalBeta = 0f

    var datapoint = 0

    private var getstatus = true
    private var checkdata = false
    private var writeAmplitude = true
    private var drawNFTstatus = false
    private var drawstatus = false

    // Timer part
    var count = 0
    var epochCount = 0

    private val dataCH1 = FloatArray(504)
    private val dataCH2 = FloatArray(504)

    private var drawCH1 = FloatArray(126)
    private var drawCH2 = FloatArray(126)

    // FFT part
    private var NFTBufferCh1 = FloatArray(500)
    private var NFTBufferCh2 = FloatArray(500)
    var nftpoint = 0

    private var NFTcalCh1 = FloatArray(256)
    private var NFTcalCh2 = FloatArray(256)

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // 決定低耗能藍芽
        .build()

    private var gatt: BluetoothGatt? = null

    private var isScanning = false

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val scanCallback = object : ScanCallback() { // 掃描範圍內的 BLE 裝置時會進入

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.name == DEVICE_NAME) { // 避免連接到其他裝置
                coroutineScope.launch {
                    data.emit(Resource.Loading(message = "Connecting to device..."))
                }
                if (isScanning) {
                    result.device.connectGatt(context, false, gattCallback) // 要連接 BLE 裝置上 GATT 的伺服器
                    isScanning = false
                    bleScanner.stopScan(this) // 停止掃描
                }
            }
        }
    }

    private var currentConnectionAttempt = 1
    private var MAXIMUM_CONNECTION_ATTEMPTS = 5

    private var clickStart = false
    private var startRecord = false

    private val gattCallback = object : BluetoothGattCallback() {
        // 連線 gatt 狀態改變時進入
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) { // 成功進入 gatt 伺服器
                    coroutineScope.launch {
                        data.emit(Resource.Loading(message = "Discovering Services..."))
                    }
                    gatt.discoverServices()
                    this@PowerNapBLEReceiveManager.gatt = gatt
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) { // 與 gatt 伺服器停止連線
                    coroutineScope.launch {
                        data.emit(
                            Resource.Success(
                                data = PowerNapResult(
                                    fSignal = arrayOf(),
                                    ConnectionState.Disconnected,
                                    dData = 0f,
                                    tData = 0f,
                                    aData = 0f,
                                    bData = 0f
                                )
                            )
                        )
                    }
                    gatt.close()
                }
            } else {
                gatt.close()
                currentConnectionAttempt += 1
                coroutineScope.launch {
                    data.emit(
                        Resource.Loading(
                            message = "Attempting to connect $currentConnectionAttempt/$MAXIMUM_CONNECTION_ATTEMPTS"
                        )
                    )
                }
                if (currentConnectionAttempt <= MAXIMUM_CONNECTION_ATTEMPTS) { // 最多嘗試五次
                    startReceiving()
                } else { // 超過五次後報錯
                    coroutineScope.launch {
                        data.emit(Resource.Error(errorMessage = "Could not connect to BLE device"))
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                printGattTable() // 印出藍芽裝置的相關資料細節
                coroutineScope.launch {
                    data.emit(Resource.Loading(message = "Adjusting MTU space..."))
                }
                gatt.requestMtu(517)// MTU 最大限制為 517
            }
        }

        // 確認是否是我們要的藍芽裝置，並啟用通知 (enableNotification)
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val characteristic =
                findCharacteristics(UART_SERVICE_UUID, UART_TX_CHARACTERISTICS_UUID)
            if (characteristic == null) {
                coroutineScope.launch {
                    data.emit(Resource.Loading(message = "Cannot find PowerNap"))
                }
                return
            }
            enableNotification(characteristic)

            coroutineScope.launch {
                data.emit(Resource.Loading(message = "Connected"))
            }
        }

        // 傳送的資料 (特徵值) 變動時進入
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            with(characteristic) {
                when (uuid) { // 類似其他語言的 switch
                    UUID.fromString(UART_TX_CHARACTERISTICS_UUID) -> { // 確認是否 UUID 物件相同
                        // XX XX XX XX XX XX 封包型式，每一封包 152 Byte，18 points，2 channel (4 Byte / point)
                        System.arraycopy(characteristic.value, 0, readBuffer, 0, readBuffer.size)

                        if (clickStart) { // 按下按鈕後進入 writeCharacteristic 把 clickStart 改成 true
                            coroutineScope.launch {
                                if (drawstatus) { // 若 record() 完成，可進入並改變畫圖的數據
                                    val signal = mixCh1AndCh2(drawCH1, drawCH2)
                                    for (i in signal.indices) {
                                        for (j in signal[i].indices) {
                                            signal[i][j] = signal[i][j] * 1000000
                                        }
                                    }
//                                    saveDataToFile(signal)

                                    CoroutineScope(Dispatchers.Default).launch {
                                        val powerNapResult = PowerNapResult(
                                            fSignal = signal,
                                            ConnectionState.Connected,
                                            dData = totalDelta,
                                            tData = totalTheta,
                                            aData = totalAlpha,
                                            bData = totalBeta,
                                        )
                                        data.emit(
                                            Resource.Success(data = powerNapResult)
                                        )
                                    }
                                    drawstatus = false
                                    getstatus = true // 畫完圖後繼續執行 record()
                                }
                            }
                        }
                        if (!startRecord) { // 若還沒開始執行 record() 進入
                            startRecord = true
                            CoroutineScope(Dispatchers.Default).launch {// 類似 Thread 委派作業
                                record()
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
    fun mixCh1AndCh2(drawCh1: FloatArray, drawCh2: FloatArray): Array<FloatArray> {
        // Create a 2D array with 2 rows (one for drawCh1, one for drawCh2)
        val mixedData = Array(2) { FloatArray(drawCh1.size) }

        // Copy drawCh1 to the first row
        System.arraycopy(drawCh1, 0, mixedData[0], 0, drawCh1.size)

        // Copy drawCh2 to the second row
        System.arraycopy(drawCh2, 0, mixedData[1], 0, drawCh2.size)

        return mixedData
    }

    private fun saveDataToFile(data: Array<FloatArray>) {
        val fileName = "Signal_data.txt"
        val file = File(context.filesDir, fileName)
        FileOutputStream(file, true).use { fos ->
            OutputStreamWriter(fos).use { writer ->
                data.forEach { array ->
                    writer.write(array.joinToString(", ") + "\n")
                }
            }
        }
        Log.d("FileCreation", "File created successfully at: ${file.absolutePath}")
    }

    override fun writeCharacteristic(): ByteArray? {
        if (gatt == null) {
            Log.w("Service", "BluetoothAdapter not initialized")
            return null
        }

        val service = gatt?.getService(UUID.fromString(UART_SERVICE_UUID))
        if (service == null) {
            Log.w("Service", "Service object is null")
            return null
        }

        val characteristic =
            service.getCharacteristic(UUID.fromString(UART_RX_CHARACTERISTICS_UUID))
        val cal = Calendar.getInstance()
        cal.time = Date()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)
        Log.d("test_Date", "$year-$month-$day $hour:$min:$sec")
        val data = byteArrayOf(
            0x55.toByte(),
            0x03.toByte(),
            0x06.toByte(),
            (year - 2000).toByte(),
            month.toByte(),
            day.toByte(),
            hour.toByte(),
            min.toByte(),
            sec.toByte(),
            0xaa.toByte()
        )

        if (!characteristic.setValue(data)) {
            Log.e("Error", "Cannot set char value")
            return null
        }

        val startSuccess = gatt!!.writeCharacteristic(characteristic)
        if (!startSuccess) {
            Log.e("Error", "Cannot start")
            return null
        }

        Log.i("Success", "Starting record")
        clickStart = true
        return data
    }

    // 以下兩個 function 是為了即時更新數據
    // Descriptor（描述符）是一種用於擴展藍牙特徵值（Characteristic）的元素
    // 特徵值包含實際數據，而描述符提供有關特徵值的附加信息或配置選項，每個特徵值都可以擁有一個或多個描述符

    private fun enableNotification(characteristic: BluetoothGattCharacteristic) { // 啟用或禁用通知和指示功能
        val cccdUuid =
            UUID.fromString(CCCD_DESCRIPTOR_UUID) // Client Characteristic Configuration Descriptor
        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> return
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccdDescriptor ->
            if (gatt?.setCharacteristicNotification(characteristic, true) == false) {
                Log.d("BLEReceiveManager", "set characteristics notification failed")
                return
            }
            writeDescription(cccdDescriptor, payload)
        }
    }

    private fun writeDescription(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        gatt?.let { gatt ->
            descriptor.value = payload
            gatt.writeDescriptor(descriptor)
        } ?: error("Not connected to a BLE device!") // 若 gatt 為 null，才回傳 error
    }

    private fun findCharacteristics(
        serviceUUID: String,
        characteristicsUUID: String
    ): BluetoothGattCharacteristic? { // 要找到符合自己特徵的裝置
        return gatt?.services?.find { service ->
            service.uuid.toString() == serviceUUID
        }?.characteristics?.find { characteristics ->
            characteristics.uuid.toString() == characteristicsUUID
        }
    }

    override fun startReceiving() { // 開始掃描 BLE 裝置
        coroutineScope.launch {
            data.emit(Resource.Loading(message = "Scanning BLE devices...")) // 若裝置名稱不對，會卡在這裡
        }
        isScanning = true // 設定 isScanning 為 True, 之後當掃描到 BLE 裝置時, 才能連接裝置的 gatt 伺服器
        bleScanner.startScan(null, scanSettings, scanCallback) // 呼叫 scanCallback function
    }

    override fun reconnect() {
        gatt?.connect()
    }

    override fun disconnect() {
        gatt?.disconnect()
    }


    override fun closeConnection() {
        bleScanner.stopScan(scanCallback)
        val characteristic = findCharacteristics(UART_SERVICE_UUID, UART_TX_CHARACTERISTICS_UUID)
        if (characteristic != null) {
            disconnectCharacteristic(characteristic)
        }
        gatt?.close()
    }

    private fun disconnectCharacteristic(characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString(CCCD_DESCRIPTOR_UUID)
        characteristic.getDescriptor(cccdUuid)?.let { cccdDescriptor ->
            if (gatt?.setCharacteristicNotification(characteristic, false) == false) {
                Log.d("TempHumidReceiveManager", "set characteristics notification failed")
                return
            }
            writeDescription(cccdDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        }
    }

    private fun plotFFT(data: FloatArray): DoubleArray {
        val numsample = data.size
        val msg = DoubleArray(numsample / 2)
        val samples = Array(numsample) { Complex(0.0, 0.0) }

        for (i in data.indices) {
            samples[i] = Complex(data[i].toDouble(), 0.0)
        }

        val transformer = FastFourierTransformer(DftNormalization.STANDARD)
        val spectrum = transformer.transform(samples, TransformType.FORWARD)

        for (i in 0 until numsample / 2) {
            try {
                val realTemp = sqrt(spectrum[i].real * spectrum[i].real + spectrum[i].imaginary * spectrum[i].imaginary)
                msg[i] = (2.0 / numsample) * abs(realTemp)
            } catch (e: Exception) {
                println("發生例外狀況：${e.message}")
            }
        }

        return msg
    }

    private fun dataConvert(bufferdata: ByteArray): Array<FloatArray> {
        var outputsig = Array(2) { FloatArray(18) { 0f } }
        val packageData = ByteArray(packageLength)

        var i = 0
        while (i < readBuffer.size) {
            if (bufferdata[i] == 173.toByte() &&
                bufferdata[i + 1] == 222.toByte() &&
                bufferdata[i + 148] == 239.toByte() &&
                bufferdata[i + 149] == 190.toByte()
            ) {
                System.arraycopy(readBuffer, i, packageData, 0, packageLength)
                // find LSB miss package ex. [y0][y1][239][190][173][222] -> bufferMiss = [y0][y1][239][190]
                if (i < packageLength) {
                    System.arraycopy(readBuffer, 0, bufferMiss, bufferMiss.size - i, i)
                    // convert missing data
                    if (bufferMiss[0] == 173.toByte() &&
                        bufferMiss[1] == 222.toByte() &&
                        bufferMiss[148] == 239.toByte() &&
                        bufferMiss[149] == 190.toByte()
                    ) {
                        outputsig = voltage(bufferMiss)
                        bufferMiss.fill(0)
                    }
                }
                i += (packageLength - 1)
                outputsig = voltage(packageData)
            }
            // find MSB miss package ex. [173][222][x0][x1]...[239][190] -> bufferMiss = [173][222][x0][x1]...[]
            if (i >= bufferdata.size - packageLength) {
                System.arraycopy(readBuffer, i + 1, bufferMiss, 0, (bufferdata.size - i - 1))
                break
            }
            i++
        }
        return outputsig
    }

    private fun bandpassFilter(signal: FloatArray): FloatArray {
        val inputLen = 504
        val order1 = 15
        val order2 = 125

        val x = DoubleArray(504)
        val y = DoubleArray(504)
        val temp = DoubleArray(504)
        val outputSig = FloatArray(126)

        val lowFc = doubleArrayOf(
            -0.001996,
            -0.005761,
            -0.01135,
            -0.006998,
            0.0257636,
            0.093232,
            0.175126,
            0.231978,
            0.231978,
            0.175126,
            0.093232,
            0.025764,
            -0.006998,
            -0.011345,
            -0.005761,
            -0.001996
        )
        val highFc = doubleArrayOf(
            -0.000288,
            -0.000299,
            -0.000314,
            -0.000334,
            -0.000360,
            -0.000392,
            -0.000430,
            -0.000475,
            -0.000528,
            -0.000588,
            -0.000656,
            -0.000732,
            -0.000818,
            -0.000913,
            -0.001018,
            -0.001134,
            -0.001261,
            -0.001400,
            -0.001551,
            -0.001714,
            -0.001892,
            -0.002083,
            -0.002290,
            -0.002512,
            -0.002751,
            -0.003008,
            -0.003283,
            -0.003578,
            -0.003894,
            -0.004232,
            -0.004593,
            -0.004979,
            -0.005393,
            -0.005836,
            -0.006310,
            -0.006818,
            -0.007363,
            -0.007949,
            -0.008579,
            -0.009259,
            -0.009994,
            -0.010791,
            -0.011658,
            -0.012604,
            -0.013642,
            -0.014786,
            -0.016054,
            -0.017469,
            -0.019061,
            -0.020866,
            -0.022935,
            -0.025336,
            -0.028163,
            -0.031547,
            -0.035685,
            -0.040876,
            -0.047606,
            -0.056712,
            -0.069775,
            -0.090187,
            -0.126764,
            -0.211831,
            -0.636334,
            0.636334,
            0.211831,
            0.126764,
            0.090187,
            0.069775,
            0.056712,
            0.047606,
            0.040876,
            0.035685,
            0.031547,
            0.028163,
            0.025336,
            0.022935,
            0.020866,
            0.019061,
            0.017469,
            0.016054,
            0.014786,
            0.013642,
            0.012604,
            0.011658,
            0.010791,
            0.009994,
            0.009259,
            0.008579,
            0.007949,
            0.007363,
            0.006818,
            0.006310,
            0.005836,
            0.005393,
            0.004979,
            0.004593,
            0.004232,
            0.003894,
            0.003578,
            0.003283,
            0.003008,
            0.002751,
            0.002512,
            0.002290,
            0.002083,
            0.001892,
            0.001714,
            0.001551,
            0.001400,
            0.001261,
            0.001134,
            0.001018,
            0.000913,
            0.000818,
            0.000732,
            0.000656,
            0.000588,
            0.000528,
            0.000475,
            0.000430,
            0.000392,
            0.000360,
            0.000334,
            0.000314,
            0.000299,
            0.000288
        )

        // Lowpass
        for (i in 0 until inputLen) {
            if (i < order1) {
                for (j in 0..order1) {
                    x[j] = if (j < order1 - i) 0.0 else signal[j - order1 + i].toDouble()
                }
            } else {
                for (j in 0..order1) {
                    x[j] = signal[i - order1 + j].toDouble()
                }
            }
            for (j in 0..order1) {
                y[i] += x[j] * lowFc[j]
            }
        }

        // Highpass
        for (i in 0 until inputLen) {
            temp[i] = y[i]
            y[i] = 0.0
        }

        for (i in 0 until inputLen) {
            if (i < order2) {
                for (j in 0..order2) {
                    x[j] = if (j < order2 - i) 0.0 else temp[j - order2 + i]
                }
            } else {
                for (j in 0..order2) {
                    x[j] = temp[i - order2 + j]
                }
            }
            for (j in 0..order2) {
                y[i] += x[j] * highFc[j]
            }
        }

        for (i in 0 until 126) {
            outputSig[i] = (y[i + 374] * -1).toFloat()
        }
        return outputSig
    }

    private fun voltage(changedata: ByteArray): Array<FloatArray> {
        val outsignal = Array(2) { FloatArray(18) { 0f } }
        val CH1 = Array(4) { FloatArray(18) { 0f } }
        val CH1data = ByteArray(3)
        val CH2 = Array(4) { FloatArray(18) { 0f } }
        val CH2data = ByteArray(3)

        var ch1Voltage: Float
        var ch2Voltage: Float

        var raw = 0
        var column = 0

        for (j in 4 until 76) {
            CH1[raw][column] = changedata[j].toFloat()
            raw++
            if (raw == 4) {
                raw = 0
                column++
            }
        }

        column = 0
        raw = 0
        for (j in 76 until 148) {
            CH2[raw][column] = changedata[j].toFloat()
            raw++
            if (raw == 4) {
                raw = 0
                column++
            }
        }

        // Convert CH1 data to voltage
        for (i in 0 until 18) {
            CH1data[0] = CH1[2][i].toInt().toByte()
            CH1data[1] = CH1[1][i].toInt().toByte()
            CH1data[2] = CH1[0][i].toInt().toByte()
            val ms = CH1data.joinToString("") { "%02X".format(it) }
            ch1Voltage = ms.toLong(radix = 16).toFloat()
            if (ch1Voltage >= 10000000) {
                ch1Voltage -= 16777216
            }
            ch1Voltage = ch1Voltage * 9 / 48 / 8388608
            outsignal[0][i] = ch1Voltage
        }

        // Convert CH2 data to voltage
        for (i in 0 until 18) {
            CH2data[0] = CH2[2][i].toInt().toByte()
            CH2data[1] = CH2[1][i].toInt().toByte()
            CH2data[2] = CH2[0][i].toInt().toByte()
            val ms2 = CH2data.joinToString("") { "%02X".format(it) }
            ch2Voltage = ms2.toLong(radix = 16).toFloat()
            if (ch2Voltage >= 10000000) {
                ch2Voltage -= 16777216
            }
            ch2Voltage = ch2Voltage * 9 / 48 / 8388608
            outsignal[1][i] = ch2Voltage
        }
        return outsignal
    }

    private fun record() {
        var recordVoltage: Array<FloatArray>
        val tmpExtraCh1 = DoubleArray(7500)
        val tmpExtraCh2 = DoubleArray(7500)

        while (true) {
            if (getstatus) {
                recordVoltage = dataConvert(readBuffer)

                for (i in 0 until 18) {
                    tempCH1[count] = recordVoltage[0][i]
                    tempCH2[count] = recordVoltage[1][i]
                    if (epochCount < 7500) {
                        tempEpochCH1[epochCount] = recordVoltage[0][i].toDouble()
                        tempEpochCH2[epochCount] = recordVoltage[1][i].toDouble()
                    } else {
                        tmpExtraCh1[epochCount - 7500] = recordVoltage[0][i].toDouble()
                        tmpExtraCh2[epochCount - 7500] = recordVoltage[1][i].toDouble()
                    }
                    count++
                    datapoint++
                    epochCount++
                }
                if (count >= 126) {
                    count = count - 126
                    if (datapoint > 504) {
                        System.arraycopy(dataCH1, 126, dataCH1, 0, 378)
                        System.arraycopy(tempCH1, 0, dataCH1, 378, 126)
                        drawCH1 = bandpassFilter(dataCH1)

                        nftpoint += 126
                        System.arraycopy(drawCH1, 0, NFTBufferCh1, 0, 126)

                        System.arraycopy(dataCH2, 126, dataCH2, 0, 378)
                        System.arraycopy(tempCH2, 0, dataCH2, 378, 126)
                        drawCH2 = bandpassFilter(dataCH2)

                        System.arraycopy(drawCH2, 0, NFTBufferCh2, 0, 126)

                        if (nftpoint / 250 >= 1) {
                            System.arraycopy(NFTBufferCh1, 0, NFTcalCh1, 0, 250)
                            System.arraycopy(NFTBufferCh2, 0, NFTcalCh2, 0, 250)

                            nftpoint -= 250
                            drawNftCH1 = plotFFT(NFTcalCh1)
                            drawNftCH2 = plotFFT(NFTcalCh2)

                            for (i in 0 until 250) {
                                if (NFTcalCh1[i] * 1000000 > 70 || NFTcalCh2[i] * 1000000 > 70) {
                                    checkdata = false
                                    break
                                }
                            }
                            if (checkdata == false) {
                                alphaCh1 = -1f
                                alphaCh2 = -1f
                                checkdata = true
                            } else {
                                alphaCh1 = (drawNftCH1[7] + drawNftCH1[8] + drawNftCH1[9] + drawNftCH1[10] + drawNftCH1[11] + drawNftCH1[12]).toFloat()
                                alphaCh2 = (drawNftCH2[7] + drawNftCH2[8] + drawNftCH2[9] + drawNftCH2[10] + drawNftCH2[11] + drawNftCH2[12]).toFloat()

                                deltaCh1 = (drawNftCH1[0] + drawNftCH1[1] + drawNftCH1[2] ).toFloat()
                                deltaCh2 = (drawNftCH2[0] + drawNftCH2[1] + drawNftCH2[2] ).toFloat()

                                thetaCh1 = (drawNftCH1[3] + drawNftCH1[4] + drawNftCH1[5] + drawNftCH1[6] ).toFloat()
                                thetaCh2 = (drawNftCH2[3] + drawNftCH2[4] + drawNftCH2[5] + drawNftCH2[6] ).toFloat()

                                betaCh1 = (drawNftCH1[15] + drawNftCH1[16] + drawNftCH1[17] + drawNftCH1[18]).toFloat()
                                betaCh2 = (drawNftCH2[15] + drawNftCH2[16] + drawNftCH2[17] + drawNftCH2[18]).toFloat()

                                if (writeAmplitude == true) {
                                    totalAlpha = (alphaCh1 + alphaCh2) * 1000000
                                    Log.d("FFT Alpha", totalAlpha.toString())

                                    totalBeta = (betaCh1 + betaCh2) * 1000000

                                    Log.d("FFT Beta", totalBeta.toString())

                                    totalDelta = (deltaCh1 + deltaCh2) * 1000000 / 3
                                    Log.d("FFT Delta", totalDelta.toString())

                                    totalTheta = (thetaCh1 + thetaCh2) * 1000000 / 3
                                    Log.d("FFT Theta", totalTheta.toString())

                                }
                            }
                            System.arraycopy(NFTcalCh1, 0, NFTcalCh1, 0, nftpoint)
                            System.arraycopy(NFTcalCh2, 0, NFTcalCh2, 0, nftpoint)
                            drawNFTstatus = true
                        }
                        drawstatus = true
                        getstatus = false
                    } else {
                        val temp2 = datapoint - 126
                        System.arraycopy(tempCH1, 0, dataCH1, temp2, 126)
                        System.arraycopy(tempCH2, 0, dataCH2, temp2, 126)
                    }
                }
                // Determine Stage
                if (epochCount >= 7500) {
                    epochCount -= 7500
                }
            }
        }
    }
}
