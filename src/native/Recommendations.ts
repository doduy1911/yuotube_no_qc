import {NativeModules} from 'react-native';
import {mapRecommendationSet, type RecommendationSet} from '../domain/musicDiscovery';

type NativeRecommendations = {getRecommendations(videoId: string): Promise<string>};
const nativeRecommendations = NativeModules.NativeVideoExtractor as NativeRecommendations;

export async function getRecommendations(videoId: string): Promise<RecommendationSet> {
  const response: unknown = JSON.parse(await nativeRecommendations.getRecommendations(videoId));
  return mapRecommendationSet(response);
}
