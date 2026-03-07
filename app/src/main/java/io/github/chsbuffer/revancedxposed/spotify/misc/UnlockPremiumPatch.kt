package io.github.chsbuffer.revancedxposed.spotify.misc

import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.util.Log
import app.revanced.extension.shared.Logger
import app.revanced.extension.shared.Utils
import app.revanced.extension.spotify.misc.UnlockPremiumPatch
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.chsbuffer.revancedxposed.callMethod
import io.github.chsbuffer.revancedxposed.callMethodOrNull
import io.github.chsbuffer.revancedxposed.findField
import io.github.chsbuffer.revancedxposed.findFirstFieldByExactType
import io.github.chsbuffer.revancedxposed.getObjectFieldOrNull
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.nio.charset.StandardCharsets
import android.content.Intent

object VLogger {
    private const val TAG = "V-DEEP-CORE"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun log(message: String) {
        Log.d(TAG, message)
        try {
            val context = Utils.getContext() ?: return
            val extDir = context.getExternalFilesDir(null) ?: return
            val logFile = File(extDir, "v_sniffer_log.txt")

            val timestamp = dateFormat.format(Date())
            synchronized(this) {
                logFile.appendText("[$timestamp] $message\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "VLogger IO Failure: ${e.message}")
        }
    }

    fun toast(message: String) {
        try {
            val context = Utils.getContext() ?: return
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            log("[V-LOGGER] Failed to show Toast: ${e.message}")
        }
    }
}

private fun ByteArray.toHexDumpShort(): String {
    return joinToString(" ") { String.format("%02X", it) }
}

object ProtobufAttributeScanner {
    private val targetKeys = listOf(
        "ads", "player-license", "shuffle", "on-demand", "streaming",
        "type", "catalogue", "high-bitrate", "financial-product",
        "audio-quality", "streaming-quality", "jam-social-session"
    )

    fun scan(payload: ByteArray) {
        for (key in targetKeys) {
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val index = indexOf(payload, keyBytes)

            if (index != -1) {
                VLogger.log("[V-PROTO-SCANNER] Found Attribute: '$key' at offset ${String.format("%04X", index)}")

                val contextSize = 20.coerceAtMost(payload.size - (index + keyBytes.size))
                val context = payload.sliceArray((index + keyBytes.size) until (index + keyBytes.size + contextSize))

                VLogger.log("[V-PROTO-SCANNER] Context Hex: ${context.toHexDumpShort()}")
            }
        }
    }

    private fun indexOf(outer: ByteArray, target: ByteArray): Int {
        if (target.isEmpty()) return -1
        for (i in 0..outer.size - target.size) {
            var found = true
            for (j in target.indices) {
                if (outer[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    private fun ByteArray.toHexDumpShort(): String {
        return joinToString(" ") { String.format("%02X", it) }
    }
}

fun SpotifyHook.InstallUIPlayDeflection() {
    VLogger.log("=== UI Play Deflection Activated ===")

    val playCommandClass = XposedHelpers.findClassIfExists("com.spotify.player.model.command.PlayCommand", classLoader)

    playCommandClass?.let { clazz ->
        XposedBridge.hookAllConstructors(clazz, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    for (i in param.args.indices) {
                        val arg = param.args[i] as? String ?: continue

                        if (arg.startsWith("spotify:track:") && !arg.contains("station")) {
                            val spoofedUri = arg.replace("spotify:track:", "spotify:station:track:")
                            param.args[i] = spoofedUri
                            VLogger.log("[UI-DEFLECTION] Rewrote '$arg' to '$spoofedUri'")
                        }
                    }
                } catch (e: Exception) {}
            }
        })
    }
}

fun SpotifyHook.InstallWorkingProductStateHook() {
    VLogger.log("=== Product State Hook Activated ===")

    ::productStateProtoFingerprint.hookMethod {
        after { param ->
            val originalMap = param.result as? Map<String, *> ?: return@after
            param.result = UnlockPremiumPatch.createOverriddenAttributesMap(originalMap)
        }
    }
}

fun SpotifyHook.InstallWorkingAdsRemoval() {
    VLogger.log("=== Ads Removal Activated ===")

    ::homeStructureGetSectionsFingerprint.hookMethod {
        after { param ->
            try {
                val sections = param.result ?: return@after
                sections.javaClass.findFirstFieldByExactType(Boolean::class.java).set(sections, true)
                UnlockPremiumPatch.removeHomeSections(sections as MutableList<*>)
            } catch (e: Exception) {
                VLogger.log("[HOME-CLEANER] Failed to mutate list: ${e.message}")
            }
        }
    }

    ::browseStructureGetSectionsFingerprint.hookMethod {
        after { param ->
            try {
                val sections = param.result ?: return@after
                sections.javaClass.findFirstFieldByExactType(Boolean::class.java).set(sections, true)
                UnlockPremiumPatch.removeBrowseSections(sections as MutableList<*>)
            } catch (e: Exception) {
                VLogger.log("[BROWSE-CLEANER] Failed to mutate list: ${e.message}")
            }
        }
    }
}

fun SpotifyHook.InstallWorkingPendragonFix() {
    VLogger.log("=== Pendragon Fix Activated ===")
    val replaceFetchRequestSingleWithError = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val result = param.result ?: return
            if (!result.javaClass.name.endsWith("SingleOnErrorReturn")) return

            try {
                val funcField = result.javaClass.declaredFields.find { it.name == "b" || it.type.name.contains("Function") }
                funcField?.isAccessible = true
                val fallbackItem = funcField?.get(result)

                val singleClass = XposedHelpers.findClass("io.reactivex.rxjava3.core.Single", classLoader)

                if (fallbackItem != null) {
                    val justMethod = XposedHelpers.findMethodExact(singleClass, "just", Object::class.java)
                    param.result = justMethod.invoke(null, fallbackItem)
                } else {
                    val neverMethod = XposedHelpers.findMethodExact(singleClass, "never", *emptyArray<Class<*>>())
                    param.result = neverMethod.invoke(null)
                    VLogger.log("[V-PENDRAGON] Ad request blocked.")
                }
            } catch (e: Exception) {}
        }
    }

    runCatching { ::pendragonJsonFetchMessageRequestFingerprint.hookMethod(replaceFetchRequestSingleWithError) }
    runCatching { ::pendragonJsonFetchMessageListRequestFingerprint.hookMethod(replaceFetchRequestSingleWithError) }
}

fun SpotifyHook.InstallAdChoke() {
    VLogger.log("=== Ad Choke Activated ===")

    val routerClass = XposedHelpers.findClassIfExists("com.spotify.cosmos.router.Router", classLoader)
    routerClass?.let { clazz ->
        XposedBridge.hookAllMethods(clazz, "resolve", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val request = param.args[0] ?: return
                val uri = request.callMethodOrNull("getUri") as? String ?: return

                if (uri.contains("hm://ad-logic", ignoreCase = true) ||
                    uri.contains("hm://ads", ignoreCase = true) ||
                    uri.contains("hm://slate") ||
                    uri.contains("hm://creative") ||
                    uri.contains("hm://formats") ||
                    uri.contains("hm://in-app-messaging") ||
                    uri.contains("hm://ad-state", ignoreCase = true)) {

                    VLogger.log("[AD-CHOKE] Blocked request to: $uri")
                    param.result = null
                }
            }
        })
    }
}

