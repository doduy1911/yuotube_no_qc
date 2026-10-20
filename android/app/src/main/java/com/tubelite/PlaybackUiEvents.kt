package com.tubelite

import com.facebook.react.ReactApplication
import com.facebook.react.bridge.ReactContext
import com.facebook.react.modules.core.DeviceEventManagerModule

internal object PlaybackUiEvents {
  const val MINI_PLAYER_COLLAPSED = "miniPlayerCollapsed"
  const val MINI_PLAYER_CLOSED = "miniPlayerClosed"

  fun emit(activity: MainActivity, eventName: String) {
    val context: ReactContext = (activity.application as? ReactApplication)?.reactHost?.currentReactContext ?: return
    context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(eventName, null)
  }
}
