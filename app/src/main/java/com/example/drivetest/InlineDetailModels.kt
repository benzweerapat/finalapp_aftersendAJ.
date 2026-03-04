package com.example.drivetest

import java.io.Serializable

data class WifiInlineDetailData(
    val ssid: String = "-",
    val rssi: Int = -999,
    val frequency: String = "-",
    val channel: String = "-",
    val linkSpeed: String = "-",
    val security: String = "-",
    val bssid: String = "-",
    val latitude: String = "-",
    val longitude: String = "-"
) : Serializable

data class CellularInlineDetailData(
    val tech: String = "-",
    val operatorName: String = "-",
    val rsrp: Int = -999,
    val rsrq: Int = -999,
    val arfcn: String = "-",
    val freqBw: String = "-",
    val pci: String = "-",
    val tac: String = "-",
    val cellId: String = "-",
    val latitude: String = "-",
    val longitude: String = "-"
) : Serializable
