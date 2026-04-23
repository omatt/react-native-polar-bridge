package com.polarbridge.sensors

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class GyrStreamManager(
  private val api: PolarBleApi,
  private val sendEvent: (String, WritableMap) -> Unit,
  private val TAG: String = "GyrStreamManager"
) {
  private var gyrDisposable: Disposable? = null

  fun fetchGyrData(deviceId: String, sensorSettings: SensorSettings, ms: Double?, sensorBufferMs: Long) {
    Log.d(TAG, "Fetch Gyroscope Data called on: $deviceId ")
    val bufferMs = ms?.toLong()?.takeIf { it >= 0 } ?: sensorBufferMs
    val isDisposed = gyrDisposable?.isDisposed ?: true
    try {
      if (isDisposed) {
        gyrDisposable = sensorSettings.requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.GYRO)
          .flatMap { settings: PolarSensorSetting ->
            api.startGyroStreaming(deviceId, settings)
          }
          .flatMapIterable { it.samples }
          .buffer(bufferMs, TimeUnit.MILLISECONDS)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(
            { samples ->
              if (samples.isEmpty()) return@subscribe

              Log.d(TAG, "Flushing GYR buffer (${samples.size} samples)")

              for (data in samples) {
                // Log.d(TAG, "GYR    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}")

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
        disposeGyrStream()
        Log.d(TAG, "GYR stream stopped")
      }
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to fetch GYR Data. Reason $polarInvalidArgument ")
    }
  }

  fun disposeGyrStream(){
    try {
      gyrDisposable?.dispose()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to dispose GYR stream cleanly. Bluetooth service likely died.", e)
    } finally {
      gyrDisposable = null
    }
  }
}