fun SpotifyHook.InstallAdAutoSkip() {
    VLogger.log("=== Ad Auto-Skip Activated ===")

    val metadataClass = XposedHelpers.findClassIfExists("com.spotify.metadata.Metadata\$Track", classLoader)
    metadataClass?.let { clazz ->
        XposedBridge.hookAllMethods(clazz, "getIsAd", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val isAd = param.result as? Boolean ?: return

                if (isAd) {
                    VLogger.log("[AUTO-SKIP] Ad detected in metadata")
                    param.result = false
                }
            }
        })
    }
}

fun SpotifyHook.InstallSlateModalAssassin() {
    VLogger.log("=== Slate Modal Blocking Activated ===")

    val dialogClass = XposedHelpers.findClassIfExists("android.app.Dialog", classLoader)

    dialogClass?.let { clazz ->
        XposedBridge.hookAllMethods(clazz, "show", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val dialog = param.thisObject
                val dialogClassname = dialog.javaClass.name.lowercase()

                if (dialogClassname.contains("slate") ||
                    dialogClassname.contains("promo") ||
                    dialogClassname.contains("interstitial") ||
                    dialogClassname.contains("ad") ||
                    dialogClassname.contains("marketing") ||
                    dialogClassname.contains("messaging") ||
                    dialogClassname.contains("pendragon")) {

                    VLogger.log("[SLATE-ASSASSIN] Blocked dialog: ${dialog.javaClass.name}")
                    param.result = null

                    try {
                        dialog.callMethod("dismiss")
                    } catch (e: Exception) {}
                }
            }
        })
    }
}

