package com.example.powernap.data

data class PowerNapResult(
    // channel 1 and 2
    val fSignal: Array<FloatArray>,
    val connectionState: ConnectionState,
    )
