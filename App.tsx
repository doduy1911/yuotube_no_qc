import React, {useCallback, useRef, useState} from 'react';
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
  View,
} from 'react-native';
import {
  SafeAreaProvider,
  SafeAreaView,
} from 'react-native-safe-area-context';
import NativeVideoExtractor from './src/native/specs/NativeVideoExtractor';
import {parseYouTubeVideoId} from './src/domain/youtubeUrl';
import {searchYouTube} from './src/native/YouTubeSearch';
import HomeScreen from './src/features/home/HomeScreen';
import SearchBox from './src/features/search/SearchBox';
import {
  getSearchErrorMessage,
  mergeUniqueVideos,
  type SearchPage,
  type VideoSummary,
} from './src/domain/youtubeSearch';

function App() {
  type LibraryVideo = VideoSummary & {positionMs: number; lastWatchedAt: number; isFavorite: boolean};
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<VideoSummary[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [nextPageToken, setNextPageToken] = useState<string | null>(null);
  const generation = useRef(0);
  const loadingMore = useRef(false);
  const [activeTab, setActiveTab] = useState<'home' | 'library'>('home');
  const [libraryMode, setLibraryMode] = useState<'history' | 'favorites'>('history');
  const [history, setHistory] = useState<LibraryVideo[]>([]);
  const [favorites, setFavorites] = useState<LibraryVideo[]>([]);

  const loadLibrary = useCallback(async () => {
    try {
      const [historyJson, favoritesJson] = await Promise.all([
        NativeVideoExtractor.getHistory(), NativeVideoExtractor.getFavorites(),
      ]);
      const mapRows = (raw: string): LibraryVideo[] => JSON.parse(raw).map((row: any) => ({
        videoId: row.videoId, title: row.title, channelTitle: row.channelTitle,
        thumbnailUrl: row.thumbnailUrl, publishedAt: null, durationMs: row.durationMs,
        positionMs: row.positionMs, lastWatchedAt: row.lastWatchedAt, isFavorite: row.isFavorite,
      }));
      setHistory(mapRows(historyJson));
      setFavorites(mapRows(favoritesJson));
    } catch {
      setHistory([]);
      setFavorites([]);
    }
  }, []);

  React.useEffect(() => { loadLibrary(); }, [loadLibrary]);

  async function openVideo(videoId: string) {
    try {
      await NativeVideoExtractor.openVideo(videoId, '');
      loadLibrary();
    } catch {
      Alert.alert('Chưa mở được video', 'Không lấy được nguồn phát. Bạn thử lại sau nhé.');
    }
  }

  function handleQueryChange(value: string) {
    if (value !== query) {
      generation.current++;
      loadingMore.current = false;
      setIsSearching(false);
      setIsLoadingMore(false);
      setResults([]);
      setNextPageToken(null);
      setSearchError('');
      setHasSearched(false);
    }
    setQuery(value);
  }

  async function submitQuery(submittedQuery = query) {
    Keyboard.dismiss();
    const normalizedQuery = submittedQuery.trim();
    const videoId = parseYouTubeVideoId(normalizedQuery);
    if (videoId) {
      await openVideo(videoId);
      return;
    }
    if (!normalizedQuery) return;
    setHasSearched(true);

    const request = ++generation.current;
    loadingMore.current = false;
    setIsSearching(true);
    setIsLoadingMore(false);
    setSearchError('');
    setResults([]);
    setNextPageToken(null);
    try {
      const page = await searchYouTube(normalizedQuery);
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
          {activeTab === 'library' ? <>
            <View style={styles.libraryHeading}>
              <Text style={styles.sectionTitle}>Thư viện</Text>
              {libraryMode === 'history' && history.length > 0 ? (
                <Pressable accessibilityRole="button" onPress={() => Alert.alert('Xóa lịch sử xem?', 'Video yêu thích vẫn được giữ lại.', [
                  {text: 'Hủy', style: 'cancel'}, {text: 'Xóa', style: 'destructive', onPress: async () => { await NativeVideoExtractor.clearHistory(); await loadLibrary(); }},
                ])}><Text style={styles.clearHistory}>Xóa</Text></Pressable>
              ) : null}
            </View>
            <View style={styles.libraryTabs}>
              <Pressable onPress={() => setLibraryMode('history')} style={[styles.libraryTab, libraryMode === 'history' && styles.libraryTabSelected]}><Text style={styles.libraryTabText}>Đã xem</Text></Pressable>
              <Pressable onPress={() => setLibraryMode('favorites')} style={[styles.libraryTab, libraryMode === 'favorites' && styles.libraryTabSelected]}><Text style={styles.libraryTabText}>Yêu thích</Text></Pressable>
            </View>
            <FlatList data={libraryMode === 'history' ? history : favorites} keyExtractor={item => item.videoId}
              ListEmptyComponent={<Text style={styles.libraryEmpty}>{libraryMode === 'history' ? 'Video đã xem sẽ xuất hiện ở đây.' : 'Video yêu thích sẽ xuất hiện ở đây.'}</Text>}
              renderItem={({item}) => <View style={styles.libraryRow}>
                <Pressable accessibilityRole="button" onPress={() => openVideo(item.videoId)} style={styles.libraryVideo}>
                  {item.thumbnailUrl ? <Image source={{uri: item.thumbnailUrl}} style={styles.thumbnail} /> : <View style={styles.thumbnailFallback} />}
                  <View style={styles.resultCopy}><Text numberOfLines={2} style={styles.resultTitle}>{item.title}</Text><Text numberOfLines={1} style={styles.resultMeta}>{item.positionMs > 0 ? 'Tiếp tục xem video' : (item.channelTitle || 'Video YouTube')}</Text></View>
                </Pressable>
                <Pressable accessibilityRole="button" accessibilityLabel={item.isFavorite ? 'Bỏ yêu thích' : 'Thêm yêu thích'} onPress={async () => { await NativeVideoExtractor.setFavorite(item.videoId, !item.isFavorite); await loadLibrary(); }}><Text style={styles.favoriteButton}>{item.isFavorite ? '♥' : '♡'}</Text></Pressable>
              </View>}
            />
          </> : <>
          <Text style={styles.sectionTitle}>Khám phá video</Text>
          <SearchBox value={query} onChangeText={handleQueryChange} onSubmit={submitQuery} />

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

          {!isSearching && results.length === 0 && !searchError && !query.trim() ? <HomeScreen onSelectVideo={video => openVideo(video.videoId)} recentVideos={history} /> : null}
          {!isSearching && hasSearched && results.length === 0 && !searchError && query.trim() ? <View testID="home-empty-state" style={styles.emptyState}>
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
          </>}
        </View>

        <View style={styles.bottomBar}>
          <Pressable accessibilityLabel="Open home" onPress={() => { setActiveTab('home'); loadLibrary(); }} style={styles.navItem}>
            <Text style={activeTab === 'home' ? styles.navIconActive : styles.navIcon}>⌂</Text>
            <Text style={activeTab === 'home' ? styles.navLabelActive : styles.navLabel}>Trang chủ</Text>
          </Pressable>
          <Pressable accessibilityLabel="Open library" onPress={() => { setActiveTab('library'); loadLibrary(); }} style={styles.navItem}>
            <Text style={activeTab === 'library' ? styles.navIconActive : styles.navIcon}>◷</Text>
            <Text style={activeTab === 'library' ? styles.navLabelActive : styles.navLabel}>Thư viện</Text>
          </Pressable>
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
  libraryHeading: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center'},
  clearHistory: {color: '#ff6259', fontSize: 13, fontWeight: '600', padding: 8},
  libraryTabs: {flexDirection: 'row', gap: 10, marginBottom: 12},
  libraryTab: {paddingHorizontal: 14, paddingVertical: 8, borderRadius: 20, backgroundColor: '#1b1b1b'},
  libraryTabSelected: {backgroundColor: '#4a201d'},
  libraryTabText: {color: '#f4f4f4', fontSize: 13},
  libraryEmpty: {color: '#999', textAlign: 'center', marginTop: 70, fontSize: 14},
  libraryRow: {flexDirection: 'row', alignItems: 'center', borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#282828'},
  libraryVideo: {flex: 1, flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10},
  favoriteButton: {color: '#ff5147', fontSize: 24, paddingHorizontal: 8},
  recentSection: {marginTop: 18},
  recentHeading: {color: '#f7f7f7', fontSize: 16, fontWeight: '700', marginBottom: 10},
  recentCard: {width: 150, marginRight: 12},
  recentThumbnail: {width: 150, height: 84, borderRadius: 8, backgroundColor: '#191919'},
  recentTitle: {color: '#ddd', fontSize: 12, marginTop: 6},
});

export default App;
