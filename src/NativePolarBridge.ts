import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
import type { DeviceConnected, DeviceTime, DiskSpace, OfflineRecording } from './PolarDataModel';

export interface Spec extends TurboModule {
  multiply(a: number, b: number): number;
  connectToDevice(deviceId: string): Promise<DeviceConnected>;
  disconnectFromDevice(deviceId: string): void;
  fetchHrData(deviceId: string): void;
  fetchAccData(deviceId: string): void;
  fetchGyrData(deviceId: string): void;
  fetchPpgData(deviceId: string): void;
  enableSdkMode(deviceId: string): void;
  disableSdkMode(deviceId: string): void;
  getDeviceTime(deviceId: string): Promise<DeviceTime>;
  setDeviceTime(deviceId: string): void;
  getDiskSpace(deviceId: string): Promise<DiskSpace>;
  startOfflineRecording(deviceId: string, features: string[]): Promise<any>;
  stopOfflineRecording(deviceId: string, features: string[]): Promise<any>;
  setPolarRecordingTrigger(deviceId: string, recordingMode: number, features: string[]): void;
  fetchOfflineRecordings(deviceId: string): Promise<OfflineRecording[]>;
  downloadOfflineRecordings(deviceId: string): void;
  deleteAllOfflineRecordings(deviceId: string): void;
  scanDevices(): void;
  disposeHrStream(): void;
  disposeAccStream(): void;
  disposeGyrStream(): void;
  disposePpgStream(): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('PolarBridge');
