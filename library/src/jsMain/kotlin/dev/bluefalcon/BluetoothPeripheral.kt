package dev.bluefalcon

actual class BluetoothPeripheral {
    actual val name: String?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    actual val services: List<BluetoothService>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    actual val uuid: String
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    actual var rssi: Float?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}
}