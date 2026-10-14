package com.tubelite

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class TubeLitePackage : BaseReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    return when (name) {
      NativeVideoExtractorModule.NAME -> NativeVideoExtractorModule(reactContext)
      NativeApkUpdaterModule.NAME -> NativeApkUpdaterModule(reactContext)
      else -> null
    }
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
    return ReactModuleInfoProvider {
      mapOf(
      NativeVideoExtractorModule.NAME to ReactModuleInfo(
        NativeVideoExtractorModule.NAME,
        NativeVideoExtractorModule::class.java.name,
        false,
        false,
        false,
        false,
      ),
      NativeApkUpdaterModule.NAME to ReactModuleInfo(
        NativeApkUpdaterModule.NAME,
        NativeApkUpdaterModule::class.java.name,
        false,
        false,
        false,
        false,
      ),
      )
    }
  }
}
