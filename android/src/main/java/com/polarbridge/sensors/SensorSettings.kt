package com.polarbridge.sensors

import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.module.annotations.ReactModule
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.*
import com.polarbridge.sensors.HrStreamManager
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.facebook.react.modules.core.DeviceEventManagerModule
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import java.time.*

class SensorSettings(
  private val api: PolarBleApi,
  private val TAG: String = "SensorSettings"
) {
  fun requestStreamSettings(identifier: String, feature: PolarBleApi.PolarDeviceDataType): Flowable<PolarSensorSetting> {
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
}
