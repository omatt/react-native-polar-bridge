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
} from 'react-native-polar-bridge';
import {useEffect, useState} from "react";

import {formatDateYYYYMMDDHHMMSS} from './services/utils';

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

export default function App() {
  // const deviceId = 'D8207828';
  requestBluetoothPermissions().then();

  const [isHRStreamToggled, setIsHRStreamToggled] = useState(false);
  const toggleHRStreamStatus = () => setIsHRStreamToggled(prev => !prev);

  const [devices, setDevices] = useState<Device[]>([]);
  const [connectedDeviceId, setConnectedDeviceId] = useState<string | null>(null);

  /**
   * Connect/Disconnect Devices
   */
  useEffect(() => {
    const onDeviceConnected = polarEmitter.addListener('onDeviceConnected', (device) => {
      console.log('Device connected:', device);
      setConnectedDeviceId(device.deviceId);
    });

    const onDeviceDisconnected = polarEmitter.addListener('onDeviceDisconnected', (device) => {
      console.log('Device disconnected:', device);
      setConnectedDeviceId(null);
      setIsHRStreamToggled(false);
    });

    return () => {
      onDeviceConnected.remove();
      onDeviceDisconnected.remove();
    };
  }, []);

  /**
   * Scan Devices
   */
  useEffect(() => {
    const onDeviceFound = polarEmitter.addListener('onDeviceFound', device => {
      console.log('Device found:', device);
      // Store device in list, update state, etc.
      setDevices(prevDevices => {
        const exists = prevDevices.some(d => d.deviceId === device.deviceId);
        return exists ? prevDevices : [...prevDevices, device];
      });
    });

    const onScanError = polarEmitter.addListener('onScanError', err => {
      console.error('Scan error:', err.message);
    });

    const onScanComplete = polarEmitter.addListener('onScanComplete', () => {
      console.log('Scan complete');
    });

    return () => {
      onDeviceFound.remove();
      onScanError.remove();
      onScanComplete.remove();
    };
  }, []);

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

  // const handleConnect = () => {
  //   connectToDevice(deviceId);
  // };
  //
  // const handleDisconnect = () => {
  //   disconnectFromDevice(deviceId);
  // };

  const handleFetchHrData = () => {
    if(connectedDeviceId != null){
      toggleHRStreamStatus();
      fetchHrData(connectedDeviceId);
    } else{
      console.log('Empty Device ID or no connected Device');
      Alert.alert('Error', 'No connected Polar device. Empty deviceId!', [{ text: 'OK' }]);
    }
  };

  const handleScanDevices = () => {
    setDevices([]);
    scanDevices();
  };

  return (
    <View style={styles.container}>
      {/*<Text>Result: {result}</Text>*/}
      {/*<View style={styles.buttonContainer}>*/}
      {/*  <Button title={`Connect ${deviceId}`} onPress={*/}
      {/*    handleConnect*/}
      {/*  }/>*/}
      {/*</View>*/}
      {/*<View style={styles.buttonContainer}>*/}
      {/*  <Button title="Disconnect" onPress={handleDisconnect}/></View>*/}
      <View style={styles.buttonContainer}>
        <Button title={devices.length > 0 ? "Clear Scanned Devices" : "Scan Devices"} onPress={handleScanDevices}/></View>
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
                              disconnectFromDevice(device.deviceId);
                              setConnectedDeviceId(null);
                            } else {
                              if (connectedDeviceId && connectedDeviceId !== device.deviceId) {
                                if(isHRStreamToggled) handleFetchHrData(); // Stop HR stream if running
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
        // PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
        // PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION,
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
