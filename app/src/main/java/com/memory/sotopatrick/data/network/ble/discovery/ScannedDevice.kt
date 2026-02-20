package com.memory.sotopatrick.data.network.ble.discovery

data class ScannedDevice(val address: String, val avatar: Char, val name: String, val rssi: Int)