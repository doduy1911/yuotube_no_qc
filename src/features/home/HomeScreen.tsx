import React, {useCallback, useEffect, useState} from 'react';
import {ActivityIndicator, Image, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View} from 'react-native';
import {getTrendingMusic} from '../../native/MusicDiscovery';
import type {TrendingSection, TrendingSectionId} from '../../domain/musicDiscovery';
import type {VideoSummary} from '../../domain/youtubeSearch';

type SectionState = {data: TrendingSection | null; loading: boolean; error: string};

export default function HomeScreen({onSelectVideo, recentVideos}: {onSelectVideo: (video: VideoSummary) => void; recentVideos: VideoSummary[]}) {
  const [vn, setVn] = useState<SectionState>({data: null, loading: true, error: ''});
  const [international, setInternational] = useState<SectionState>({data: null, loading: true, error: ''});
  const setters = {vn: setVn, international: setInternational};

  const load = useCallback(async (section: TrendingSectionId, forceRefresh = false) => {
    const setState = setters[section];
    setState(previous => ({...previous, loading: true, error: ''}));
    try {
      const data = await getTrendingMusic(section, forceRefresh);
      setState({data, loading: false, error: ''});
    } catch {
      setState(previous => ({...previous, loading: false, error: 'Không tải được nhạc thịnh hành. Hãy thử lại.'}));
    }
  // setters are stable state dispatchers.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { load('vn'); load('international'); }, [load]);
  const refreshing = vn.loading || international.loading;
  const refresh = () => { load('vn', true); load('international', true); };

  return <ScrollView testID="trending-home" style={styles.container} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor="#ff5147" />}>
    <Section testID="trending-vn-cards" title="Thịnh hành Việt Nam" state={vn} onRetry={() => load('vn', true)} onSelectVideo={onSelectVideo} />
    <Section testID="trending-international-cards" title="Nhạc quốc tế · Mỹ & Anh" state={international} onRetry={() => load('international', true)} onSelectVideo={onSelectVideo} />
    {recentVideos.length > 0 ? <View style={styles.recentSection}>
      <Text style={styles.heading}>Xem gần đây</Text>
      <ScrollView testID="recent-cards" horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.cards}>{recentVideos.slice(0, 6).map(video => <Pressable key={video.videoId} accessibilityRole="button" onPress={() => onSelectVideo(video)} style={styles.card}>
        {video.thumbnailUrl ? <Image source={{uri: video.thumbnailUrl}} style={styles.thumbnail} /> : <View style={styles.thumbnail} />}
        <Text numberOfLines={2} style={styles.title}>{video.title}</Text>
      </Pressable>)}</ScrollView>
    </View> : null}
  </ScrollView>;
}

function Section({title, state, onRetry, onSelectVideo, testID}: {title: string; state: SectionState; onRetry: () => void; onSelectVideo: (video: VideoSummary) => void; testID: string}) {
  const {data, loading, error} = state;
  return <View style={styles.section}>
    <Text style={styles.heading}>{title}</Text>
    {data?.stale ? <Text style={styles.caption}>Đang hiển thị dữ liệu đã lưu · {new Date(data.updatedAt).toLocaleString()}</Text> : null}
    {error ? <View style={styles.stateRow}><Text style={styles.error}>{error}</Text><Pressable accessibilityRole="button" onPress={onRetry}><Text style={styles.retry}>Thử lại</Text></Pressable></View> : null}
    {!data && loading ? <View style={styles.stateRow}><ActivityIndicator color="#ff5147" /><Text style={styles.caption}>Đang tải…</Text></View> : null}
    {data && data.items.length === 0 ? <Text style={styles.caption}>Chưa có bài để hiển thị.</Text> : null}
    {data && data.regions.length > 0 ? <ScrollView testID={testID} horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.cards}>{data.items.map(video => <Pressable key={video.videoId} accessibilityRole="button" onPress={() => onSelectVideo(video)} style={styles.card}>
      {video.thumbnailUrl ? <Image source={{uri: video.thumbnailUrl}} style={styles.thumbnail} /> : <View style={styles.thumbnail} />}
      <Text numberOfLines={2} style={styles.title}>{video.title}</Text><Text numberOfLines={1} style={styles.channel}>{video.channelTitle}</Text>
      {video.durationMs ? <Text style={styles.duration}>{formatDuration(video.durationMs)}</Text> : null}
    </Pressable>)}</ScrollView> : null}
  </View>;
}

function formatDuration(durationMs: number) {
  const seconds = Math.floor(durationMs / 1000);
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

const styles = StyleSheet.create({
  container: {flex: 1, marginTop: 8},
  section: {marginBottom: 18}, recentSection: {marginBottom: 24}, heading: {color: '#f7f7f7', fontSize: 16, fontWeight: '700', marginBottom: 9},
  caption: {color: '#999', fontSize: 11, marginBottom: 8}, cards: {flexDirection: 'row', gap: 10},
  card: {width: 145}, thumbnail: {width: 145, height: 82, borderRadius: 8, backgroundColor: '#191919'},
  title: {color: '#eee', fontSize: 12, fontWeight: '600', marginTop: 6, lineHeight: 16},
  channel: {color: '#999', fontSize: 11, marginTop: 3}, duration: {color: '#aaa', fontSize: 10, marginTop: 2},
  stateRow: {flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 6},
  error: {color: '#ff8a82', fontSize: 12, flex: 1}, retry: {color: '#ff6259', fontSize: 12, fontWeight: '700'},
});
