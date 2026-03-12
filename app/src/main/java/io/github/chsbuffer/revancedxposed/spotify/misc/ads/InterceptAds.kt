package io.github.chsbuffer.revancedxposed.spotify.misc.ads

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook
import java.net.InetAddress
import java.net.UnknownHostException

private const val TAG = "AD_DIAG"

/**
 * ══════════════════════════════════════════════════════════════
 * DNS DOMAIN DISCOVERY MODE
 * ══════════════════════════════════════════════════════════════
 *
 * Set this to `true` to log ALL domains that Spotify tries to resolve.
 * Use this to discover new ad domains when Spotify changes them.
 *
 * HOW TO USE:
 *   1. Set DNS_DISCOVERY_MODE = true (below)
 *   2. Rebuild: ./gradlew :app:assembleUniversalRelease
 *   3. Install the new APK on your device
 *   4. Open Spotify and play music for a few minutes (let ads appear)
 *   5. Run this command on your PC to capture the domain log:
 *
 *      adb logcat -s AD_DIAG | grep "DNS_LOG"
 *
 *   6. Review the output — you'll see every domain Spotify contacts:
 *
 *      DNS_LOG: ✓ ALLOWED  spclient.wg.spotify.com
 *      DNS_LOG: ✓ ALLOWED  api-partner.spotify.com
 *      DNS_LOG: ★ BLOCKED  ads.spotify.com → 127.0.0.1
 *      DNS_LOG: ✓ ALLOWED  newdomain.spotify.com        ← is this an ad domain?
 *
 *   7. Look for domains with keywords like: ad, track, analytics,
 *      sponsored, log, metric, telemetry, event, pixel, beacon
 *   8. Add suspicious domains to the `blockedDomains` set below
 *   9. Set DNS_DISCOVERY_MODE = false and rebuild for normal use
 *
 * TIP: To save the log to a file for easier review:
 *      adb logcat -s AD_DIAG | grep "DNS_LOG" > ~/spotify_domains.txt
 *
 * WARNING: Keep this OFF for daily use — it generates a LOT of log output.
 * ══════════════════════════════════════════════════════════════
 */
private const val DNS_DISCOVERY_MODE = false

/**
 * Ad suppression — Phase 21: DNS-level + Path-level ad blocking.
 *
 * Dual-layer network ad blocking:
 *
 * Layer 1 (DNS): Blocks ad-only domains at DNS resolution level by
 * sinkholing them to 127.0.0.1. Same technique as AdGuard DNS / Pi-hole.
 *
 * Layer 2B (Path): Blocks ad-related URL paths on shared domains like
 * spclient.spotify.com that also carry legitimate traffic (playlists,
 * profiles, playback). DNS can't block these — we need URL-level
 * inspection via OkHttp interceptor hooks.
 *
 * The domain list was verified working via AdGuard DNS — zero ads,
 * zero disruption, music plays perfectly.
 */