fun SpotifyHook.InstallNuclearAdScrubber() {
    VLogger.log("=== Nuclear Ad Scrubber Activated ===")

    val trackModelClass = XposedHelpers.findClassIfExists("com.spotify.metadata.Metadata\$Track", classLoader)
    trackModelClass?.let { clazz ->
        try {
            XposedBridge.hookAllMethods(clazz, "getIsAd", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val isAd = param.result as? Boolean ?: return
                    if (isAd) {
                        VLogger.log("[NUCLEAR-SCRUBBER] Audio ad detected")

                        param.result = false

                        try {
                            val durationField = param.thisObject.javaClass.getDeclaredField("duration_")
                            durationField.isAccessible = true
                            durationField.set(param.thisObject, 0)

                            val fileField = param.thisObject.javaClass.getDeclaredField("file_")
                            fileField.isAccessible = true
                            fileField.set(param.thisObject, java.util.Collections.emptyList<Any>())
                        } catch (e: Exception) {}
                    }
                }
            })
        } catch (e: Exception) {
            VLogger.log("[NUCLEAR-SCRUBBER] Failed to hook getIsAd: ${e.message}")
        }
    }

    val playerStateClass = XposedHelpers.findClassIfExists("com.spotify.player.model.AutoValue_PlayerState", classLoader)
        ?: XposedHelpers.findClassIfExists("com.spotify.player.model.PlayerState", classLoader)

    if (playerStateClass != null) {
        var hookPlaced = false

        playerStateClass.declaredMethods.forEach { method ->
            if (method.name == "track" && !java.lang.reflect.Modifier.isAbstract(method.modifiers)) {
                try {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val optionalTrack = param.result ?: return

                            try {
                                val isPresentMethod = optionalTrack.javaClass.getMethod("isPresent")
                                val isPresent = isPresentMethod.invoke(optionalTrack) as Boolean

                                if (isPresent) {
                                    val getMethod = optionalTrack.javaClass.getMethod("get")
                                    val contextTrack = getMethod.invoke(optionalTrack)

                                    val metadataMethod = contextTrack.javaClass.getMethod("metadata")
                                    val metadataMap = metadataMethod.invoke(contextTrack) as? Map<String, String>

                                    if (metadataMap != null && (metadataMap.containsKey("is_ad") || metadataMap.containsKey("ad_id"))) {
                                        VLogger.log("[AUTO-SKIP] Ad loaded in player state")

                                        val uriMethod = contextTrack.javaClass.getMethod("uri")
                                        val uriStr = uriMethod.invoke(contextTrack) as String
                                        VLogger.log("Target URI: $uriStr")
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    })
                    hookPlaced = true
                } catch (e: Exception) {}
            }
        }

        if (hookPlaced) {
            VLogger.log("[AUTO-SKIP] Hook placed successfully")
        }
    }

    val promoCardClass = XposedHelpers.findClassIfExists("com.spotify.interstitial.display.InterstitialActivity", classLoader)
    promoCardClass?.let { clazz ->
        try {
            XposedBridge.hookAllMethods(clazz, "onCreate", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    VLogger.log("[VISUAL-ASSASSIN] Blocked interstitial activity")
                    val activity = param.thisObject as android.app.Activity
                    activity.finish()
                    param.result = null
                }
            })
        } catch (e: Exception) {
            VLogger.log("[VISUAL-ASSASSIN] Failed: ${e.message}")
        }
    }
}

fun SpotifyHook.InstallPlayabilityForcer() {
    VLogger.log("=== Playability Forcer Activated ===")

    val trackClass = XposedHelpers.findClassIfExists("com.spotify.metadata.Metadata\$Track", classLoader)

    trackClass?.let { clazz ->
        XposedBridge.hookAllMethods(clazz, "getIsPlayable", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                param.result = true
            }
        })

        XposedBridge.hookAllMethods(clazz, "getIsRestricted", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                param.result = false
            }
        })
    }

    val trackRepClasses = listOf(
        "com.spotify.nowplaying.models.TrackRepresentation\$Normal",
        "com.spotify.nowplaying.engine.models.NowPlayingState\$Track",
        "com.spotify.music.features.track.playability.Playability"
    )

    trackRepClasses.forEach { className ->
        val clazz = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach

        clazz.declaredMethods.filter { it.returnType == Boolean::class.java }.forEach { method ->
            val mName = method.name.lowercase()

            if (mName.contains("playable")) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) { param.result = true }
                })
            } else if (mName.contains("restricted") || mName.contains("premiumonly") || mName.contains("locked")) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) { param.result = false }
                })
            }
        }
    }
}

