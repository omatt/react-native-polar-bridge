import {
  Text,
  View,
  StyleSheet,
  Button,
  Platform,
  PermissionsAndroid,
  type Permission,
  NativeModules, NativeEventEmitter, Alert
} from 'react-native';
import {
  connectToDevice,
  disconnectFromDevice,
  scanDevices,
  fetchHrData,
  disposeHrStream,
  emittedEventId,
  fetchAccData,
  disposeAccStream,
  disposeGyrStream,
  fetchGyrData,
  disposePpgStream,
  fetchPpgData,
} from 'react-native-polar-bridge';
import {useEffect, useState} from "react";

import {formatDateYYYYMMDDHHMMSS, formatPolarTimestamp} from './services/utils';
import {logAccToCSV, logGyroToCSV, logHeartRateToCSV, logPpgToCSV} from './services/writer';

// const result = multiply(3, 7);

const nativeModule = NativeModules.YourNativeModuleName;
const polarEmitter = new NativeEventEmitter(nativeModule);

type Device = {
  deviceId: string;
  name?: string;
  address?: string;
  rssi?: string;
  isConnectable?: string;
};

const displayDialogNoConnectedDevice = () => {
  console.log('Empty Device ID or no connected Device');
  return (
    Alert.alert('Error', 'No connected Polar device. Empty deviceId!', [{ text: 'OK' }])
  );
}

