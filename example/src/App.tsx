import {
  Text,
  View,
  StyleSheet,
  Button,
  Platform,
  PermissionsAndroid,
  type Permission,
  NativeModules, NativeEventEmitter
} from 'react-native';
import {
  connectToDevice,
  disconnectFromDevice,
  scanDevices,
  fetchHrData,
  multiply,
} from 'react-native-polar-bridge';
import {useEffect, useState} from "react";

import {formatDateYYYYMMDDHHMMSS} from './services/utils';

const result = multiply(3, 7);

const nativeModule = NativeModules.YourNativeModuleName;
const polarEmitter = new NativeEventEmitter(nativeModule);

export default function App() {
  const deviceId = 'D8455025';
  requestBluetoothPermissions().then();

  const [isToggled, setIsToggled] = useState(false);
  const toggle = () => setIsToggled(prev => !prev);

  /**
   * Listen for HR data stream from Android PolarBLE SDK
   */
  useEffect(() => {
    const hrListener = polarEmitter.addListener('PolarHrData', (data) => {
      // console.log('Received HR data:', data);
      console.log('Heart Rate:', `${data.hr} bpm ${formatDateYYYYMMDDHHMMSS(new Date())}`);
    });

    const errorListener = polarEmitter.addListener('PolarHrError', (error) => {
      console.error('HR stream error:', error);
    });

    const completeListener = polarEmitter.addListener('PolarHrComplete', (msg) => {
      console.log('HR stream complete:', msg);
    });

    return () => {
      hrListener.remove();
      errorListener.remove();
      completeListener.remove();
    };
  }, []);

  const handleConnect = () => {
    connectToDevice(deviceId);
  };

  const handleDisconnect = () => {
    disconnectFromDevice(deviceId);
  };

  const handleFetchHrData = () => {
    toggle();
    fetchHrData(deviceId);
  };

  const handleScanDevices = () => {
    scanDevices();
  };

  return (
    <View style={styles.container}>
      <Text>Result: {result}</Text>
      <View style={styles.buttonContainer}>
        <Button title={`Connect ${deviceId}`} onPress={
          handleConnect
        }/>
      </View>
      <View style={styles.buttonContainer}>
        <Button title="Disconnect" onPress={handleDisconnect}/></View>
      <View style={styles.buttonContainer}>
        <Button title="Scan Devices" onPress={handleScanDevices}/></View>
      <View style={styles.buttonContainer}>
        <Button title={isToggled ? 'Stop Streaming HR Data' : 'Start Streaming HR Data'}
                onPress={handleFetchHrData}/></View>
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
        PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
        PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION,
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
  },
});
