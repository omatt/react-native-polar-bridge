import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  multiply(a: number, b: number): number;
  connectToDevice(deviceId: string): void;
  disconnectFromDevice(deviceId: string): void;
  fetchHrData(deviceId: string): void;
  scanDevices(): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('PolarBridge');
