import {NativeModules} from 'react-native';

export interface Spec {
  getDeviceInfo(): Promise<string>;
  getUpdateState(): Promise<string>;
  startDownload(releaseJson: string): Promise<string>;
  cancelDownload(): Promise<string>;
  inspectDownloadedApk(): Promise<string>;
  installDownloadedApk(): Promise<string>;
}

export default NativeModules.NativeApkUpdater as Spec;
