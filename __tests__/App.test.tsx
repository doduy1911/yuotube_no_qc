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
  },
}));
jest.mock('../src/native/YouTubeSearch', () => ({
  searchYouTube: (...args: unknown[]) => mockSearchYouTube(...args),
}));

test('renders correctly', async () => {
  let app: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(() => {
    app = ReactTestRenderer.create(<App />);
  });

  expect(app!.root.findByProps({ testID: 'home-title' }).props.children).toBe(
    'TubeLite',
  );
  expect(
    app!.root.findByProps({ testID: 'search-input' }).props.placeholder,
  ).toBe('Tìm video hoặc dán link YouTube');
  expect(app!.root.findByProps({ testID: 'home-empty-state' })).toBeTruthy();
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
  await ReactTestRenderer.act(() => {
    app = ReactTestRenderer.create(<App />);
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