fun SpotifyHook.InstallVisualAdSniper() {
    VLogger.log("=== Visual Ad Sniper Activated ===")

    val adActivities = listOf(
        "com.spotify.adsdisplay.display.VideoAdActivity",
        "com.spotify.adsdisplay.display.SponsoredSessionActivity",
        "com.spotify.adsdisplay.display.AdActivity",
        "com.spotify.interstitial.display.InterstitialActivity",
        "com.spotify.adsdisplay.browser.inapp.InAppBrowserActivity"
    )

    adActivities.forEach { activityName ->
        val clazz = XposedHelpers.findClassIfExists(activityName, classLoader) ?: return@forEach

        XposedBridge.hookAllMethods(clazz, "onCreate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                VLogger.log("[AD-SNIPER] Blocked: $activityName")
                val activity = param.thisObject as android.app.Activity
                activity.finish()
            }
        })
    }

    runCatching {
        val dynamicAdActivityClass = ::adActivityFingerprint.clazz
        XposedBridge.hookAllMethods(dynamicAdActivityClass, "onCreate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as android.app.Activity
                VLogger.log("[DYNAMIC-SNIPER] Blocked obfuscated ad activity")
                activity.finish()
            }
        })
    }
}

fun SpotifyHook.InstallGlobalUIAssassin() {
    VLogger.log("=== Global UI Assassin Activated ===")

    val activityClass = XposedHelpers.findClassIfExists("android.app.Activity", classLoader)

    activityClass?.let { clazz ->
        XposedBridge.hookAllMethods(clazz, "onCreate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as android.app.Activity
                val className = activity.javaClass.name.lowercase()

                val intentStr = activity.intent?.toString()?.lowercase() ?: ""
                val extrasStr = activity.intent?.extras?.keySet()?.joinToString { key ->
                    "$key=${activity.intent?.extras?.get(key)}"
                }?.lowercase() ?: ""

                if (className.contains("adactivity") ||
                    className.contains("sponsor") ||
                    className.contains("interstitial") ||
                    className.contains("promo") ||
                    className.contains("inappbrowser") ||
                    intentStr.contains("ad_id") ||
                    intentStr.contains("is_ad") ||
                    extrasStr.contains("ad_id") ||
                    extrasStr.contains("sponsored")) {

                    VLogger.log("[GLOBAL-ASSASSIN] Blocked: $className")
                    activity.finish()
                }
            }
        })
    }
}

fun SpotifyHook.InstallGodsEyeTracer() {
    VLogger.log("=== God's Eye Tracer Activated ===")

    val mediaSessionClasses = listOf(
        "android.support.v4.media.session.MediaSessionCompat",
        "android.media.session.MediaSession"
    )

    mediaSessionClasses.forEach { className ->
        val clazz = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach

        clazz.declaredMethods.filter { it.name == "setMetadata" }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val metadata = param.args[0] ?: return

                        val bundleMethod = metadata.javaClass.getMethod("getBundle")
                        val bundle = bundleMethod.invoke(metadata) as? android.os.Bundle ?: return

                        val title = bundle.getString("android.media.metadata.TITLE")?.lowercase() ?: ""
                        val album = bundle.getString("android.media.metadata.ALBUM")?.lowercase() ?: ""

                        if (title.contains("advertisement") || title.contains("spotify") || album.contains("ad")) {
                            VLogger.log("[GODS-EYE] Ad metadata detected")

                            val trace = Thread.currentThread().stackTrace
                            for (i in 3..10) {
                                if (i < trace.size) {
                                    VLogger.log("   Origin: ${trace[i].className}.${trace[i].methodName}")
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }
            })
        }
    }

    val exoPlayerClasses = listOf(
        "com.google.android.exoplayer2.ExoPlayerImpl",
        "androidx.media3.exoplayer.ExoPlayerImpl",
        "com.google.android.exoplayer2.SimpleExoPlayer"
    )

    exoPlayerClasses.forEach { className ->
        val clazz = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach

        clazz.declaredMethods.filter { it.name == "setMediaItem" || it.name == "setMediaItems" }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val mediaItem = param.args[0] ?: return
                        val mediaItemStr = mediaItem.toString().lowercase()

                        if (mediaItemStr.contains("/ads/") ||
                            mediaItemStr.contains("sponsor") ||
                            mediaItemStr.contains("ad-") ||
                            mediaItemStr.contains("googleads")) {

                            VLogger.log("[EXO-SNIPER] Video ad blocked")
                            param.args[0] = null
                            param.result = null
                        }
                    } catch (e: Exception) {}
                }
            })
        }
    }

    val okHttpBuilder = XposedHelpers.findClassIfExists("okhttp3.Request\$Builder", classLoader)
    okHttpBuilder?.let { clazz ->
        XposedBridge.hookAllMethods(clazz, "url", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val url = param.args[0].toString().lowercase()

                if ((url.contains(".mp3") || url.contains(".mp4") || url.contains("audio-ads")) &&
                    (url.contains("ad") || url.contains("sponsor"))) {
                    VLogger.log("[RAW-NET] Audio/video ad file blocked: $url")

                    val trace = Thread.currentThread().stackTrace
                    VLogger.log("Origin: ${trace[4].className}.${trace[4].methodName}")
                }
            }
        })
    }
}

