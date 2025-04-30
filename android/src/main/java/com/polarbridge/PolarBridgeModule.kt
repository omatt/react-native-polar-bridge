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

@ReactModule(name = PolarBridgeModule.NAME)
class PolarBridgeModule(reactContext: ReactApplicationContext) :
  NativePolarBridgeSpec(reactContext) {

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
    try {
      api.connectToDevice(deviceId)
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to connect to device. Reason $polarInvalidArgument ")
    }
  }

  override fun disconnectFromDevice(deviceId: String) {
    try {
      api.disconnectFromDevice(deviceId)
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to disconnect from device. Reason $polarInvalidArgument ")
    }
  }

  companion object {
    const val NAME = "PolarBridge"
    private const val TAG = "PolarBridgeModule"
    private const val API_LOGGER_TAG = "API LOGGER"
    private const val PERMISSION_REQUEST_CODE = 1
  }
}
