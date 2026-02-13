import {
  Text,
  View,
  StyleSheet,
  Button,
  NativeModules,
  NativeEventEmitter,
  Alert, ScrollView, Switch,
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
  enableSdkMode,
  disableSdkMode,
  getDeviceTime,
  setDeviceTime,
  setPolarRecordingTrigger,
  OfflineRecordingTriggerMode,
  getDiskSpace,
  OfflineRecordingFeature,
  fetchOfflineRecordings,
  deleteAllOfflineRecordings, downloadOfflineRecordings, startOfflineRecording, stopOfflineRecording, multiply,
} from 'react-native-polar-bridge';
import { useEffect, useState } from 'react';

import {
  formatDateYYYYMMDDHHMMSS,
  formatPolarTimestamp, formatPolarTimestampToUnixTimeStamp,
} from './services/utils';
import {
  logAccToCSV,
  logGyroToCSV,
  logHeartRateToCSV,
  logPpgToCSV,
} from './services/writer';
import type {
  AccData, DeviceConnected,
  DeviceTime,
  DiskSpace,
  GyrData,
  HrData, OfflineRecording,
  PpgData, ScannedDevice,
} from '../../src/PolarDataModel';
import {checkPermission, requestPermissions} from "./utils/permissions_manager";

const result = multiply(3, 7);

const {PolarBridge} = NativeModules;
const polarEmitter = new NativeEventEmitter(PolarBridge);

const displayDialogNoConnectedDevice = () => {
  console.log('Empty Device ID or no connected Device');
  return Alert.alert('Error', 'No connected Polar device. Empty deviceId!', [
    { text: 'OK' },
  ]);
};