fun SpotifyHook.InstallFirehoseAndInvisibleShield() {
    VLogger.log("=== Firehose Shield Activated ===")

    val layoutInflaterClass = XposedHelpers.findClassIfExists("android.view.LayoutInflater", classLoader)

    layoutInflaterClass?.let { clazz ->
        XposedBridge.hookAllMethods(clazz, "inflate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val resourceId = param.args[0] as? Int ?: return
                    val inflater = param.thisObject as android.view.LayoutInflater
                    val context = inflater.context ?: return
                    val resourceName = context.resources.getResourceEntryName(resourceId).lowercase()

                    val view = param.result as? android.view.View ?: return

                    val isAdPayload = resourceName.contains("ad_overlay") ||
                            resourceName.contains("mraid_webview") ||
                            resourceName.contains("slate_ad") ||
                            resourceName.contains("npv_slate") ||
                            resourceName.contains("upsell_dialog") ||
                            resourceName.contains("promotional_banner") ||
                            resourceName.contains("snack_bar") ||
                            resourceName.contains("muted_video_ad") ||
                            resourceName.contains("embedded_npv_ad") ||
                            resourceName.contains("generic_embeddedad") ||
                            resourceName.contains("companion_ad") ||
                            resourceName.contains("recent_ads") ||
                            resourceName.contains("marquee_overlay") ||
                            resourceName.contains("sponsored_playlist") ||
                            resourceName.contains("brand_billboard") ||
                            resourceName.contains("discovery_takeover") ||
                            resourceName.contains("native_inline_engagement")

                    if (isAdPayload) {
                        view.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: android.view.View) {
                                v.alpha = 0.01f

                                val handler = android.os.Handler(android.os.Looper.getMainLooper())

                                val hunterRunnable = object : Runnable {
                                    override fun run() {
                                        if (v.windowToken == null) return

                                        var killed = false
                                        fun huntAndKill(group: android.view.ViewGroup) {
                                            for (i in 0 until group.childCount) {
                                                val child = group.getChildAt(i)
                                                try {
                                                    val childRes = child.context.resources.getResourceEntryName(child.id).lowercase()

                                                    if (childRes.contains("cta") || childRes.contains("learn_more") ||
                                                        childRes.contains("visit_site") || childRes.contains("action") ||
                                                        childRes.contains("close") || childRes.contains("skip") ||
                                                        childRes.contains("dismiss")) {

                                                        var detonated = false
                                                        try {
                                                            val getListenerInfo = android.view.View::class.java.getDeclaredMethod("getListenerInfo")
                                                            getListenerInfo.isAccessible = true
                                                            val listenerInfo = getListenerInfo.invoke(child)

                                                            if (listenerInfo != null) {
                                                                val mOnClickListener = listenerInfo.javaClass.getDeclaredField("mOnClickListener")
                                                                mOnClickListener.isAccessible = true
                                                                val clickListener = mOnClickListener.get(listenerInfo) as? android.view.View.OnClickListener

                                                                if (clickListener != null) {
                                                                    clickListener.onClick(child)
                                                                    VLogger.log("[FIREHOSE] Triggered button: $childRes")
                                                                    detonated = true
                                                                }
                                                            }
                                                        } catch (e: Exception) {}

                                                        if (!detonated) {
                                                            child.performClick()
                                                            child.callOnClick()
                                                        }

                                                        killed = true

                                                        handler.postDelayed({
                                                            v.visibility = android.view.View.GONE
                                                            val params = v.layoutParams
                                                            if (params != null) {
                                                                params.width = 0
                                                                params.height = 0
                                                                v.layoutParams = params
                                                            }
                                                        }, 300)

                                                        return
                                                    }
                                                } catch (e: Exception) {}
                                                if (!killed && child is android.view.ViewGroup) huntAndKill(child)
                                            }
                                        }

                                        if (v is android.view.ViewGroup) huntAndKill(v)

                                        if (!killed) {
                                            handler.postDelayed(this, 500)
                                        }
                                    }
                                }
                                handler.postDelayed(hunterRunnable, 500)
                            }

                            override fun onViewDetachedFromWindow(v: android.view.View) {}
                        })
                    }
                } catch (e: Exception) {}
            }
        })
    }
}

