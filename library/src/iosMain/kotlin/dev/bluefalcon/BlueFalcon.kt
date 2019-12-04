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
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_MSEC
import platform.darwin.NSEC_PER_SEC
import platform.darwin.NSObject
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

actual class BlueFalcon actual constructor(
    context: ApplicationContext,
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
        Log.v { "connect $bluetoothPeripheral" }

        centralManager.connectPeripheral(bluetoothPeripheral.bluetoothDevice, null)
    }

    actual fun disconnect(bluetoothPeripheral: BluetoothPeripheral) {
        Log.v { "disconnect $bluetoothPeripheral" }

        centralManager.cancelPeripheralConnection(bluetoothPeripheral.bluetoothDevice)
    }

    @Throws
    actual fun scan() {
        when (centralManager.state) {
            CBManagerStateUnknown -> throw BluetoothUnknownException()
            CBManagerStateResetting -> throw BluetoothResettingException()
            CBManagerStateUnsupported -> throw BluetoothUnsupportedException()
            CBManagerStateUnauthorized -> throw BluetoothPermissionException()
            CBManagerStatePoweredOff -> throw BluetoothNotEnabledException()
            CBManagerStatePoweredOn -> {
                Log.v { "BT Scan started" }

                isScanning = true
                centralManager.startScan()
            }
        }
    }

    private fun CBCentralManager.startScan() {
        if (centralManager.state == CBManagerStateResetting) {
            Log.d { "wait until resetting done" }
            dispatch_after(
                dispatch_time(DISPATCH_TIME_NOW, 100 * NSEC_PER_MSEC.toLong()),
                dispatch_get_main_queue()
            ) {
                startScan()
            }
            return
        }

        val options: Map<Any?, Any> =
            mapOf(CBCentralManagerScanOptionAllowDuplicatesKey to NSNumber(true))
        if (serviceUUID != null) {
            scanForPeripheralsWithServices(listOf(serviceUUID), options)
        } else {
            scanForPeripheralsWithServices(null, options)
        }
    }

    actual fun stopScanning() {
        Log.v { "stopScanning" }

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
        Log.v { "Change MTU size called but not needed." }

        notifyDelegates { this.didUpdateMTU(bluetoothPeripheral) }
    }

    inner class BluetoothPeripheralManager : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            when (central.state) {
                CBManagerStateUnknown -> Log.d { "State 0 is .unknown" }
                CBManagerStateResetting -> Log.d { "State 1 is .resetting" }
                CBManagerStateUnsupported -> Log.d { "State 2 is .unsupported" }
                CBManagerStateUnauthorized -> Log.d { "State 3 is .unauthorised" }
                CBManagerStatePoweredOff -> Log.d { "State 4 is .poweredOff" }
                CBManagerStatePoweredOn -> Log.d { "State 5 is .poweredOn" }
                else -> Log.d { "State unknown - ${central.state.toInt()}" }
            }

            if (central.state != CBManagerStatePoweredOn) {
                notifyDelegates { this.scanDidFailed(IllegalStateException("state is ${central.state}")) }
            }
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber
        ) {
            if (!isScanning) return

            Log.d { "Discovered device $didDiscoverPeripheral" }
            val device = BluetoothPeripheral(didDiscoverPeripheral, rssiValue = RSSI.floatValue)
            notifyDelegates { this.didDiscoverDevice(device) }

            // we discover some device, now to prevent BT sleep - restart scanning
            // https://stackoverflow.com/questions/16577546/continuous-scanning-for-ios-corebluetooth-central-manager
            // https://stackoverflow.com/questions/29715667/core-bluetooth-cbperipheral-disconnects-every-10-seconds
            with(centralManager) {
                stopScan()
                startScan()
            }
        }

        override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
            Log.d { "DidConnectPeripheral ${didConnectPeripheral.name}" }

            val device = BluetoothPeripheral(didConnectPeripheral)
            notifyDelegates { this.didConnect(device) }

            didConnectPeripheral.delegate = peripheralDelegate
            didConnectPeripheral.discoverServices(null)
        }

        override fun centralManager(
            central: CBCentralManager,
            didDisconnectPeripheral: CBPeripheral,
            error: NSError?
        ) {
            Log.d { "DidDisconnectPeripheral ${didDisconnectPeripheral.name}" }

            val device = BluetoothPeripheral(didDisconnectPeripheral)

            notifyDelegates { this.didDisconnect(device) }
        }
    }

    inner class PeripheralDelegate : NSObject(), CBPeripheralDelegateProtocol {

        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverServices: NSError?
        ) {
            if (didDiscoverServices != null) {
                Log.e { "Error with service discovery $didDiscoverServices" }
                return
            }

            val device = BluetoothPeripheral(peripheral)

            notifyDelegates { this.didDiscoverServices(device) }

            peripheral.services
                ?.mapNotNull { it as? CBService }
                ?.forEach {
                    peripheral.discoverCharacteristics(null, it)
                }
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverCharacteristicsForService: CBService,
            error: NSError?
        ) {
            if (error != null) {
                Log.e { "Error with characteristic discovery $didDiscoverCharacteristicsForService" }
                return
            }

            val device = BluetoothPeripheral(peripheral)
            notifyDelegates { this.didDiscoverCharacteristics(device) }
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateValueForCharacteristic: CBCharacteristic,
            error: NSError?
        ) {
            Log.v { "didUpdateValueForCharacteristic" }

            if (error != null) {
                Log.e { "Error with characteristic update $error" }
                return
            }

            val device = BluetoothPeripheral(peripheral)
            val characteristic = BluetoothCharacteristic(didUpdateValueForCharacteristic)

            notifyDelegates {
                this.didCharacteristcValueChanged(device, characteristic)
            }
        }
    }
}
