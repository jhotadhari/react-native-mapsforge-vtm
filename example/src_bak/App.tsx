import { StyleSheet, View } from 'react-native';
import { MapsforgeVtmView } from 'react-native-mapsforge-vtm';

export default function App() {
  return (
    <View style={styles.container}>
      <MapsforgeVtmView color="#ff0000" style={styles.box} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
});