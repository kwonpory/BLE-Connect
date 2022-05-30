package com.example.bleconnect

class GattAttributes {
    companion object {
        private var attributes: HashMap<String, String> = HashMap()

        const val BLOOD_PRESSURE_MEASUREMENT = "00002a35-0000-1000-8000-00805f9b34fb"
        const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"

        init {
            // GATT Services
            attributes["00001800-0000-1000-8000-00805f9b34fb"] = "Generic Access"
            attributes["00001801-0000-1000-8000-00805f9b34fb"] = "Generic Attribute"
            attributes["0000180a-0000-1000-8000-00805f9b34fb"] = "Device Information"
            attributes["00001810-0000-1000-8000-00805f9b34fb"] = "Blood Pressure"
            attributes["0000180f-0000-1000-8000-00805f9b34fb"] = "Battery Service"
            attributes["00001805-0000-1000-8000-00805f9b34fb"] = "Current Time Service"

            // GATT Characteristics
            attributes["00002a00-0000-1000-8000-00805f9b34fb"] = "Device Name"
            attributes["00002a01-0000-1000-8000-00805f9b34fb"] = "Appearance"
            attributes["00002a02-0000-1000-8000-00805f9b34fb"] = "Peripheral Privacy Flag"
            attributes["00002a03-0000-1000-8000-00805f9b34fb"] = "Reconnection Address"
            attributes["00002a04-0000-1000-8000-00805f9b34fb"] = "Peripheral Preferred Connection Parameters"

            attributes["00002a05-0000-1000-8000-00805f9b34fb"] = "Service Changed"

            attributes["00002a23-0000-1000-8000-00805f9b34fb"] = "System ID"
            attributes["00002a24-0000-1000-8000-00805f9b34fb"] = "Model Number String"
            attributes["00002a25-0000-1000-8000-00805f9b34fb"] = "Serial Number String"
            attributes["00002a26-0000-1000-8000-00805f9b34fb"] = "Firmware Revision String"
            attributes["00002a27-0000-1000-8000-00805f9b34fb"] = "Hardware Revision String"
            attributes["00002a28-0000-1000-8000-00805f9b34fb"] = "Software Revision String"
            attributes["00002a29-0000-1000-8000-00805f9b34fb"] = "Manufacturer Name String"
            attributes["00002a2a-0000-1000-8000-00805f9b34fb"] = "IEEE 11073-20601 Regulatory Certification Data List"

            attributes["00002a35-0000-1000-8000-00805f9b34fb"] = "Blood Pressure Measurement"
            attributes["00002a49-0000-1000-8000-00805f9b34fb"] = "Blood Pressure Feature"

            attributes["00002a19-0000-1000-8000-00805f9b34fb"] = "Battery Level"

            attributes["00002a2b-0000-1000-8000-00805f9b34fb"] = "Current Time"
        }

        fun lookup(uuid: String, defaultName: String): String {
            val name: String? = attributes[uuid]
            return name ?: defaultName
        }
    }
}