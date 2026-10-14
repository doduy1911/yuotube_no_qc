import {
  mapUpdateCheckResponse,
  validateDownloadedUpdate,
  type UpdateRelease,
} from './apkUpdate';

const release: UpdateRelease = {
  id: 'release-123',
  packageName: 'com.tubelite',
  versionCode: 3,
  versionName: '2.1.0',
  releaseNotes: 'Sửa lỗi',
  downloadUrl: 'https://updates.example.com/downloads/release-123/app.apk',
  sizeBytes: 100,
  sha256: 'a'.repeat(64),
  signingCertSha256: 'b'.repeat(64),
};

describe('mapUpdateCheckResponse', () => {
  it('maps a newer TubeLite release from the public API', () => {
    expect(mapUpdateCheckResponse({updateAvailable: true, release}, 'com.tubelite', 2)).toEqual({
      updateAvailable: true,
      release,
    });
  });

  it('returns current when no release is available', () => {
    expect(mapUpdateCheckResponse({updateAvailable: false, release: null, reason: 'UP_TO_DATE'}, 'com.tubelite', 2))
      .toEqual({updateAvailable: false, reason: 'UP_TO_DATE'});
  });

  it('rejects wrong package, invalid digest and non-newer release', () => {
    expect(() => mapUpdateCheckResponse({updateAvailable: true, release: {...release, packageName: 'com.other'}}, 'com.tubelite', 2)).toThrow(/package/i);
    expect(() => mapUpdateCheckResponse({updateAvailable: true, release: {...release, sha256: 'bad'}}, 'com.tubelite', 2)).toThrow('SHA-256');
    expect(() => mapUpdateCheckResponse({updateAvailable: true, release: {...release, versionCode: 2}}, 'com.tubelite', 2)).toThrow(/mới hơn/i);
  });
});

describe('validateDownloadedUpdate', () => {
  const artifact = {
    packageName: 'com.tubelite', versionCode: 3, versionName: '2.1.0', sizeBytes: 100,
    sha256: 'a'.repeat(64), signingCertSha256: 'b'.repeat(64),
  };

  it('accepts an artifact matching the app, release, checksum and installed signer', () => {
    expect(() => validateDownloadedUpdate(release, artifact, 'b'.repeat(64))).not.toThrow();
  });

  it('rejects package, version, hash, size and certificate mismatches', () => {
    expect(() => validateDownloadedUpdate(release, {...artifact, packageName: 'com.other'}, 'b'.repeat(64))).toThrow(/package/i);
    expect(() => validateDownloadedUpdate(release, {...artifact, versionCode: 4}, 'b'.repeat(64))).toThrow(/version/i);
    expect(() => validateDownloadedUpdate(release, {...artifact, sha256: 'c'.repeat(64)}, 'b'.repeat(64))).toThrow('SHA-256');
    expect(() => validateDownloadedUpdate(release, {...artifact, sizeBytes: 99}, 'b'.repeat(64))).toThrow(/kích thước/i);
    expect(() => validateDownloadedUpdate(release, {...artifact, signingCertSha256: 'c'.repeat(64)}, 'b'.repeat(64))).toThrow(/chữ ký/i);
    expect(() => validateDownloadedUpdate(release, artifact, 'c'.repeat(64))).toThrow(/chữ ký/i);
  });
});
