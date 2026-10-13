import {NativeModules} from 'react-native';
import {mapSearchPage, type SearchPage} from '../domain/youtubeSearch';

type NativeSearchModule = {
  searchVideos(query: string, pageToken: string): Promise<string>;
};

const nativeSearch = NativeModules.NativeVideoExtractor as NativeSearchModule;

export async function searchYouTube(
  query: string,
  pageToken = '',
): Promise<SearchPage> {
  const json = await nativeSearch.searchVideos(query, pageToken);
  return mapSearchPage(JSON.parse(json) as unknown);
}
