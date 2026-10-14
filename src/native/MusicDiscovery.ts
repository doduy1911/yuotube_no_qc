import {NativeModules} from 'react-native';
import {mapTrendingSection, type TrendingSection, type TrendingSectionId} from '../domain/musicDiscovery';

type NativeMusicDiscovery = {
  getTrendingMusic(section: TrendingSectionId, forceRefresh: boolean): Promise<string>;
};

const nativeDiscovery = NativeModules.NativeVideoExtractor as NativeMusicDiscovery;

export async function getTrendingMusic(section: TrendingSectionId, forceRefresh = false): Promise<TrendingSection> {
  return mapTrendingSection(JSON.parse(await nativeDiscovery.getTrendingMusic(section, forceRefresh)) as unknown);
}
