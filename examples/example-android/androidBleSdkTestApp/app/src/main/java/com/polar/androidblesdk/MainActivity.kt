package com.polar.androidblesdk

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.util.Pair
import com.google.android.material.snackbar.Snackbar
import com.polar.androidblesdk.util.FileExporter
import com.polar.androidblesdk.util.generateNewFile
import com.polar.androidblesdk.util.getSaveFolder
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.*
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import java.io.File
import java.util.*

/*//THESE ARE FOR THE HEAR RATE SENSOR
private const val HR_SERVICE_UUID = "0000180D-0000-1000-8000-00805F9B34FB"
private const val CHAR_FOR_READ_UUID = "00002A38-0000-1000-8000-00805F9B34FB"
private const val CHAR_FOR_WRITE_UUID = "25AE1443-05D3-4C5B-8281-93D4E07420CF"
private const val CHAR_FOR_NOTIFY_UUID = "00002A37-0000-1000-8000-00805F9B34FB"
private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"

 */



// THESE ARE (HOPEFULLY) FOR THE V02 MAX SENSOR
private const val HR_SERVICE_UUID = "00001523-1212-EFDE-1523-785FEABCD123"
private const val CHAR_FOR_READ_UUID = "00002A38-0000-1000-8000-00805F9B34FB" //DON'T KNOW WHAT TO PUT BUT I DON'T NEED TO READ ANYTHING ANYWAY
private const val CHAR_FOR_WRITE_UUID = "25AE1443-05D3-4C5B-8281-93D4E07420CF" //SAME HERE
private const val CHAR_FOR_NOTIFY_UUID = "00001527-1212-EFDE-1523-785FEABCD123" //SECOND CHAR OF THIS ONE IS DEF. BREATHS PER MIN
private const val CHAR_FOR_NOTIFY_UUID2 = "00001528-1212-EFDE-1523-785FEABCD123"
private const val CHAR_FOR_NOTIFY_UUID3 = "00001529-1212-EFDE-1523-785FEABCD123" //
private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"




