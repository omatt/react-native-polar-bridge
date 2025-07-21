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

export function disposeHrStream(){
  return PolarBridge.disposeHrStream();
}

export const emittedEventId = Object.freeze({
  SCAN_DEVICE_FOUND: 'onDeviceFound',
  SCAN_DEVICE_ERROR: 'onScanError',
  SCAN_DEVICE_COMPLETE: 'onScanComplete',
  POLAR_HR_DATA: 'PolarHrData',
  POLAR_HR_ERROR: 'PolarHrError',
  POLAR_HR_COMPLETE: 'PolarHrComplete',
});
