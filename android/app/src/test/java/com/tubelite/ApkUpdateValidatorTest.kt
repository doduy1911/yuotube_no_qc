package com.tubelite

import org.junit.Assert.assertThrows
import org.junit.Test

class ApkUpdateValidatorTest {
  private val release = UpdateReleaseMetadata(
    packageName = "com.tubelite", versionCode = 3, versionName = "2.1.0", sizeBytes = 100,
    sha256 = "a".repeat(64), signingCertSha256 = "b".repeat(64),
  )
  private val apk = DownloadedApkMetadata(
    packageName = "com.tubelite", versionCode = 3, versionName = "2.1.0", sizeBytes = 100,
    sha256 = "a".repeat(64), signingCertSha256 = "b".repeat(64),
  )

  @Test fun acceptsExactCompatibleArtifact() {
    ApkUpdateValidator.validate(release, apk, "b".repeat(64))
  }

  @Test fun rejectsPackageVersionChecksumAndSignatureMismatch() {
    assertThrows(IllegalArgumentException::class.java) {
      ApkUpdateValidator.validate(release, apk.copy(packageName = "com.other"), "b".repeat(64))
    }
    assertThrows(IllegalArgumentException::class.java) {
      ApkUpdateValidator.validate(release, apk.copy(versionCode = 4), "b".repeat(64))
    }
    assertThrows(IllegalArgumentException::class.java) {
      ApkUpdateValidator.validate(release, apk.copy(sizeBytes = 99), "b".repeat(64))
    }
    assertThrows(IllegalArgumentException::class.java) {
      ApkUpdateValidator.validate(release, apk.copy(sha256 = "c".repeat(64)), "b".repeat(64))
    }
    assertThrows(IllegalArgumentException::class.java) {
      ApkUpdateValidator.validate(release, apk.copy(signingCertSha256 = "c".repeat(64)), "b".repeat(64))
    }
    assertThrows(IllegalArgumentException::class.java) {
      ApkUpdateValidator.validate(release, apk, "c".repeat(64))
    }
  }
}
