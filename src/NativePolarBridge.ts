import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  multiply(a: number, b: number): number;
  connectToDevice(deviceId: string): void;
  disconnectFromDevice(deviceId: string): void;
  fetchHrData(deviceId: string): void;
  fetchAccData(deviceId: string): void;
  fetchGyrData(deviceId: string): void;
  fetchPpgData(deviceId: string): void;
  enableSdkMode(deviceId: string): void;
  disableSdkMode(deviceId: string): void;
  getDeviceTime(deviceId: string): void;
  setDeviceTime(deviceId: string): void;
  getDiskSpace(deviceId: string): void;
  setPolarRecordingTrigger(deviceId: string, recordingMode: number, features: string[]): void;
  fetchOfflineRecordings(deviceId: string): void;
  deleteAllOfflineRecordings(deviceId: string): void;
  scanDevices(): void;
  disposeHrStream(): void;
  disposeAccStream(): void;
  disposeGyrStream(): void;
  disposePpgStream(): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('PolarBridge');
