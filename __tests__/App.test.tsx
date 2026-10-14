/**
 * @format
 */

import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import App from '../App';
import type {SearchPage} from '../src/domain/youtubeSearch';

const mockSearchYouTube = jest.fn();

jest.mock('react-native-safe-area-context', () =>
  require('react-native-safe-area-context/jest/mock').default,
);
jest.mock('../src/native/specs/NativeVideoExtractor', () => ({
  __esModule: true,
  default: {
    getVideoDetails: jest.fn(),
    openVideo: jest.fn().mockResolvedValue(true),
    getHistory: jest.fn().mockResolvedValue('[]'),
    getFavorites: jest.fn().mockResolvedValue('[]'),
    setFavorite: jest.fn().mockResolvedValue(true),
    clearHistory: jest.fn().mockResolvedValue(true),
    getTrendingMusic: jest.fn().mockImplementation((section: string) => Promise.resolve(JSON.stringify({section, items: [], regions: [section === 'vn' ? 'VN' : 'US', ...(section === 'international' ? ['GB'] : [])], updatedAt: 1, stale: false}))),
  },
}));
jest.mock('../src/native/specs/NativeApkUpdater', () => ({
  __esModule: true,
  default: {
    getDeviceInfo: jest.fn().mockResolvedValue('{}'),
    getUpdateState: jest.fn().mockResolvedValue(JSON.stringify({state: 'idle'})),
    startDownload: jest.fn(), cancelDownload: jest.fn(), inspectDownloadedApk: jest.fn(), installDownloadedApk: jest.fn(),
  },
}));
jest.mock('../src/native/YouTubeSearch', () => ({
  searchYouTube: (...args: unknown[]) => mockSearchYouTube(...args),
}));
jest.mock('../src/native/MusicDiscovery', () => ({
  getTrendingMusic: jest.fn().mockResolvedValue({section: 'vn', items: [], regions: ['VN'], updatedAt: 1, stale: false}),
}));
jest.mock('../src/native/SearchSuggestions', () => ({
  getSearchSuggestions: jest.fn().mockResolvedValue([]),
}));

test('renders correctly', async () => {
  let app: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    app = ReactTestRenderer.create(<App />);
    await Promise.resolve();
    await Promise.resolve();
  });

  expect(app!.root.findByProps({ testID: 'home-title' }).props.children).toBe(
    'TubeLite',
  );
  expect(
    app!.root.findByProps({ testID: 'search-input' }).props.placeholder,
  ).toBe('Tìm video hoặc dán link YouTube');
  expect(app!.root.findByProps({testID: 'trending-home'})).toBeTruthy();
  expect(app!.root.findByProps({children: 'Thịnh hành Việt Nam'})).toBeTruthy();
});

test('shows trending sections and recent history on the scrollable home tab', async () => {
  const native = require('../src/native/specs/NativeVideoExtractor').default;
  native.getHistory.mockResolvedValue(JSON.stringify([{
    videoId: 'dQw4w9WgXcQ', title: 'Recently watched', channelTitle: 'Channel',
    thumbnailUrl: null, durationMs: 120000, positionMs: 0, lastWatchedAt: 10, isFavorite: false,
  }]));
  let app: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    app = ReactTestRenderer.create(<App />);
    await Promise.resolve();
    await Promise.resolve();
  });
  expect(app!.root.findByProps({children: 'Thịnh hành Việt Nam'})).toBeTruthy();
  expect(app!.root.findByProps({children: 'Xem gần đây'})).toBeTruthy();
  expect(app!.root.findByProps({children: 'Recently watched'})).toBeTruthy();
  ['trending-vn-cards', 'trending-international-cards', 'recent-cards'].forEach(testID => {
    expect(app!.root.findByProps({testID})).toBeTruthy();
  });
});

