import {NativeModules} from 'react-native';

export interface Spec {
  getVideoDetails(videoId: string): Promise<string>;
  openVideo(videoId: string, qualityId: string): Promise<boolean>;
}

export default NativeModules.NativeVideoExtractor as Spec;
