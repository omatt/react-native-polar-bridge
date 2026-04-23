package com.polarbridge.sensors

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarSensorSetting
import com.polar.sdk.api.model.PolarPpgData
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class PpgStreamManager(
  private val api: PolarBleApi,
  private val sendEvent: (String, WritableMap) -> Unit,
  private val TAG: String = "PpgStreamManager"
) {
  private var ppgDisposable: Disposable? = null

  fun fetchPpgData(deviceId: String, sensorSettings: SensorSettings, ms: Double?, sensorBufferMs: Long) {
    Log.d(TAG, "Fetch Photoplethysmograph Data called on: $deviceId ")
    val bufferMs = ms?.toLong()?.takeIf { it >= 0 } ?: sensorBufferMs
    val isDisposed = ppgDisposable?.isDisposed ?: true
    try {
      if (isDisposed) {
        ppgDisposable = sensorSettings.requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.PPG)
          .flatMap { settings: PolarSensorSetting ->
            api.startPpgStreaming(deviceId, settings)
          }
          .filter { it.type == PolarPpgData.PpgDataType.PPG3_AMBIENT1 }
          .flatMapIterable { it.samples }
          .buffer(bufferMs, TimeUnit.MILLISECONDS)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(
            { samples ->
              if (samples.isEmpty()) return@subscribe

              Log.d(TAG, "Flushing PPG buffer (${samples.size} samples)")

              for (data in samples) {
                // Log.d(TAG, "PPG    ppg0: ${data.channelSamples[0]} ppg1: ${data.channelSamples[1]} ppg2: ${data.channelSamples[2]} ambient: ${data.channelSamples[3]} timeStamp: ${data.timeStamp}")

                val event: WritableMap = Arguments.createMap()
                // Float not supported
                // See: https://github.com/facebook/react-native/issues/9685
                // See: https://javadoc.io/doc/com.facebook.react/react-native/0.20.1/com/facebook/react/bridge/WritableMap.html
                event.putString("ppg0", "${data.channelSamples[0]}")
                event.putString("ppg1", "${data.channelSamples[1]}")
                event.putString("ppg2", "${data.channelSamples[2]}")
                event.putString("ambient", "${data.channelSamples[3]}")
                // Long not supported, use double as workaround
                event.putDouble("ppgTimestamp", data.timeStamp.toDouble())

                sendEvent("PolarPpgData", event)
              }
            },
            { error: Throwable ->
              Log.e(TAG, "PPG stream failed. Reason $error")

              val errorEvent = Arguments.createMap()
              errorEvent.putString("error", error.toString())
              sendEvent("PolarPpgError", errorEvent)
            },
            {
              Log.d(TAG, "PPG stream complete")

              val completeEvent = Arguments.createMap()
              completeEvent.putString("message", "PPG stream complete")
              sendEvent("PolarPpgComplete", completeEvent)
            }
          )
      } else {
        disposePpgStream()
        Log.d(TAG, "PPG stream stopped")
      }
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to fetch PPG Data. Reason $polarInvalidArgument ")
    }
  }

  fun disposePpgStream(){
    try {
      ppgDisposable?.dispose()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to dispose PPG stream cleanly. Bluetooth service likely died.", e)
    } finally {
      ppgDisposable = null
    }
  }
}
