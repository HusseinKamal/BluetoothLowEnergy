package com.hussein.bluetoothlowenergy

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.text.TextUtils
import android.widget.ExpandableListView.OnChildClickListener
import android.widget.SimpleExpandableListAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.hussein.bluetoothlowenergy.BluetoothLeService.Companion.ACTION_DATA_AVAILABLE
import com.hussein.bluetoothlowenergy.BluetoothLeService.Companion.ACTION_GATT_CONNECTED
import com.hussein.bluetoothlowenergy.BluetoothLeService.Companion.ACTION_GATT_DISCONNECTED
import com.hussein.bluetoothlowenergy.BluetoothLeService.Companion.ACTION_GATT_DISCOVERED
import com.hussein.bluetoothlowenergy.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity(),OnDeviceListener,GPSHelper.OnLocationEnableListener {
    private lateinit var binding: ActivityMainBinding
    private var bluetoothAdapter: BluetoothAdapter?=null
    private var bluetoothLeScanner:BluetoothLeScanner?=null
    private var selectedDevice:BluetoothDevice?=null
    private var scanning = false
    private var connected=false
    private val handler = Handler()
    // Stops scanning after 10 seconds.
    private val SCAN_PERIOD: Long = 30000

    private lateinit var leDeviceListAdapter:DevicesAdapter
    private var devicesList= ArrayList<BluetoothDevice>()

    //Connect with other devices with GATT Server
    private var bluetoothService : BluetoothLeService? = null

    private lateinit var mGattCharacteristics:MutableList<MutableList<BluetoothGattCharacteristic>>
    private val LIST_NAME = "NAME"
    private val LIST_UUID = "UUID"
    private var mNotifyCharacteristic: BluetoothGattCharacteristic? = null


    // Code to manage Service lifecycle.
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            bluetoothService = (service as BluetoothLeService.LocalBinder).getService()
            bluetoothService?.let { bluetooth ->
                // call functions on service to check connection and connect to devices
                if (!bluetooth.initialize()) {
                   Toast.makeText(this@MainActivity, "Unable to initialize Bluetooth",Toast.LENGTH_SHORT).show()
                   finish()
                }
                // perform device connection
                if(selectedDevice!=null) {
                    bluetooth.connect(selectedDevice!!.address)
                }

            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothService = null
        }
    }

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_GATT_CONNECTED -> {
                    connected = true
                    updateConnectionState("Connected")
                }
                ACTION_GATT_DISCONNECTED -> {
                    connected = false
                    updateConnectionState("DisConnected")
                }
                ACTION_GATT_DISCOVERED -> {
                    // Show all the supported services and characteristics on the user interface.
                    displayGattServices(bluetoothService?.getSupportedGattServices() as MutableList<BluetoothGattService>?)
                }
                ACTION_DATA_AVAILABLE -> {
                    updateConnectionState("Connected")
                    //connected = true
                    //updateConnectionState("Available")
                }
             }
            }
        }

    private fun updateConnectionState(str: String) {
        runOnUiThread { binding.mConnectionState.text = str }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
        if (bluetoothService != null && selectedDevice!=null) {
            val result = bluetoothService!!.connect(selectedDevice!!.address)
            Toast.makeText(this@MainActivity, "Connect request result=$result",Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(gattUpdateReceiver)
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        return IntentFilter().apply {
            addAction(ACTION_GATT_CONNECTED)
            addAction(ACTION_GATT_DISCONNECTED)
            addAction(ACTION_GATT_DISCOVERED)
            addAction(ACTION_DATA_AVAILABLE)
        }
    }


    private var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            checkPermission()
        }
        else
        {
            Toast.makeText(this,"Please Enable Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Data binding views
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //Bluetooth adapter configuration
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter=manager.adapter

        if(bluetoothAdapter!=null &&bluetoothAdapter!!.bluetoothLeScanner!=null) {
            bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
        }

        leDeviceListAdapter= DevicesAdapter(this,devicesList,this)
        binding.rvDevices.layoutManager=LinearLayoutManager(this,LinearLayoutManager.VERTICAL,false)
        binding.rvDevices.adapter=leDeviceListAdapter

        binding.mGattServicesList.setOnChildClickListener(servicesListClickListner);

        openBluetoothSetting()

    }
    private fun openBluetoothSetting()
    {
        //open bluetooth
        val blueToothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        resultLauncher.launch(blueToothIntent)
    }
    private fun checkPermission()
    {
        //Should Enable Location in BLE for Android SDK >= 10
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ), 2
                )
                if(!GPSHelper.isLocationEnabled(this)) {
                    GPSHelper(this,this)
                }
                else
                {
                    scanLeDevice()
                }

            }
            else
            {
                if(!GPSHelper.isLocationEnabled(this)) {
                    GPSHelper(this,this)
                }
                else
                {
                    scanLeDevice()
                }
            }
        }
        else
        {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED
                ||ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED
                ||ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                ||ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ), 2
                )
                if(!GPSHelper.isLocationEnabled(this)) {
                    GPSHelper(this,this)
                }
                else
                {
                    scanLeDevice()
                }
            }
            else
            {
                if(!GPSHelper.isLocationEnabled(this)) {
                    GPSHelper(this,this)
                }
                else
                {
                    scanLeDevice()
                }
            }
        }
    }


    private fun scanLeDevice() {
        if(bluetoothLeScanner==null) {
            if(bluetoothAdapter!=null)
            {
                bluetoothLeScanner=bluetoothAdapter!!.bluetoothLeScanner
            }
        }
        if (!scanning) { // Stops scanning after a pre-defined scan period.
            handler.postDelayed({
                scanning = false
                bluetoothLeScanner!!.startScan(leScanCallback)
            }, SCAN_PERIOD)
            scanning = true
            bluetoothLeScanner!!.startScan(leScanCallback)
        } else {
            scanning = false
            bluetoothLeScanner!!.startScan(leScanCallback)
        }

        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    // Device scan callback.
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if(!devicesList.contains(result.device) && result.device!=null) {
                if(!TextUtils.isEmpty(result.device.name)) {
                    devicesList.add(result.device)
                    leDeviceListAdapter.notifyDataSetChanged()
                }
            }

        }
    }

    override fun onSelectDevice(devicePosition: Int) {
        selectedDevice=devicesList[devicePosition]
        bluetoothService!!.connect(selectedDevice!!.address)
    }
    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
        bluetoothService = null
    }

    override fun onLocationEnabled(isEnable:Boolean) {
        if(isEnable) {
            scanLeDevice()
        }
        else
        {
            checkPermission()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && GPSHelper.isLocationEnabled(this)) {
                    scanLeDevice()
                }
                else
                {
                    checkPermission()
                }
            }
            else
            {
                checkPermission()
            }
        }
        else
        {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
                &&ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
                &&ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                &&ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && GPSHelper.isLocationEnabled(this)) {
                    scanLeDevice()
                }
                else
                {
                    checkPermission()
                }
            }
            else
            {
                checkPermission()
            }

        }
    }


    // Demonstrates how to iterate through the supported GATT
    // Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the
    // ExpandableListView on the UI.
    private fun displayGattServices(gattServices: MutableList<BluetoothGattService>?) {
        if (gattServices == null) return
        var uuid: String?
        val unknownServiceString ="Unknow Service"
        val unknownCharaString = "Unknown Characteristic"
        val gattServiceData: MutableList<HashMap<String, String>> = mutableListOf()
        val gattCharacteristicData: MutableList<ArrayList<HashMap<String, String>>> =
            mutableListOf()
        mGattCharacteristics = mutableListOf()

        // Loops through available GATT Services.
        gattServices.forEach { gattService ->
            val currentServiceData = HashMap<String, String>()
            uuid = gattService.uuid.toString()
            currentServiceData[LIST_NAME] = SampleGattAttributes.lookup(uuid!!, unknownServiceString)
            currentServiceData[LIST_UUID] = uuid!!
            gattServiceData += currentServiceData

            val gattCharacteristicGroupData: ArrayList<HashMap<String, String>> = arrayListOf()
            val gattCharacteristics = gattService.characteristics
            val charas: MutableList<BluetoothGattCharacteristic> = mutableListOf()

            // Loops through available Characteristics.
            gattCharacteristics.forEach { gattCharacteristic ->
                charas += gattCharacteristic
                val currentCharaData: HashMap<String, String> = hashMapOf()
                uuid = gattCharacteristic.uuid.toString()
                currentCharaData[LIST_NAME] = SampleGattAttributes.lookup(uuid!!, unknownCharaString)
                currentCharaData[LIST_UUID] = uuid!!
                gattCharacteristicGroupData += currentCharaData
            }
            mGattCharacteristics += charas
            gattCharacteristicData += gattCharacteristicGroupData
        }

        val gattServiceAdapter = SimpleExpandableListAdapter(
            this,
            gattServiceData,
            android.R.layout.simple_expandable_list_item_2,
            arrayOf(LIST_NAME, LIST_UUID),
            intArrayOf(android.R.id.text1, android.R.id.text2),
            gattCharacteristicData,
            android.R.layout.simple_expandable_list_item_2,
            arrayOf(LIST_NAME, LIST_UUID),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        binding.mGattServicesList.setAdapter(gattServiceAdapter)
    }

    private val servicesListClickListner =
        OnChildClickListener { parent, v, groupPosition, childPosition, id ->
            if (mGattCharacteristics != null) {
                val characteristic: BluetoothGattCharacteristic =
                    mGattCharacteristics[groupPosition].get(childPosition)
                val charaProp = characteristic.properties
                if (charaProp or BluetoothGattCharacteristic.PROPERTY_READ > 0) {
                    // If there is an active notification on a characteristic, clear
                    // it first so it doesn't update the data field on the user interface.
                    if (mNotifyCharacteristic != null) {
                        bluetoothService!!.setCharacteristicNotification(
                            mNotifyCharacteristic!!, false
                        )
                        mNotifyCharacteristic = null
                    }
                    bluetoothService!!.readCharacteristic(characteristic)
                }
                if (charaProp or BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                    mNotifyCharacteristic = characteristic
                    bluetoothService!!.setCharacteristicNotification(
                        characteristic, true
                    )
                }
                return@OnChildClickListener true
            }
            false
        }


}