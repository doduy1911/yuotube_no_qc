import {NativeModules} from 'react-native';

type NativeSearchSuggestions = {getSearchSuggestions(query: string): Promise<string>};
const nativeSuggestions = NativeModules.NativeVideoExtractor as NativeSearchSuggestions;

export async function getSearchSuggestions(query: string): Promise<string[]> {
  const value: unknown = JSON.parse(await nativeSuggestions.getSearchSuggestions(query));
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}