fun SpotifyHook.InstallScoutLogicSniper() {
    VLogger.log("=== Scout Logic Sniper Activated ===")

    val quicksilverClass = XposedHelpers.findClassIfExists("com.spotify.messaging.quicksilver.view.QuicksilverActivity", classLoader)
    quicksilverClass?.let { clazz ->
        XposedBridge.hookAllMethods(clazz, "onCreate", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as android.app.Activity
                activity.finish()
                VLogger.toast("Promo popup blocked")
            }
        })
    }

    val mraidClass = XposedHelpers.findClassIfExists("com.spotify.ads.display.mraid.MraidWebView", classLoader)
    mraidClass?.let { clazz ->
        XposedBridge.hookAllMethods(clazz, "loadUrl", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = null
                VLogger.toast("Rich media ad blocked")
            }
        })
    }

    val ctaClass = XposedHelpers.findClassIfExists("com.spotify.ads.freetier.Cta", classLoader)
    ctaClass?.let { clazz ->
        clazz.declaredMethods.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val mName = method.name.lowercase()
                    if (mName.contains("click") || mName.contains("action") || mName.contains("open") || mName.contains("execute")) {
                        if (method.returnType == Void.TYPE) {
                            param.result = null
                            VLogger.toast("Redirect blocked")
                        }
                    }
                }
            })
        }
    }
}

fun SpotifyHook.InstallFinalBossAssassin() {
    VLogger.log("=== Final Boss Assassin Activated ===")

    val videoEngineClass = XposedHelpers.findClassIfExists("com.spotify.mobile.videonative.VideoEngine", classLoader)
    videoEngineClass?.let { clazz ->
        clazz.declaredMethods.forEach { method ->
            if (method.name.lowercase().contains("fetch") || method.name.lowercase().contains("manifest")) {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        VLogger.log("[VIDEO-ENGINE] Video fetch blocked")
                        param.result = null
                    }
                })
            }
        }
    }

    val okHttpBuilder = XposedHelpers.findClassIfExists("okhttp3.Request\$Builder", classLoader)
    okHttpBuilder?.let { clazz ->
        XposedBridge.hookAllMethods(clazz, "url", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val urlStr = param.args[0].toString().lowercase()

                    val isAdNetwork = urlStr.contains("sp-ad-cdn.spotify.com") ||
                            urlStr.contains("video-ads-static.googlesyndication") ||
                            urlStr.contains("googleusercontent.com/spotify.com") ||
                            urlStr.contains("aet.spotify.com") ||
                            urlStr.contains("gabo-receiver-service") ||
                            urlStr.contains("pubads.g.doubleclick.net")

                    if (isAdNetwork) {
                        param.args[0] = "http://127.0.0.1/blackhole.mp4"
                        VLogger.log("[NETWORK-BLACKHOLE] Video proxy rerouted")
                    }
                } catch (e: Exception) {}
            }
        })
    }
}

fun SpotifyHook.InstallArtistPageRestorer() {
    Log.e("V_SONAR", "=== Artist Page Restorer Activated ===")

    val configClasses = listOf(
        "com.spotify.remoteconfig.internal.ProductStateProto",
        "com.spotify.remoteconfig.ConfigResponse"
    )

    configClasses.forEach { className ->
        val clazz = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach

        clazz.declaredMethods.filter { it.returnType == String::class.java }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result as? String ?: return
                    if (result == "SHUFFLE_ONLY" || result == "FREE_TIER_LIMIT" || result == "restricted") {
                        param.result = "PREMIUM_TIER"
                        Log.e("V_SONAR", "[CONFIG] Overrode: $result")
                    }
                }
            })
        }

        clazz.declaredMethods.filter { it.returnType == Boolean::class.java || it.returnType == java.lang.Boolean.TYPE }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val mName = method.name.lowercase()
                    if (mName.contains("upsell") || mName.contains("restricted") || mName.contains("limit")) {
                        if (param.result == true) {
                            param.result = false
                            Log.e("V_SONAR", "[CONFIG] Overrode boolean: $mName")
                        }
                    }
                }
            })
        }
    }

    val artistClasses = listOf(
        "com.spotify.music.artist.model.ArtistCapabilities",
        "com.spotify.music.artist.model.ArtistModel",
        "com.spotify.hubframework.model.HubModel"
    )

    artistClasses.forEach { className ->
        val clazz = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach

        clazz.declaredMethods.filter { it.returnType == Boolean::class.java || it.returnType == java.lang.Boolean.TYPE }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val mName = method.name.lowercase()

                    if (mName.contains("ondemand") || mName.contains("canplay") || mName.contains("premium")) {
                        if (param.result == false) {
                            param.result = true
                            Log.e("V_SONAR", "[ARTIST] Enabled: $mName")
                        }
                    }

                    if (mName.contains("shuffleonly") || mName.contains("restricted") || mName.contains("upsell")) {
                        if (param.result == true) {
                            param.result = false
                            Log.e("V_SONAR", "[ARTIST] Disabled: $mName")
                        }
                    }
                }
            })
        }
    }
}

