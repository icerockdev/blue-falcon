package dev.bluefalcon

import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBService

actual class BluetoothPeripheral(
    val bluetoothDevice: CBPeripheral,
    val rssiValue: Float?,
    advertisementData: Map<Any?, *>? = null
) {
    actual val name: String? = bluetoothDevice.name
        ?: advertisementData?.get(CBAdvertisementDataLocalNameKey) as? String

    actual var rssi: Float? = rssiValue

    actual val services: List<BluetoothService>
        get() = bluetoothDevice.services?.map {
            BluetoothService(it as CBService)
        } ?: emptyList()

    actual val uuid: String
        get() = bluetoothDevice.identifier.UUIDString
}