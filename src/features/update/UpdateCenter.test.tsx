import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import UpdateCenter from './UpdateCenter';

jest.mock('../../native/specs/NativeApkUpdater', () => ({__esModule: true, default: {
  getDeviceInfo: jest.fn(), getUpdateState: jest.fn(), startDownload: jest.fn(),
  cancelDownload: jest.fn(), inspectDownloadedApk: jest.fn(), installDownloadedApk: jest.fn(),
}}));
const mockNative = require('../../native/specs/NativeApkUpdater').default as {
  getDeviceInfo: jest.Mock; getUpdateState: jest.Mock; startDownload: jest.Mock;
  cancelDownload: jest.Mock; inspectDownloadedApk: jest.Mock; installDownloadedApk: jest.Mock;
};

const release = {
  id: 'rel-3', packageName: 'com.tubelite', versionCode: 3, versionName: '2.1.0',
  releaseNotes: 'Cải thiện ổn định', downloadUrl: 'http://10.0.2.2:3000/downloads/rel-3/app.apk',
  sizeBytes: 10, sha256: 'a'.repeat(64), signingCertSha256: 'b'.repeat(64),
};

beforeEach(() => {
  jest.clearAllMocks();
  mockNative.getDeviceInfo.mockResolvedValue(JSON.stringify({
    packageName: 'com.tubelite', currentVersionCode: 2, sdkInt: 36,
    supportedAbis: ['arm64-v8a'], serverUrl: 'http://10.0.2.2:3000',
  }));
  mockNative.getUpdateState.mockResolvedValue(JSON.stringify({state: 'idle'}));
  mockNative.startDownload.mockResolvedValue(JSON.stringify({state: 'downloading', release}));
  globalThis.fetch = jest.fn().mockResolvedValue({ok: true, status: 200, json: async () => ({updateAvailable: true, release})}) as typeof fetch;
});

it('checks for an update and starts its persisted native download', async () => {
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => { renderer = ReactTestRenderer.create(<UpdateCenter />); });
  await ReactTestRenderer.act(async () => {
    const button = renderer.root.findByProps({testID: 'check-update'});
    await button.props.onPress();
  });
  expect(mockNative.getDeviceInfo).toHaveBeenCalledTimes(1);
  expect(globalThis.fetch).toHaveBeenCalledTimes(1);
  expect(mockNative.startDownload).toHaveBeenCalledWith(JSON.stringify(release));
  expect(renderer.root.findByProps({testID: 'update-status'}).props.children).toContain('Đang tải TubeLite 2.1.0');
  await ReactTestRenderer.act(async () => renderer.unmount());
});

it('guards against rapid duplicate update checks', async () => {
  let finish!: (value: {ok: boolean; status: number; json: () => Promise<unknown>}) => void;
  (globalThis.fetch as jest.Mock).mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => { renderer = ReactTestRenderer.create(<UpdateCenter />); });
  await ReactTestRenderer.act(async () => {
    const button = renderer.root.findByProps({testID: 'check-update'});
    button.props.onPress(); button.props.onPress();
    for (let i = 0; i < 10; i++) await Promise.resolve();
  });
  expect(globalThis.fetch).toHaveBeenCalledTimes(1);
  await ReactTestRenderer.act(async () => {
    finish!({ok: true, status: 200, json: async () => ({updateAvailable: false, release: null, reason: 'UP_TO_DATE'})});
    await Promise.resolve(); await Promise.resolve(); await Promise.resolve();
  });
  await ReactTestRenderer.act(async () => renderer.unmount());
});

it('shows the current version result after a completed installation', async () => {
  mockNative.getUpdateState.mockResolvedValue(JSON.stringify({state: 'installed'}));
  (globalThis.fetch as jest.Mock).mockResolvedValue({
    ok: true, status: 200,
    json: async () => ({updateAvailable: false, release: null, reason: 'UP_TO_DATE'}),
  });
  let renderer!: ReactTestRenderer.ReactTestRenderer;
  await ReactTestRenderer.act(async () => { renderer = ReactTestRenderer.create(<UpdateCenter />); });
  await ReactTestRenderer.act(async () => {
    await renderer.root.findByProps({testID: 'check-update'}).props.onPress();
  });
  expect(renderer.root.findByProps({testID: 'update-status'}).props.children).toBe('TubeLite đang ở phiên bản mới nhất.');
  await ReactTestRenderer.act(async () => renderer.unmount());
});
