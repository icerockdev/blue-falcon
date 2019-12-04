package dev.bluefalcon

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult

actual class BluetoothPeripheral(
    val bluetoothDevice: BluetoothDevice,
    val scanResult: ScanResult? = null
) {
    actual val name: String?
        get() = scanResult?.scanRecord?.deviceName ?: bluetoothDevice.name

    actual val services: List<BluetoothService>
        get() = deviceServices

    actual val uuid: String
        get() = bluetoothDevice.address

    actual var rssi: Float? = scanResult?.rssi?.toFloat()

    var deviceServices: List<BluetoothService> = listOf()
}
