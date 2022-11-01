package com.hussein.bluetoothlowenergy

import android.bluetooth.BluetoothDevice

interface OnDeviceListener {
    fun onSelectDevice(devicePosition: Int)
}