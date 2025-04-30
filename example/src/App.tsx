import {Text, View, StyleSheet, Button} from 'react-native';
import {connectToDevice, disconnectFromDevice, multiply} from 'react-native-polar-bridge';

const result = multiply(3, 7);

export default function App() {
  const deviceId = 'B4291522';

  // Function to handle connect button press
  const handleConnect = () => {
    connectToDevice(deviceId);
  };

  // Function to handle disconnect button press
  const handleDisconnect = () => {
    disconnectFromDevice(deviceId);
  };

  return (
    <View style={styles.container}>
      <Text>Result: {result}</Text>
      <View style={styles.buttonContainer}>
        <Button title="Connect" onPress={
          handleConnect
        }/>
      </View>
      <View style={styles.buttonContainer}>
        <Button title="Disconnect" onPress={handleDisconnect}/></View>
    </View>
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

  buttonContainer: {
    marginTop: 20,
  },
});
