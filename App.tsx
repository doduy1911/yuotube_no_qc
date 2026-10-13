import React, {useRef, useState} from 'react';
import {
  ActivityIndicator,
  FlatList,
  Image,
  Keyboard,
  Alert,
  Pressable,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import {
  SafeAreaProvider,
  SafeAreaView,
} from 'react-native-safe-area-context';
import NativeVideoExtractor from './src/native/specs/NativeVideoExtractor';
import {parseYouTubeVideoId} from './src/domain/youtubeUrl';
import {searchYouTube} from './src/native/YouTubeSearch';
import {
  getSearchErrorMessage,
  mergeUniqueVideos,
  type SearchPage,
  type VideoSummary,
} from './src/domain/youtubeSearch';

function App() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<VideoSummary[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [nextPageToken, setNextPageToken] = useState<string | null>(null);
  const generation = useRef(0);
  const loadingMore = useRef(false);

  async function openVideo(videoId: string) {
    try {
      await NativeVideoExtractor.openVideo(videoId, '');
    } catch {
      Alert.alert('Chưa mở được video', 'Không lấy được nguồn phát. Bạn thử lại sau nhé.');
    }
  }

  async function submitQuery() {
    Keyboard.dismiss();
    const videoId = parseYouTubeVideoId(query);
    if (videoId) {
      await openVideo(videoId);
      return;
    }
    if (!query.trim()) return;

    const request = ++generation.current;
    loadingMore.current = false;
    setIsSearching(true);
    setIsLoadingMore(false);
    setSearchError('');
    setResults([]);
    setNextPageToken(null);
    try {
      const page = await searchYouTube(query.trim());
      if (request !== generation.current) return;
      setResults(page.items);
      setNextPageToken(page.nextPageToken);
    } catch (error) {
      if (request !== generation.current) return;
      setSearchError(getSearchErrorMessage(error));
    } finally {
      if (request === generation.current) setIsSearching(false);
    }
  }

  async function loadNextPage() {
    if (!nextPageToken || loadingMore.current || isSearching) return;
    const request = generation.current;
    const token = nextPageToken;
    loadingMore.current = true;
    setIsLoadingMore(true);
    try {
      const page: SearchPage = await searchYouTube(query.trim(), token);
      if (request !== generation.current) return;
      setResults(current => mergeUniqueVideos(current, page.items));
      setNextPageToken(page.nextPageToken);
    } catch (error) {
      if (request === generation.current) setSearchError(getSearchErrorMessage(error));
    } finally {
      if (request === generation.current) {
        loadingMore.current = false;
        setIsLoadingMore(false);
      }
    }
  }

  return (
    <SafeAreaProvider>
      <StatusBar barStyle="light-content" />
      <SafeAreaView style={styles.screen}>
        <View style={styles.header}>
          <View style={styles.brandMark}>
            <View style={styles.playTriangle} />
          </View>
          <Text testID="home-title" style={styles.brandName}>
            TubeLite
          </Text>
          <View style={styles.headerSpacer} />
          <Text style={styles.headerCaption}>BETA</Text>
        </View>

        <View style={styles.content}>
          <Text style={styles.sectionTitle}>Khám phá video</Text>
          <View style={styles.searchBox}>
            <Text style={styles.searchIcon}>⌕</Text>
            <TextInput
              testID="search-input"
              accessibilityLabel="Tìm kiếm video trên YouTube"
              style={styles.searchInput}
              placeholder="Tìm video hoặc dán link YouTube"
              value={query}
              onChangeText={setQuery}
              onSubmitEditing={submitQuery}
              placeholderTextColor="#8b8b8b"
              returnKeyType="search"
              selectionColor="#ff453a"
            />
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Tìm kiếm hoặc mở video"
              onPress={submitQuery}
              style={styles.openButton}>
              <Text style={styles.openButtonLabel}>
                {parseYouTubeVideoId(query) ? 'Mở' : 'Tìm'}
              </Text>
            </Pressable>
          </View>

          {isSearching && results.length === 0 ? (
            <View style={styles.statusState}>
              <ActivityIndicator color="#ff453a" />
              <Text style={styles.statusText}>Đang tìm video…</Text>
            </View>
          ) : null}

          {searchError ? (
            <Text accessibilityRole="alert" style={styles.errorText}>
              {searchError}
            </Text>
          ) : null}

          {results.length > 0 ? (
            <FlatList
              testID="search-results"
              data={results}
              keyExtractor={item => item.videoId}
              renderItem={({item}) => (
                <Pressable
                  accessibilityRole="button"
                  onPress={() => openVideo(item.videoId)}
                  style={styles.resultRow}>
                  {item.thumbnailUrl ? (
                    <Image source={{uri: item.thumbnailUrl}} style={styles.thumbnail} />
                  ) : <View style={styles.thumbnailFallback} />}
                  <View style={styles.resultCopy}>
                    <Text numberOfLines={2} style={styles.resultTitle}>{item.title}</Text>
                    <Text numberOfLines={1} style={styles.resultMeta}>{item.channelTitle}</Text>
                  </View>
                </Pressable>
              )}
              onEndReached={loadNextPage}
              onEndReachedThreshold={0.6}
              ListFooterComponent={isLoadingMore ? (
                <ActivityIndicator style={styles.listSpinner} color="#ff453a" />
              ) : undefined}
            />
          ) : null}

          {!isSearching && results.length === 0 && !searchError ? <View testID="home-empty-state" style={styles.emptyState}>
            <View style={styles.emptyIconRing}>
              <View style={styles.emptyPlayTriangle} />
            </View>
            <Text style={styles.emptyTitle}>
              {query.trim() ? 'Không tìm thấy video' : 'Video bạn muốn xem'}
            </Text>
            <Text style={styles.emptyDescription}>
              {query.trim()
                ? 'Thử từ khóa khác hoặc dán link YouTube.'
                : 'Tìm kiếm một chủ đề hoặc dán liên kết video để bắt đầu.'}
            </Text>
          </View> : null}
        </View>

        <View style={styles.bottomBar}>
          <View style={styles.navItem}>
            <Text style={styles.navIconActive}>⌂</Text>
            <Text style={styles.navLabelActive}>Trang chủ</Text>
          </View>
          <View style={styles.navItem}>
            <Text style={styles.navIcon}>◷</Text>
            <Text style={styles.navLabel}>Thư viện</Text>
          </View>
        </View>
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#0b0b0b',
  },
  header: {
    height: 62,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    borderBottomColor: '#222222',
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  brandMark: {
    width: 30,
    height: 23,
    borderRadius: 7,
    backgroundColor: '#ff453a',
    alignItems: 'center',
    justifyContent: 'center',
  },
  playTriangle: {
    width: 0,
    height: 0,
    marginLeft: 2,
    borderTopWidth: 5,
    borderBottomWidth: 5,
    borderLeftWidth: 8,
    borderTopColor: 'transparent',
    borderBottomColor: 'transparent',
    borderLeftColor: '#ffffff',
  },
  brandName: {
    marginLeft: 10,
    color: '#f7f7f7',
    fontSize: 20,
    fontWeight: '800',
    letterSpacing: -0.6,
  },
  headerSpacer: {
    flex: 1,
  },
  headerCaption: {
    color: '#ff6259',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 1.2,
  },
  content: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 24,
  },
  sectionTitle: {
    color: '#f7f7f7',
    fontSize: 22,
    fontWeight: '700',
    letterSpacing: -0.4,
    marginBottom: 18,
  },
  searchBox: {
    height: 52,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#343434',
    backgroundColor: '#191919',
  },
  searchIcon: {
    color: '#a6a6a6',
    fontSize: 27,
    lineHeight: 30,
    marginRight: 10,
  },
  searchInput: {
    flex: 1,
    color: '#f7f7f7',
    fontSize: 14,
    paddingVertical: 0,
  },
  openButton: {
    minWidth: 44,
    height: 34,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 10,
    backgroundColor: '#ff453a',
    paddingHorizontal: 11,
  },
  openButtonLabel: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: '700',
  },
  statusState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  statusText: {
    color: '#a6a6a6',
    fontSize: 14,
  },
  errorText: {
    marginTop: 20,
    color: '#ff8a82',
    fontSize: 14,
    lineHeight: 21,
  },
  resultRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#282828',
  },
  thumbnail: {
    width: 120,
    height: 68,
    borderRadius: 8,
    backgroundColor: '#191919',
  },
  thumbnailFallback: {
    width: 120,
    height: 68,
    borderRadius: 8,
    backgroundColor: '#191919',
  },
  resultCopy: {
    flex: 1,
    gap: 6,
  },
  resultTitle: {
    color: '#f7f7f7',
    fontSize: 14,
    fontWeight: '600',
    lineHeight: 19,
  },
  resultMeta: {
    color: '#999999',
    fontSize: 12,
  },
  listSpinner: {
    paddingVertical: 16,
  },
  emptyState: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 22,
    paddingBottom: 42,
  },
  emptyIconRing: {
    width: 82,
    height: 82,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 41,
    backgroundColor: '#211514',
    borderWidth: 1,
    borderColor: '#46211e',
    marginBottom: 22,
  },
  emptyPlayTriangle: {
    width: 0,
    height: 0,
    marginLeft: 4,
    borderTopWidth: 11,
    borderBottomWidth: 11,
    borderLeftWidth: 17,
    borderTopColor: 'transparent',
    borderBottomColor: 'transparent',
    borderLeftColor: '#ff5147',
  },
  emptyTitle: {
    color: '#f4f4f4',
    fontSize: 17,
    fontWeight: '700',
    marginBottom: 8,
  },
  emptyDescription: {
    color: '#999999',
    fontSize: 13,
    lineHeight: 20,
    textAlign: 'center',
    maxWidth: 290,
  },
  bottomBar: {
    height: 66,
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    borderTopColor: '#222222',
    borderTopWidth: StyleSheet.hairlineWidth,
    backgroundColor: '#101010',
  },
  navItem: {
    minWidth: 90,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 3,
  },
  navIconActive: {
    color: '#ff5147',
    fontSize: 22,
    lineHeight: 24,
  },
  navIcon: {
    color: '#888888',
    fontSize: 21,
    lineHeight: 24,
  },
  navLabelActive: {
    color: '#ff6259',
    fontSize: 10,
    fontWeight: '700',
  },
  navLabel: {
    color: '#888888',
    fontSize: 10,
    fontWeight: '500',
  },
});

export default App;
