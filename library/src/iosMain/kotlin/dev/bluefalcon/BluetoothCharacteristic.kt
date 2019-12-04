package dev.bluefalcon

import platform.CoreBluetooth.CBCharacteristic

actual class BluetoothCharacteristic(val characteristic: CBCharacteristic) {
    actual val name: String?
        get() = characteristic.UUID.description

    actual val value: String?
        get() = characteristic.value?.string()
}
