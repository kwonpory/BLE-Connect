package com.example.bleconnect

import android.annotation.SuppressLint
import android.app.ListActivity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

@SuppressLint("MissingPermission")
class DeviceScanActivity : ListActivity() {
    private lateinit var mLeDeviceListAdapter: LeDeviceListAdapter // 스캔 리스트 어댑터
    private var mBluetoothAdapter: BluetoothAdapter? = null    // 블루투스 어댑터
    private var mScanning: Boolean = false  // 스캐닝 확인 값
    private lateinit var mHandler: Handler  // 핸들러

    companion object {
        private const val REQUEST_ENABLE_BT: Int = 1
        // 10초 후에 스캔 종료
        private const val SCAN_PERIOD: Long = 10000   // 스캔 주기

        class ViewHolder {
            lateinit var deviceName: TextView
            lateinit var deviceAddress: TextView
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.setTitle(R.string.title_devices)
        mHandler = Handler()    // 핸들러 객체 생성
        // BLE 지원 여부 확인
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }
        // 블루투스 어댑터 초기화
        val bluetoothManager: BluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        mBluetoothAdapter = bluetoothManager.adapter
        // 블루투스 지원 안하는 경우
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).isVisible = false
            menu.findItem(R.id.menu_scan).isVisible = true
            menu.findItem(R.id.menu_refresh).actionView = null
        } else {
            menu.findItem(R.id.menu_stop).isVisible = true
            menu.findItem(R.id.menu_scan).isVisible = false
            menu.findItem(R.id.menu_refresh)?.setActionView(R.layout.actionbar_indeterminate_progress)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_scan -> {     // scan 메뉴 클릭 시 스캔 시작
                mLeDeviceListAdapter.clear()
                scanLeDevice(true)
            }
            R.id.menu_stop -> {     // stop 클릭 시 스캔 중지
                scanLeDevice(false)
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        // 블루투스 사용 가능 상태인지 확인.
        // 꺼져있으면 활성화 다이얼로그 창에 접근 요청
        if (!mBluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
        // list view adapter 초기화
        mLeDeviceListAdapter = LeDeviceListAdapter()
        listAdapter = mLeDeviceListAdapter
        scanLeDevice(true)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 사용자가 블루투스 허용 안한 경우
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_CANCELED) {
            finish()    // 앱 종료
            return
        }
    }

    override fun onPause() {
        super.onPause()
        scanLeDevice(false)
        mLeDeviceListAdapter.clear()
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {   // 각 아이템을 클릭했을 때
        if (mScanning) {    // 스캔 중지
            mScanning = false
            mBluetoothAdapter?.stopLeScan(mLeScanCallback)
        }
        val device: BluetoothDevice = mLeDeviceListAdapter.getDevice(position)    // 해당 위치의 디바이스

        val intent = Intent(this, DeviceControlActivity::class.java)    // intent 객체 생성
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.name)  // intent에 클릭된 디바이스 정보 넣기
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.address)

        startActivity(intent)   // 새로운 액티비티 ㄱㄱ
    }

    // 디바이스 스캔 메서드
    fun scanLeDevice(enable: Boolean) {
        when (enable) {
            true -> {
                // 스캔 주기가 끝나면 스캔 종료
                mHandler.postDelayed({
                    mScanning = false
                    mBluetoothAdapter?.stopLeScan(mLeScanCallback)
                    invalidateOptionsMenu()
                }, SCAN_PERIOD)
                // 스캔 시작
                mScanning = true
                mBluetoothAdapter?.startLeScan(mLeScanCallback)
            }
            else -> {
                // 스캔 중지
                mScanning = false
                mBluetoothAdapter?.stopLeScan(mLeScanCallback)
            }
        }
        invalidateOptionsMenu()
    }

    // 스캐닝을 통해 찾은 디바이스를 담을 어댑터
    private inner class LeDeviceListAdapter : BaseAdapter() {
        private var mLeDevices: MutableList<BluetoothDevice>
        private var mInflator: LayoutInflater?

        init {
            mLeDevices = mutableListOf()
            mInflator = this@DeviceScanActivity.layoutInflater
        }
        // 디바이스 추가 함수
        fun addDevice(device: BluetoothDevice) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device)
            }
        }

        fun getDevice(position: Int): BluetoothDevice {
            return mLeDevices[position]
        }

        fun clear() {
            mLeDevices.clear()
        }

        override fun getCount(): Int {
            return mLeDevices.size
        }

        override fun getItem(i: Int): Any {
            return mLeDevices[i]
        }

        override fun getItemId(i: Int): Long {
            return i.toLong()
        }

        override fun getView(i: Int, view: View?, viewGroup: ViewGroup?): View {
            val viewHolder: ViewHolder
            val resultView: View
            if (view == null) {
                resultView = mInflator!!.inflate(R.layout.listitem_device, null)
                viewHolder = ViewHolder()
                viewHolder.deviceName = resultView.findViewById(R.id.device_name)
                viewHolder.deviceAddress = resultView.findViewById(R.id.device_address)
                resultView?.tag = viewHolder
            } else {
                viewHolder = view.tag as ViewHolder
                resultView = view
            }
            val device: BluetoothDevice = mLeDevices[i]
            val deviceName: String? =  device.name

            viewHolder.deviceName.text = deviceName
            viewHolder.deviceAddress.text = device.address
            return resultView
        }
    }

    // 디바이스 스캔 콜백
    private val mLeScanCallback = BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
        runOnUiThread {
            if (device.name != null && device.name.isNotEmpty()) {
                mLeDeviceListAdapter.addDevice(device)
                mLeDeviceListAdapter.notifyDataSetChanged()
            }
        }
    }
}