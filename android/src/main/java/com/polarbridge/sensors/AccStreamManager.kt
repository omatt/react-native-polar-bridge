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

class AccStreamManager(
  private val api: PolarBleApi,
  private val sendEvent: (String, WritableMap) -> Unit,
  private val TAG: String = "AccStreamManager"
) {
  private var accDisposable: Disposable? = null

  fun fetchAccData(deviceId: String, sensorSettings: SensorSettings, ms: Double?, sensorBufferMs: Long) {
    Log.d(TAG, "Fetch Accelerometer Data called on: $deviceId ")
    val bufferMs = ms?.toLong()?.takeIf { it >= 0 } ?: sensorBufferMs
    val isDisposed = accDisposable?.isDisposed ?: true
    try{
      if (isDisposed) {
        accDisposable = sensorSettings.requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC)
          .flatMap { settings: PolarSensorSetting ->
            api.startAccStreaming(deviceId, settings)
          }
          .flatMapIterable { it.samples }
          .buffer(bufferMs, TimeUnit.MILLISECONDS)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(
            { samples ->
              if (samples.isEmpty()) return@subscribe
              Log.d(TAG, "Flushing ACC buffer (${samples.size} samples)")

              for (data in samples) {
                // Log.d(TAG, "ACC    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}")

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
        disposeAccStream()
        Log.d(TAG, "ACC stream stopped")
      }
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to fetch ACC Data. Reason $polarInvalidArgument ")
    }
  }

  fun disposeAccStream(){
    try {
      accDisposable?.dispose()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to dispose ACC stream cleanly. Bluetooth service likely died.", e)
    } finally {
      accDisposable = null
    }
  }
}
