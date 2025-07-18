import PolarBridge from './NativePolarBridge';

export function multiply(a: number, b: number): number {
  return PolarBridge.multiply(a, b);
}

export function connectToDevice(deviceId: string) {
  return PolarBridge.connectToDevice(deviceId);
}

export function disconnectFromDevice(deviceId: string) {
  return PolarBridge.disconnectFromDevice(deviceId);
}

export function scanDevices(){
  return PolarBridge.scanDevices();
}

export function fetchHrData(deviceId: string) {
  return PolarBridge.fetchHrData(deviceId);
}