fun SpotifyHook.InstallDeepLinkAssassin() {
    Log.e("V_SONAR", "=== Deep Link Assassin Activated ===")

    val activityClass = XposedHelpers.findClassIfExists("android.app.Activity", classLoader)
    activityClass?.let { clazz ->
        XposedBridge.hookAllMethods(clazz, "startActivity", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                    val dataString = intent.dataString?.lowercase() ?: ""

                    if (dataString.contains("spotify:ad:") ||
                        dataString.contains("spotify:upsell:") ||
                        dataString.contains("spotify:promo:")) {

                        Log.e("V_SONAR", "[DEEP-LINK] Blocked: $dataString")

                        val emptyIntent = Intent()
                        val intentIndex = param.args.indexOf(intent)
                        if (intentIndex != -1) param.args[intentIndex] = emptyIntent
                    }
                } catch (e: Exception) {
                    Log.e("V_SONAR", "Deep link error: ${e.message}")
                }
            }
        })
    }
}

fun SpotifyHook.InstallMasterFeatureWeaponizer() {
    VLogger.log("=== Master Feature Weaponizer Activated ===")

    val forceTrueList = listOf(
        "app_events_killswitch",
        "bnc_ad_network_callouts_disabled",
        "bnc_limit_facebook_tracking",
        "deferred_analytics_collection",
        "FBSDKFeatureRestrictiveDataFiltering",
        "FBSDKFeaturePrivacyProtection",
        "FBSDKFeaturePIIFiltering",
        "FBSDKFeatureEventDeactivation",
        "FBSDKFeatureFilterSensitiveParams",
        "FBSDKFeatureBannedParamFiltering",
        "picture_in_picture",
        "shake_to_report",
        "android-media-session.media3_enabled",
        "create_button_enabled",
        "key_lyrics_on_npv_visible"
    )

    val forceFalseList = listOf(
        "auto_event_setup_enabled",
        "app_events_if_auto_log_subs",
        "FBSDKFeatureIAPLogging",
        "FBSDKFeatureIAPLoggingLib2",
        "FBSDKFeatureIAPLoggingLib5To7",
        "FBSDKFeatureIAPLoggingSK2",
        "FBSDKFeatureCodelessEvents",
        "FBSDKFeatureAppEventsCloudbridge",
        "FBSDKFeatureAEM",
        "fb_mobile_purchase"
    )

    val sharedPrefsClass = XposedHelpers.findClassIfExists("android.app.SharedPreferencesImpl", classLoader)
    sharedPrefsClass?.let { clazz ->
        XposedBridge.hookAllMethods(clazz, "getBoolean", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val key = param.args[0] as? String ?: return

                if (forceTrueList.contains(key) && param.result != true) {
                    param.result = true
                    VLogger.log("[CACHE] Enabled: $key")
                } else if (forceFalseList.contains(key) && param.result != false) {
                    param.result = false
                    VLogger.log("[CACHE] Disabled: $key")
                }
            }
        })
    }

    val jsonObjectClass = XposedHelpers.findClassIfExists("org.json.JSONObject", classLoader)
    jsonObjectClass?.let { clazz ->
        XposedBridge.hookAllMethods(clazz, "optBoolean", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val key = param.args[0] as? String ?: return

                if (forceTrueList.contains(key) && param.result != true) {
                    param.result = true
                } else if (forceFalseList.contains(key) && param.result != false) {
                    param.result = false
                }
            }
        })

        XposedBridge.hookAllMethods(clazz, "getBoolean", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val key = param.args[0] as? String ?: return

                if (forceTrueList.contains(key)) {
                    param.result = true
                } else if (forceFalseList.contains(key)) {
                    param.result = false
                }
            }
        })
    }
}

