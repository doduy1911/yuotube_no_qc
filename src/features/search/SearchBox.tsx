import React, {useEffect, useRef, useState} from 'react';
import {Pressable, StyleSheet, Text, TextInput, View} from 'react-native';
import {parseYouTubeVideoId} from '../../domain/youtubeUrl';
import {getSearchSuggestions} from '../../native/SearchSuggestions';

type Props = {value: string; onChangeText: (value: string) => void; onSubmit: (value: string) => void};

export default function SearchBox({value, onChangeText, onSubmit}: Props) {
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const generation = useRef(0);
  const suppressSuggestionsFor = useRef<string | null>(null);
  const hasVideoLink = Boolean(parseYouTubeVideoId(value));

  useEffect(() => {
    const query = value.trim();
    const request = ++generation.current;
    if (suppressSuggestionsFor.current === query) {
      suppressSuggestionsFor.current = null;
      return;
    }
    if (query.length < 2 || hasVideoLink) return;
    const timer = setTimeout(() => {
      if (request !== generation.current) return;
      getSearchSuggestions(query).then(items => {
        if (request !== generation.current) return;
        const seen = new Set<string>();
        setSuggestions(items.map(item => item.trim()).filter(item => {
          const key = item.toLocaleLowerCase();
          if (!item || seen.has(key)) return false;
          seen.add(key);
          return true;
        }).slice(0, 8));
      }).catch(() => {
        if (request === generation.current) setSuggestions([]);
      });
    }, 300);
    return () => clearTimeout(timer);
  }, [value, hasVideoLink]);

  const handleChange = (text: string) => {
    generation.current++;
    setSuggestions([]);
    onChangeText(text);
  };
  const handleSubmit = (text: string) => {
    generation.current++;
    setSuggestions([]);
    onSubmit(text);
  };
  const selectSuggestion = (text: string) => {
    generation.current++;
    suppressSuggestionsFor.current = text.trim();
    setSuggestions([]);
    onChangeText(text);
    onSubmit(text);
  };

  return <View>
    <View style={styles.box}>
      <Text style={styles.icon}>⌕</Text>
      <TextInput testID="search-input" accessibilityLabel="Tìm kiếm video trên YouTube" style={styles.input}
        placeholder="Tìm video hoặc dán link YouTube" placeholderTextColor="#8b8b8b" value={value} onChangeText={handleChange}
        onSubmitEditing={() => handleSubmit(value)} returnKeyType="search" selectionColor="#ff453a" />
      <Pressable accessibilityRole="button" accessibilityLabel="Tìm kiếm hoặc mở video" onPress={() => handleSubmit(value)} style={styles.button}>
        <Text style={styles.buttonText}>{hasVideoLink ? 'Mở' : 'Tìm'}</Text>
      </Pressable>
    </View>
    {suggestions.length > 0 ? <View testID="search-suggestions" style={styles.suggestions}>{suggestions.map(item => <Pressable key={item} testID="search-suggestion" accessibilityRole="button" onPress={() => selectSuggestion(item)} style={styles.suggestion}>
      <Text style={styles.suggestionIcon}>⌕</Text><Text testID="search-suggestion-label" numberOfLines={1} style={styles.suggestionText}>{item}</Text>
    </Pressable>)}</View> : null}
  </View>;
}

const styles = StyleSheet.create({
  box: {height: 52, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, borderRadius: 14, borderWidth: 1, borderColor: '#343434', backgroundColor: '#191919'},
  icon: {color: '#a6a6a6', fontSize: 25, marginRight: 10}, input: {flex: 1, color: '#f7f7f7', fontSize: 14, paddingVertical: 0},
  button: {minWidth: 44, height: 34, alignItems: 'center', justifyContent: 'center', borderRadius: 10, backgroundColor: '#ff453a', paddingHorizontal: 11},
  buttonText: {color: 'white', fontWeight: '700', fontSize: 12},
  suggestions: {marginTop: 6, borderRadius: 12, overflow: 'hidden', backgroundColor: '#191919', borderWidth: StyleSheet.hairlineWidth, borderColor: '#343434'},
  suggestion: {minHeight: 44, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#303030'},
  suggestionIcon: {color: '#a6a6a6', fontSize: 20, marginRight: 12}, suggestionText: {color: '#eee', fontSize: 14, flex: 1},
});
