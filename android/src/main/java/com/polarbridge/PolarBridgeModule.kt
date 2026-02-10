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
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import java.time.Instant

@ReactModule(name = PolarBridgeModule.NAME)
class PolarBridgeModule(reactContext: ReactApplicationContext) :
  NativePolarBridgeSpec(reactContext) {
  private val reactContext: ReactApplicationContext = reactContext
  private val SENSOR_BUFFER_MS = 10_000L

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
  private var ppgDisposable: Disposable? = null

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

  override fun connectToDevice(deviceId: String, promise: Promise) {
    val map: WritableMap = Arguments.createMap()
    var deviceConnected = false
    var batteryReceived = false
    api.setApiCallback(object : PolarBleApiCallback() {
      override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
        Log.d("Polar", "Connected: ${polarDeviceInfo.deviceId}")
        map.putString("connectedDeviceId", polarDeviceInfo.deviceId)
        deviceConnected = true
        runResolve()
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
        map.putInt("batteryLevel", level)
        batteryReceived = true
        runResolve()
      }

      private fun runResolve() {
        if (deviceConnected && batteryReceived) {
          promise.resolve(map)
        }
      }
    })
    Log.e(TAG, "Connect device: $deviceId ")
    try {
      api.connectToDevice(deviceId)
    } catch (polarInvalidArgument: PolarInvalidArgument) {
      Log.e(TAG, "Failed to connect to device. Reason $polarInvalidArgument ")
      promise.reject("INVALID_ARGUMENT", "Invalid device ID", polarInvalidArgument)
    }
  }

  override fun disconnectFromDevice(deviceId: String) {
    Log.e(TAG, "Disconnect device: $deviceId ")
    try {
      api.disconnectFromDevice(deviceId)
    } catch (polarInvalidArgument: PolarInvalidArgument) {
      Log.e(TAG, "Failed to disconnect from device. Reason $polarInvalidArgument ")
    }
  }

  override fun enableSdkMode(deviceId: String) {
    Log.e(TAG, "Enable SDK Mode device: $deviceId ")
    try {
      // Dispose all existing streams. SDK mode enable command stops all the streams
      // but client is not informed. This is workaround for the bug.
      disposeAllStreams()
      api.enableSDKMode(deviceId)
    } catch (polarInvalidArgument: PolarInvalidArgument) {
      Log.e(TAG, "Failed to enable SDK mode on device. Reason $polarInvalidArgument ")
    }
  }

  override fun disableSdkMode(deviceId: String) {
    Log.e(TAG, "Disable SDK Mode device: $deviceId ")
    try {
      api.disableSDKMode(deviceId)
    } catch (polarInvalidArgument: PolarInvalidArgument) {
      Log.e(TAG, "Failed to disable SDK mode on device. Reason $polarInvalidArgument ")
    }
  }

  val recordingKey = PolarRecordingSecret(
    byteArrayOf(
      0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
      0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07
    )
  )

  override fun setPolarRecordingTrigger(deviceId: String, recordingMode: Double, features: ReadableArray) {
    Log.e(TAG, "Set Offline Recording Trigger on device: $deviceId, Recording Mode: $recordingMode")

    // Placeholder recording secret key for Offline Recording
    // Use to encrypt/decrypt offline recordings

    // Check for configured trigger mode
    val recordingTrigger = when (recordingMode.toInt()) {
      0 -> PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED
      1 -> PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START
      2 -> PolarOfflineRecordingTriggerMode.TRIGGER_EXERCISE_START
      else -> PolarOfflineRecordingTriggerMode.TRIGGER_DISABLED
    }

    try {
      val triggerSettings = PolarOfflineRecordingTrigger(
        triggerMode = recordingTrigger,
        triggerFeatures = buildTriggerFeatures(features),
      )

      // Secret Key is optional, can be implemented if needed, currently set to null
      api.setOfflineRecordingTrigger(deviceId, triggerSettings, recordingKey)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          {
            Log.d(TAG, "Offline Recording Trigger set successfully for device: $deviceId")
          },
          { error ->
            Log.e(TAG, "Failed to set trigger: ${error.localizedMessage}", error)
          }
        )

      // Check if changes has been applied
      api.getOfflineRecordingTriggerSetup(deviceId)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { trigger: PolarOfflineRecordingTrigger ->
            // Success callback
            Log.d(TAG, "Offline recording trigger fetched successfully")
            Log.d(TAG, "Trigger ID: ${trigger.triggerMode}")
            Log.d(TAG, "Trigger Features: ${trigger.triggerFeatures}")
          },
          { error: Throwable ->
            // Error callback
            Log.e("PolarTrigger", "Error fetching trigger: ${error.localizedMessage}", error)
          }
        )
    } catch (polarInvalidArgument: PolarInvalidArgument) {
      Log.e(TAG, "Failed to set Offline Recording Trigger on device. Reason $polarInvalidArgument ")
    }
  }

  override fun fetchOfflineRecordings(deviceId: String, promise: Promise) {
    try {
      val array: WritableArray = Arguments.createArray()
      api.listOfflineRecordings(deviceId)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { polarOfflineRecordingEntry: PolarOfflineRecordingEntry ->
            Log.d(
              TAG,
              "next: ${polarOfflineRecordingEntry.date} path: ${polarOfflineRecordingEntry.path}, size: ${polarOfflineRecordingEntry.size}"
            )

            val map: WritableMap = Arguments.createMap()
            map.putDouble("recTimestamp", polarOfflineRecordingEntry.date.time.toDouble())
            map.putString("path", polarOfflineRecordingEntry.path)
            map.putDouble("size", polarOfflineRecordingEntry.size.toDouble())
            array.pushMap(map)
          },
          { error: Throwable ->
            Log.e(TAG, "Failed to list recordings: $error")
            promise.reject("FETCH_RECORDING_ERROR", "Failed to fetch offline recordings", error)
          },
          {
            Log.d(TAG, "List recordings complete")
            promise.resolve(array)
          }
        )
    } catch (polarInvalidArgument: PolarInvalidArgument) {
      Log.e(TAG, "Failed to fetch offline recordings. Reason $polarInvalidArgument ")
      promise.reject("INVALID_ARGUMENT", "Invalid device ID", polarInvalidArgument)
    }
  }

  override fun startOfflineRecording(deviceId: String, features: ReadableArray, promise: Promise) {
    // Convert ReadableArray to List<String>
    val featureList = List(features.size()) { index -> features.getString(index).orEmpty() }
    val polarSensorSettings = buildTriggerFeatures(features);
    try {
      for (feature in featureList) {
        when (feature) {
          "OfflineHR" -> {
            Log.d(TAG, "Start OfflineHR Recording")
            startPolarOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.HR,
              polarSensorSettings[PolarBleApi.PolarDeviceDataType.HR], promise)
          }
          "OfflineACC" -> {
            Log.d(TAG, "Start OfflineACC Recording")
            startPolarOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.ACC,
              polarSensorSettings[PolarBleApi.PolarDeviceDataType.ACC], promise)
          }
          "OfflineGYR" -> {
            Log.d(TAG, "Start OfflineGYR Recording")
            startPolarOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.GYRO,
              polarSensorSettings[PolarBleApi.PolarDeviceDataType.GYRO], promise)
          }
          "OfflinePPG" -> {
            Log.d(TAG, "Start OfflinePPG Recording")
            startPolarOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.PPG,
              polarSensorSettings[PolarBleApi.PolarDeviceDataType.PPG], promise)
          }
          "OfflineMAG" -> {
            Log.d(TAG, "Start OfflineMAG Recording")
            startPolarOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.MAGNETOMETER,
              polarSensorSettings[PolarBleApi.PolarDeviceDataType.MAGNETOMETER], promise)
          }
          "OfflinePPI" -> {
            Log.d(TAG, "Start OfflinePPI Recording")
            startPolarOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.PPI,
              polarSensorSettings[PolarBleApi.PolarDeviceDataType.PPI], promise)
          }
          else -> {
            Log.d(TAG, "Unknown feature: $feature")
          }
        }
      }
    } catch (polarInvalidArgument: PolarInvalidArgument) {
      Log.e(TAG, "Failed to start offline recording. Reason $polarInvalidArgument ")
    }
  }

  override fun stopOfflineRecording(deviceId: String, features: ReadableArray, promise: Promise) {
    // Convert ReadableArray to List<String>
    val featureList = List(features.size()) { index -> features.getString(index).orEmpty() }
    try {
      for (feature in featureList) {
        when (feature) {
          "OfflineHR" -> {
            Log.d(TAG, "STOP OfflineHR Recording")
            stopPolarOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.HR, promise)
          }

          "OfflineACC" -> {
            Log.d(TAG, "STOP OfflineACC Recording")
            stopPolarOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.ACC, promise)
          }

          "OfflineGYR" -> {
            Log.d(TAG, "STOP OfflineGYR Recording")
            stopPolarOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.GYRO, promise)
          }

          "OfflinePPG" -> {
            Log.d(TAG, "STOP OfflineGYR Recording")
            stopPolarOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.PPG, promise)
          }

          "OfflineMAG" -> {
            Log.d(TAG, "STOP OfflineMAG Recording")
            stopPolarOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.MAGNETOMETER, promise)
          }

          "OfflinePPI" -> {
            Log.d(TAG, "STOP OfflinePPI Recording")
            stopPolarOfflineRecording(deviceId, PolarBleApi.PolarDeviceDataType.PPI, promise)
          }
          else -> {
            Log.d(TAG, "Unknown feature: $feature")
          }
        }
      }
    } catch (polarInvalidArgument: PolarInvalidArgument) {
      Log.e(TAG, "Failed to stop offline recording. Reason $polarInvalidArgument ")
    }
  }

  private fun startPolarOfflineRecording(deviceId: String, recordingDataType: PolarBleApi.PolarDeviceDataType,
                                         sensorSetting: PolarSensorSetting?,
                                         promise: Promise){
    val map: WritableMap = Arguments.createMap()
    api.startOfflineRecording(deviceId, recordingDataType, sensorSetting, recordingKey)
      .subscribe(
      {
        Log.d(TAG, "START offline ${recordingDataType} recording completed")
        map.putString("result", "completed");
        promise.resolve(map)
      },
      { throwable: Throwable ->
        Log.e(TAG, "" + throwable.toString())
        promise.reject("error", "Failed to start offline ${recordingDataType} recording", throwable)
      }
    )
  }

  private fun stopPolarOfflineRecording(deviceId: String, recordingDataType: PolarBleApi.PolarDeviceDataType,
                                        promise: Promise){
    val map: WritableMap = Arguments.createMap()
    api.stopOfflineRecording(deviceId, recordingDataType)
      .subscribe(
        {
          Log.d(TAG, "STOP offline ${recordingDataType} recording completed")
          map.putString("result", "completed");
          promise.resolve(map)
        },
        { throwable: Throwable ->
          Log.e(TAG, "" + throwable.toString())
          promise.reject("error", "Failed to stop offline ${recordingDataType} recording", throwable)
        }
      )
  }

  override fun downloadOfflineRecordings(deviceId: String){
    try {
      api.listOfflineRecordings(deviceId)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { polarOfflineRecordingEntry: PolarOfflineRecordingEntry ->
            Log.d(TAG, "next: ${polarOfflineRecordingEntry.date} path: ${polarOfflineRecordingEntry.path}, size: ${polarOfflineRecordingEntry.size}")

            api.getOfflineRecord(deviceId, polarOfflineRecordingEntry, recordingKey)
              // If no secret key was used:
              // api.getOfflineRecord(deviceId, offlineEntry)
              .subscribe(
                {
                  Log.d(TAG, "Recording ${polarOfflineRecordingEntry.path} downloaded. Size: ${polarOfflineRecordingEntry.size}")
                  when (it) {
                    is PolarOfflineRecordingData.HrOfflineRecording -> {
                      Log.d(TAG, "HR Offline Recording started at ${it.startTime.time}")
                      var index = 0;
                      val intervalInMs = 1000; // 1Hz
                      val firstSampleDateUTC = it.startTime.timeInMillis + intervalInMs
                      for (sample in it.data.samples) {
                        val unixTimestamp = firstSampleDateUTC + intervalInMs * index++;
                        val timestamp = Instant.ofEpochMilli(unixTimestamp)
                        Log.d(TAG, "HR data: ${timestamp} hr: ${sample.hr} correctedHr: ${sample.correctedHr} ppgQuality: ${sample.ppgQuality} entry $index of ${it.data.samples.size}")

                        val event: WritableMap = Arguments.createMap()
                        event.putString("data", OfflineRecording.HR.name)
                        event.putInt("hr", sample.hr)
                        event.putInt("correctedHr", sample.correctedHr)
                        event.putInt("ppgQuality", sample.ppgQuality)
                        event.putDouble("size", it.data.samples.size.toDouble()) // Use to track data transfer progress
                        event.putDouble("timestamp", unixTimestamp.toDouble())
                        sendEvent("PolarOfflineRecording", event)
                      }
                    }
                    is PolarOfflineRecordingData.AccOfflineRecording -> {
                      Log.d(TAG, "ACC Offline Recording started at ${it.startTime.time}")
                      var index = 0;
                      for (sample in it.data.samples) {
                        Log.d(TAG, "ACC data: time: ${sample.timeStamp} X: ${sample.x} Y: ${sample.y} Z: ${sample.z} entry ${++index} of ${it.data.samples.size}")

                        val event: WritableMap = Arguments.createMap()
                        event.putString("data", OfflineRecording.ACC.name)
                        event.putInt("accX", sample.x)
                        event.putInt("accY", sample.y)
                        event.putInt("accZ", sample.z)
                        event.putDouble("size", it.data.samples.size.toDouble())
                        event.putDouble("timestamp", sample.timeStamp.toDouble())
                        sendEvent("PolarOfflineRecording", event)
                      }
                    }
                    is PolarOfflineRecordingData.GyroOfflineRecording -> {
                      Log.d(TAG, "GYRO Offline Recording started at ${it.startTime.time}")
                      var index = 0;
                      for (sample in it.data.samples) {
                        Log.d(TAG, "GYRO data: ${sample.timeStamp} X: ${sample.x} Y: ${sample.y} Z: ${sample.z} entry ${++index} of ${it.data.samples.size}")

                        val event: WritableMap = Arguments.createMap()
                        event.putString("data", OfflineRecording.GYRO.name)
                        event.putString("gyrX", "${sample.x}")
                        event.putString("gyrY", "${sample.y}")
                        event.putString("gyrZ", "${sample.z}")
                        event.putDouble("size", it.data.samples.size.toDouble())
                        event.putDouble("timestamp", sample.timeStamp.toDouble())
                        sendEvent("PolarOfflineRecording", event)
                      }
                    }
                    is PolarOfflineRecordingData.PpgOfflineRecording -> {
                      Log.d(TAG, "PPG Offline Recording started at ${it.startTime.time}")
                      var index = 0;
                      for (sample in it.data.samples) {
                        Log.d(TAG, "PPG data: ${sample.timeStamp} ppg0 ${sample.channelSamples[0]} ppg1 ${sample.channelSamples[1]} ppg2 ${sample.channelSamples[2]} ambient ${sample.channelSamples[3]} entry ${++index} of ${it.data.samples.size}")

                        val event: WritableMap = Arguments.createMap()
                        event.putString("data", OfflineRecording.PPG.name)
                        event.putString("ppg0", "${sample.channelSamples[0]}")
                        event.putString("ppg1", "${sample.channelSamples[1]}")
                        event.putString("ppg2", "${sample.channelSamples[2]}")
                        event.putString("ambient", "${sample.channelSamples[3]}")
                        event.putDouble("size", it.data.samples.size.toDouble())
                        event.putDouble("timestamp", sample.timeStamp.toDouble())
                        sendEvent("PolarOfflineRecording", event)
                      }
                    }
                    else -> {
                      Log.d(TAG, "Recording type is not yet implemented")
                    }
                  }
                }
              )
          },
          { error: Throwable -> Log.e(TAG, "Failed to list recordings: $error") },
          { Log.d(TAG, "list recordings complete") }
        )
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to fetch offline recordings. Reason $polarInvalidArgument ")
    }
  }

  override fun deleteAllOfflineRecordings(deviceId: String){
    val entryCache: MutableMap<String, MutableList<PolarOfflineRecordingEntry>> = mutableMapOf()
    var deleteEntryCount = AtomicInteger(0)
    try {
      api.listOfflineRecordings(deviceId)
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSubscribe {
          entryCache[deviceId] = mutableListOf()
        }
        .map {
          entryCache[deviceId]?.add(it)
          it
        }
        .subscribe(
          { polarOfflineRecordingEntry: PolarOfflineRecordingEntry ->
            // Fetch record file
            Log.d(
              TAG,
              "next: ${polarOfflineRecordingEntry.date} path: ${polarOfflineRecordingEntry.path}, size: ${polarOfflineRecordingEntry.size}"
            )

            // Delete record file
            try {
              api.removeOfflineRecord(deviceId, polarOfflineRecordingEntry)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                  {
                    Log.d(TAG, "Recording file ${deleteEntryCount.incrementAndGet()} out of ${entryCache[deviceId]?.size} deleted")
                  },
                  { error ->
                    val errorString = "Recording file deletion failed: $error"
                    Log.e(TAG, errorString)
                  }
                )

            } catch (e: Exception) {
              Log.e(TAG, "Delete offline recording failed on entry ...", e)
            }
          },
          { error: Throwable -> Log.e(TAG, "Failed to list recordings: $error") },
          { Log.d(TAG, "delete all recordings complete") }
        )
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to delete all offline recordings. Reason $polarInvalidArgument ")
    }
  }

  // Sets the date time on the Polar device
  override fun setDeviceTime(deviceId: String) {
    val calendar = Calendar.getInstance()
    calendar.time = Date()
    Log.e(TAG, "Set device: $deviceId time to ${calendar.time}")
    try {
      api.setLocalTime(deviceId, calendar)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          {
            val timeSetString = "time ${calendar.time} set to device"
            Log.d(TAG, timeSetString)
          },
          { error: Throwable -> Log.e(TAG, "set time failed: $error") }
        )
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to set device time. Reason $polarInvalidArgument ")
    }
  }

  override fun getDeviceTime(deviceId: String, promise: Promise) {
    Log.e(TAG, "Get Devices Time")
    api.getLocalTime(deviceId)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { calendar ->
          val timeGetString = "${calendar.time} read from the device"

          val map: WritableMap = Arguments.createMap()
          map.putString("time", "${calendar.time}")
          // Long not supported, use double as workaround
          // See: https://github.com/facebook/react-native/issues/9685
          map.putDouble("timeMs", calendar.timeInMillis.toDouble())
          promise.resolve(map)
        },
        { error: Throwable ->
          Log.e(TAG, "get time failed: $error")
          promise.reject("GET_DEVICE_TIME_ERROR", "Failed to get device time", error)
        }
      )
  }

  override fun getDiskSpace(deviceId: String, promise: Promise) {
    Log.e(TAG, "Get Disk Space")
    api.getDiskSpace(deviceId)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { diskSpace ->
          Log.d(TAG, "Disk space left: ${diskSpace.freeSpace}/${diskSpace.totalSpace} Bytes")
          // Long not supported, use double as workaround
          // See: https://github.com/facebook/react-native/issues/9685
          val map: WritableMap = Arguments.createMap()
          map.putDouble("freeSpace", diskSpace.freeSpace.toDouble())
          map.putDouble("totalSpace", diskSpace.totalSpace.toDouble())
          promise.resolve(map)
        },
        { error: Throwable ->
          Log.e(TAG, "Get disk space failed: $error")
          promise.reject("GET_DISK_SPACE_ERROR", "Failed to get device disk space", error)
        }
      )
  }

  override fun doFactoryReset(deviceId: String) {
    val calendar = Calendar.getInstance()
    calendar.time = Date()
    Log.e(TAG, "Set device: $deviceId time to ${calendar.time}")
    try {
      api.doFactoryReset(deviceId, preservePairingInformation = true)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          {
            Log.d(TAG, "send do factory reset to device")
          },
          { error: Throwable -> Log.e(TAG, "doFactoryReset() failed: $error") }
        )
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to do factory reset. Reason $polarInvalidArgument ")
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

  data class HrSampleWithTimestamp(
    val data: PolarHrData.PolarHrSample,
    val timestampMs: Long
  )

  override fun fetchHrData(deviceId: String, ms: Double?) {
    Log.e(TAG, "Fetch Heart Data called on: $deviceId ")
    val bufferMs = ms?.toLong()?.takeIf { it >= 0 } ?: SENSOR_BUFFER_MS
    val isDisposed = hrDisposable?.isDisposed ?: true
    try{
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
              for (item in samples) {
                val sample = item.data
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
                event.putDouble("timestamp", item.timestampMs.toDouble())

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
        // Contains settings set by default
        sensorSettings.first.settings.forEach { setting ->
          Log.d(TAG, "First Setting: $setting")
        }
        // Contains all available settings
        sensorSettings.second.settings.forEach { setting ->
          Log.d(TAG, "Second Setting: $setting")
        }
        Single.just(sensorSettings.first).toFlowable()  // Load default device settings
      }
  }

  override fun fetchAccData(deviceId: String, ms: Double?) {
    Log.e(TAG, "Fetch Accelerometer Data called on: $deviceId ")
    val bufferMs = ms?.toLong()?.takeIf { it >= 0 } ?: SENSOR_BUFFER_MS
    val isDisposed = accDisposable?.isDisposed ?: true
    try{
      if (isDisposed) {
        accDisposable = requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC)
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

  override fun fetchGyrData(deviceId: String, ms: Double?) {
    Log.e(TAG, "Fetch Gyroscope Data called on: $deviceId ")
    val bufferMs = ms?.toLong()?.takeIf { it >= 0 } ?: SENSOR_BUFFER_MS
    val isDisposed = gyrDisposable?.isDisposed ?: true
    try {
      if (isDisposed) {
        gyrDisposable = requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.GYRO)
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

  override fun fetchPpgData(deviceId: String, ms: Double?) {
    Log.e(TAG, "Fetch Photoplethysmograph Data called on: $deviceId ")
    val bufferMs = ms?.toLong()?.takeIf { it >= 0 } ?: SENSOR_BUFFER_MS
    val isDisposed = ppgDisposable?.isDisposed ?: true
    try {
      if (isDisposed) {
        ppgDisposable = requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.PPG)
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
                Log.d(TAG, "PPG    ppg0: ${data.channelSamples[0]} ppg1: ${data.channelSamples[1]} ppg2: ${data.channelSamples[2]} ambient: ${data.channelSamples[3]} timeStamp: ${data.timeStamp}")

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
        ppgDisposable?.dispose()
        Log.d(TAG, "PPG stream stopped")
      }
    } catch(polarInvalidArgument: PolarInvalidArgument){
      Log.e(TAG, "Failed to fetch PPG Data. Reason $polarInvalidArgument ")
    }
  }

  fun buildTriggerFeatures(features: ReadableArray): Map<PolarBleApi.PolarDeviceDataType, PolarSensorSetting?> {
    // Convert ReadableArray to List<String>
    val featureList = List(features.size()) { index -> features.getString(index).orEmpty() }

    // Prepare the trigger feature map
    val triggerFeatures = mutableMapOf<PolarBleApi.PolarDeviceDataType, PolarSensorSetting?>()

    for (feature in featureList) {
      when (feature) {
        "OfflineHR" -> {
          Log.d(TAG, "Configuring OfflineHR settings")
          triggerFeatures[PolarBleApi.PolarDeviceDataType.HR] = null
        }

        "OfflineACC" -> {
          Log.d(TAG, "Configuring OfflineACC settings")
          triggerFeatures[PolarBleApi.PolarDeviceDataType.ACC] = PolarSensorSetting(
            mapOf(
              PolarSensorSetting.SettingType.SAMPLE_RATE to 52,
              PolarSensorSetting.SettingType.RESOLUTION to 16,
              PolarSensorSetting.SettingType.RANGE to 8,
              PolarSensorSetting.SettingType.CHANNELS to 3
            )
          )
        }

        "OfflineGYR" -> {
          Log.d(TAG, "Configuring OfflineGYR settings")
          triggerFeatures[PolarBleApi.PolarDeviceDataType.GYRO] = PolarSensorSetting(
            mapOf(
              PolarSensorSetting.SettingType.SAMPLE_RATE to 52,
              PolarSensorSetting.SettingType.RESOLUTION to 16,
              PolarSensorSetting.SettingType.RANGE to 2000,
              PolarSensorSetting.SettingType.CHANNELS to 3
            )
          )
        }

        "OfflinePPG" -> {
          Log.d(TAG, "Configuring OfflinePPG settings")
          triggerFeatures[PolarBleApi.PolarDeviceDataType.PPG] = PolarSensorSetting(
            mapOf(
              PolarSensorSetting.SettingType.SAMPLE_RATE to 55,
              PolarSensorSetting.SettingType.RESOLUTION to 22,
              PolarSensorSetting.SettingType.CHANNELS to 4
            )
          )
        }

        "OfflineMAG" -> {
          Log.d(TAG, "Configuring OfflineMAG settings")
          triggerFeatures[PolarBleApi.PolarDeviceDataType.MAGNETOMETER] = PolarSensorSetting(
            mapOf(
              PolarSensorSetting.SettingType.SAMPLE_RATE to 10,
              PolarSensorSetting.SettingType.RESOLUTION to 16,
              PolarSensorSetting.SettingType.RANGE to 50,
              PolarSensorSetting.SettingType.CHANNELS to 3
            )
          )
        }

        "OfflinePPI" -> {
          Log.d(TAG, "Configuring OfflinePPI settings")
          triggerFeatures[PolarBleApi.PolarDeviceDataType.PPI] = null
        }

        else -> {
          Log.d(TAG, "Unknown feature: $feature")
        }
      }
    }

    return triggerFeatures
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

  override fun disposePpgStream(){
    ppgDisposable?.dispose()
  }

  private fun disposeAllStreams() {
    hrDisposable?.dispose()
    accDisposable?.dispose()
    gyrDisposable?.dispose()
    ppgDisposable?.dispose()
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

enum class OfflineRecording {
  HR, ACC, GYRO, PPG
}

