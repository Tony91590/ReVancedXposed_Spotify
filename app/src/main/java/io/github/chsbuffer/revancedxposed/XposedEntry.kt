package io.github.chsbuffer.revancedxposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook
import io.github.chsbuffer.revancedxposed.spotify.misc.ads.InterceptAds

class XposedEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {

        // 🎯 cible uniquement Spotify
        if (lpparam.packageName != "com.spotify.music") return

        val hook = SpotifyHook(lpparam.classLoader)
        hook.InterceptAds()
    }
}