fun SpotifyHook.InstallVideoAdForensicsAndKiller() {
    VLogger.log("=== Video Ad Forensics Activated ===")

    val okHttpBuilderClass = XposedHelpers.findClassIfExists("okhttp3.Request\$Builder", classLoader)
    okHttpBuilderClass?.let { clazz ->
        XposedBridge.hookAllMethods(clazz, "url", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val urlStr = param.args[0].toString().lowercase()

                    val isVideoProxy = urlStr.contains("googleusercontent.com/spotify.com")
                    val isTracker = urlStr.contains("cdn.branch.io") || urlStr.contains("uriskiplist")

                    if (isVideoProxy || isTracker) {
                        VLogger.log("[VIDEO-PROXY] Blocked: $urlStr")

                        val trace = Thread.currentThread().stackTrace
                        for (i in 3..8) {
                            if (i < trace.size) {
                                VLogger.log("   Origin: ${trace[i].className}.${trace[i].methodName}")
                            }
                        }

                        param.args[0] = "http://127.0.0.1/v_blackhole_video_ad.mp4"
                    }
                } catch (e: Exception) {}
            }
        })
    }

    val exoClasses = listOf(
        "com.google.android.exoplayer2.ExoPlayerImpl",
        "androidx.media3.exoplayer.ExoPlayerImpl",
        "com.google.android.exoplayer2.SimpleExoPlayer"
    )

    exoClasses.forEach { className ->
        val clazz = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach
        clazz.declaredMethods.filter { it.name == "setMediaItem" || it.name == "setMediaItems" || it.name == "addMediaItem" }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val mediaItem = param.args[0] ?: return
                        val mediaItemStr = mediaItem.toString().lowercase()

                        if (mediaItemStr.contains("googleusercontent.com") || mediaItemStr.contains("ad")) {
                            VLogger.log("[EXO-CHOKEHOLD] Video proxy intercepted")
                            param.args[0] = null
                            param.result = null
                        }
                    } catch (e: Exception) {}
                }
            })
        }
    }

    val inflaterClass = java.util.zip.Inflater::class.java
    XposedBridge.hookAllMethods(inflaterClass, "inflate", object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                val outputBuffer = param.args[0] as? ByteArray ?: return
                val bytesRead = param.result as? Int ?: return

                if (bytesRead > 20) {
                    val snippet = String(outputBuffer.take(bytesRead.coerceAtMost(1024)).toByteArray(), Charsets.UTF_8).lowercase()

                    if (snippet.contains("maxads") || snippet.contains("leavebehindads") || snippet.contains("requestId")) {
                        VLogger.log("[MANIFEST-SNIFFER] Ad manifest intercepted")
                    }
                }
            } catch (e: Exception) {}
        }
    })
}

@Suppress("UNCHECKED_CAST")
fun SpotifyHook.UnlockPremium(prefs: de.robv.android.xposed.XSharedPreferences) {

    if (prefs.getBoolean("enable_visual_ads_block", true)) {
        InstallVideoAdForensicsAndKiller()
        InstallFinalBossAssassin()
        InstallScoutLogicSniper()
        InstallGlobalUIAssassin()
        InstallSlateModalAssassin()
        InstallVisualAdSniper()
        InstallWorkingPendragonFix()
        InstallFirehoseAndInvisibleShield()
    }

    if (prefs.getBoolean("enable_audio_ads_block", true)) {
        InstallAdAutoSkip()
        InstallNuclearAdScrubber()
        InstallAdChoke()
    }

    if (prefs.getBoolean("enable_ui_fixes", true)) {
        InstallArtistPageRestorer()
        InstallDeepLinkAssassin()
        InstallWorkingAdsRemoval()
        InstallPlayabilityForcer()
    }

    XposedHelpers.findAndHookMethod(
        "com.spotify.player.model.command.options.AutoValue_PlayerOptionOverrides\$Builder",
        classLoader,
        "build",
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.thisObject.callMethod("shufflingContext", false)
            }
        }
    )

    val contextMenuViewModelClazz = ::contextMenuViewModelClass.clazz
    XposedBridge.hookAllConstructors(
        contextMenuViewModelClazz, object : XC_MethodHook() {
            val isPremiumUpsell = ::isPremiumUpsellField.field

            override fun beforeHookedMethod(param: MethodHookParam) {
                val parameterTypes = (param.method as java.lang.reflect.Constructor<*>).parameterTypes
                for (i in 0 until param.args.size) {
                    if (parameterTypes[i].name != "java.util.List") continue
                    val original = param.args[i] as? List<*> ?: continue
                    val filtered = original.filter {
                        it!!.callMethod("getViewModel").let { isPremiumUpsell.get(it) } != true
                    }
                    param.args[i] = filtered
                }
            }
        }
    )
}
