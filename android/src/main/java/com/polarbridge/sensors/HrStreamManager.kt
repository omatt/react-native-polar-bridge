package com.polarbridge.sensors

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarHrData
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class HrStreamManager(
  private val api: PolarBleApi,
  private val sendEvent: (String, WritableMap) -> Unit,
  private val TAG: String = "HrStreamManager"
) {
  private var hrDisposable: Disposable? = null

  fun fetchHrData(deviceId: String, ms: Double?, sensorBufferMs: Long) {
    Log.d(TAG, "Fetch Heart Data called on: $deviceId ")

    val bufferMs = ms?.toLong()?.takeIf { it >= 0 } ?: sensorBufferMs
    val isDisposed = hrDisposable?.isDisposed ?: true

    try {
      if (isDisposed) {
        hrDisposable = api.startHrStreaming(deviceId)
          .flatMapIterable { hrData ->
            hrData.samples.map { sample ->
              HrSampleWithTimestamp(
                data = sample,
                timestampMs = System.currentTimeMillis()
              )
            }
          }
          .buffer(bufferMs, TimeUnit.MILLISECONDS)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(
            { samples ->
              if (samples.isEmpty()) return@subscribe

              Log.d(TAG, "Flushing HR buffer (${samples.size} samples)")

              samples.forEach { item ->
                val sample = item.data

                val event = Arguments.createMap().apply {
                  putInt("hr", sample.hr)

                  val rrsArray = Arguments.createArray()
                  sample.rrsMs.forEach { rrsArray.pushInt(it) }

                  putArray("rrsMs", rrsArray)
                  putBoolean("rrAvailable", sample.rrAvailable)
                  putBoolean("contactStatus", sample.contactStatus)
                  putBoolean("contactStatusSupported", sample.contactStatusSupported)
                  putDouble("timestamp", item.timestampMs.toDouble())
                }

                sendEvent("PolarHrData", event)
              }
            },
            { error ->
              Log.e(TAG, "HR stream failed. Reason $error")

              val errorEvent = Arguments.createMap().apply {
                putString("error", error.toString())
              }

              sendEvent("PolarHrError", errorEvent)
            },
            {
              Log.d(TAG, "HR stream complete")

              val completeEvent = Arguments.createMap().apply {
                putString("message", "HR stream complete")
              }

              sendEvent("PolarHrComplete", completeEvent)
            }
          )
      } else {
        disposeHrStream()
        Log.d(TAG, "HR stream stopped")
      }
    } catch (e: PolarInvalidArgument) {
      Log.e(TAG, "Failed to fetch HR Data. Reason $e")
    }
  }

  fun disposeHrStream() {
    try {
      hrDisposable?.dispose()
    } catch (e: Exception) {
      // Bluetooth service is dead, cleanup command failed.
      Log.w(TAG, "Failed to dispose HR stream cleanly. Bluetooth service likely died.", e)
    } finally {
      // Nullify the reference to flag that the stream is gone,
      // preventing memory leaks or further attempts to dispose of a dead stream.
      hrDisposable = null
    }
  }

  data class HrSampleWithTimestamp(
    val data: PolarHrData.PolarHrSample,
    val timestampMs: Long
  )
}
