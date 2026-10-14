export type UpdateRelease = {
  id: string;
  packageName: string;
  versionCode: number;
  versionName: string;
  releaseNotes: string | null;
  downloadUrl: string;
  sizeBytes: number;
  sha256: string;
  signingCertSha256: string;
};

export type UpdateCheckResult =
  | {updateAvailable: true; release: UpdateRelease}
  | {updateAvailable: false; reason: 'UP_TO_DATE' | 'NO_COMPATIBLE_RELEASE'};

export type DownloadedApkMetadata = {
  packageName: string;
  versionCode: number;
  versionName: string;
  sizeBytes: number;
  sha256: string;
  signingCertSha256: string;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isPositiveInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
}

function digest(value: unknown, label: string): string {
  if (typeof value !== 'string' || !/^[a-f\d]{64}$/i.test(value)) {
    throw new Error(`${label} SHA-256 không hợp lệ.`);
  }
  return value.toLowerCase();
}

export function mapUpdateCheckResponse(
  payload: unknown,
  packageName: string,
  currentVersionCode: number,
): UpdateCheckResult {
  if (!isRecord(payload) || typeof payload.updateAvailable !== 'boolean') {
    throw new Error('Phản hồi cập nhật không hợp lệ.');
  }
  if (!payload.updateAvailable) {
    if (payload.release !== null || (payload.reason !== 'UP_TO_DATE' && payload.reason !== 'NO_COMPATIBLE_RELEASE')) {
      throw new Error('Phản hồi cập nhật không hợp lệ.');
    }
    return {updateAvailable: false, reason: payload.reason};
  }

  const value = payload.release;
  if (!isRecord(value) || typeof value.id !== 'string') throw new Error('Thông tin bản cập nhật không hợp lệ.');
  if (value.packageName !== packageName) throw new Error('Package của bản cập nhật không khớp.');
  if (!isPositiveInteger(value.versionCode) || value.versionCode <= currentVersionCode) {
    throw new Error('Bản cập nhật phải có version mới hơn.');
  }
  if (typeof value.versionName !== 'string'
    || !(typeof value.releaseNotes === 'string' || value.releaseNotes === null)
    || typeof value.downloadUrl !== 'string'
    || !isPositiveInteger(value.sizeBytes)) throw new Error('Thông tin bản cập nhật không hợp lệ.');
  let parsedUrl: URL;
  try { parsedUrl = new URL(value.downloadUrl); }
  catch { throw new Error('Link tải APK không hợp lệ.'); }
  if (parsedUrl.protocol !== 'https:' && parsedUrl.protocol !== 'http:') {
    throw new Error('Link tải APK không hợp lệ.');
  }
  return {updateAvailable: true, release: {
    id: value.id,
    packageName,
    versionCode: value.versionCode,
    versionName: value.versionName,
    releaseNotes: value.releaseNotes,
    downloadUrl: parsedUrl.toString(),
    sizeBytes: value.sizeBytes,
    sha256: digest(value.sha256, 'APK'),
    signingCertSha256: digest(value.signingCertSha256, 'Chứng thư ký'),
  }};
}

export function validateDownloadedUpdate(
  release: UpdateRelease,
  apk: DownloadedApkMetadata,
  installedSigningCertSha256: string,
) {
  if (apk.packageName !== release.packageName) throw new Error('Package trong APK không khớp.');
  if (apk.versionCode !== release.versionCode || apk.versionName !== release.versionName) {
    throw new Error('Version trong APK không khớp.');
  }
  if (apk.sizeBytes !== release.sizeBytes) throw new Error('Kích thước APK không khớp.');
  if (digest(apk.sha256, 'APK') !== digest(release.sha256, 'APK')) throw new Error('Checksum SHA-256 của APK không khớp.');
  const expectedCert = digest(release.signingCertSha256, 'Chứng thư ký');
  if (digest(apk.signingCertSha256, 'Chứng thư ký') !== expectedCert
    || digest(installedSigningCertSha256, 'Chứng thư ký hiện tại') !== expectedCert) {
    throw new Error('Chữ ký APK không tương thích với ứng dụng đã cài.');
  }
}
