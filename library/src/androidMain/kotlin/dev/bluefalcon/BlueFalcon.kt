package dev.bluefalcon

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import java.util.*

actual class BlueFalcon actual constructor(
    private val context: ApplicationContext,
    private val serviceUUID: String?
) {
    actual val delegates: MutableSet<BlueFalconDelegate> = mutableSetOf()
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val mBluetoothScanCallBack = BluetoothScanCallBack()
    private val mGattClientCallback = GattClientCallback()
    actual var isScanning: Boolean = false

    actual fun connect(bluetoothPeripheral: BluetoothPeripheral) {
        Log.v { "connect $bluetoothPeripheral" }

        bluetoothPeripheral.bluetoothDevice.connectGatt(context, false, mGattClientCallback)
    }

    actual fun disconnect(bluetoothPeripheral: BluetoothPeripheral) {
        Log.v { "disconnect $bluetoothPeripheral" }

        mGattClientCallback.gattForDevice(bluetoothPeripheral.bluetoothDevice)?.disconnect()

        notifyDelegates { this.didDisconnect(bluetoothPeripheral) }
    }

    actual fun stopScanning() {
        Log.v { "stopScanning" }

        isScanning = false
        bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(mBluetoothScanCallBack)
    }

    actual fun scan() {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            throw BluetoothPermissionException()
        Log.v { "BT Scan started" }

        isScanning = true

        val filterBuilder = ScanFilter.Builder()
        serviceUUID?.let {
            filterBuilder.setServiceUuid(ParcelUuid(UUID.fromString(it)))
        }
        val filter = filterBuilder.build()
        val filters = listOf(filter)
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val bluetoothScanner = bluetoothManager.adapter?.bluetoothLeScanner
        bluetoothScanner?.startScan(filters, settings, mBluetoothScanCallBack)
    }

    private fun fetchCharacteristic(
        bluetoothCharacteristic: BluetoothCharacteristic,
        gatt: BluetoothGatt
    ): List<BluetoothCharacteristic> =
        gatt.services.flatMap { service ->
            service.characteristics.filter {
                it.uuid == bluetoothCharacteristic.characteristic.uuid
            }.map {
                BluetoothCharacteristic(it)
            }
        }

    actual fun readCharacteristic(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristic: BluetoothCharacteristic
    ) {
        mGattClientCallback.gattForDevice(bluetoothPeripheral.bluetoothDevice)?.let { gatt ->
            fetchCharacteristic(bluetoothCharacteristic, gatt)
                .forEach { gatt.readCharacteristic(it.characteristic) }
        }
    }

    actual fun notifyCharacteristic(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristic: BluetoothCharacteristic,
        notify: Boolean
    ) {
        mGattClientCallback.gattForDevice(bluetoothPeripheral.bluetoothDevice)?.let { gatt ->
            fetchCharacteristic(bluetoothCharacteristic, gatt)
                .forEach {
                    gatt.setCharacteristicNotification(it.characteristic, notify)
                    it.characteristic.descriptors.forEach { descriptor ->
                        descriptor.value =
                            if (notify) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else byteArrayOf(
                                0x00,
                                0x00
                            )
                        gatt.writeDescriptor(descriptor)
                    }
                }
        }
    }

    actual fun writeCharacteristic(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristic: BluetoothCharacteristic,
        value: String
    ) {
        mGattClientCallback.gattForDevice(bluetoothPeripheral.bluetoothDevice)?.let { gatt ->
            fetchCharacteristic(bluetoothCharacteristic, gatt)
                .forEach {
                    it.characteristic.setValue(value)
                    gatt.writeCharacteristic(it.characteristic)
                }
        }
    }

    actual fun changeMTU(bluetoothPeripheral: BluetoothPeripheral, mtuSize: Int) {
        mGattClientCallback.gattForDevice(bluetoothPeripheral.bluetoothDevice)?.requestMtu(mtuSize)
    }

    inner class BluetoothScanCallBack : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            addScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { addScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e { "Failed to scan with code $errorCode" }

            notifyDelegates { this.scanDidFailed(IllegalStateException("code: $errorCode")) }
        }

        private fun addScanResult(result: ScanResult?) {
            Log.d { "result $result" }

            val device = result?.device ?: return

            val devicePeripheral = BluetoothPeripheral(device, result)

            Log.v { "device $devicePeripheral" }

            notifyDelegates { this.didDiscoverDevice(devicePeripheral) }
        }

    }

    inner class GattClientCallback : BluetoothGattCallback() {

        private val gatts: MutableList<BluetoothGatt> = mutableListOf()

        private fun addGatt(gatt: BluetoothGatt) {
            if (gatts.firstOrNull { it.device == gatt.device } == null) {
                gatts.add(gatt)
            }
        }

        fun gattForDevice(bluetoothDevice: BluetoothDevice): BluetoothGatt? =
            gatts.firstOrNull { it.device == bluetoothDevice }

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.v { "onConnectionStateChange" }

            gatt?.let { bluetoothGatt ->
                bluetoothGatt.device.let {
                    addGatt(bluetoothGatt)
                    bluetoothGatt.readRemoteRssi()
                    bluetoothGatt.discoverServices()
                    val bluetoothPeripheral = BluetoothPeripheral(bluetoothGatt.device)

                    notifyDelegates { this.didConnect(bluetoothPeripheral) }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.d { "onServicesDiscovered $gatt $status" }
            if (status != BluetoothGatt.GATT_SUCCESS) return

            val bluetoothDevice = gatt?.device ?: return
            val services = gatt.services ?: return

            Log.v { "onServicesDiscovered -> $services" }

            val bluetoothPeripheral = BluetoothPeripheral(bluetoothDevice)
            bluetoothPeripheral.deviceServices = services.map { BluetoothService(it) }

            notifyDelegates {
                this.didDiscoverServices(bluetoothPeripheral)
                this.didDiscoverCharacteristics(bluetoothPeripheral)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.v { "onMtuChanged $mtu status:$status" }

            if (status != BluetoothGatt.GATT_SUCCESS) return

            val bluetoothDevice = gatt?.device ?: return

            val bluetoothPeripheral = BluetoothPeripheral(bluetoothDevice)
            notifyDelegates { this.didUpdateMTU(bluetoothPeripheral) }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            Log.v { "onReadRemoteRssi $rssi" }

            val bluetoothDevice = gatt?.device ?: return

            val bluetoothPeripheral = BluetoothPeripheral(bluetoothDevice)
            bluetoothPeripheral.rssi = rssi.toFloat()

            notifyDelegates { this.didRssiUpdate(bluetoothPeripheral) }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            handleCharacteristicValueChange(gatt, characteristic)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            handleCharacteristicValueChange(gatt, characteristic)
        }

        private fun handleCharacteristicValueChange(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            Log.d { "handleCharacteristicValueChange $gatt $characteristic" }

            val forcedCharacteristic = characteristic ?: return
            val bluetoothDevice = gatt?.device ?: return

            val btCharacteristic = BluetoothCharacteristic(forcedCharacteristic)
            val btPeripheral = BluetoothPeripheral(bluetoothDevice)

            notifyDelegates {
                this.didCharacteristcValueChanged(btPeripheral, btCharacteristic)
            }
        }
    }

}