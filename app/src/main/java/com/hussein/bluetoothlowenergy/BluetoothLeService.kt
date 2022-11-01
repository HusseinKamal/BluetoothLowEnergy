package com.hussein.bluetoothlowenergy

import android.app.Service
import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.STATE_CONNECTING
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import java.util.*


class BluetoothLeService : Service() {

    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionState = STATE_DISCONNECTED

    companion object {
        const val ACTION_GATT_CONNECTED =
            "com.hussein.bluetoothlowenergy.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED =
            "com.hussein.bluetoothlowenergy.ACTION_GATT_DISCONNECTED"

        const val ACTION_GATT_DISCOVERED =
            "com.hussein.bluetoothlowenergy.ACTION_GATT_DISCOVERED"

        const val ACTION_DATA_AVAILABLE =
            "com.hussein.bluetoothlowenergy.ACTION_GATT_AVAILABLE"

        const val EXTRA_DATA = "com.hussein.bluetoothlowenergy..EXTRA_DATA"

        val UUID_HEART_RATE_MEASUREMENT: UUID =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT)

        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTED = 2

    }

    private val bluetoothGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                connectionState = STATE_CONNECTED
                broadcastUpdate(ACTION_GATT_CONNECTED)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                connectionState = STATE_DISCONNECTED
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_DISCOVERED)
            } else {
                Toast.makeText(this@BluetoothLeService,"onServicesDiscovered received: $status",Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
        }
        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdateWithCharacteristic(ACTION_DATA_AVAILABLE, characteristic!!)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            broadcastUpdateWithCharacteristic(ACTION_DATA_AVAILABLE, characteristic!!)
        }
    }
    fun setCharacteristicNotification(characteristic: BluetoothGattCharacteristic, enabled: Boolean) {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            Toast.makeText(this@BluetoothLeService,"BluetoothAdapter not initialized.",Toast.LENGTH_SHORT).show()
            return
        }
        bluetoothGatt!!.setCharacteristicNotification(characteristic, enabled)

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.uuid)) {
            val descriptor = characteristic.getDescriptor(
                UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG)
            )
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            bluetoothGatt!!.writeDescriptor(descriptor)
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after `BluetoothGatt#discoverServices()` completes successfully.
     *
     * @return A `List` of supported services.
     */
    fun getSupportedGattServices(): List<BluetoothGattService?>? {
        return if (bluetoothGatt == null) null else bluetoothGatt!!.services
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService() : BluetoothLeService {
            return this@BluetoothLeService
        }
    }


    fun initialize(): Boolean {
        //Bluetooth adapter configuration
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter=manager.adapter
        if (bluetoothAdapter == null) {
            Log.e("BluetoothAdpater", "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    fun connect(address: String): Boolean {
        bluetoothAdapter?.let { adapter ->
            try {
                val device = adapter.getRemoteDevice(address)
                // connect to the GATT server on the device
                bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback)
                connectionState = STATE_CONNECTING;
                return true
            } catch (exception: IllegalArgumentException) {
               Toast.makeText(this@BluetoothLeService,"Device not found with provided address.  Unable to connect.",Toast.LENGTH_SHORT).show()
                return false
            }
        } ?: run {
            Toast.makeText(this@BluetoothLeService,"BluetoothAdapter not initialized.",Toast.LENGTH_SHORT).show()
            return false
        }
    }
    override fun onUnbind(intent: Intent?): Boolean {
        close()
        return super.onUnbind(intent)
    }

    private fun close() {
        bluetoothGatt?.let { gatt ->
            gatt.close()
            bluetoothGatt = null
        }
    }
    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    private fun broadcastUpdateWithCharacteristic(action: String, characteristic: BluetoothGattCharacteristic) {
        val intent = Intent(action)

        /**This is special handling for the Heart Rate Measurement profile.Data
        parsing is carried out as per profile specifications.**/
        when (characteristic.uuid) {
            UUID_HEART_RATE_MEASUREMENT -> {
                val flag = characteristic.properties
                val format = when (flag and 0x01) {
                    0x01 -> {
                        Toast.makeText(this@BluetoothLeService,"Heart rate format UINT16.",Toast.LENGTH_SHORT).show()
                        BluetoothGattCharacteristic.FORMAT_UINT16
                    }
                    else -> {
                        Toast.makeText(this@BluetoothLeService,"Heart rate format UINT8.",Toast.LENGTH_SHORT).show()
                        BluetoothGattCharacteristic.FORMAT_UINT8
                    }
                }
                val heartRate = characteristic.getIntValue(format, 1)
                Toast.makeText(this@BluetoothLeService,"Received heart rate: %d",Toast.LENGTH_SHORT).show()
                intent.putExtra(EXTRA_DATA, (heartRate).toString())
            }
            else -> {
                // For all other profiles, writes the data formatted in HEX.
                val data: ByteArray? = characteristic.value
                if (data?.isNotEmpty() == true) {
                    val hexString: String = data.joinToString(separator = " ") {
                        String.format("%02X", it)
                    }
                    intent.putExtra(EXTRA_DATA, "$data\n$hexString")
                }
            }
        }
        sendBroadcast(intent)
    }
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.let { gatt ->
            gatt.readCharacteristic(characteristic)
        } ?: run {
            Toast.makeText(this@BluetoothLeService,"BluetoothAdapter not initialized.",Toast.LENGTH_SHORT).show()
            return
        }
    }
}