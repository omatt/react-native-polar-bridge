import {Alert, Platform} from "react-native";
import {
  checkMultiple,
  openSettings,
  PERMISSIONS,
  requestMultiple,
  RESULTS,
  type Permission,
} from "react-native-permissions";

const TAG = 'PermissionManager';

export async function checkPermission(){
  let isGranted = false;
  if(Platform.OS === 'ios'){
    const statuses = await checkMultiple([PERMISSIONS.IOS.BLUETOOTH]);
    const permissionBluetooth = statuses[PERMISSIONS.IOS.BLUETOOTH];
    const permissionBluetoothGranted = permissionBluetooth === RESULTS.GRANTED;

    console.log(TAG, 'Granted all IOS permissions?', `${permissionBluetooth} ${permissionBluetoothGranted}`);

    isGranted = permissionBluetoothGranted;
  }
  if(Platform.OS === 'android'){
    const statuses = await checkMultiple([PERMISSIONS.ANDROID.BLUETOOTH_SCAN, PERMISSIONS.ANDROID.BLUETOOTH_CONNECT]);
    const permissionBluetoothScan = statuses[PERMISSIONS.ANDROID.BLUETOOTH_SCAN];
    const permissionBluetoothConnect = statuses[PERMISSIONS.ANDROID.BLUETOOTH_CONNECT];

    const permissionBluetoothScanGranted = permissionBluetoothScan === RESULTS.GRANTED;
    const permissionBluetoothConnectGranted = permissionBluetoothConnect === RESULTS.GRANTED;

    isGranted = permissionBluetoothScanGranted && permissionBluetoothConnectGranted;
    console.log(TAG, `Granted all ANDROID permissions? ${permissionBluetoothScanGranted} ${permissionBluetoothConnectGranted}`);
  }
  return isGranted;
}

export async function requestPermissions() {
  if(Platform.OS === 'ios'){
    checkMultiple([PERMISSIONS.IOS.BLUETOOTH])
      .then((statuses) => {
        console.log(TAG, 'CHECK PERMISSIONS.IOS.BLUETOOTH', statuses[PERMISSIONS.IOS.BLUETOOTH]);

        let permissions: Permission[] = [];
        if(statuses[PERMISSIONS.IOS.BLUETOOTH] === RESULTS.DENIED){permissions.push(PERMISSIONS.IOS.BLUETOOTH);}

        if(permissions.length > 0) {requestMultiple(permissions).then();}
      });
  } else if(Platform.OS === 'android') {
    checkMultiple([PERMISSIONS.ANDROID.BLUETOOTH_SCAN, PERMISSIONS.ANDROID.BLUETOOTH_CONNECT])
      .then((statuses) => {
        console.log(TAG, 'CHECK PERMISSIONS.ANDROID.BLUETOOTH_SCAN', statuses[PERMISSIONS.ANDROID.BLUETOOTH_SCAN]);
        console.log(TAG, 'CHECK PERMISSIONS.ANDROID.BLUETOOTH_CONNECT', statuses[PERMISSIONS.ANDROID.BLUETOOTH_CONNECT]);
        let permissions: Permission[] = [];
        if(statuses[PERMISSIONS.ANDROID.BLUETOOTH_SCAN] !== RESULTS.GRANTED){
          permissions.push(PERMISSIONS.ANDROID.BLUETOOTH_SCAN);
        }
        if(statuses[PERMISSIONS.ANDROID.BLUETOOTH_CONNECT] !== RESULTS.GRANTED){
          permissions.push(PERMISSIONS.ANDROID.BLUETOOTH_CONNECT);
        }

        console.log(TAG, `Permission(s) To Be Requested ${permissions.length}`);
        if(permissions.length > 0) {
          // console.log(TAG, 'Permission Requested!');
          requestMultiple(permissions).then((status) => {
            if(status[PERMISSIONS.ANDROID.BLUETOOTH_SCAN] === RESULTS.BLOCKED ||
              status[PERMISSIONS.ANDROID.BLUETOOTH_CONNECT] === RESULTS.BLOCKED){
              openAppSettings();
            }
          });
        }
      });
  }
}

const openAppSettings = () =>{
  return (
    Alert.alert(
      'Berechtigungen sind blockiert',
      'Vorgang kann nicht fortgesetzt werden, da die Berechtigungen BLOCKIERT sind. Bitte öffnen Sie die App-Einstellungen, um die Berechtigungen manuell zu erteilen.',
      [{
        text: 'OK',
        onPress: () =>{
          console.log('Open Settings');
          openSettings('application').then();
        }
      },],
      { cancelable: true }
    )
  );
}