@Suppress("UNCHECKED_CAST")
fun SpotifyHook.InterceptAds() {
    Log.i(TAG, "═══ InterceptAds v21 (DNS + Path block) starting ═══")
    var hooks = 0

    // ══════════════════════════════════════════════════════════════
    // Blocked ad-serving domains
    // Verified working via AdGuard DNS — zero ads, zero disruption.
    // ══════════════════════════════════════════════════════════════
    val blockedDomains = setOf(
        "audio-ak-spotify-com.akamaized.net",
        "analytics.spotify.com",
        "adstats.spotify.com",
        "adeventtracker.spotify.com",
        "segment-data-us-east.zqtk.net",
        "live.ravelin.click",
        "weblb-wg.gslb.spotify.com",
        "tracking.spotify.com",
        "redirect.spotify.net",
        "log.spotify.com",
        "firebaseinstallations.googleapis.com",
        "firebase-settings.crashlytics.com",
        "crashdump.spotify.com",
        "cdn.branch.io",
        "api2.branch.io",
        "sponsored-recommendations.spotify.com",
        "pagead2.googlesyndication.com",
        "bs.serving-sys.com",
        "bounceexchange.com",
        "sb.scorecardresearch.com",
        "b.scorecardresearch.com",
        "audio2.spotify.com",
        "desktop.spotify.com",
        "ads.spotify.com",
        "spclient.wg.spotify.com"
    )

    // ══════════════════════════════════════════════════════════════
    // Blocked URL path prefixes (for shared domains like spclient)
    //
    // These paths serve ads on domains that ALSO carry legitimate
    // traffic (playlists, profiles, playback), so we can't block
    // the entire domain at DNS level — we block only the ad paths.
    //
    // Discovered via mitmproxy traffic capture.
    // ══════════════════════════════════════════════════════════════
    val blockedPathPrefixes = setOf(
        "/ads/",
        "/ad-logic/",
        "/ad-monetization/",
        "/v1/ads/",
        "/v2/ads/",
        "/v3/ads/",
        "/ads?"
    )

    // Domains that use mixed ad + legitimate traffic
    // (regional spclient variants like gew4-spclient, gae2-spclient, etc.)
    fun isSpclientDomain(host: String?): Boolean {
        if (host == null) return false
        val h = host.lowercase()
        return h.contains("spclient") && h.endsWith(".spotify.com")
    }

    fun isBlockedPath(path: String?): Boolean {
        if (path == null) return false
        val p = path.lowercase()
        return blockedPathPrefixes.any { prefix -> p.startsWith(prefix) }
    }

    // Loopback address for sinkholing
    val loopback = InetAddress.getByAddress("blocked.local", byteArrayOf(127, 0, 0, 1))
    val loopbackArray = arrayOf(loopback)

    // Helper: check if a hostname matches any blocked domain
    // Supports both exact match and subdomain match (e.g. "foo.ads.spotify.com")
    fun isBlocked(host: String?): Boolean {
        if (host == null) return false
        val h = host.lowercase()
        return blockedDomains.any { domain ->
            h == domain || h.endsWith(".$domain")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // LAYER 1: Hook InetAddress DNS resolution
    // ══════════════════════════════════════════════════════════════
    //
    // This is the fundamental network hook — ALL Java network calls
    // (OkHttp, HttpURLConnection, WebView, etc.) ultimately go through
    // InetAddress for DNS resolution.

    // ── 1A: InetAddress.getAllByName(String) → InetAddress[] ──────
    try {
        val method = InetAddress::class.java.getMethod("getAllByName", String::class.java)
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val host = param.args[0] as? String ?: return
                if (isBlocked(host)) {
                    Log.i(TAG, "★ DNS: BLOCKED getAllByName($host) → 127.0.0.1")
                    param.result = loopbackArray
                    if (DNS_DISCOVERY_MODE) {
                        Log.i(TAG, "DNS_LOG: ★ BLOCKED  $host → 127.0.0.1")
                    }
                } else if (DNS_DISCOVERY_MODE) {
                    Log.i(TAG, "DNS_LOG: ✓ ALLOWED  $host")
                }
            }
        })
        Log.i(TAG, "✓ 1A: InetAddress.getAllByName hooked")
        hooks++
    } catch (e: Throwable) { Log.w(TAG, "✗ 1A: getAllByName: ${e.message}") }

    // ── 1B: InetAddress.getByName(String) → InetAddress ──────────
    try {
        val method = InetAddress::class.java.getMethod("getByName", String::class.java)
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val host = param.args[0] as? String ?: return
                if (isBlocked(host)) {
                    Log.i(TAG, "★ DNS: BLOCKED getByName($host) → 127.0.0.1")
                    param.result = loopback
                    if (DNS_DISCOVERY_MODE) {
                        Log.i(TAG, "DNS_LOG: ★ BLOCKED  $host → 127.0.0.1")
                    }
                } else if (DNS_DISCOVERY_MODE) {
                    Log.i(TAG, "DNS_LOG: ✓ ALLOWED  $host")
                }
            }
        })
        Log.i(TAG, "✓ 1B: InetAddress.getByName hooked")
        hooks++
    } catch (e: Throwable) { Log.w(TAG, "✗ 1B: getByName: ${e.message}") }

    // ══════════════════════════════════════════════════════════════
    // LAYER 2: Hook URL/connection layer (defense-in-depth)
    // ══════════════════════════════════════════════════════════════
    //
    // Some libraries may cache DNS or use custom resolvers.
    // We also hook java.net.URL.openConnection() as a fallback.

    // ── 2A: java.net.URL.openConnection() ────────────────────────
    try {
        val urlClass = java.net.URL::class.java
        val openConn = urlClass.getMethod("openConnection")
        XposedBridge.hookMethod(openConn, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val url = param.thisObject as? java.net.URL ?: return
                if (isBlocked(url.host)) {
                    Log.i(TAG, "★ URL: BLOCKED openConnection(${url.host}${url.path})")
                    param.throwable = java.io.IOException("Blocked: ${url.host}")
                }
            }
        })
        Log.i(TAG, "✓ 2A: URL.openConnection hooked")
        hooks++
    } catch (e: Throwable) { Log.w(TAG, "✗ 2A: URL.openConnection: ${e.message}") }

    // ── 2B: Path-level blocking on spclient domains ───────────────
    //
    // spclient domains (gew4-spclient.spotify.com, etc.) serve BOTH
    // ad traffic and legitimate traffic on the same domain.
    // DNS can't block these — we intercept at the URL/path level.
    //
    // Hooks OkHttp's RealCall to intercept requests before they're
    // sent, returning an empty 204 response for ad paths.
    try {
        val interceptorClass = Class.forName("okhttp3.Interceptor\$Chain", false, classLoader)
        val requestClass = Class.forName("okhttp3.Request", false, classLoader)
        val responseClass = Class.forName("okhttp3.Response", false, classLoader)
        val responseBuilderClass = Class.forName("okhttp3.Response\$Builder", false, classLoader)
        val protocolClass = Class.forName("okhttp3.Protocol", false, classLoader)
        val responseBodyClass = Class.forName("okhttp3.ResponseBody", false, classLoader)
        val mediaTypeClass = Class.forName("okhttp3.MediaType", false, classLoader)
        val httpUrlClass = Class.forName("okhttp3.HttpUrl", false, classLoader)

        // Get the HttpUrl accessors
        val urlMethod = requestClass.getMethod("url")
        val hostMethod = httpUrlClass.getMethod("host")
        val encodedPathMethod = httpUrlClass.getMethod("encodedPath")

        // Get Response.Builder methods
        val newBuilder = responseBuilderClass.getConstructor()
        val builderRequest = responseBuilderClass.getMethod("request", requestClass)
        val builderProtocol = responseBuilderClass.getMethod("protocol", protocolClass)
        val builderCode = responseBuilderClass.getMethod("code", Int::class.java)
        val builderMessage = responseBuilderClass.getMethod("message", String::class.java)
        val builderBody = responseBuilderClass.getMethod("body", responseBodyClass)
        val builderBuild = responseBuilderClass.getMethod("build")

        // Get Protocol.HTTP_1_1
        val http11 = protocolClass.getField("HTTP_1_1").get(null)

        // Get ResponseBody.create(MediaType, String)
        val parseMediaType = mediaTypeClass.getMethod("parse", String::class.java)
        val emptyMediaType = parseMediaType.invoke(null, "text/plain")
        val createBody = responseBodyClass.getMethod("create", mediaTypeClass, String::class.java)

        // Hook the proceed(Request) method on Interceptor.Chain
        val proceedMethod = interceptorClass.getMethod("proceed", requestClass)
        XposedBridge.hookMethod(proceedMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val request = param.args[0] ?: return
                    val httpUrl = urlMethod.invoke(request) ?: return
                    val host = hostMethod.invoke(httpUrl) as? String ?: return
                    val path = encodedPathMethod.invoke(httpUrl) as? String ?: return

                    if (isSpclientDomain(host) && isBlockedPath(path)) {
                        Log.i(TAG, "★ PATH: BLOCKED $host$path")
                        if (DNS_DISCOVERY_MODE) {
                            Log.i(TAG, "DNS_LOG: ★ PATH_BLOCKED  $host$path")
                        }

                        // Build an empty 204 No Content response
                        val emptyBody = createBody.invoke(null, emptyMediaType, "")
                        val builder = newBuilder.newInstance()
                        builderRequest.invoke(builder, request)
                        builderProtocol.invoke(builder, http11)
                        builderCode.invoke(builder, 204)
                        builderMessage.invoke(builder, "Blocked by RVX")
                        builderBody.invoke(builder, emptyBody)
                        param.result = builderBuild.invoke(builder)
                    } else if (DNS_DISCOVERY_MODE && isSpclientDomain(host)) {
                        Log.i(TAG, "DNS_LOG: ✓ PATH_ALLOWED  $host$path")
                    }
                } catch (_: Throwable) { /* don't crash on path check failures */ }
            }
        })
        Log.i(TAG, "✓ 2B: OkHttp path-level blocking hooked")
        hooks++
    } catch (e: Throwable) { Log.w(TAG, "✗ 2B: OkHttp path blocking: ${e.message}") }

    // ══════════════════════════════════════════════════════════════
    // LAYER 3: OkHttp DNS interceptor (Spotify uses OkHttp internally)
    // ══════════════════════════════════════════════════════════════
    //
    // Hook OkHttp's Dns interface to block resolution of ad domains.
    // OkHttp can bypass InetAddress if a custom Dns is provided.

    try {
        val dnsInterface = Class.forName("okhttp3.Dns", false, classLoader)
        // Hook the Dns.SYSTEM field's lookup method
        val systemDnsField = dnsInterface.getField("SYSTEM")
        val systemDns = systemDnsField.get(null) ?: throw Exception("SYSTEM dns is null")
        val lookupMethod = systemDns.javaClass.getMethod("lookup", String::class.java)
        XposedBridge.hookMethod(lookupMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val host = param.args[0] as? String ?: return
                if (isBlocked(host)) {
                    Log.i(TAG, "★ OkHttp: BLOCKED Dns.lookup($host) → 127.0.0.1")
                    param.result = listOf(loopback)
                }
            }
        })
        Log.i(TAG, "✓ 3: OkHttp Dns.SYSTEM.lookup hooked")
        hooks++
    } catch (e: Throwable) { Log.w(TAG, "✗ 3: OkHttp Dns: ${e.message}") }

    // ══════════════════════════════════════════════════════════════
    // LAYER 4: Visual ad suppression (retained for defense-in-depth)
    // ══════════════════════════════════════════════════════════════

    val dk = getDexKit()
    dk.withBridge { bridge ->

        // ── 4A: DisplayAdActivity.onCreate → finish() ────────────────
        try {
            val displayAdClass = Class.forName(
                "com.spotify.adsdisplay.display.DisplayAdActivity", false, classLoader
            )
            val onCreateMethod = displayAdClass.getDeclaredMethod(
                "onCreate", android.os.Bundle::class.java
            )
            onCreateMethod.isAccessible = true
            XposedBridge.hookMethod(onCreateMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val activity = param.thisObject as android.app.Activity
                        Log.i(TAG, "★ 4A: DisplayAdActivity.finish()")
                        activity.finish()
                    } catch (e: Throwable) {
                        Log.w(TAG, "4A: ${e.message}")
                    }
                }
            })
            Log.i(TAG, "✓ 4A: DisplayAdActivity → finish()")
            hooks++
        } catch (e: Throwable) { Log.w(TAG, "✗ 4A: ${e.message}") }

        // ── 4B: InAppBrowserActivity.onCreate → finish() ─────────────
        try {
            val browserClass = Class.forName(
                "com.spotify.adsdisplay.browser.inapp.InAppBrowserActivity", false, classLoader
            )
            val onCreateMethod = browserClass.getDeclaredMethod(
                "onCreate", android.os.Bundle::class.java
            )
            onCreateMethod.isAccessible = true
            XposedBridge.hookMethod(onCreateMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val activity = param.thisObject as android.app.Activity
                        Log.i(TAG, "★ 4B: InAppBrowserActivity.finish()")
                        activity.finish()
                    } catch (e: Throwable) {
                        Log.w(TAG, "4B: ${e.message}")
                    }
                }
            })
            Log.i(TAG, "✓ 4B: InAppBrowserActivity → finish()")
            hooks++
        } catch (e: Throwable) { Log.w(TAG, "✗ 4B: ${e.message}") }

        // ── 4C: PlayerState.Builder.adBreakContext → skip ────────────
        try {
            val methods = bridge.findMethod {
                matcher {
                    declaredClass("com.spotify.player.model.AutoValue_PlayerState\$Builder")
                    name("adBreakContext")
                    paramCount(1)
                }
            }
            for (i in 0 until methods.size) {
                val member = methods[i].getMethodInstance(classLoader)
                XposedBridge.hookMethod(member, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        Log.i(TAG, "★ 4C: PlayerState.adBreakContext() → SKIPPED")
                        param.result = param.thisObject
                    }
                })
                Log.i(TAG, "✓ 4C: PlayerState.Builder.adBreakContext → skip")
                hooks++
            }
        } catch (e: Throwable) { Log.w(TAG, "✗ 4C: adBreakContext: ${e.message}") }

        // ── 4D: deserializeAdBreakContext → null ─────────────────────
        try {
            val methods = bridge.findMethod {
                matcher {
                    declaredClass("com.spotify.player.model.PlayerState_Deserializer")
                    name("deserializeAdBreakContext")
                }
            }
            for (i in 0 until methods.size) {
                val member = methods[i].getMethodInstance(classLoader)
                XposedBridge.hookMethod(member, XC_MethodReplacement.returnConstant(null))
                Log.i(TAG, "✓ 4D: deserializeAdBreakContext → null")
                hooks++
            }
        } catch (e: Throwable) { Log.w(TAG, "✗ 4D: ${e.message}") }

        // ── 4E: Builder.track(ContextTrack) → filter spotify:ad URIs ─
        val builderTargets = listOf(
            "com.spotify.player.model.AutoValue_PlayerState\$Builder",
            "com.spotify.player.model.AutoValue_PlayerQueue\$Builder"
        )
        for (className in builderTargets) {
            try {
                val methods = bridge.findMethod {
                    matcher {
                        declaredClass(className)
                        name("track")
                        addParamType("com.spotify.player.model.ContextTrack")
                    }
                }
                for (i in 0 until methods.size) {
                    val member = methods[i].getMethodInstance(classLoader)
                    XposedBridge.hookMethod(member, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val arg = param.args?.firstOrNull() ?: return
                            val uri = getTrackUri(arg)
                            if (uri != null && uri.startsWith("spotify:ad")) {
                                Log.i(TAG, "★ 4E: FILTERED ad track [$uri]")
                                param.result = param.thisObject
                            }
                        }
                    })
                    hooks++
                }
                Log.i(TAG, "✓ 4E: track filter on $className")
            } catch (e: Throwable) { Log.w(TAG, "✗ 4E $className: ${e.message}") }
        }
    }

    Log.i(TAG, "═══ InterceptAds v21 complete: $hooks hooks ═══")
}

private fun getTrackUri(obj: Any?): String? {
    if (obj == null) return null
    return try {
        val uriMethod = obj.javaClass.methods.firstOrNull {
            (it.name == "uri" || it.name == "getUri") && it.parameterCount == 0
        }
        uriMethod?.let { it.isAccessible = true; it.invoke(obj)?.toString() }
            ?: run {
                val uriField = obj.javaClass.allFields().firstOrNull {
                    it.name.lowercase().contains("uri") && it.type == String::class.java
                }
                uriField?.let { it.isAccessible = true; it.get(obj)?.toString() }
            }
    } catch (_: Throwable) { null }
}

private fun Class<*>.allFields(): Sequence<java.lang.reflect.Field> = sequence {
    var c: Class<*>? = this@allFields
    while (c != null && c != Any::class.java) {
        yieldAll(c.declaredFields.asSequence())
        c = c.superclass
    }
}