export default function App() {
  // const deviceId = 'D8207828';
  const deviceId = 'D8455025';
  requestBluetoothPermissions().then();

  const [isHRStreamToggled, setIsHRStreamToggled] = useState(false);
  const toggleHRStreamStatus = () => setIsHRStreamToggled(prev => !prev);

  const [isAccStreamToggled, setIsAccStreamToggled] = useState(false);
  const toggleAccStreamStatus = () => setIsAccStreamToggled(prev => !prev);

  const [isGyrStreamToggled, setIsGyrStreamToggled] = useState(false);
  const toggleGyrStreamStatus = () => setIsGyrStreamToggled(prev => !prev);

  const [isPpgStreamToggled, setIsPpgStreamToggled] = useState(false);
  const togglePpgStreamStatus = () => setIsPpgStreamToggled(prev => !prev);

  const [devices, setDevices] = useState<Device[]>([]);
  const [connectedDeviceId, setConnectedDeviceId] = useState<string | null>(null);

  /**
   * Scan Devices
   */
  useEffect(() => {
    const onDeviceFound = polarEmitter.addListener(emittedEventId.SCAN_DEVICE_FOUND, device => {
      console.log('Device found:', device);
      // Store device in list, update state, etc.
      setDevices(prevDevices => {
        const exists = prevDevices.some(d => d.deviceId === device.deviceId);
        return exists ? prevDevices : [...prevDevices, device];
      });
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

  /**
   * Listen for device data stream from Android PolarBLE SDK
   */
  useEffect(() => {
    const hrListener = polarEmitter.addListener(emittedEventId.POLAR_HR_DATA, (data) => {
      // console.log('Received HR data:', data);
      console.log('Heart Rate:', `${data.hr} bpm timestamp: ${formatDateYYYYMMDDHHMMSS(new Date())}`);
      logHeartRateToCSV(data.hr).then();
    });

    const errorHrListener = polarEmitter.addListener(emittedEventId.POLAR_HR_ERROR, (error) => {
      console.log('HR stream error:', error);
      Alert.alert('Error', 'Polar device is yet to be detected. Try starting HR Stream again.', [{
        text: 'OK',
        onPress: () => {
          console.log('OK Pressed');
          toggleHRStreamStatus();
          disposeHrStream();
        },
      }]);
    });

    const completeHrListener = polarEmitter.addListener(emittedEventId.POLAR_HR_COMPLETE, (msg) => {
      console.log('HR stream complete:', msg);
    });

    const accListener = polarEmitter.addListener(emittedEventId.POLAR_ACC_DATA, (data) => {
      console.log('ACC Stream:', `x: ${data.accX} y: ${data.accY} z: ${data.accZ} timestamp: ${formatPolarTimestamp(data.accTimestamp)}`);
      logAccToCSV(data.accX, data.accY, data.accZ, formatPolarTimestamp(data.accTimestamp)).then();
    });

    const errorAccListener = polarEmitter.addListener(emittedEventId.POLAR_ACC_ERROR, (error) => {
      console.log('ACC stream error:', error);
      Alert.alert('Error', 'Polar device is yet to be detected. Try starting ACC Stream again.', [{
        text: 'OK',
        onPress: () => {
          console.log('OK Pressed');
          toggleAccStreamStatus();
          disposeAccStream();
        },
      }]);
    });

    const completeAccListener = polarEmitter.addListener(emittedEventId.POLAR_ACC_COMPLETE, (msg) => {
      console.log('GYR stream complete:', msg);
    });

    const gyrListener = polarEmitter.addListener(emittedEventId.POLAR_GYR_DATA, (data) => {
      console.log('GYR Stream:', `x: ${data.gyrX} y: ${data.gyrY} z: ${data.gyrZ} timestamp: ${formatPolarTimestamp(data.gyrTimestamp)}`);
      logGyroToCSV(data.gyrX, data.gyrY, data.gyrZ, formatPolarTimestamp(data.gyrTimestamp)).then();
    });

    const errorGyrListener = polarEmitter.addListener(emittedEventId.POLAR_GYR_ERROR, (error) => {
      console.log('GYR stream error:', error);
      Alert.alert('Error', 'Polar device is yet to be detected. Try starting GYR Stream again.', [{
        text: 'OK',
        onPress: () => {
          console.log('OK Pressed');
          toggleGyrStreamStatus();
          disposeGyrStream();
        },
      }]);
    });

    const completeGyrListener = polarEmitter.addListener(emittedEventId.POLAR_GYR_COMPLETE, (msg) => {
      console.log('GYR stream complete:', msg);
    });

    const ppgListener = polarEmitter.addListener(emittedEventId.POLAR_PPG_DATA, (data) => {
      console.log('PPG Stream:', `ppg0: ${data.ppg0} ppg1: ${data.ppg1} ppg2: ${data.ppg2} ambient: ${data.ambient} timestamp: ${formatPolarTimestamp(data.ppgTimestamp)}`);
      logPpgToCSV(data.ppg0, data.ppg1, data.ppg2, data.ambient, formatPolarTimestamp(data.ppgTimestamp)).then();
    });

    const errorPpgListener = polarEmitter.addListener(emittedEventId.POLAR_PPG_ERROR, (error) => {
      console.log('PPG stream error:', error);
      Alert.alert('Error', 'Polar device is yet to be detected. Try starting PPG Stream again.', [{
        text: 'OK',
        onPress: () => {
          console.log('OK Pressed');
          togglePpgStreamStatus();
          disposePpgStream();
        },
      }]);
    });

    const completePpgListener = polarEmitter.addListener(emittedEventId.POLAR_PPG_COMPLETE, (msg) => {
      console.log('PPG stream complete:', msg);
    });

    return () => {
      hrListener.remove();
      errorHrListener.remove();
      completeHrListener.remove();
      accListener.remove();
      errorAccListener.remove();
      completeAccListener.remove();
      gyrListener.remove();
      errorGyrListener.remove();
      completeGyrListener.remove();
      ppgListener.remove();
      errorPpgListener.remove();
      completePpgListener.remove();
    };
  }, []);

  const handleConnect = () => {
    connectToDevice(deviceId);
  };
  //
  // const handleDisconnect = () => {
  //   disconnectFromDevice(deviceId);
  // };

  const handleFetchHrData = () => {
    if(connectedDeviceId != null){
      toggleHRStreamStatus();
      fetchHrData(connectedDeviceId);
    } else{
      displayDialogNoConnectedDevice();
    }
  };

  const handleFetchAccData = () => {
    if(connectedDeviceId != null){
      toggleAccStreamStatus();
      fetchAccData(connectedDeviceId);
    } else{
      displayDialogNoConnectedDevice();
    }
  };

  const handleFetchGyrData = () => {
    if(connectedDeviceId != null){
      toggleGyrStreamStatus();
      fetchGyrData(connectedDeviceId);
    } else{
      displayDialogNoConnectedDevice();
    }
  };

  const handleFetchPpgData = () => {
    if(connectedDeviceId != null){
      togglePpgStreamStatus();
      fetchPpgData(connectedDeviceId);
    } else{
      displayDialogNoConnectedDevice();
    }
  };

  const handleScanDevices = () => {
    setDevices([]);
    scanDevices();
  };

  return (
    <View style={styles.container}>
      {/*<Text>Result: {result}</Text>*/}
      <View style={styles.buttonContainer}>
        <Button title={`Connect ${deviceId}`} onPress={
          handleConnect
        }/>
      </View>
      {/*<View style={styles.buttonContainer}>*/}
      {/*  <Button title="Disconnect" onPress={handleDisconnect}/></View>*/}
      <View style={styles.buttonContainer}>
        <Button title={devices.length > 0 ? "Clear Scanned Devices" : "Scan Devices"} onPress={() => {
          handleScanDevices();
        }}/></View>
      {devices.length > 0 && (
        <View style={{ marginTop: 30, width: '100%' }}>
          <Text style={{ fontWeight: 'bold', marginBottom: 10 }}>Scanned Devices:</Text>
          {devices.map(device => {
              const isConnected = connectedDeviceId === device.deviceId;
              return (
                <View key={device.deviceId}
                      style={styles.deviceItem}>
                  <View>
                    <Text>ID: {device.deviceId}</Text>
                    <Text>Name: {device.name || 'N/A'}</Text>
                    <Text>RSSI: {device.rssi || 'N/A'}</Text>
                  </View>
                  <Button title={isConnected ? 'Disconnect' : 'Connect'}
                          onPress={() => {
                            if (isConnected) {
                              if(isHRStreamToggled) handleFetchHrData(); // Stop HR stream if running
                              if(isAccStreamToggled) handleFetchAccData(); // Stop ACC stream if running
                              if(isGyrStreamToggled) handleFetchGyrData(); // Stop GYR stream if running
                              if(isPpgStreamToggled) handleFetchPpgData(); // Stop PPG stream if running
                              disconnectFromDevice(device.deviceId);
                              setConnectedDeviceId(null);
                            } else {
                              if (connectedDeviceId && connectedDeviceId !== device.deviceId) {
                                if(isHRStreamToggled) handleFetchHrData(); // Stop HR stream if running
                                if(isAccStreamToggled) handleFetchAccData(); // Stop ACC stream if running
                                if(isGyrStreamToggled) handleFetchGyrData(); // Stop GYR stream if running
                                if(isPpgStreamToggled) handleFetchPpgData(); // Stop PPG stream if running
                                disconnectFromDevice(connectedDeviceId);
                              }
                              connectToDevice(device.deviceId);
                              setConnectedDeviceId(device.deviceId);
                            }
                          }}/>
                </View>
              );
            }
          )}
        </View>
      )}
      <View style={styles.buttonContainer}>
        <Button title={isHRStreamToggled ? 'Stop Streaming HR Data' : 'Start Streaming HR Data'}
                onPress={() => {
                  if (isHRStreamToggled){
                    toggleHRStreamStatus();
                    disposeHrStream();
                  }
                  else handleFetchHrData();
                }}/></View>
      <View style={styles.buttonContainer}>
        <Button title={isAccStreamToggled ? 'Stop Streaming ACC Data' : 'Start Streaming ACC Data'}
                onPress={() => {
                  if (isAccStreamToggled){
                    toggleAccStreamStatus();
                    disposeAccStream();
                  }
                  else handleFetchAccData();
                }}/></View>
      <View style={styles.buttonContainer}>
        <Button title={isGyrStreamToggled ? 'Stop Streaming GYR Data' : 'Start Streaming GYR Data'}
                onPress={() => {
                  if (isGyrStreamToggled){
                    toggleGyrStreamStatus();
                    disposeGyrStream();
                  }
                  else handleFetchGyrData();
                }}/></View>
      <View style={styles.buttonContainer}>
        <Button title={isPpgStreamToggled ? 'Stop Streaming PPG Data' : 'Start Streaming PPG Data'}
                onPress={() => {
                  if (isPpgStreamToggled){
                    togglePpgStreamStatus();
                    disposePpgStream();
                  }
                  else handleFetchPpgData();
                }}/></View>
      {/*<View style={styles.buttonContainer}>*/}
      {/*  <Button title="Request Bluetooth Permission" onPress={ requestBluetoothPermissions }/></View>*/}
    </View>
  );
}

export const requestBluetoothPermissions = async (): Promise<boolean> => {
  if (Platform.OS === 'android') {
    try {
      // Android 12+ (API 31+)
      const permissions: Permission[] = [
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
        PermissionsAndroid.PERMISSIONS.READ_EXTERNAL_STORAGE,
        PermissionsAndroid.PERMISSIONS.WRITE_EXTERNAL_STORAGE,
      ];

      const granted = await PermissionsAndroid.requestMultiple(permissions);

      const allGranted = Object.values(granted).every(
        (status) => status === PermissionsAndroid.RESULTS.GRANTED
      );

      const denied: string[] = [];

      permissions.forEach((perm) => {
        const status = granted[perm];
        if (status !== PermissionsAndroid.RESULTS.GRANTED) {
          denied.push(perm);
        }
      });

      if (denied.length > 0) {
        console.warn('Denied permissions:', denied.join(', '));
        // Alert.alert(
        //   'Missing Permissions',
        //   `The following permissions were not granted:\n\n${denied.join('\n')}`
        // );
        return false;
      }

      if (!allGranted) {
        console.warn('Not all Bluetooth permissions granted');
        return false;
      }

      return true;
    } catch (err) {
      console.error('Failed to request Bluetooth permissions:', err);
      return false;
    }
  }

  return true;
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
    backgroundColor: '#FFF',
  },

  buttonContainer: {
    marginTop: 20,
    width: '70%',
  },

  deviceItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
    padding: 10,
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 5,
  }
});
