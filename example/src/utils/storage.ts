import AsyncStorage from '@react-native-async-storage/async-storage';

const POLAR_DEVICE_ID_KEY = 'polar_connected_device_id';

/**
 * Save the connected Polar device ID
 */
export const setConnectedPolarDeviceId = async (deviceId: string): Promise<void> => {
  try {
    await AsyncStorage.setItem(POLAR_DEVICE_ID_KEY, deviceId);
  } catch (e) {
    console.error('Failed to save data:', e);
  }
};

/**
 * Get the currently stored Polar device ID
 */
export const getConnectedPolarDeviceId = async (): Promise<string | null> => {
  try {
    return await AsyncStorage.getItem(POLAR_DEVICE_ID_KEY);
  } catch (e) {
    console.error('Failed to fetch data:', e);
    throw e;
  }
};

/**
 * Remove the stored Polar device ID (e.g. on disconnect)
 */
export const clearConnectedPolarDeviceId = async (): Promise<void> => {
  await AsyncStorage.removeItem(POLAR_DEVICE_ID_KEY);
};
