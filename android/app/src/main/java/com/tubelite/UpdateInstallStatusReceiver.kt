package com.tubelite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

class UpdateInstallStatusReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val preferences = context.getSharedPreferences(NativeApkUpdaterModule.PREFS, Context.MODE_PRIVATE)
    when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        preferences.edit().putString(NativeApkUpdaterModule.KEY_INSTALL_RESULT, "waiting_for_user").apply()
        val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
      }
      PackageInstaller.STATUS_SUCCESS -> preferences.edit()
        .putString(NativeApkUpdaterModule.KEY_INSTALL_RESULT, "installing").apply()
      else -> {
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Cài đặt bị hủy hoặc thất bại."
        preferences.edit().putString(NativeApkUpdaterModule.KEY_INSTALL_RESULT, "error:$message").apply()
      }
    }
  }
}
