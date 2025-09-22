package com.polarbridge

import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.*
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.facebook.react.modules.core.DeviceEventManagerModule
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single

@ReactModule(name = PolarBridgeModule.NAME)
class PolarBridgeModule(reactContext: ReactApplicationContext) :
  NativePolarBridgeSpec(reactContext) {
  private val reactContext: ReactApplicationContext = reactContext

  override fun getName(): String {
    return NAME
  }

  // Example method
  // See https://reactnative.dev/docs/native-modules-android
  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }

  // ATTENTION! Replace with the device ID from your device.
  private var deviceId = "B4291522"

  private var hrDisposable: Disposable? = null
  private var scanDisposable: Disposable? = null
  private var accDisposable: Disposable? = null
  private var gyrDisposable: Disposable? = null

  private val api: PolarBleApi by lazy {
    // Notice all features are enabled
    PolarBleApiDefaultImpl.defaultImplementation(
      reactApplicationContext.applicationContext,
      setOf(
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

  override fun connectToDevice(deviceId: String) {
    api.setApiCallback(object : PolarBleApiCallback(){

      override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
        Log.d("Polar", "Connected: ${polarDeviceInfo.deviceId}")
      }

      override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
        TODO("Not yet implemented")
      }

      override fun htsNotificationReceived(
        identifier: String,
        data: PolarHealthThermometerData
      ) {
        TODO("Not yet implemented")
      }

      override fun batteryLevelReceived(identifier: String, level: Int) {
        Log.d("Polar", "Battery for $identifier: $level%")
      }
    })
    Log.e(TAG, "Connect device: $deviceId ")
    try {
      api.connectToDevice(deviceId)
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to connect to device. Reason $polarInvalidArgument ")
    }
  }

  override fun disconnectFromDevice(deviceId: String) {
    Log.e(TAG, "Disconnect device: $deviceId ")
    try {
      api.disconnectFromDevice(deviceId)
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to disconnect from device. Reason $polarInvalidArgument ")
    }
  }

  override fun scanDevices() {
    Log.e(TAG, "Scan Devices")
    val isDisposed = scanDisposable?.isDisposed ?: true
    if (isDisposed) {
      scanDisposable = api.searchForDevice()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { polarDeviceInfo: PolarDeviceInfo ->
            Log.d(TAG, "polar device found id: " + polarDeviceInfo.deviceId + " address: " + polarDeviceInfo.address + " rssi: " + polarDeviceInfo.rssi + " name: " + polarDeviceInfo.name + " isConnectable: " + polarDeviceInfo.isConnectable)

            val device = Arguments.createMap().apply {
              putString("deviceId", polarDeviceInfo.deviceId)
              putString("address", polarDeviceInfo.address)
              putInt("rssi", polarDeviceInfo.rssi)
              putString("name", polarDeviceInfo.name)
              putBoolean("isConnectable", polarDeviceInfo.isConnectable)
            }
            sendEvent("onDeviceFound", device)
          },
          { error: Throwable ->
            Log.e(TAG, "Device scan failed. Reason $error")
            sendEvent("onScanError", Arguments.createMap().apply {
              putString("message", error.message)
            })
          },
          {
            Log.d(TAG, "complete")
          }
        )
    } else {
      scanDisposable?.dispose()
      Log.d(TAG, "Device scan stopped")
    }
  }

  override fun fetchHrData(deviceId: String) {
    Log.e(TAG, "Fetch Heart Data called on: $deviceId ")
    val isDisposed = hrDisposable?.isDisposed ?: true
    try{
      if (isDisposed) {
        hrDisposable = api.startHrStreaming(deviceId)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(
            { hrData: PolarHrData ->
              Log.i(TAG, "PolarHrData ${hrData.samples.size}")
              for (sample in hrData.samples) {
                Log.d(TAG, "HR     bpm: ${sample.hr} " +
                  "rrs: ${sample.rrsMs} " +
                  "rrAvailable: ${sample.rrAvailable} " +
                  "contactStatus: ${sample.contactStatus} " +
                  "contactStatusSupported: ${sample.contactStatusSupported}")

                val event: WritableMap = Arguments.createMap()
                event.putInt("hr", sample.hr)

                val rrsArray: WritableArray = Arguments.createArray()
                sample.rrsMs.forEach { rrsValue ->
                  rrsArray.pushInt(rrsValue)
                }
                event.putArray("rrsMs", rrsArray)
                event.putBoolean("rrAvailable", sample.rrAvailable)
                event.putBoolean("contactStatus", sample.contactStatus)
                event.putBoolean("contactStatusSupported", sample.contactStatusSupported)

                sendEvent("PolarHrData", event)
              }
            },
            { error: Throwable ->
              Log.e(TAG, "HR stream failed. Reason $error")

              val errorEvent = Arguments.createMap()
              errorEvent.putString("error", error.toString())
              sendEvent("PolarHrError", errorEvent)
            },
            {
              Log.d(TAG, "HR stream complete")

              val completeEvent = Arguments.createMap()
              completeEvent.putString("message", "HR stream complete")
              sendEvent("PolarHrComplete", completeEvent)
            }
          )
      } else {
        // NOTE dispose will stop streaming if it is "running"
        hrDisposable?.dispose()
        Log.d(TAG, "HR stream stopped")
      }
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to fetch HR Data. Reason $polarInvalidArgument ")
    }
  }

  private fun requestStreamSettings(identifier: String, feature: PolarBleApi.PolarDeviceDataType): Flowable<PolarSensorSetting> {
    val availableSettings = api.requestStreamSettings(identifier, feature)
    val allSettings = api.requestFullStreamSettings(identifier, feature)
      .onErrorReturn { error: Throwable ->
        Log.w(TAG, "Full stream settings are not available for feature $feature. REASON: $error")
        PolarSensorSetting(emptyMap())
      }
    return Single.zip(availableSettings, allSettings) { available: PolarSensorSetting, all: PolarSensorSetting ->
      if (available.settings.isEmpty()) {
        throw Throwable("Settings are not available")
      } else {
        Log.d(TAG, "Feature " + feature + " available settings " + available.settings)
        Log.d(TAG, "Feature " + feature + " all settings " + all.settings)
        return@zip android.util.Pair(available, all)
      }
    }
      .observeOn(AndroidSchedulers.mainThread())
      .toFlowable()
      .flatMap { sensorSettings: android.util.Pair<PolarSensorSetting, PolarSensorSetting> ->
//        DialogUtility.showAllSettingsDialog(
//          this@MainActivity,
//          sensorSettings.first.settings,
//          sensorSettings.second.settings
//        ).toFlowable()
        Single.just(sensorSettings.first).toFlowable()  // Load default device settings
      }
  }

  override fun fetchAccData(deviceId: String) {
    Log.e(TAG, "Fetch Accelerometer Data called on: $deviceId ")
    val isDisposed = accDisposable?.isDisposed ?: true
    try{
      if (isDisposed) {
        accDisposable = requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC)
          .flatMap { settings: PolarSensorSetting ->
            api.startAccStreaming(deviceId, settings)
          }
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(
            { polarAccelerometerData: PolarAccelerometerData ->
              for (data in polarAccelerometerData.samples) {
                Log.d(TAG, "ACC    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}")

                val event: WritableMap = Arguments.createMap()
                event.putInt("accX", data.x)
                event.putInt("accY", data.y)
                event.putInt("accZ", data.z)
                // Long not supported, use double as workaround
                // See: https://github.com/facebook/react-native/issues/9685
                event.putDouble("accTimestamp", data.timeStamp.toDouble())

                sendEvent("PolarAccData", event)
              }
            },
            { error: Throwable ->
              Log.e(TAG, "ACC stream failed. Reason $error")

              val errorEvent = Arguments.createMap()
              errorEvent.putString("error", error.toString())
              sendEvent("PolarAccError", errorEvent)
            },
            {
              Log.d(TAG, "ACC stream complete")

              val completeEvent = Arguments.createMap()
              completeEvent.putString("message", "ACC stream complete")
              sendEvent("PolarAccComplete", completeEvent)
            }
          )
      } else {
        // NOTE dispose will stop streaming if it is "running"
        accDisposable?.dispose()
        Log.d(TAG, "ACC stream stopped")
      }
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to fetch ACC Data. Reason $polarInvalidArgument ")
    }
  }

  override fun fetchGyrData(deviceId: String) {
    Log.e(TAG, "Fetch Gyroscope Data called on: $deviceId ")
    val isDisposed = gyrDisposable?.isDisposed ?: true
    try {
      if (isDisposed) {
        gyrDisposable = requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.GYRO)
          .flatMap { settings: PolarSensorSetting ->
            api.startGyroStreaming(deviceId, settings)
          }
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(
            { polarGyroData: PolarGyroData ->
              for (data in polarGyroData.samples) {
                Log.d(TAG, "GYR    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}")

                val event: WritableMap = Arguments.createMap()
                // Float not supported
                // See: https://github.com/facebook/react-native/issues/9685
                // See: https://javadoc.io/doc/com.facebook.react/react-native/0.20.1/com/facebook/react/bridge/WritableMap.html
                event.putString("gyrX", "${data.x}")
                event.putString("gyrY", "${data.y}")
                event.putString("gyrZ", "${data.z}")
                // Long not supported, use double as workaround
                event.putDouble("gyrTimestamp", data.timeStamp.toDouble())

                sendEvent("PolarGyrData", event)
              }
            },
            { error: Throwable ->
              Log.e(TAG, "GYR stream failed. Reason $error")

              val errorEvent = Arguments.createMap()
              errorEvent.putString("error", error.toString())
              sendEvent("PolarGyrError", errorEvent)
            },
            {
              Log.d(TAG, "GYR stream complete")

              val completeEvent = Arguments.createMap()
              completeEvent.putString("message", "GYR stream complete")
              sendEvent("PolarGyrComplete", completeEvent)
            }
          )
      } else {
        gyrDisposable?.dispose()
        Log.d(TAG, "GYR stream stopped")
      }
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to fetch GYR Data. Reason $polarInvalidArgument ")
    }
  }

  override fun disposeHrStream(){
    hrDisposable?.dispose()
  }

  override fun disposeAccStream(){
    accDisposable?.dispose()
  }

  override fun disposeGyrStream(){
    gyrDisposable?.dispose()
  }

  private fun sendEvent(eventName: String, params: WritableMap?) {
    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }


  companion object {
    const val NAME = "PolarBridge"
    private const val TAG = "PolarBridgeModule"
    private const val API_LOGGER_TAG = "API LOGGER"
    private const val PERMISSION_REQUEST_CODE = 1
  }
}
