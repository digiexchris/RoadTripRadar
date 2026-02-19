import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'ca.voiditswarranty.roadtripradar',
  appName: 'RoadTripRadar',
  webDir: 'dist',
  server: {
    androidScheme: 'http'
  }
};

export default config;
