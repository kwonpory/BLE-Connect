package com.example.bleconnect

import android.app.Activity
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ExpandableListView
import android.widget.SimpleExpandableListAdapter
import android.widget.TextView

class DeviceControlActivity : Activity() {
    companion object {
        private val TAG: String = DeviceControlActivity::class.java.simpleName
        const val EXTRAS_DEVICE_NAME: String = "DEVICE_NAME"
        const val EXTRAS_DEVICE_ADDRESS: String = "DEVICE_ADDRESS"
        // intent filter 구성
        private fun makeGattUpdateIntentFilter(): IntentFilter {
            val intentFilter = IntentFilter()
            intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
            intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED)
            intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
            return intentFilter
        }
    }

    private var mConnectionState: TextView? = null
    private var mSystolic: TextView? = null
    private var mDiastolic: TextView? = null
    private var mPulse: TextView? = null
    private var mDeviceName: String? = null
    private var mDeviceAddress: String? = null
    private var mGattServicesList: ExpandableListView? = null
    private var mBluetoothLeService: BluetoothLeService? = null
    private var mGattCharacteristics: MutableList<MutableList<BluetoothGattCharacteristic>>? =
        mutableListOf(mutableListOf())
    private var mConnected: Boolean  = false
    private var mNotifyCharacteristic: BluetoothGattCharacteristic? = null

    private val LIST_NAME = "NAME"
    private val LIST_UUID = "UUID"

    // Service lifecycle 관리
    private val mServiceConnection = object: ServiceConnection {
        // 서비스 연결된 경우
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            // BluetoothLeService 객체 초기화
            mBluetoothLeService = (service as BluetoothLeService.LocalBinder).getService()
            if (!mBluetoothLeService!!.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth")
                finish()
            }
            // 디바이스 연결
            mBluetoothLeService!!.connect(mDeviceAddress)
        }
        // 서비스 끊긴 경우
        override fun onServiceDisconnected(componentName: ComponentName) {
            mBluetoothLeService = null  // null로 초기화
        }
    }

    // 서비스에서 실행한 이벤트 처리
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.
    // This can be a result of read or notification operations.
    private val mGattUpdateReceiver: BroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    mConnected = true
                    updateConnectionState(R.string.connected)
                    invalidateOptionsMenu()
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    mConnected = false
                    updateConnectionState(R.string.disconnected)
                    invalidateOptionsMenu()
