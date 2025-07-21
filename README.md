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
fetchHrData(deviceId);
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

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
