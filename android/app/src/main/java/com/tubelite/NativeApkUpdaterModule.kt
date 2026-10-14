package com.tubelite

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class NativeApkUpdaterModule(private val context: ReactApplicationContext) : ReactContextBaseJavaModule(context) {
  private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

  override fun getName() = NAME

  @ReactMethod
  fun getDeviceInfo(promise: Promise) {
    try {
      val packageName = context.packageName
      val installed = context.packageManager.getPackageInfo(packageName, signingFlags())
      val versionCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else @Suppress("DEPRECATION") installed.versionCode.toLong()
      val signer = packageSigner(installed)
      val result = JSONObject()
        .put("packageName", packageName)
        .put("currentVersionCode", versionCode)
        .put("sdkInt", Build.VERSION.SDK_INT)
        .put("supportedAbis", org.json.JSONArray(Build.SUPPORTED_ABIS.toList()))
        .put("serverUrl", BuildConfig.UPDATE_SERVER_URL)
        .put("signingCertSha256", signer)
      promise.resolve(result.toString())
    } catch (error: Throwable) {
      promise.reject("UPDATE_DEVICE_INFO", "Không đọc được thông tin ứng dụng.", error)
    }
  }

  @ReactMethod
  fun startDownload(releaseJson: String, promise: Promise) {
    try {
      val release = JSONObject(releaseJson)
      val savedId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
      val savedRelease = preferences.getString(KEY_RELEASE, null)?.let(::JSONObject)
      if (savedId >= 0 && savedRelease?.optString("id") == release.optString("id") && isReusableDownload(savedId)) {
        if (preferences.getString(KEY_INSTALL_RESULT, null)?.startsWith("error:") == true) {
          preferences.edit().remove(KEY_INSTALL_RESULT).apply()
        }
        promise.resolve(stateJson())
        return
      }
      if (savedId >= 0) downloadManager.remove(savedId)
      val url = Uri.parse(release.getString("downloadUrl"))
      require(url.scheme == "https" || (BuildConfig.DEBUG && url.scheme == "http")) { "Link tải không an toàn." }
      require(release.getString("packageName") == context.packageName) { "Package APK không khớp." }
      require(release.getLong("versionCode") > installedVersionCode()) { "Bản cập nhật không mới hơn." }
      val request = DownloadManager.Request(url)
        .setTitle("TubeLite ${release.getString("versionName")}")
        .setDescription("Đang tải bản cập nhật")
        .setMimeType("application/vnd.android.package-archive")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(false)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "tubelite-update-${release.getString("id")}.apk")
      val downloadId = downloadManager.enqueue(request)
      preferences.edit().putLong(KEY_DOWNLOAD_ID, downloadId).putString(KEY_RELEASE, release.toString())
        .remove(KEY_INSTALL_RESULT).apply()
      promise.resolve(stateJson())
    } catch (error: Throwable) {
      promise.reject("UPDATE_DOWNLOAD", error.message ?: "Không thể bắt đầu tải APK.", error)
    }
  }

  @ReactMethod
  fun getUpdateState(promise: Promise) {
    try { promise.resolve(stateJson()) }
    catch (error: Throwable) { promise.reject("UPDATE_STATE", "Không đọc được trạng thái tải.", error) }
  }

  @ReactMethod
  fun cancelDownload(promise: Promise) {
    val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
    if (id >= 0) downloadManager.remove(id)
    preferences.edit().remove(KEY_DOWNLOAD_ID).remove(KEY_RELEASE).remove(KEY_INSTALL_RESULT).apply()
    promise.resolve(stateJson())
  }

  @ReactMethod
  fun inspectDownloadedApk(promise: Promise) {
    try {
      val (file, release) = verifiedArtifact()
      val packageInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags())
        ?: error("File tải về không phải APK hợp lệ.")
      val version = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
      val metadata = DownloadedApkMetadata(packageInfo.packageName, version, packageInfo.versionName.orEmpty(), file.length(), sha256(file), packageSigner(packageInfo))
      promise.resolve(JSONObject()
        .put("packageName", metadata.packageName)
        .put("versionCode", metadata.versionCode)
        .put("versionName", metadata.versionName)
        .put("sizeBytes", metadata.sizeBytes)
        .put("sha256", metadata.sha256)
        .put("signingCertSha256", metadata.signingCertSha256)
        .toString())
    } catch (error: Throwable) {
      promise.reject("UPDATE_VERIFY", error.message ?: "Không xác minh được APK.", error)
    }
  }

  @ReactMethod
  fun installDownloadedApk(promise: Promise) {
    try {
      if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
        val activity = context.currentActivity ?: throw IllegalStateException("Mở lại TubeLite để cấp quyền cài đặt.")
        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        promise.resolve("permission_required")
        return
      }
      val (file, release) = verifiedArtifact()
      val installer = context.packageManager.packageInstaller
      val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL)
      params.setAppPackageName(release.packageName)
      val sessionId = installer.createSession(params)
      installer.openSession(sessionId).use { session ->
        FileInputStream(file).use { input -> session.openWrite("base.apk", 0, file.length()).use { output ->
          input.copyTo(output)
          session.fsync(output)
        } }
        preferences.edit().putLong(KEY_TARGET_VERSION, release.versionCode).putString(KEY_INSTALL_RESULT, "installing").apply()
        val statusIntent = Intent(context, UpdateInstallStatusReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
        val pending = PendingIntent.getBroadcast(context, sessionId, statusIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        session.commit(pending.intentSender)
      }
      promise.resolve("installing")
    } catch (error: Throwable) {
      promise.reject("UPDATE_INSTALL", error.message ?: "Không thể mở trình cài đặt.", error)
    }
  }

  private fun stateJson(): String {
    val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
    val release = preferences.getString(KEY_RELEASE, null)?.let(::JSONObject)
    val targetVersion = preferences.getLong(KEY_TARGET_VERSION, -1L)
    val currentVersion = installedVersionCode()
    val storedResult = preferences.getString(KEY_INSTALL_RESULT, null)
    if (targetVersion >= 0 && currentVersion >= targetVersion && storedResult != null) {
      preferences.edit().putString(KEY_INSTALL_RESULT, "installed").apply()
    } else if (storedResult == "installed") {
      preferences.edit().putString(KEY_INSTALL_RESULT, "error:Chưa xác nhận được phiên bản mới sau khi cài đặt.").apply()
    }
    val installResult = preferences.getString(KEY_INSTALL_RESULT, null)
    if (installResult != null && installResult != "installing" && installResult != "waiting_for_user") {
      return JSONObject().put("state", if (installResult == "installed") "installed" else "error")
        .put("message", installResult.removePrefix("error:")).put("release", release).toString()
    }
    if (id < 0) return JSONObject().put("state", "idle").toString()
    val query = DownloadManager.Query().setFilterById(id)
    downloadManager.query(query).use { cursor ->
      if (!cursor.moveToFirst()) return JSONObject().put("state", "error").put("message", "Không tìm thấy lượt tải.").toString()
      val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
      val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
      val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
      val state = when (status) {
        DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING -> "downloading"
        DownloadManager.STATUS_PAUSED -> "paused"
        DownloadManager.STATUS_SUCCESSFUL -> "readyToInstall"
        else -> "error"
      }
      val result = JSONObject().put("state", state).put("downloadedBytes", downloaded)
        .put("totalBytes", total).put("release", release)
      if (status == DownloadManager.STATUS_FAILED) result.put("message", "Tải APK thất bại (mã ${cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))}).")
      if (installResult == "waiting_for_user") result.put("state", "waitingForUser")
      return result.toString()
    }
  }

  private fun isReusableDownload(id: Long): Boolean {
    downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
      if (!cursor.moveToFirst()) return false
      val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
      return status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING
        || status == DownloadManager.STATUS_PAUSED || status == DownloadManager.STATUS_SUCCESSFUL
    }
  }

  private fun verifiedArtifact(): Pair<File, UpdateReleaseMetadata> {
    val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
    val releaseJson = preferences.getString(KEY_RELEASE, null) ?: error("Không có bản cập nhật đang tải.")
    val releaseJsonObject = JSONObject(releaseJson)
    val release = UpdateReleaseMetadata(
      releaseJsonObject.getString("packageName"), releaseJsonObject.getLong("versionCode"),
      releaseJsonObject.getString("versionName"), releaseJsonObject.getLong("sizeBytes"),
      releaseJsonObject.getString("sha256"), releaseJsonObject.getString("signingCertSha256"),
    )
    val file = downloadedFile(id)
    require(file.isFile && file.length() == release.sizeBytes) { "Kích thước APK không khớp hoặc file chưa hoàn tất." }
    val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags())
      ?: error("File tải về không phải APK hợp lệ.")
    archive.applicationInfo?.sourceDir = file.absolutePath
    archive.applicationInfo?.publicSourceDir = file.absolutePath
    val version = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else @Suppress("DEPRECATION") archive.versionCode.toLong()
    val metadata = DownloadedApkMetadata(archive.packageName, version, archive.versionName.orEmpty(), file.length(), sha256(file), packageSigner(archive))
    ApkUpdateValidator.validate(release, metadata, installedSigner())
    require(version > installedVersionCode()) { "Bản APK không mới hơn phiên bản đã cài." }
    return file to release
  }

  private fun downloadedFile(id: Long): File {
    require(id >= 0) { "Không có lượt tải APK." }
    downloadManager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
      require(cursor.moveToFirst()) { "Không tìm thấy lượt tải APK." }
      require(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
        "Tải APK chưa hoàn tất."
      }
      val uri = Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)))
      return when (uri.scheme) {
        "file" -> File(uri.path ?: error("Đường dẫn APK không hợp lệ."))
      else -> error("DownloadManager trả về URI APK không hỗ trợ.")
      }
    }
  }

  private fun installedVersionCode(): Long {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
  }

  private fun installedSigner(): String = packageSigner(context.packageManager.getPackageInfo(context.packageName, signingFlags()))

  @Suppress("DEPRECATION")
  private fun signingFlags(): Int = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

  @Suppress("DEPRECATION")
  private fun packageSigner(info: PackageInfo): String {
    val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
    val signature = signatures?.firstOrNull() ?: error("Không đọc được chứng thư ký APK.")
    return MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
  }

  private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  companion object {
    const val NAME = "NativeApkUpdater"
    const val PREFS = "tubelite_apk_update"
    const val KEY_DOWNLOAD_ID = "download_id"
    const val KEY_RELEASE = "release_json"
    const val KEY_TARGET_VERSION = "install_target_version"
    const val KEY_INSTALL_RESULT = "install_result"
    const val ACTION_INSTALL_STATUS = "com.tubelite.APK_INSTALL_STATUS"
  }
}