//                    clearUI()
                }
                BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED -> {
                    // 사용자 인터페이스에서 지원되는 모든 서비스 및 특성을 표시
                    displayGattServices(mBluetoothLeService!!.getSupportedGattServices())
                }
                BluetoothLeService.ACTION_DATA_AVAILABLE -> {
                    displayData(intent.getParcelableExtra(BluetoothLeService.EXTRA_DATA)!!)
                }
            }
        }
    }

    private val servicesListClickListener = ExpandableListView.OnChildClickListener {
            parent: ExpandableListView?, view: View?, groupPosition: Int, childPosition: Int, id: Long ->

        if (mGattCharacteristics != null) {
            val characteristic: BluetoothGattCharacteristic =
                mGattCharacteristics!![groupPosition][childPosition]
            val charaProp: Int = characteristic.properties
            if (charaProp or BluetoothGattCharacteristic.PROPERTY_READ > 0) {
                if (mNotifyCharacteristic != null) {
                    mBluetoothLeService!!.setCharacteristicNotification(
                        mNotifyCharacteristic!!, false
                    )
                    mNotifyCharacteristic = null
                }
                mBluetoothLeService!!.readCharacteristic(characteristic)
            }
            if (charaProp or BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                mNotifyCharacteristic = characteristic
                mBluetoothLeService!!.setCharacteristicNotification(
                    characteristic, true
                )
            }
            return@OnChildClickListener true
        }
        false
    }

    private fun clearUI() {
        mGattServicesList!!.setAdapter(null as SimpleExpandableListAdapter?)
        mSystolic!!.setText(R.string.no_data)
        mDiastolic!!.setText(R.string.no_data)
        mPulse!!.setText(R.string.no_data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gatt_services_characteristics)

        // Intent로 같이 넘어온 디바이스 이름과 주소 추출
        val intent: Intent = intent
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME)
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS)

        // UI에 디바이스 주소 표시
        findViewById<TextView>(R.id.device_address).text = mDeviceAddress
        mGattServicesList = findViewById(R.id.gatt_services_list)   // 리스트 초기화
        mGattServicesList?.setOnChildClickListener(servicesListClickListener)    // 클릭 리스너
        mConnectionState = findViewById(R.id.connection_state)  // 연결 상태
        // 블루투스로부터 받아온 데이터
        mSystolic = findViewById(R.id.systolic)
        mDiastolic = findViewById(R.id.diastolic)
        mPulse = findViewById(R.id.pulse)

        actionBar?.title = mDeviceName  // 액션바 타이틀
        actionBar?.setDisplayHomeAsUpEnabled(true)  // 액션바 UP 버튼

        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE)    // 서비스 실행
    }

    override fun onResume() {
        super.onResume()
        // receiver 객체 시스템에 등록
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter())
        if (mBluetoothLeService != null) {
            val result: Boolean = mBluetoothLeService!!.connect(mDeviceAddress)
            Log.d(TAG, "Connect request result = $result")
        }
    }

    override fun onPause() {
        super.onPause()
        // broadcast receiver 해제
        unregisterReceiver(mGattUpdateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(mServiceConnection)
        mBluetoothLeService = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.gatt_services, menu)
        when (mConnected) {
            true -> {
                menu.findItem(R.id.menu_connect).isVisible = false
                menu.findItem(R.id.menu_disconnect).isVisible = true
            }
            else -> {
                menu.findItem(R.id.menu_connect).isVisible = true
                menu.findItem(R.id.menu_disconnect).isVisible = false
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_connect -> {
                mBluetoothLeService?.connect(mDeviceAddress)
                return true
            }
            R.id.menu_disconnect -> {
                mBluetoothLeService?.disconnect()
                return true
            }
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateConnectionState(resourceId: Int) {
        runOnUiThread {
            mConnectionState?.setText(resourceId)
        }
    }

    private fun displayData(data: BloodPressure) {
        mSystolic?.text = data.systolic.toString()
        mDiastolic?.text = data.diastolic.toString()
        mPulse?.text = data.pulse.toString()
    }

    private fun displayGattServices(gattServices: List<BluetoothGattService>?) {
        if (gattServices == null) return

        var uuid: String?
        val unknownServiceString: String = resources.getString(R.string.unknown_service)
        val unknownCharaString: String = resources.getString(R.string.unknown_characteristic)
        val gattServiceData: MutableList<HashMap<String, String>> = mutableListOf()
        val gattCharacteristicData: MutableList<ArrayList<HashMap<String, String>>> = mutableListOf()
        mGattCharacteristics = mutableListOf()

        // 사용 가능한 GATT Service Loop
        gattServices.forEach { gattService ->
            val currentServiceData = HashMap<String, String>()
            uuid = gattService.uuid.toString()
            currentServiceData[LIST_NAME] =
                GattAttributes.lookup(uuid!!, unknownServiceString)
            currentServiceData[LIST_UUID] = uuid!!
            gattServiceData += currentServiceData

            val gattCharacteristicGroupData: ArrayList<HashMap<String, String>> = arrayListOf()
            val gattCharacteristics = gattService.characteristics
            val charas: MutableList<BluetoothGattCharacteristic> = mutableListOf()

            // 사용 가능한 Characteristic Loop
            gattCharacteristics.forEach { gattCharacteristic ->
                charas += gattCharacteristic
                val currentCharaData: HashMap<String, String> = hashMapOf()
                uuid = gattCharacteristic.uuid.toString()
                currentCharaData[LIST_NAME] =
                    GattAttributes.lookup(uuid!!, unknownCharaString)
                currentCharaData[LIST_UUID] = uuid!!
                gattCharacteristicGroupData += currentCharaData
            }
            mGattCharacteristics!! += charas
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
        mGattServicesList?.setAdapter(gattServiceAdapter)
    }
}