import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'io.ionic.starter',
  appName: 'weather-radar',
  webDir: 'dist',
  server: {
    androidScheme: 'http'
  }
};

export default config;