export default function App() {
  const deviceId = 'D8207828';
  // const deviceId = 'D8455025';
  console.log(`Result: ${result}`)
  // requestBluetoothPermissions().then();

  checkPermission().then((isGranted: boolean) =>{
    if(!isGranted){
      requestPermissions().then();
    }
  });

  const [isHRStreamToggled, setIsHRStreamToggled] = useState(false);
  const toggleHRStreamStatus = () => setIsHRStreamToggled((prev) => !prev);

  const [isAccStreamToggled, setIsAccStreamToggled] = useState(false);
  const toggleAccStreamStatus = () => setIsAccStreamToggled((prev) => !prev);

  const [isGyrStreamToggled, setIsGyrStreamToggled] = useState(false);
  const toggleGyrStreamStatus = () => setIsGyrStreamToggled((prev) => !prev);

  const [isPpgStreamToggled, setIsPpgStreamToggled] = useState(false);
  const togglePpgStreamStatus = () => setIsPpgStreamToggled((prev) => !prev);

  const [isSdkModeToggled, setIsSdkModeToggled] = useState(false);
  const toggleSdkModeStatus = () => setIsSdkModeToggled((prev) => !prev);

  const [devices, setDevices] = useState<ScannedDevice[]>([]);
  const [connectedDeviceId, setConnectedDeviceId] = useState<string | null>(
    null
  );

  const [isLogCSVEnabled, setIsLogCSVEnabled] = useState(false);
  const toggleLogCSVSwitch = () => setIsLogCSVEnabled(prev => !prev);

  const offlineRecordingFeatureList = [OfflineRecordingFeature.OFFLINE_HR,
    OfflineRecordingFeature.OFFLINE_ACC,
    OfflineRecordingFeature.OFFLINE_GYR,
    OfflineRecordingFeature.OFFLINE_PPG,
    // OfflineRecordingFeature.OFFLINE_MAG,
    // OfflineRecordingFeature.OFFLINE_PPI
  ];

  /**
   * Scan Devices
   */
  useEffect(() => {
    const onDeviceFound = polarEmitter.addListener(
      emittedEventId.SCAN_DEVICE_FOUND,
      (device: ScannedDevice) => {
        console.log('Device found:', device);
        // Store device in list, update state, etc.
        setDevices((prevDevices) => {
          const exists = prevDevices.some(
            (d) => d.deviceId === device.deviceId
          );
          return exists ? prevDevices : [...prevDevices, device];
        });
      }
    );

    const onScanError = polarEmitter.addListener(
      emittedEventId.SCAN_DEVICE_ERROR,
      (err) => {
        console.error('Scan error:', err.message);
      }
    );

    const onScanComplete = polarEmitter.addListener(
      emittedEventId.SCAN_DEVICE_COMPLETE,
      () => {
        console.log('Scan complete');
      }
    );

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
    const hrListener = polarEmitter.addListener(
      emittedEventId.POLAR_HR_DATA,
      (data: HrData) => {
        // console.log('Received HR data:', data);
        console.log(
          'Heart Rate:', `Polar device ${deviceId}`,
          `${data.hr} bpm timestamp: ${formatDateYYYYMMDDHHMMSS(data.timestamp)}`
        );
        if(isLogCSVEnabled) {
          logHeartRateToCSV(data.hr).then();
        }
      }
    );

    const errorHrListener = polarEmitter.addListener(
      emittedEventId.POLAR_HR_ERROR,
      (error) => {
        console.log('HR stream error:', error);
        Alert.alert(
          'Error',
          'Polar device is yet to be detected. Try starting HR Stream again.',
          [
            {
              text: 'OK',
              onPress: () => {
                console.log('OK Pressed');
                toggleHRStreamStatus();
                disposeHrStream();
              },
            },
          ]
        );
      }
    );

    const completeHrListener = polarEmitter.addListener(
      emittedEventId.POLAR_HR_COMPLETE,
      (msg) => {
        console.log('HR stream complete:', msg);
      }
    );

    const accListener = polarEmitter.addListener(
      emittedEventId.POLAR_ACC_DATA,
      (data: AccData) => {
        console.log(
          'ACC Stream:', `Polar device ${deviceId}`,
          `x: ${data.accX} y: ${data.accY} z: ${data.accZ} timestamp: ${formatPolarTimestamp(data.accTimestamp)}`
        );
        if(isLogCSVEnabled) {
          logAccToCSV(data.accX, data.accY, data.accZ, `${formatPolarTimestampToUnixTimeStamp(data.accTimestamp)}`).then();
        }
      }
    );

    const errorAccListener = polarEmitter.addListener(
      emittedEventId.POLAR_ACC_ERROR,
      (error) => {
        console.log('ACC stream error:', error);
        Alert.alert(
          'Error',
          'Polar device is yet to be detected. Try starting ACC Stream again.',
          [
            {
              text: 'OK',
              onPress: () => {
                console.log('OK Pressed');
                toggleAccStreamStatus();
                disposeAccStream();
              },
            },
          ]
        );
      }
    );

    const completeAccListener = polarEmitter.addListener(
      emittedEventId.POLAR_ACC_COMPLETE,
      (msg) => {
        console.log('GYR stream complete:', msg);
      }
    );

    const gyrListener = polarEmitter.addListener(
      emittedEventId.POLAR_GYR_DATA,
      (data: GyrData) => {
        console.log(
          'GYR Stream:', `Polar device ${deviceId}`,
          `x: ${data.gyrX} y: ${data.gyrY} z: ${data.gyrZ} timestamp: ${formatPolarTimestamp(data.gyrTimestamp)}`
        );
        if(isLogCSVEnabled){
          logGyroToCSV(Number(data.gyrX), Number(data.gyrY), Number(data.gyrZ), `${formatPolarTimestampToUnixTimeStamp(data.gyrTimestamp)}`).then();
        }
      }
    );

    const errorGyrListener = polarEmitter.addListener(
      emittedEventId.POLAR_GYR_ERROR,
      (error) => {
        console.log('GYR stream error:', error);
        Alert.alert(
          'Error',
          'Polar device is yet to be detected. Try starting GYR Stream again.',
          [
            {
              text: 'OK',
              onPress: () => {
                console.log('OK Pressed');
                toggleGyrStreamStatus();
                disposeGyrStream();
              },
            },
          ]
        );
      }
    );

    const completeGyrListener = polarEmitter.addListener(
      emittedEventId.POLAR_GYR_COMPLETE,
      (msg) => {
        console.log('GYR stream complete:', msg);
      }
    );

    const ppgListener = polarEmitter.addListener(
      emittedEventId.POLAR_PPG_DATA,
      (data: PpgData) => {
        console.log(
          'PPG Stream:', `Polar device ${deviceId}`,
          `ppg0: ${data.ppg0} ppg1: ${data.ppg1} ppg2: ${data.ppg2} ambient: ${data.ambient} timestamp: ${formatPolarTimestamp(data.ppgTimestamp)}`
        );
        if(isLogCSVEnabled) {
          logPpgToCSV(Number(data.ppg0), Number(data.ppg1), Number(data.ppg2), Number(data.ambient), `${formatPolarTimestampToUnixTimeStamp(data.ppgTimestamp)}`).then();
        }
      }
    );

    const errorPpgListener = polarEmitter.addListener(
      emittedEventId.POLAR_PPG_ERROR,
      (error) => {
        console.log('PPG stream error:', error);
        Alert.alert(
          'Error',
          'Polar device is yet to be detected. Try starting PPG Stream again.',
          [
            {
              text: 'OK',
              onPress: () => {
                console.log('OK Pressed');
                togglePpgStreamStatus();
                disposePpgStream();
              },
            },
          ]
        );
      }
    );

    const completePpgListener = polarEmitter.addListener(
      emittedEventId.POLAR_PPG_COMPLETE,
      (msg) => {
        console.log('PPG stream complete:', msg);
      }
    );

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
  }, [isLogCSVEnabled]);
  //
  // useEffect(() => {
  //   const downloadOfflineRecordingListener = polarEmitter.addListener(
  //     emittedEventId.POLAR_OFFLINE_RECORDING,
  //     (offlineRecording: any) => {
  //       console.log(`${offlineRecording}`);
  //     }
  //   );
  //   return () =>{
  //     downloadOfflineRecordingListener.remove();
  //   };
  // }, []);

  const handleConnect = (deviceId: string) => {
    console.log('DEBUG ME handleConnect');
    connectToDevice(deviceId).then((device: DeviceConnected) =>{
      console.log(`DEBUG ME Connected Polar Device: ${device.connectedDeviceId} Battery Level: ${device.batteryLevel}%`);
      setConnectedDeviceId(deviceId);
    });
  };
  //
  // const handleDisconnect = () => {
  //   disconnectFromDevice(deviceId);
  // };

  const BUFFER_MS = 5_000;

  const handleFetchHrData = () => {
    if (connectedDeviceId != null) {
      toggleHRStreamStatus();
      /**
       * bufferMs is an optional parameter. When not set, the package sends
       * the sensor data from the Polar device every 10 seconds by default.
       */
      fetchHrData(connectedDeviceId);
      // fetchHrData(connectedDeviceId, BUFFER_MS);
    } else {
      displayDialogNoConnectedDevice();
    }
  };

  const handleFetchAccData = () => {
    if (connectedDeviceId != null) {
      toggleAccStreamStatus();
      fetchAccData(connectedDeviceId, null);  // null falls back to default
    } else {
      displayDialogNoConnectedDevice();
    }
  };

  const handleFetchGyrData = () => {
    if (connectedDeviceId != null) {
      toggleGyrStreamStatus();
      fetchGyrData(connectedDeviceId, -1);  // negative value falls back to default
    } else {
      displayDialogNoConnectedDevice();
    }
  };

  const handleFetchPpgData = () => {
    if (connectedDeviceId != null) {
      togglePpgStreamStatus();
      fetchPpgData(connectedDeviceId, BUFFER_MS);
    } else {
      displayDialogNoConnectedDevice();
    }
  };

  const handleScanDevices = () => {
    setDevices([]);
    scanDevices();
  };

  const handleSdkMode = () => {
    if (connectedDeviceId != null) {
      toggleSdkModeStatus();
      if(isSdkModeToggled) {
        disableSdkMode(connectedDeviceId);
      } else {
        enableSdkMode(connectedDeviceId);
      }
    } else {
      displayDialogNoConnectedDevice();
    }
  };

  const stopAllStream = () => {
    if (isHRStreamToggled) handleFetchHrData(); // Stop HR stream if running
    if (isAccStreamToggled) handleFetchAccData(); // Stop ACC stream if running
    if (isGyrStreamToggled) handleFetchGyrData(); // Stop GYR stream if running
    if (isPpgStreamToggled) handleFetchPpgData(); // Stop PPG stream if running
  };

  const getOfflineRecordingList = (connectedDeviceId: string) =>{
    fetchOfflineRecordings(connectedDeviceId).then((offlineRecordings: OfflineRecording[]) =>{
      console.log('Polar Offline Recording', `List length ${offlineRecordings.length}`);
      // Sort by oldest to newest
      offlineRecordings.sort((a, b) => a.recTimestamp - b.recTimestamp);
      offlineRecordings.forEach((offlineRecordingEntry: OfflineRecording) =>{
        console.log('Polar Offline Recording', `Recording Start: ${offlineRecordingEntry.recTimestamp} Path: ${offlineRecordingEntry.path} Size: ${offlineRecordingEntry.size}`);
      });
    });
  }

  const getPolarDeviceTime = (connectedDeviceId: string)=>{
    getDeviceTime(connectedDeviceId).then((deviceTime: DeviceTime) =>{
      console.log('Polar Device Time',
        `${deviceTime.time} ms: ${deviceTime.timeMs} converted timeInMs: ${formatDateYYYYMMDDHHMMSS(deviceTime.timeMs)}`
      );
    });
  }

  const getPolarDiskSpace = (connectedDeviceId: string)=>{
    getDiskSpace(connectedDeviceId).then((diskSpace: DiskSpace) =>{
      console.log('Polar Disk Space', `Disk space: ${diskSpace.freeSpace} / ${diskSpace.totalSpace} Bytes`);
    });
  }

  const startPolarOfflineRecording = (connectedDeviceId: string)=>{
    getPolarDiskSpace(connectedDeviceId);
    startOfflineRecording(connectedDeviceId, offlineRecordingFeatureList).then((data) =>{
      console.log('Polar Start Offline Recording', `Result: ${data.result}`);
    });
  }

  const stopPolarOfflineRecording = (connectedDeviceId: string)=>{
    getPolarDiskSpace(connectedDeviceId);
    stopOfflineRecording(connectedDeviceId, offlineRecordingFeatureList).then((data) =>{
      console.log('Polar Stop Offline Recording', `Result: ${data.result}`);
    });
  }

  return (
    <ScrollView style={styles.scrollContainer}>
      <View style={styles.container}>
        {/*<Text>Result: {result}</Text>*/}
        <View style={styles.buttonContainer}>
          <Button title={`Connect ${deviceId}`}
                  onPress={() =>{handleConnect(deviceId)}} />
        </View>
        {/*<View style={styles.buttonContainer}>*/}
        {/*  <Button title="Disconnect" onPress={handleDisconnect}/></View>*/}
        <View style={styles.buttonContainer}>
          <Button
            title={devices.length > 0 ? 'Clear Scanned Devices' : 'Scan Devices'}
            onPress={() => {
              handleScanDevices();
            }}
          />
        </View>
        {devices.length > 0 && (
          <View style={{ marginTop: 30, width: '100%' }}>
            <Text style={{ fontWeight: 'bold', marginBottom: 10 }}>
              Scanned Devices:
            </Text>
            {devices.map((device) => {
              const isConnected = connectedDeviceId === device.deviceId;
              return (
                <View key={device.deviceId} style={styles.deviceItem}>
                  <View>
                    <Text>ID: {device.deviceId}</Text>
                    <Text>Name: {device.name || 'N/A'}</Text>
                    <Text>RSSI: {device.rssi || 'N/A'}</Text>
                  </View>
                  <Button
                    title={isConnected ? 'Disconnect' : 'Connect'}
                    onPress={() => {
                      if (isConnected) {
                        // Disable SDK Mode if enabled
                        if(isSdkModeToggled) handleSdkMode();
                        stopAllStream();
                        disconnectFromDevice(device.deviceId);
                        setConnectedDeviceId(null);
                      } else {
                        if (
                          connectedDeviceId &&
                          connectedDeviceId !== device.deviceId
                        ) {
                          // Disable SDK Mode if enabled
                          if(isSdkModeToggled) handleSdkMode();
                          stopAllStream();
                          disconnectFromDevice(connectedDeviceId);
                        }
                        handleConnect(device.deviceId);
                      }
                    }}
                  />
                </View>
              );
            })}
          </View>
        )}
        <View style={styles.buttonContainer}>
          <Button
            title={isSdkModeToggled ? 'Disable SDK Mode' : 'Enable SDK Mode'}
            onPress={() => {
              // Dispose all existing streams. SDK mode enable command stops all the streams
              // but client is not informed. This is workaround for the bug.
              stopAllStream();
              handleSdkMode();
            }}
          />
        </View>
        <View style={styles.switchContainer}>
          <Switch
            trackColor={{false: '#767577', true: '#81b0ff'}}
            thumbColor={isLogCSVEnabled ? '#f5dd4b' : '#f4f3f4'}
            ios_backgroundColor="#3e3e3e"
            onValueChange={toggleLogCSVSwitch}
            value={isLogCSVEnabled}
          />
          <Text style={styles.label}>Store Stream into CSV</Text>
        </View>
        <View style={styles.buttonContainer}>
          <Button
            title={
              isHRStreamToggled
                ? 'Stop Streaming HR Data'
                : 'Start Streaming HR Data'
            }
            onPress={() => {
              if (isHRStreamToggled) {
                toggleHRStreamStatus();
                disposeHrStream();
              } else handleFetchHrData();
            }}
          />
        </View>
        <View style={styles.buttonContainer}>
          <Button
            title={
              isAccStreamToggled
                ? 'Stop Streaming ACC Data'
                : 'Start Streaming ACC Data'
            }
            onPress={() => {
              if (isAccStreamToggled) {
                toggleAccStreamStatus();
                disposeAccStream();
              } else handleFetchAccData();
            }}
          />
        </View>
        <View style={styles.buttonContainer}>
          <Button
            title={
              isGyrStreamToggled
                ? 'Stop Streaming GYR Data'
                : 'Start Streaming GYR Data'
            }
            onPress={() => {
              if (isGyrStreamToggled) {
                toggleGyrStreamStatus();
                disposeGyrStream();
              } else handleFetchGyrData();
            }}
          />
        </View>
        <View style={styles.buttonContainer}>
          <Button
            title={
              isPpgStreamToggled
                ? 'Stop Streaming PPG Data'
                : 'Start Streaming PPG Data'
            }
            onPress={() => {
              if (isPpgStreamToggled) {
                togglePpgStreamStatus();
                disposePpgStream();
              } else handleFetchPpgData();
            }}
          />
        </View>
        <View style={styles.buttonContainer}>
          <Button
            title='Set Device Time'
            onPress={() => {
              if (connectedDeviceId != null) {
                setDeviceTime(connectedDeviceId);
              } else {
                displayDialogNoConnectedDevice();
              }
            }}
          />
        </View>
        <View style={styles.buttonContainer}>
          <Button
            title='Get Device Time'
            onPress={() => {
              if (connectedDeviceId != null) {
                getPolarDeviceTime(connectedDeviceId);
              } else {
                displayDialogNoConnectedDevice();
              }
            }}
          />
        </View>
        <View style={styles.buttonContainer}>
          <Button
            title='Get Disk Space'
            onPress={() => {
              if (connectedDeviceId != null) {
                getPolarDiskSpace(connectedDeviceId);
              } else {
                displayDialogNoConnectedDevice();
              }
            }}
          />
        </View>
        <View style={styles.buttonContainer}>
          <Button
            title='Start Offline Recording'
            onPress={() => {
              if (connectedDeviceId != null) {
                startPolarOfflineRecording(connectedDeviceId);
              } else {
                displayDialogNoConnectedDevice();
              }
            }}
          />
        </View>
        <View style={styles.buttonContainer}>
          <Button
            title='Stop Offline Recording'
            onPress={() => {
              if (connectedDeviceId != null) {
                stopPolarOfflineRecording(connectedDeviceId);
              } else {
                displayDialogNoConnectedDevice();
              }
            }}
          />
        </View>
        <View style={styles.buttonContainer}>
          <Button
            title='Get Offline Recordings'
            onPress={() => {
              if (connectedDeviceId != null) {
                getOfflineRecordingList(connectedDeviceId);
              } else {
                displayDialogNoConnectedDevice();
              }
            }}
          />
        </View>
        <View style={styles.buttonContainer}>
          <Button
            title='Delete All Offline Recordings'
            onPress={() => {
              if (connectedDeviceId != null) {
                deleteAllOfflineRecordings(connectedDeviceId);
              } else {
                displayDialogNoConnectedDevice();
              }
            }}
          />
        </View>
        <View style={styles.buttonContainer}>
          <Button
            title='Download Offline Recordings'
            onPress={() => {
              if (connectedDeviceId != null) {
                downloadOfflineRecordings(connectedDeviceId);
              } else {
                displayDialogNoConnectedDevice();
              }
            }}
          />
        </View>
        <View style={styles.buttonContainer}>
          <Button
            title='Enable Recording Trigger'
            onPress={() => {
              if (connectedDeviceId != null) {
                setPolarRecordingTrigger(connectedDeviceId,
                  OfflineRecordingTriggerMode.TRIGGER_SYSTEM_START,
                  offlineRecordingFeatureList);
              } else {
                displayDialogNoConnectedDevice();
              }
            }}
          />
        </View>
        <View style={styles.buttonContainer}>
          <Button
            title='Disable Recording Trigger'
            onPress={() => {
              if (connectedDeviceId != null) {
                setPolarRecordingTrigger(connectedDeviceId,
                  OfflineRecordingTriggerMode.TRIGGER_DISABLED,
                  offlineRecordingFeatureList);
              } else {
                displayDialogNoConnectedDevice();
              }
            }}
          />
        </View>
        {/*<View style={styles.buttonContainer}>*/}
        {/*  <Button title="Request Bluetooth Permission" onPress={ requestBluetoothPermissions }/></View>*/}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
    backgroundColor: '#FFF',
  },

  scrollContainer: {
    flex: 1,
    backgroundColor: '#FFF'
  },

  switchContainer: {
    marginTop: 20,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },

  label: {
    fontSize: 16,
    color: '#000',
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
  },
});
