/**
 * Use these type alis if needed to map values from listening to Polar Events.
 *
 * Sample:
 *
 * polarEmitter.addListener(emittedEventId.POLAR_HR_DATA, (data: HrData) => {
 *     console.log('Polar', 'Heart Rate:', `${data.hr} bpm`);
 * });
 *
 */

export type ScannedDevice = {
  deviceId: string;
  address?: string;
  rssi?: number;
  name?: string;
  isConnectable?: boolean;
};

export type HrData = {
  hr: number;
  rrsMs: number[];
  rrAvailable: boolean;
  contactStatus: boolean;
  contactStatusSupported: boolean;
  timestamp: number;
};

export type AccData = {
  accX: number;
  accY: number;
  accZ: number;
  accTimestamp: number;
};

export type GyrData = {
  gyrX: string;
  gyrY: string;
  gyrZ: string;
  gyrTimestamp: number;
};

export type PpgData = {
  ppg0: string;
  ppg1: string;
  ppg2: string;
  ambient: string;
  ppgTimestamp: number;
};

export type DeviceTime = {
  time: string;
  timeMs: number;
};

export type DiskSpace = {
  freeSpace: number;
  totalSpace: number;
};

export type OfflineRecording = {
  recTimestamp: number;
  path: string;
  size: number;
};

export type DeviceConnected = {
  connectedDeviceId: string;
  batteryLevel : number;
};
