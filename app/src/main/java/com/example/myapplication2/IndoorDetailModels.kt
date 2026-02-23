package com.example.myapplication2

import java.io.Serializable

data class CellularDetailArgs(
    val tech: String,
    val operatorName: String,
    val rsrp: Int,
    val rsrq: Int,
    val sinr: String,
    val arfcn: String,
    val freqBw: String,
    val pci: String,
    val tac: String,
    val cellId: String,
    val latitude: String,
    val longitude: String,
    val floor: String,
    val relHeight: String,
    val absAltitude: String,
    val pressure: String
) : Serializable

data class WifiDetailArgs(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val signalQuality: String,
    val snr: String,
    val freq: String,
    val channel: String,
    val bw: String,
    val linkSpeed: String,
    val security: String,
    val latitude: String,
    val longitude: String,
    val floor: String,
    val relHeight: String,
    val absAltitude: String,
    val pressure: String
) : Serializable