test('a late response from an older query cannot replace newer results', async () => {
  mockSearchYouTube.mockReset();
  let resolveFirst!: (page: SearchPage) => void;
  let resolveSecond!: (page: SearchPage) => void;
  mockSearchYouTube.mockImplementation((query: string) =>
    new Promise<SearchPage>(resolve => {
      if (query === 'first query') resolveFirst = resolve;
      else resolveSecond = resolve;
    }),
  );

  let app: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    app = ReactTestRenderer.create(<App />);
    await Promise.resolve();
    await Promise.resolve();
  });
  const input = app!.root.findByProps({testID: 'search-input'});

  await ReactTestRenderer.act(() => {
    input.props.onChangeText('first query');
  });
  await ReactTestRenderer.act(async () => {
    app!.root.findByProps({testID: 'search-input'}).props.onSubmitEditing();
  });
  await ReactTestRenderer.act(() => {
    app!.root.findByProps({testID: 'search-input'}).props.onChangeText('second query');
  });
  await ReactTestRenderer.act(async () => {
    app!.root.findByProps({testID: 'search-input'}).props.onSubmitEditing();
  });

  const result = (videoId: string, title: string) => ({
    videoId,
    title,
    channelTitle: 'Test channel',
    thumbnailUrl: null,
    publishedAt: null,
    durationMs: 1000,
  });

  await ReactTestRenderer.act(async () => {
    resolveSecond({items: [result('dQw4w9WgXcQ', 'Second query result')], nextPageToken: null});
    await Promise.resolve();
    await Promise.resolve();
  });
  await ReactTestRenderer.act(async () => {
    resolveFirst({items: [result('abcdefghijk', 'First query result')], nextPageToken: null});
    await Promise.resolve();
    await Promise.resolve();
  });

  expect(app!.root.findByProps({children: 'Second query result'})).toBeTruthy();
  expect(app!.root.findAllByProps({children: 'First query result'})).toHaveLength(0);
});

test('does not show no-results state before a search is submitted', async () => {
  mockSearchYouTube.mockReset().mockResolvedValue({items: [], nextPageToken: null});
  let app: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    app = ReactTestRenderer.create(<App />);
    await Promise.resolve();
    await Promise.resolve();
  });
  await ReactTestRenderer.act(async () => {
    app!.root.findByProps({testID: 'search-input'}).props.onChangeText('em cua');
    await Promise.resolve();
  });
  const emptyStateBeforeSubmit = app!.root.findAllByProps({testID: 'home-empty-state'}).length;
  expect(mockSearchYouTube).not.toHaveBeenCalled();
  await ReactTestRenderer.act(async () => {
    await app!.root.findByProps({testID: 'search-input'}).props.onSubmitEditing();
    await Promise.resolve();
    await Promise.resolve();
  });
  const emptyStateAfterSubmit = app!.root.findAllByProps({testID: 'home-empty-state'}).length;
  await ReactTestRenderer.act(async () => { app!.unmount(); });
  expect(emptyStateBeforeSubmit).toBe(0);
  expect(emptyStateAfterSubmit).toBeGreaterThan(0);
  expect(mockSearchYouTube).toHaveBeenCalledTimes(1);
  expect(mockSearchYouTube).toHaveBeenCalledWith('em cua');
});

test('opens the library and renders persisted history and favorites', async () => {
  const stored = JSON.stringify([{
    videoId: 'dQw4w9WgXcQ', title: 'Saved video', channelTitle: 'Channel',
    thumbnailUrl: null, durationMs: 120000, positionMs: 30000,
    lastWatchedAt: 10, isFavorite: true,
  }]);
  const native = require('../src/native/specs/NativeVideoExtractor').default;
  native.getHistory.mockResolvedValue(stored);
  native.getFavorites.mockResolvedValue(stored);
  let app: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => {
    app = ReactTestRenderer.create(<App />);
    await Promise.resolve();
  });
  await ReactTestRenderer.act(async () => {
    app!.root.findByProps({accessibilityLabel: 'Open library'}).props.onPress();
    await Promise.resolve();
  });
  expect(app!.root.findByProps({children: 'Saved video'})).toBeTruthy();
  expect(app!.root.findByProps({children: 'Tiếp tục xem video'})).toBeTruthy();
  expect(app!.root.findByProps({accessibilityLabel: 'Bỏ yêu thích'})).toBeTruthy();
});
