package dev.bluefalcon

import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerScanOptionAllowDuplicatesKey
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateResetting
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

actual class BlueFalcon actual constructor(
    private val context: ApplicationContext,
    private val serviceUUID: String?
) {
    actual val delegates: MutableSet<BlueFalconDelegate> = mutableSetOf()

    private val centralManager: CBCentralManager
    private val bluetoothPeripheralManager = BluetoothPeripheralManager()
    private val peripheralDelegate = PeripheralDelegate()
    actual var isScanning: Boolean = false

    init {
        centralManager = CBCentralManager(bluetoothPeripheralManager, dispatch_get_main_queue())
    }

    actual fun connect(bluetoothPeripheral: BluetoothPeripheral) {
        centralManager.connectPeripheral(bluetoothPeripheral.bluetoothDevice, null)
    }

    actual fun disconnect(bluetoothPeripheral: BluetoothPeripheral) {
        centralManager.cancelPeripheralConnection(bluetoothPeripheral.bluetoothDevice)
    }

    @Throws
    actual fun scan() {
        isScanning = true
        when (centralManager.state) {
            CBManagerStateUnknown -> throw BluetoothUnknownException()
            CBManagerStateResetting -> throw BluetoothResettingException()
            CBManagerStateUnsupported -> throw BluetoothUnsupportedException()
            CBManagerStateUnauthorized -> throw BluetoothPermissionException()
            CBManagerStatePoweredOff -> throw BluetoothNotEnabledException()
            CBManagerStatePoweredOn -> {
                val options: Map<Any?, Any> =
                    mapOf(CBCentralManagerScanOptionAllowDuplicatesKey to NSNumber(true))
                if (serviceUUID != null) {
                    centralManager.scanForPeripheralsWithServices(listOf(serviceUUID), options)
                } else {
                    centralManager.scanForPeripheralsWithServices(null, options)
                }
            }
        }
    }

    actual fun stopScanning() {
        isScanning = false
        centralManager.stopScan()
    }

    actual fun readCharacteristic(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristic: BluetoothCharacteristic
    ) {
        bluetoothPeripheral.bluetoothDevice.readValueForCharacteristic(bluetoothCharacteristic.characteristic)
    }

    actual fun notifyCharacteristic(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristic: BluetoothCharacteristic,
        notify: Boolean
    ) {
        bluetoothPeripheral.bluetoothDevice.setNotifyValue(
            notify,
            bluetoothCharacteristic.characteristic
        )
    }

    actual fun writeCharacteristic(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristic: BluetoothCharacteristic,
        value: String
    ) {
        val formattedString = NSString.create(string = value)
        formattedString.dataUsingEncoding(NSUTF8StringEncoding)?.let {
            bluetoothPeripheral.bluetoothDevice.writeValue(
                it,
                bluetoothCharacteristic.characteristic,
                CBCharacteristicWriteWithResponse
            )
        }
    }

    actual fun changeMTU(bluetoothPeripheral: BluetoothPeripheral, mtuSize: Int) {
        println("Change MTU size called but not needed.")
        delegates.forEach {
            it.didUpdateMTU(bluetoothPeripheral)
        }
    }

    inner class BluetoothPeripheralManager : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            when (central.state) {
                CBManagerStateUnknown -> log("State 0 is .unknown")
                CBManagerStateResetting -> log("State 1 is .resetting")
                CBManagerStateUnsupported -> log("State 2 is .unsupported")
                CBManagerStateUnauthorized -> log("State 3 is .unauthorised")
                CBManagerStatePoweredOff -> log("State 4 is .poweredOff")
                CBManagerStatePoweredOn -> log("State 5 is .poweredOn")
                else -> log("State ${central.state.toInt()}")
            }
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber
        ) {
            if (isScanning) {
                log("Discovered device $didDiscoverPeripheral")
                val device = BluetoothPeripheral(didDiscoverPeripheral, rssiValue = RSSI.floatValue)
                delegates.forEach {
                    it.didDiscoverDevice(device)
                }
            }
        }

        override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
            log("DidConnectPeripheral ${didConnectPeripheral.name}")
            val device = BluetoothPeripheral(didConnectPeripheral, rssiValue = null)
            delegates.forEach {
                it.didConnect(device)
            }
            didConnectPeripheral.delegate = peripheralDelegate
            didConnectPeripheral.discoverServices(null)
        }

        override fun centralManager(
            central: CBCentralManager,
            didDisconnectPeripheral: CBPeripheral,
            error: NSError?
        ) {
            log("DidDisconnectPeripheral ${didDisconnectPeripheral.name}")
            val device = BluetoothPeripheral(didDisconnectPeripheral, rssiValue = null)
            delegates.forEach {
                it.didDisconnect(device)
            }
        }

    }

    inner class PeripheralDelegate : NSObject(), CBPeripheralDelegateProtocol {

        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverServices: NSError?
        ) {
            if (didDiscoverServices != null) {
                log("Error with service discovery ${didDiscoverServices}")
            } else {
                val device = BluetoothPeripheral(peripheral, rssiValue = null)
                delegates.forEach {
                    it.didDiscoverServices(device)
                }
                peripheral.services
                    ?.mapNotNull { it as? CBService }
                    ?.forEach {
                        peripheral.discoverCharacteristics(null, it)
                    }
            }
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverCharacteristicsForService: CBService,
            error: NSError?
        ) {
            if (error != null) {
                log("Error with characteristic discovery ${didDiscoverCharacteristicsForService}")
            }
            val device = BluetoothPeripheral(peripheral, rssiValue = null)
            delegates.forEach {
                it.didDiscoverCharacteristics(device)
            }
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateValueForCharacteristic: CBCharacteristic,
            error: NSError?
        ) {
            if (error != null) {
                log("Error with characteristic update ${error}")
            }
            log("didUpdateValueForCharacteristic")
            val device = BluetoothPeripheral(peripheral, rssiValue = null)
            val characteristic = BluetoothCharacteristic(didUpdateValueForCharacteristic)
            delegates.forEach {
                it.didCharacteristcValueChanged(
                    device,
                    characteristic
                )
            }
        }
    }

}
