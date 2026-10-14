package com.tubelite

data class UpdateReleaseMetadata(
  val packageName: String,
  val versionCode: Long,
  val versionName: String,
  val sizeBytes: Long,
  val sha256: String,
  val signingCertSha256: String,
)

data class DownloadedApkMetadata(
  val packageName: String,
  val versionCode: Long,
  val versionName: String,
  val sizeBytes: Long,
  val sha256: String,
  val signingCertSha256: String,
)

object ApkUpdateValidator {
  fun validate(release: UpdateReleaseMetadata, apk: DownloadedApkMetadata, installedSigningCertSha256: String) {
    require(apk.packageName == release.packageName) { "Package trong APK không khớp." }
    require(apk.versionCode == release.versionCode && apk.versionName == release.versionName) { "Version trong APK không khớp." }
    require(apk.sizeBytes == release.sizeBytes && apk.sizeBytes > 0) { "Kích thước APK không khớp." }
    require(normalizeDigest(apk.sha256) == normalizeDigest(release.sha256)) { "Checksum SHA-256 của APK không khớp." }
    val expectedCertificate = normalizeDigest(release.signingCertSha256)
    require(normalizeDigest(apk.signingCertSha256) == expectedCertificate
      && normalizeDigest(installedSigningCertSha256) == expectedCertificate) {
      "Chữ ký APK không tương thích với ứng dụng đã cài."
    }
  }

  fun normalizeDigest(value: String): String {
    require(value.matches(Regex("^[a-fA-F0-9]{64}$"))) { "SHA-256 không hợp lệ." }
    return value.lowercase()
  }
}
