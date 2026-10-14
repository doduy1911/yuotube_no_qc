import {NativeModules} from 'react-native';

export interface Spec {
  getVideoDetails(videoId: string): Promise<string>;
  openVideo(videoId: string, qualityId: string): Promise<boolean>;
  getHistory(): Promise<string>;
  getFavorites(): Promise<string>;
  setFavorite(videoId: string, enabled: boolean): Promise<boolean>;
  clearHistory(): Promise<boolean>;
  getTrendingMusic(section: 'vn' | 'international', forceRefresh: boolean): Promise<string>;
  getSearchSuggestions(query: string): Promise<string>;
  getRecommendations(videoId: string): Promise<string>;
}

export default NativeModules.NativeVideoExtractor as Spec;
