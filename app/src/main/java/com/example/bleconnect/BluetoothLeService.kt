package com.example.bleconnect

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.util.*

@SuppressLint("MissingPermission")
class BluetoothLeService : Service() {
    companion object {
        private val TAG: String = BluetoothLeService::class.java.simpleName
        private const val STATE_DISCONNECTED: Int = 0
        private const val STATE_CONNECTING: Int = 1
        private const val STATE_CONNECTED: Int = 2

        const val ACTION_GATT_CONNECTED = "com.example.ble_connect.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.ble_connect.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED = "com.example.ble_connect.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "com.example.ble_connect.ACTION_DATA_AVAILABLE"
        const val EXTRA_DATA = "com.example.ble_connect.EXTRA_DATA"

        val UUID_BLOOD_PRESSURE_MEASUREMENT: UUID = UUID.fromString(GattAttributes.BLOOD_PRESSURE_MEASUREMENT)
    }

    private var mConnectionState: Int = STATE_DISCONNECTED
    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothDeviceAddress: String? = null
    private var mBluetoothGatt: BluetoothGatt? = null

    // GATT event에 대한 콜백 메서드 구현
    // ex) 연결 변경 및 겁색된 서비스
    private val mGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            val intentAction: String
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    intentAction = ACTION_GATT_CONNECTED
                    mConnectionState = STATE_CONNECTED
                    broadcastUpdate(intentAction)
                    Log.i(TAG, "Connected to GATT server.")
                    // connection 성공 후, service 검색 시도
                    Log.i(
                        TAG, "Attempting to start service discovery: " +
                            mBluetoothGatt?.discoverServices())
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    intentAction = ACTION_GATT_DISCONNECTED
                    mConnectionState = STATE_DISCONNECTED
                    Log.i(TAG, "Disconnected from GATT server.")
                    broadcastUpdate(intentAction)
                }
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val services: List<BluetoothGattService>
            val service: BluetoothGattService
            val characteristic: BluetoothGattCharacteristic

            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)

                    services = gatt.services
                    service = services[4]
                    characteristic = service.getCharacteristic(UUID_BLOOD_PRESSURE_MEASUREMENT)
                    setCharacteristicNotification(
                        characteristic, true
                    )
                    readCharacteristic(characteristic)
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
                }
                else -> Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
                }
            }
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
        }
    }

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic) {
        val intent = Intent(action)

        when (characteristic.uuid) {
            UUID_BLOOD_PRESSURE_MEASUREMENT -> {
                val flag = characteristic.properties
                var format: Int = -1
                if ((flag and 0x01) != 0) {
                    format = BluetoothGattCharacteristic.FORMAT_UINT16
                    Log.d(TAG, "Blood Pressure format UINT16.")
                } else {
                    format = BluetoothGattCharacteristic.FORMAT_UINT8
                    Log.d(TAG, "Blood Pressure format UINT8.")
                }

                val bloodPressure = BloodPressure(
                    characteristic.getIntValue(format, 1),
                    characteristic.getIntValue(format, 3),
                    characteristic.getIntValue(format, 14)
                )
                Log.d(TAG, "Received Blood Pressure: $bloodPressure")
                intent.putExtra(EXTRA_DATA, bloodPressure)
            }
            else -> {
                val data: ByteArray? = characteristic.value
                if (data?.isNotEmpty() == true) {
                    val stringBuilder = StringBuilder(data.size)
                    for (byteChar in data) stringBuilder.append(String.format("%02X ", byteChar))
                    intent.putExtra(EXTRA_DATA, "${String(data)}\n${stringBuilder.toString()}")
                }
            }
        }
        sendBroadcast(intent)
    }

    inner class LocalBinder : Binder() {
        fun getService() : BluetoothLeService {
            return this@BluetoothLeService
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onUnbind(intent: Intent): Boolean {
        close()
        return super.onUnbind(intent)
    }

    private val mBinder: IBinder = LocalBinder()

    // Bluetooth adapter 초기화
    // initialization 성공하면 return true
    fun initialize(): Boolean {
        // BluetoothManager 생성
        if (mBluetoothManager == null) {
            mBluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.")
                return false
            }
        }
        // BluetoothAdapter 생성
        mBluetoothAdapter = mBluetoothManager?.adapter
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    // BLE 디바이스의 GATT 서버에 connect
    fun connect(address: String?): Boolean {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.")
            return false
        }
        // 이전에 연결된 장치인 경우 reconnect
        if (mBluetoothDeviceAddress != null && address == mBluetoothDeviceAddress && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.")
            return if (mBluetoothGatt!!.connect()) {
                mConnectionState = STATE_CONNECTING
                true
            } else {
                false
            }
        }
        val device: BluetoothDevice? = mBluetoothAdapter!!.getRemoteDevice(address)
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.")
            return false
        }
        // Bluetooth Device GATT Server에 연결
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback)
        Log.d(TAG, "Trying to create a new connection.")
        mBluetoothDeviceAddress = address
        mConnectionState = STATE_CONNECTING
        return true
    }

    // 기존 연결 해제 or 보류 중인 연결 취소
    // 연결 해제 결과는 BluetoothGattCallback 클래스의 onConnectionStateChange() 콜백을 통해 비동기적으로 보고됨
    fun disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }
        mBluetoothGatt?.disconnect()
    }

    fun close() {
        if (mBluetoothGatt == null) {
            return
        }
        mBluetoothGatt?.close()
        mBluetoothGatt = null
    }

    // 지정된 {@code BluetoothGattCharacteristic}에 대한 읽기 요청
    // read result는 BluetoothGattCallback#onCharacteristicRead() 콜백을 통해 비동기적으로 보고됨
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }
        mBluetoothGatt?.readCharacteristic(characteristic)
    }

    // 지정된 특성에 애한 notification을 enable or disable
    fun setCharacteristicNotification(characteristic: BluetoothGattCharacteristic, enabled: Boolean) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }
        mBluetoothGatt?.setCharacteristicNotification(characteristic, enabled)

        if (UUID_BLOOD_PRESSURE_MEASUREMENT == characteristic.uuid) {
            val descriptor: BluetoothGattDescriptor = characteristic.getDescriptor(
                UUID.fromString(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG)
            )
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            mBluetoothGatt?.writeDescriptor(descriptor)
        }
    }

    // 연결된 디바이스에서 지원되는 GATT 서비스 목록 검색
    // 해당 호출은 BluetoothGatt#discoverServices가 완료된 후 호출해야 함
    fun getSupportedGattServices(): List<BluetoothGattService>? {
        if (mBluetoothGatt == null) return null
        return mBluetoothGatt!!.services
    }
}