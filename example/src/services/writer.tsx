import RNFS from "react-native-fs";
import {formatDateYYYYMMDDHHMMSS} from "./utils";

export async function logHeartRateToCSV(hr: number) {
  const csvFilePath = `${RNFS.DownloadDirectoryPath}/polar_heart_rate_log.csv`;
  const timestamp = formatDateYYYYMMDDHHMMSS(new Date());
  const csvHrLine = `${timestamp},${hr}\n`;

  try {
    const fileExists = await RNFS.exists(csvFilePath);

    if (!fileExists) {
      // Write header and first line
      const header = 'timestamp,heart_rate\n';
      await RNFS.writeFile(csvFilePath, header + csvHrLine, 'utf8');
    } else {
      // Append new line
      await RNFS.appendFile(csvFilePath, csvHrLine, 'utf8');
    }

    console.log(`✅ Logged HR to CSV: ${csvHrLine.trim()} on path: ${csvFilePath}`);
  } catch (error) {
    console.error('❌ Failed to log HR to CSV:', error);
  }
}

export async function logAccToCSV(x: number, y: number, z: number, timestamp: String) {
  const csvFilePath = `${RNFS.DownloadDirectoryPath}/polar_accelerometer_log.csv`;
  const csvAccLine = `${timestamp},${x},${y},${z}\n`;

  try {
    const fileExists = await RNFS.exists(csvFilePath);

    if (!fileExists) {
      // Write header and first line
      const header = 'timestamp,x,y,z\n';
      await RNFS.writeFile(csvFilePath, header + csvAccLine, 'utf8');
    } else {
      // Append new line
      await RNFS.appendFile(csvFilePath, csvAccLine, 'utf8');
    }

    console.log(`✅ Logged ACC to CSV: ${csvAccLine.trim()} on path: ${csvFilePath}`);
  } catch (error) {
    console.error('❌ Failed to log ACC to CSV:', error);
  }
}

export async function logGyroToCSV(x: number, y: number, z: number, timestamp: String) {
  const csvFilePath = `${RNFS.DownloadDirectoryPath}/polar_gyro_log.csv`;
  const csvGyrLine = `${timestamp},${x},${y},${z}\n`;

  try {
    const fileExists = await RNFS.exists(csvFilePath);

    if (!fileExists) {
      // Write header and first line
      const header = 'timestamp,x,y,z\n';
      await RNFS.writeFile(csvFilePath, header + csvGyrLine, 'utf8');
    } else {
      // Append new line
      await RNFS.appendFile(csvFilePath, csvGyrLine, 'utf8');
    }

    console.log(`✅ Logged GYR to CSV: ${csvGyrLine.trim()} on path: ${csvFilePath}`);
  } catch (error) {
    console.error('❌ Failed to log GYR to CSV:', error);
  }
}

export async function logPpgToCSV(ppg0: number, ppg1: number, ppg2: number, ambient: number, timestamp: String) {
  const csvFilePath = `${RNFS.DownloadDirectoryPath}/polar_ppg_log.csv`;
  const csvGyrLine = `${timestamp},${ppg0},${ppg1},${ppg2},${ambient}\n`;

  try {
    const fileExists = await RNFS.exists(csvFilePath);

    if (!fileExists) {
      // Write header and first line
      const header = 'timestamp,ppg0,ppg1,ppg2,ambient\n';
      await RNFS.writeFile(csvFilePath, header + csvGyrLine, 'utf8');
    } else {
      // Append new line
      await RNFS.appendFile(csvFilePath, csvGyrLine, 'utf8');
    }

    console.log(`✅ Logged PPG to CSV: ${csvGyrLine.trim()} on path: ${csvFilePath}`);
  } catch (error) {
    console.error('❌ Failed to log PPG to CSV:', error);
  }
}
