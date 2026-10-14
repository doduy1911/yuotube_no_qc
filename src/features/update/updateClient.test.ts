import {createUpdateClient} from './updateClient';

const device = {
  packageName: 'com.tubelite', currentVersionCode: 2, sdkInt: 36,
  supportedAbis: ['arm64-v8a'], serverUrl: 'https://updates.example.com',
};
const responseBody = {
  updateAvailable: true,
  release: {
    id: 'rel-3', packageName: 'com.tubelite', versionCode: 3, versionName: '2.1.0',
    releaseNotes: null, downloadUrl: 'https://updates.example.com/a.apk', sizeBytes: 10,
    sha256: 'a'.repeat(64), signingCertSha256: 'b'.repeat(64),
  },
};

it('sends the installed package, version, SDK and ABI to update check', async () => {
  const native = {getDeviceInfo: jest.fn().mockResolvedValue(JSON.stringify(device))};
  const fetcher = jest.fn().mockResolvedValue({ok: true, status: 200, json: async () => responseBody});
  const result = await createUpdateClient(native, fetcher).check();
  expect(fetcher).toHaveBeenCalledWith('https://updates.example.com/api/v1/updates/check', expect.objectContaining({
    method: 'POST',
    body: JSON.stringify({packageName: 'com.tubelite', currentVersionCode: 2, sdkInt: 36, supportedAbis: ['arm64-v8a']}),
  }));
  expect(result.updateAvailable).toBe(true);
});

it('shares an in-flight check instead of sending duplicate requests', async () => {
  let finish!: (value: {ok: boolean; status: number; json: () => Promise<unknown>}) => void;
  const native = {getDeviceInfo: jest.fn().mockResolvedValue(JSON.stringify(device))};
  const fetcher = jest.fn((_url: string, _init: RequestInit) => new Promise<{ok: boolean; status: number; json: () => Promise<unknown> }>(resolve => { finish = resolve; }));
  const client = createUpdateClient(native, fetcher);
  const first = client.check();
  const second = client.check();
  expect(first).toBe(second);
  await Promise.resolve();
  await Promise.resolve();
  expect(fetcher).toHaveBeenCalledTimes(1);
  finish({ok: true, status: 200, json: async () => responseBody});
  await expect(first).resolves.toMatchObject({updateAvailable: true});
});

it('reports an unregistered app without starting a download', async () => {
  const native = {getDeviceInfo: jest.fn().mockResolvedValue(JSON.stringify(device))};
  const fetcher = jest.fn().mockResolvedValue({ok: false, status: 404, json: async () => ({})});
  await expect(createUpdateClient(native, fetcher).check()).rejects.toThrow('chưa được đăng ký');
});