class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val API_LOGGER_TAG = "API LOGGER"
        private const val PERMISSION_REQUEST_CODE = 1
    }

    // ATTENTION! Replace with the device ID from your device.
    private var deviceIdArray = arrayOf("C19E1A21", "C929ED29")

    private val api: PolarBleApi by lazy {
        // Notice all features are enabled
        PolarBleApiDefaultImpl.defaultImplementation(
            applicationContext, setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION
            )
        )
    }
    private lateinit var broadcastDisposable: Disposable
    private var scanDisposable: Disposable? = null
    private var autoConnectDisposable: Disposable? = null
    private var dcDisposable: Disposable? = null //this on is mine
    private var dcDisposable2Test: Disposable? = null
    private var dcDisposableArray: Array<Disposable?>? = null
    private var hrDisposable: Disposable? = null
    private var ecgDisposable: Disposable? = null
    private var accDisposable: Disposable? = null
    private var gyrDisposable: Disposable? = null
    private var magDisposable: Disposable? = null
    private var ppgDisposable: Disposable? = null
    private var ppiDisposable: Disposable? = null
    private var sdkModeEnableDisposable: Disposable? = null
    private lateinit var hRFileName: File
    private lateinit var aCCFileName: File
    private lateinit var gYRFileName: File
    private lateinit var mAGFileName: File
    private lateinit var pPGFileName: File


    private var sdkModeEnabledStatus = false
    private var deviceConnected = false
    private var bluetoothEnabled = false
    private var secondNotifyConnected = false
    private var thirdNotifyConnected = false

    private lateinit var connectButton: Button
    private lateinit var autoConnectButton: Button
    private lateinit var scanButton: Button
    private lateinit var connectNpButton: Button
    private lateinit var dataCollectButton: Button
    private lateinit var toggleSdkModeButton: Button
    private lateinit var changeSdkModeLedAnimationStatusButton: Button
    private lateinit var changePpiModeLedAnimationStatusButton: Button
    private lateinit var doFactoryResetButton: Button

    //BEGINNING OF NON POLAR CODE
    private val scanFilter =
        ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(HR_SERVICE_UUID))).build()


    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private var connectedGatt: BluetoothGatt? = null
    private var characteristicForRead: BluetoothGattCharacteristic? = null
    private var characteristicForWrite: BluetoothGattCharacteristic? = null
    private var characteristicForNotify1: BluetoothGattCharacteristic? = null
    private var characteristicForNotify2: BluetoothGattCharacteristic? = null
    private var characteristicForNotify3: BluetoothGattCharacteristic? = null
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.M)
    private val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT).setReportDelay(0).build()

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.d(TAG, "connected to $deviceAddress")

                    Handler(Looper.getMainLooper()).post {
                        gatt.discoverServices() //this might cause an error b/c i'm not posting the lifecycle state
                        //maybe add in the lifecycle state later
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "disconnected from $deviceAddress")
                    //setConnectGattToNull() may or may not need this
                    gatt.close()
                }
            } else {
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered services.count=${gatt.services.size} status=$status")

            if (status == 129) {
                Log.d(TAG, "ERROR 129")
                gatt.disconnect()
                return
            }
            val service = gatt.getService(UUID.fromString(HR_SERVICE_UUID)) ?: run {
                Log.d(TAG, "error: service not fount: $HR_SERVICE_UUID")
                gatt.disconnect()
                return
            }
            subscribeToNotifications(service.getCharacteristic(UUID.fromString(CHAR_FOR_NOTIFY_UUID)),gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (secondNotifyConnected && !thirdNotifyConnected){
                Log.d(TAG,"connectiong to 1529")
                val service = gatt.getService(UUID.fromString(HR_SERVICE_UUID))
                subscribeToNotifications(service.getCharacteristic(UUID.fromString(CHAR_FOR_NOTIFY_UUID3)),gatt)
                thirdNotifyConnected = true
            }

            if (!secondNotifyConnected){
                Log.d(TAG,"connecting to 1528")
                val service = gatt.getService(UUID.fromString(HR_SERVICE_UUID))
                subscribeToNotifications(service.getCharacteristic(UUID.fromString(CHAR_FOR_NOTIFY_UUID2)),gatt)
                secondNotifyConnected = true
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
        ) {
            val uuidString = characteristic.uuid.toString()
            val strValueForDebug = characteristic.value

            var logString = ""
            var secondToLastIdx = 0
            for(i in 0..(strValueForDebug.size-2)){
                logString += "${strValueForDebug[i]}, "
                secondToLastIdx = i
            }
            logString += "${strValueForDebug[secondToLastIdx+1]}\n"
            Log.d(TAG, "$uuidString Data: $logString")
            val file = File("${getSaveFolder().absolutePath}/${uuidString}.txt")
            file.appendText(logString)
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeStopBleScan() {
        bleScanner.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToNotifications(
        characteristic: BluetoothGattCharacteristic, gatt: BluetoothGatt
    ) {
        Log.d(TAG, "does this run twice")
        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                Log.d(TAG, "ERROR: setNotification(true) failed for ${characteristic.uuid}")
                return
            }

            generateNewFile("${characteristic.uuid}.txt")
            cccDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccDescriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name: String? = result.scanRecord?.deviceName ?: result.device.name
            Log.d(TAG, "NonPolar Connected to $name")
            safeStopBleScan()
            //if (!secondTest){
            result.device.connectGatt(
                this@MainActivity, false, gattCallback, BluetoothDevice.TRANSPORT_LE
            )
            Log.d(TAG, "FIRST CALL BACK")
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            Log.d(TAG, "onBatchScanResults, Ignoring")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.d(TAG, "onScanFailed errorCode=$errorCode")
            safeStopBleScan()
        }
    }

    private val listScanCallback = object : ScanCallback(){
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            Log.d(TAG, "Device Found: ${result.device.name}")
        }
    }
    //END OF NON POLAR CODE

    private fun setTimeStamp(deviceIDforFunc: String){
        val rightNow = Calendar.getInstance()
        rightNow.time = Date()
        api.setLocalTime(deviceIDforFunc,rightNow)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe()
    }
    private fun subscribeToPolarHR(deviceIDforFunc: String) {//THIS IS THE METHOD FOR CONNECTING A SECOND POLAR SENSOR.
        var newDisposable: Disposable =
            api.startHrStreaming(deviceIDforFunc).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ hrData: PolarHrData ->
                    for (sample in hrData.samples) {
                        val logString =
                            "$deviceIDforFunc  HR   bpm: ${sample.hr} rrs: ${sample.rrsMs} rrAvailable: ${sample.rrAvailable} contactStatus: ${sample.contactStatus} contactStatusSupported: ${sample.contactStatusSupported}"
                        Log.d(TAG, logString)
                        val fileString = "${System.currentTimeMillis()};${sample.hr}"
                        val file = File("${getSaveFolder().absolutePath}/$deviceIDforFunc-HRData.txt \n")
                        file.appendText(fileString)
                    }
                }, { error: Throwable ->
                    toggleButtonUp(dataCollectButton, "Data stream failed")
                    Log.e(TAG, "HR stream failed. Reason $error")
                }, { Log.d(TAG, "HR stream complete") })
    }

    private fun subscribeToPolarACC(deviceIDforFunc: String) {
        val accSettingsMap: MutableMap<PolarSensorSetting.SettingType, Int> =
            EnumMap(PolarSensorSetting.SettingType::class.java)
        accSettingsMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = 52
        accSettingsMap[PolarSensorSetting.SettingType.RESOLUTION] = 16
        accSettingsMap[PolarSensorSetting.SettingType.RANGE] = 8
        accSettingsMap[PolarSensorSetting.SettingType.CHANNELS] = 3
        val accSettings = PolarSensorSetting(accSettingsMap)
        accDisposable = api.startAccStreaming(deviceIDforFunc, accSettings)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ accData: PolarAccelerometerData ->
                for (data in accData.samples) {
                    val logString = "$deviceIDforFunc ACC    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}"
                    val file = File("${getSaveFolder().absolutePath}/$deviceIDforFunc-ACCData.txt")
                    val fileString = "${System.currentTimeMillis()};${data.timeStamp};${data.x};${data.y};${data.z}; \n"
                    file.appendText(fileString)
                    Log.d(TAG, logString)
                }
            }, { error: Throwable ->
                Log.e(TAG, "Acc stream failed because $error")
            }, { Log.d(TAG, "acc stream complete") })
    }

    private fun subscribeToPolarGYR(deviceIDforFunc: String) {
        val gyrSettingsMap: MutableMap<PolarSensorSetting.SettingType, Int> =
            EnumMap(PolarSensorSetting.SettingType::class.java)
        gyrSettingsMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = 52
        gyrSettingsMap[PolarSensorSetting.SettingType.RESOLUTION] = 16
        gyrSettingsMap[PolarSensorSetting.SettingType.RANGE] = 2000
        gyrSettingsMap[PolarSensorSetting.SettingType.CHANNELS] = 3
        val gyrSettings = PolarSensorSetting(gyrSettingsMap)
        gyrDisposable =
            api.startGyroStreaming(deviceIDforFunc, gyrSettings).observeOn(AndroidSchedulers.mainThread())
                .subscribe({ gyrData: PolarGyroData ->
                    for (data in gyrData.samples) {
                        val logString = "$deviceIDforFunc GYR    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}"
                        Log.d(TAG, logString)
                        val fileString = "${System.currentTimeMillis()};${data.timeStamp};${data.x};${data.y};${data.z} \n"
                        val file = File("${getSaveFolder().absolutePath}/$deviceIDforFunc-GYRData.txt")
                        file.appendText(fileString)
                    }
                }, { error: Throwable ->
                    Log.e(TAG, "GYR stream failed. Reason $error")
                }, { Log.d(TAG, "GYR stream complete") })
    }

    private fun subscribeToPolarMAG(deviceIDforFunc: String) {
        val magSettingsMap: MutableMap<PolarSensorSetting.SettingType, Int> =
            EnumMap(PolarSensorSetting.SettingType::class.java)
        magSettingsMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = 20
        magSettingsMap[PolarSensorSetting.SettingType.RESOLUTION] = 16
        magSettingsMap[PolarSensorSetting.SettingType.RANGE] = 50
        magSettingsMap[PolarSensorSetting.SettingType.CHANNELS] = 3
        val magSettings = PolarSensorSetting(magSettingsMap)
        magDisposable = api.startMagnetometerStreaming(deviceIDforFunc, magSettings)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ polarMagData: PolarMagnetometerData ->
                for (data in polarMagData.samples) {
                    val logString = "$deviceIDforFunc MAG    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}"
                    Log.d(TAG,logString)
                    val fileString = "${System.currentTimeMillis()};${data.timeStamp};${data.x};${data.y};${data.z} \n"
                    val file = File("${getSaveFolder().absolutePath}/$deviceIDforFunc-MAGData.txt")
                    file.appendText(fileString)
                }
            }, { error: Throwable ->
                Log.e(TAG, "MAGNETOMETER stream failed. Reason $error")
            }, { Log.d(TAG, "MAGNETOMETER stream complete") })
    }

    private fun subscribeToPolarPPG(deviceIDforFunc: String) {
        val ppgSettingsMap: MutableMap<PolarSensorSetting.SettingType, Int> =
            EnumMap(PolarSensorSetting.SettingType::class.java)
        ppgSettingsMap[PolarSensorSetting.SettingType.SAMPLE_RATE] = 135 //sensors appear to have different sample rates for ppg.
        //only one sample rate is availible when sdk mode is turned off. I haven't messed with sdk mode yet, so hoping to keep it off for now.
        //unless SDK mode is necessary for some reason, i would like to find sensors that have all the same ppg sample rates for the initial app.
        //probably all the ones that jin bought on sale will have 55 Hz sample rate.
        ppgSettingsMap[PolarSensorSetting.SettingType.RESOLUTION] = 22
        ppgSettingsMap[PolarSensorSetting.SettingType.CHANNELS] = 4
        val ppgSettings = PolarSensorSetting(ppgSettingsMap)
        ppgDisposable =
            api.startPpgStreaming(deviceIDforFunc, ppgSettings).subscribe({ polarPpgData: PolarPpgData ->
                    if (polarPpgData.type == PolarPpgData.PpgDataType.PPG3_AMBIENT1) {
                        for (data in polarPpgData.samples) {
                            val logString = "$deviceIDforFunc PPG    ppg0: ${data.channelSamples[0]} ppg1: ${data.channelSamples[1]} ppg2: ${data.channelSamples[2]} ambient: ${data.channelSamples[3]} timeStamp: ${data.timeStamp}"
                            Log.d(TAG, logString)
                            val fileString = "${System.currentTimeMillis()};${data.timeStamp};${data.channelSamples[0]};${data.channelSamples[1]};${data.channelSamples[2]};${data.channelSamples[3]} \n"
                            val file = File("${getSaveFolder().absolutePath}/$deviceIDforFunc-PPGData.txt")
                            file.appendText(fileString)
                        }
                    }
                }, { error: Throwable ->
                    Log.e(TAG, "PPG stream failed. Reason $error")
                }, { Log.d(TAG, "PPG stream complete") })
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "version: " + PolarBleApiDefaultImpl.versionInfo())
        connectButton = findViewById(R.id.connect_button)
        autoConnectButton = findViewById(R.id.auto_connect_button)
        scanButton = findViewById(R.id.scan_button)
        connectNpButton = findViewById(R.id.connect_np_button)
        dataCollectButton = findViewById(R.id.data_collect_button)
        toggleSdkModeButton = findViewById(R.id.toggle_SDK_mode)
        changeSdkModeLedAnimationStatusButton =
            findViewById(R.id.change_sdk_mode_led_animation_status)
        changePpiModeLedAnimationStatusButton =
            findViewById(R.id.change_ppi_mode_led_animation_status)
        doFactoryResetButton = findViewById(R.id.do_factory_reset)

        api.setPolarFilter(false)

        // If there is need to log what is happening inside the SDK, it can be enabled like this:
        val enableSdkLogs = false
        if (enableSdkLogs) {
            api.setApiLogger { s: String -> Log.d(API_LOGGER_TAG, s) }
        }

        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d(TAG, "BLE power: $powered")
                bluetoothEnabled = powered
                if (powered) {
                    showToast("Phone Bluetooth on")
                } else {
                    showToast("Phone Bluetooth off")
                }
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTED: ${polarDeviceInfo.deviceId}")
                val deviceId = polarDeviceInfo.deviceId
                deviceConnected = true
                val buttonText = getString(R.string.disconnect_from_device, deviceId)
                toggleButtonDown(connectButton, buttonText)
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTING: ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "DISCONNECTED: ${polarDeviceInfo.deviceId}")
                deviceConnected = false
                val buttonText = getString(R.string.connect_to_device, deviceIdArray[0])
                toggleButtonUp(connectButton, buttonText)
                toggleButtonUp(toggleSdkModeButton, R.string.enable_sdk_mode)
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.d(TAG, "DIS INFO uuid: $uuid value: $value")
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "BATTERY LEVEL: $level")
            }

            override fun hrNotificationReceived(
                identifier: String, data: PolarHrData.PolarHrSample
            ) {
                // deprecated
            }
        })

        connectButton.text = getString(R.string.connect_to_device, deviceIdArray[0])
        connectButton.setOnClickListener {
            try {
                if (deviceConnected) {
                    for (deviceId in deviceIdArray) {
                        api.disconnectFromDevice(deviceId)
                    }
                } else {
                    val rightNow = Calendar.getInstance()
                    for (deviceId in deviceIdArray) {
                        api.connectToDevice(deviceId)

                    }
                }
            } catch (polarInvalidArgument: PolarInvalidArgument) {
                val attempt = if (deviceConnected) {
                    "disconnect"
                } else {
                    "connect"
                }
                Log.e(TAG, "Failed to $attempt. Reason $polarInvalidArgument ")
            }
        }

        //CONNECT TO NON-POLAR DEVICE
        connectNpButton.setOnClickListener {
            //APPARENTLY THIS SCANS FOR, CONNECTS TO, THEN STARTS COLLECTING DATA
            //bleScanner.startScan(mutableListOf(scanFilter), scanSettings, scanCallback)
            //going to try getting a list of devices
            bleScanner.startScan(listScanCallback)
        }

        autoConnectButton.setOnClickListener {
            if (autoConnectDisposable != null) {
                autoConnectDisposable?.dispose()
            }
            autoConnectDisposable = api.autoConnectToDevice(-60, "180D", null)
                .subscribe({ Log.d(TAG, "auto connect search complete") },
                    { throwable: Throwable -> Log.e(TAG, "" + throwable.toString()) })
        }

        scanButton.setOnClickListener {
            val isDisposed = scanDisposable?.isDisposed ?: true
            if (isDisposed) {
                toggleButtonDown(scanButton, R.string.scanning_devices)
                scanDisposable = api.searchForDevice().observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ polarDeviceInfo: PolarDeviceInfo ->
                        Log.d(
                            TAG,
                            "polar device found id: " + polarDeviceInfo.deviceId + " address: " + polarDeviceInfo.address + " rssi: " + polarDeviceInfo.rssi + " name: " + polarDeviceInfo.name + " isConnectable: " + polarDeviceInfo.isConnectable
                        )
                    }, { error: Throwable ->
                        toggleButtonUp(scanButton, "Scan devices")
                        Log.e(TAG, "Device scan failed. Reason $error")
                    }, {
                        toggleButtonUp(scanButton, "Scan devices")
                        Log.d(TAG, "complete")
                    })
            } else {
                toggleButtonUp(scanButton, "Scan devices")
                scanDisposable?.dispose()
            }
        }

        dataCollectButton.setOnClickListener {
            val isDisposed = dcDisposable?.isDisposed ?: true
            if (isDisposed) {
                toggleButtonDown(dataCollectButton, "Stop Collecting Data")

                for (deviceId in deviceIdArray) {

                    setTimeStamp(deviceId)
                    subscribeToPolarHR(deviceId)
                    subscribeToPolarACC(deviceId)
                    subscribeToPolarGYR(deviceId)
                    subscribeToPolarMAG(deviceId)
                    subscribeToPolarPPG(deviceId)

                    hRFileName = generateNewFile("$deviceId-HRData.txt")
                    aCCFileName = generateNewFile("$deviceId-ACCData.txt")
                    gYRFileName = generateNewFile("$deviceId-GYRData.txt")
                    mAGFileName = generateNewFile("$deviceId-MAGData.txt")
                    pPGFileName = generateNewFile("$deviceId-PPGData.txt")

                    hRFileName.appendText("Phone timestamp;HR [bpm] \n")
                    aCCFileName.appendText("Phone timestamp;sensor timestamp [ns];X [mg];Y [mg];Z [mg] \n")
                    gYRFileName.appendText("Phone timestamp;sensor timestamp [ns];X [dps];Y [dps];Z [dps] \n")
                    mAGFileName.appendText("Phone timestamp;sensor timestamp [ns];X [G];Y [G];Z [G] \n")
                    pPGFileName.appendText("Phone timestamp;sensor timestamp [ns];channel 0;channel 1;channel 2;ambient \n")
                }



            } else {
                toggleButtonUp(dataCollectButton, "Start Data Collection")
                dcDisposable?.dispose()
            }
        }

        /* I might still need this, issue with ppg sample rates
        toggleSdkModeButton.setOnClickListener {
            toggleSdkModeButton.isEnabled = false
            if (!sdkModeEnabledStatus) {
                sdkModeEnableDisposable =
                    api.enableSDKMode(deviceId).observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            Log.d(TAG, "SDK mode enabled")
                            // at this point dispose all existing streams. SDK mode enable command
                            // stops all the streams but client is not informed. This is workaround
                            // for the bug.
                            disposeAllStreams()
                            toggleSdkModeButton.isEnabled = true
                            sdkModeEnabledStatus = true
                            toggleButtonDown(toggleSdkModeButton, R.string.disable_sdk_mode)
                        }, { error ->
                            toggleSdkModeButton.isEnabled = true
                            val errorString = "SDK mode enable failed: $error"
                            showToast(errorString)
                            Log.e(TAG, errorString)
                        })
            } else {
                sdkModeEnableDisposable =
                    api.disableSDKMode(deviceId).observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            Log.d(TAG, "SDK mode disabled")
                            toggleSdkModeButton.isEnabled = true
                            sdkModeEnabledStatus = false
                            toggleButtonUp(toggleSdkModeButton, R.string.enable_sdk_mode)
                        }, { error ->
                            toggleSdkModeButton.isEnabled = true
                            val errorString = "SDK mode disable failed: $error"
                            showToast(errorString)
                            Log.e(TAG, errorString)
                        })
            }
        }*/


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
                    ), PERMISSION_REQUEST_CODE
                )
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE
                )
            }
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (index in 0..grantResults.lastIndex) {
                if (grantResults[index] == PackageManager.PERMISSION_DENIED) {
                    Log.w(TAG, "No sufficient permissions")
                    showToast("No sufficient permissions")
                    return
                }
            }
            Log.d(TAG, "Needed permissions are granted")
        }
    }

    public override fun onPause() {
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        api.foregroundEntered()
    }

    public override fun onDestroy() {
        super.onDestroy()
        api.shutDown()
    }

    private fun toggleButtonDown(button: Button, text: String? = null) {
        toggleButton(button, true, text)
    }

    private fun toggleButtonDown(button: Button, @StringRes resourceId: Int) {
        toggleButton(button, true, getString(resourceId))
    }

    private fun toggleButtonUp(button: Button, text: String? = null) {
        toggleButton(button, false, text)
    }

    private fun toggleButtonUp(button: Button, @StringRes resourceId: Int) {
        toggleButton(button, false, getString(resourceId))
    }

    private fun toggleButton(button: Button, isDown: Boolean, text: String? = null) {
        if (text != null) button.text = text

        var buttonDrawable = button.background
        buttonDrawable = DrawableCompat.wrap(buttonDrawable!!)
        if (isDown) {
            DrawableCompat.setTint(buttonDrawable, resources.getColor(R.color.primaryDarkColor))
        } else {
            DrawableCompat.setTint(buttonDrawable, resources.getColor(R.color.primaryColor))
        }
        button.background = buttonDrawable
    }


    private fun showToast(message: String) {
        val toast = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG)
        toast.show()
    }

    private fun disposeAllStreams() {
        ecgDisposable?.dispose()
        accDisposable?.dispose()
        gyrDisposable?.dispose()
        magDisposable?.dispose()
        ppgDisposable?.dispose()
    }
}