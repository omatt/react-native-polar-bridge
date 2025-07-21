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
import com.facebook.react.modules.core.DeviceEventManagerModule

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

  val isDisposed = hrDisposable?.isDisposed ?: true
  override fun fetchHrData(deviceId: String) {
    Log.e(TAG, "Fetch Heart Data called on: $deviceId ")
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

  override fun disposeHrStream(){
    hrDisposable?.dispose()
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
