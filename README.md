# react-native-polar-bridge

Testing Polar SDK on React Native

## Installation

```sh
npm install react-native-polar-bridge
```

## Usage

This React Native library uses the [polar-ble-sdk](https://github.com/polarofficial/polar-ble-sdk).

⚠️ The library only has support for Android devices.

### Connecting to Device

```js
import { connectToDevice } from 'react-native-polar-bridge';

const deviceId = 'D8765432';

export default function App(){

  // ...

  oDevice(deviceId);
}

```

### Disconnecting from Device

```js
disconnectFromDevice(deviceId);
```

### Scan Devices

Use `scanDevices()` to scan for nearby Polar devices

```js
scanDevices();
```

and listen for emitted events for the result.

```js
const [devices, setDevices] = useState<Device[]>([]);

useEffect(() => {
  const onDeviceFound = polarEmitter.addListener(emittedEventId.SCAN_DEVICE_FOUND, device => {
    console.log('Device found:', device);
  });

  const onScanError = polarEmitter.addListener(emittedEventId.SCAN_DEVICE_ERROR, err => {
    console.error('Scan error:', err.message);
  });

  const onScanComplete = polarEmitter.addListener(emittedEventId.SCAN_DEVICE_COMPLETE, () => {
    console.log('Scan complete');
  });

  return () => {
    onDeviceFound.remove();
    onScanError.remove();
    onScanComplete.remove();
  };
}, []);
```

### Heart Rate Stream

Ensure that the `deviceId` of the Polar device is connected before calling the function.

```js
fetchHrData(connectedDeviceId);
```

Listen for emitted events of the HR data.

```js
useEffect(() => {
  const hrListener = polarEmitter.addListener(emittedEventId.POLAR_HR_DATA, (data) => {
    console.log('Heart Rate:', `${data.hr} bpm ${formatDateYYYYMMDDHHMMSS(new Date())}`);
  });

  const errorListener = polarEmitter.addListener(emittedEventId.POLAR_HR_ERROR, (error) => {
    console.log('HR stream error:', error);
  });

  const completeListener = polarEmitter.addListener(emittedEventId.POLAR_HR_COMPLETE, (msg) => {
    console.log('HR stream complete:', msg);
  });

  return () => {
    hrListener.remove();
    errorListener.remove();
    completeListener.remove();
  };
}, []);
```

### Accelerometer

Ensure that the `deviceId` of the Polar device is connected before calling the function. The default sampling rate is 52Hz and can be changed with [SDK mode enabled](https://github.com/polarofficial/polar-ble-sdk/issues/163) on the device.

```js
fetchAccData(connectedDeviceId);
```

Listen for emitted events of the Accelerometer data. The Accelerometer returns a timestamp in their own [time system](https://github.com/polarofficial/polar-ble-sdk/blob/master/documentation/TimeSystemExplained.md) - which is respresented in nanoseconds since 2000-01-01 00:00:00.

```js
const accListener = polarEmitter.addListener(emittedEventId.POLAR_ACC_DATA, (data) => {
  console.log('ACC Stream:', `x: ${data.accX} y: ${data.accY} z: ${data.accZ} timestamp: ${formatPolarTimestamp(data.accTimestamp)}`);
});
```

### Gyroscope

Ensure that the `deviceId` of the Polar device is connected before calling the function. Like the Accelerometer, the default sampling rate is 52Hz and can be changed with SDK mode enabled on the device.

```js
fetchGyrData(connectedDeviceId);
```

Listen for emitted events of the Gyroscope data. The Gyroscope also returns a timestamp in Polar time system.

```js
const gyrListener = polarEmitter.addListener(emittedEventId.POLAR_GYR_DATA, (data) => {
  console.log('GYR Stream:', `x: ${data.gyrX} y: ${data.gyrY} z: ${data.gyrZ} timestamp: ${formatPolarTimestamp(data.gyrTimestamp)}`);
});
```

### Photoplethysmogram (PPG)

Ensure that the `deviceId` of the Polar device is connected before calling the function. The default sampling rate is 55Hz and can be changed with SDK mode enabled on the device.

```js
fetchPpgData(connectedDeviceId);
```

Listen for emitted events of the PPG data. This also returns a timestamp in Polar time system.

```js
const ppgListener = polarEmitter.addListener(emittedEventId.POLAR_PPG_DATA, (data) => {
  console.log('PPG Stream:', `ppg0: ${data.ppg0} ppg1: ${data.ppg1} ppg2: ${data.ppg2} ambient: ${data.ambient} timestamp: ${formatPolarTimestamp(data.ppgTimestamp)}`);
});
```

### Offline Recording Trigger

Offline recording can be initiated with `PolarBleApi.startOfflineRecording()`. With recording trigger, we can configure the Polar device to start the recording either on exercise start with `TRIGGER_EXERCISE_START` or when the Polar device has been turned on with `TRIGGER_SYSTEM_START`.

```js
// Configure features needed in the offline recording
// List of available recording feature, feature availability may depend on the Polar device
const offlineRecordingFeatureList = [OfflineRecordingFeature.OFFLINE_HR,
  OfflineRecordingFeature.OFFLINE_ACC,
  OfflineRecordingFeature.OFFLINE_GYR,
  OfflineRecordingFeature.OFFLINE_PPG,
  OfflineRecordingFeature.OFFLINE_MAG,
  OfflineRecordingFeature.OFFLINE_PPI
];

setPolarRecordingTrigger(connectedDeviceId,
  OfflineRecordingTriggerMode.TRIGGER_SYSTEM_START,
  offlineRecordingFeatureList);
```

To disable offline recording trigger, set `TRIGGER_DISABLED`

```js
setPolarRecordingTrigger(connectedDeviceId,
  OfflineRecordingTriggerMode.TRIGGER_DISABLED,
  offlineRecordingFeatureList);
```

### SDK Mode

Enabling SDK mode on supported Polar devices gives more sampling rate and range options. See [Polar documentation](https://github.com/polarofficial/polar-ble-sdk/blob/master/documentation/products/PolarVeritySense.md#sdk-mode-capabilities-in-polar-verity-sense) for more details.

```js
enableSdkMode(connectedDeviceId);
disableSdkMode(connectedDeviceId);
```

### Device Time

Set the time on Polar device using the current time set on the smartphone.

```js
setDeviceTime(connectedDeviceId);
```

Fetch the time on the Polar device. `data.timeMs` is in unixTime in milliseconds.

```js
getDeviceTime(connectedDeviceId);

polarEmitter.addListener(
  emittedEventId.POLAR_DEVICE_TIME,
  (data) => {
    console.log('Polar Device Time', `${data.time} ms: ${data.timeMs}`);
  }
);
```

### Disk Space

Supported Polar devices can store offline recordings on its storage and remaining storage space can be tracked using this API.

```js
getDiskSpace(connectedDeviceId);

polarEmitter.addListener(
  emittedEventId.POLAR_DISK_SPACE,
  (data) => {
    console.log('Polar Disk Space', `Disk space: ${data.freeSpace} / ${data.totalSpace} Bytes`);
  }
);
```

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
