import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import {DeviceEventEmitter} from 'react-native';
import App from './App';

jest.mock('./src/native/specs/NativeVideoExtractor', () => ({__esModule: true, default: {
  getHistory: jest.fn().mockResolvedValue('[]'), getFavorites: jest.fn().mockResolvedValue('[]'),
  openVideo: jest.fn().mockResolvedValue(true), clearHistory: jest.fn().mockResolvedValue(true),
  setFavorite: jest.fn().mockResolvedValue(true),
}}));
jest.mock('./src/native/YouTubeSearch', () => ({searchYouTube: jest.fn()}));
jest.mock('react-native-safe-area-context', () => {
  const ReactModule = require('react');
  const Native = require('react-native');
  return {
    SafeAreaProvider: ({children}: {children: React.ReactNode}) => ReactModule.createElement(Native.View, null, children),
    SafeAreaView: ({children, style}: {children: React.ReactNode; style?: unknown}) => ReactModule.createElement(Native.View, {style}, children),
  };
});
jest.mock('./src/native/MusicDiscovery', () => ({getTrendingMusic: jest.fn().mockResolvedValue({items: [], regions: [], stale: false, updatedAt: 0})}));
jest.mock('./src/features/update/UpdateCenter', () => () => null);
jest.mock('./src/features/search/SearchBox', () => () => null);
jest.mock('./src/features/home/HomeScreen', () => {
  const ReactModule = require('react');
  const Native = require('react-native');
  return () => ReactModule.createElement(Native.Text, {testID: 'home-content'}, 'HOME CONTENT');
});

const mockNative = require('./src/native/specs/NativeVideoExtractor').default as {getHistory: jest.Mock};

describe('mini-player navigation events', () => {
  let renderer!: ReactTestRenderer.ReactTestRenderer;

  beforeEach(() => { jest.clearAllMocks(); });
  afterEach(async () => { if (renderer) await ReactTestRenderer.act(async () => renderer.unmount()); });

  it('returns to Home when playback is collapsed', async () => {
    await ReactTestRenderer.act(async () => { renderer = ReactTestRenderer.create(<App />); });
    await ReactTestRenderer.act(async () => { renderer.root.findByProps({testID: 'nav-library'}).props.onPress(); });
    expect(renderer.root.findAllByProps({testID: 'home-content'})).toHaveLength(0);
    await ReactTestRenderer.act(async () => { DeviceEventEmitter.emit('miniPlayerCollapsed'); });
    expect(renderer.root.findByProps({testID: 'home-content'})).toBeTruthy();
  });

  it('refreshes library data after the mini-player closes', async () => {
    await ReactTestRenderer.act(async () => { renderer = ReactTestRenderer.create(<App />); });
    const callsBeforeClose = mockNative.getHistory.mock.calls.length;
    await ReactTestRenderer.act(async () => { DeviceEventEmitter.emit('miniPlayerClosed'); });
    expect(mockNative.getHistory.mock.calls.length).toBeGreaterThan(callsBeforeClose);
  });
});
