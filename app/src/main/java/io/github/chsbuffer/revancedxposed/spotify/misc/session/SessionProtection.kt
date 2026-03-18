package io.github.chsbuffer.revancedxposed.spotify.misc.session

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook

private const val TAG = "SESSION_PROTECT"

/**
 * Thread-safe holder for cached auth response data.
 * Must be top-level (not local) so @Volatile works.
 */
private object AuthCache {
    @Volatile var body: String? = null
    @Volatile var contentType: Any? = null // okhttp3.MediaType
}

/**
 * ══════════════════════════════════════════════════════════════
 * Session Protection — prevents forced logouts on app restart.
 *
 * Problem:
 *   Spotify's server detects premium attribute overrides and
 *   invalidates the session token. While the app is running,
 *   dealer domain blocking prevents the logout command from
 *   arriving. But on cold restart, the app tries to refresh
 *   the token and the server rejects it → forced logout.
 *
 * Solution (3 layers):
 *   Layer 1: Cache the last successful token refresh response
 *            from login5.spotify.com. When the server returns
 *            401 (token revoked), replay the cached 200 response.
 *
 *   Layer 2: Block known detection/sync paths on spclient that
 *            report attribute mismatches to the server.
 *
 *   Layer 3: Protect stored auth credentials in SharedPreferences
 *            from being cleared during forced logout flows.
 * ══════════════════════════════════════════════════════════════
 */
@Suppress("UNCHECKED_CAST")
fun SpotifyHook.SessionProtection() {
    Log.i(TAG, "═══ SessionProtection v1 starting ═══")
    var hooks = 0

    // ══════════════════════════════════════════════════════════════
    // LAYER 1: Token refresh response caching & replay
    // ══════════════════════════════════════════════════════════════

    try {
        val chainClass = Class.forName("okhttp3.Interceptor\$Chain", false, classLoader)
        val reqClass = Class.forName("okhttp3.Request", false, classLoader)
        val respClass = Class.forName("okhttp3.Response", false, classLoader)
        val bodyClass = Class.forName("okhttp3.ResponseBody", false, classLoader)
        val mtClass = Class.forName("okhttp3.MediaType", false, classLoader)
        val urlClass = Class.forName("okhttp3.HttpUrl", false, classLoader)
        val builderClass = Class.forName("okhttp3.Response\$Builder", false, classLoader)

        // Reflection handles
        val reqUrl = reqClass.getMethod("url")
        val urlHost = urlClass.getMethod("host")
        val urlPath = urlClass.getMethod("encodedPath")
        val respCode = respClass.getMethod("code")
        val respReq = respClass.getMethod("request")
        val respBody = respClass.getMethod("body")
        val respNewBuilder = respClass.getMethod("newBuilder")
        val bodyStr = bodyClass.getMethod("string")
        val bodyCT = bodyClass.getMethod("contentType")
        val bodyCreate = bodyClass.getMethod("create", mtClass, String::class.java)
        val bCode = builderClass.getMethod("code", Int::class.java)
        val bMsg = builderClass.getMethod("message", String::class.java)
        val bBody = builderClass.getMethod("body", bodyClass)
        val bBuild = builderClass.getMethod("build")

        // peekBody lets us read without consuming — optional
        val peekBody = try {
            respClass.getMethod("peekBody", Long::class.javaPrimitiveType)
        } catch (_: Throwable) { null }

        val proceed = chainClass.getMethod("proceed", reqClass)
        XposedBridge.hookMethod(proceed, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val resp = param.result ?: return
                    val req = respReq.invoke(resp) ?: return
                    val url = reqUrl.invoke(req) ?: return
                    val host = urlHost.invoke(url) as? String ?: return
                    val path = urlPath.invoke(url) as? String ?: ""

                    // Only intercept auth/token endpoints
                    val isAuth = host.contains("login5") ||
                        (host.endsWith(".spotify.com") && (
                            path.contains("/v3/login") ||
                            path.contains("/token") ||
                            path.contains("/auth/token")
                        ))
                    if (!isAuth) return

                    val code = respCode.invoke(resp) as Int

                    if (code in 200..299) {
                        // ── Cache successful auth response ───────────
                        try {
                            val text: String?
                            if (peekBody != null) {
                                val peeked = peekBody.invoke(resp, 65536L)
                                text = bodyStr.invoke(peeked) as? String
                            } else {
                                val b = respBody.invoke(resp) ?: return
                                val ct = bodyCT.invoke(b)
                                text = bodyStr.invoke(b) as? String
                                // Body was consumed — rebuild response
                                if (text != null) {
                                    val nb = bodyCreate.invoke(null, ct, text)
                                    val rb = respNewBuilder.invoke(resp)
                                    bBody.invoke(rb, nb)
                                    param.result = bBuild.invoke(rb)
                                }
                            }
                            if (text != null && text.contains("access_token")) {
                                AuthCache.body = text
                                try {
                                    val b = respBody.invoke(
                                        if (peekBody != null) resp else param.result
                                    )
                                    if (b != null) AuthCache.contentType = bodyCT.invoke(b)
                                } catch (_: Throwable) {}
                                Log.i(TAG, "✓ CACHED auth response from $host$path (${text.length} chars)")
                            }
                        } catch (e: Throwable) {
                            Log.w(TAG, "Cache read failed: ${e.message}")
                        }

                    } else if (code == 401 || code == 403) {
                        // ── Token rejected: replay cached response ───
                        Log.w(TAG, "★ AUTH REJECTED: $code from $host$path")
                        val cached = AuthCache.body
                        if (cached != null) {
                            Log.i(TAG, "★ REPLAYING cached auth response (${cached.length} chars)")
                            val ct = AuthCache.contentType
                            val replayBody = bodyCreate.invoke(null, ct, cached)
                            val rb = respNewBuilder.invoke(resp)
                            bCode.invoke(rb, 200)
                            bMsg.invoke(rb, "OK")
                            bBody.invoke(rb, replayBody)
                            param.result = bBuild.invoke(rb)
                        } else {
                            Log.w(TAG, "✗ No cached auth response available for replay")
                        }
                    }
                } catch (_: Throwable) { /* don't crash on auth intercept */ }
            }
        })
        Log.i(TAG, "✓ Layer 1: Auth token refresh interceptor hooked")
        hooks++
    } catch (e: Throwable) {
        Log.w(TAG, "✗ Layer 1: ${e.message}")
    }

    // ══════════════════════════════════════════════════════════════
    // LAYER 2: Block detection/sync endpoints
    // ══════════════════════════════════════════════════════════════
    //
    // Spotify uses certain spclient paths to sync attribute state
    // with the server. Blocking these prevents the server from
    // discovering that premium attributes were overridden.

    try {
        val chainClass = Class.forName("okhttp3.Interceptor\$Chain", false, classLoader)
        val reqClass = Class.forName("okhttp3.Request", false, classLoader)
        val bodyClass = Class.forName("okhttp3.ResponseBody", false, classLoader)
        val builderClass = Class.forName("okhttp3.Response\$Builder", false, classLoader)
        val protocolClass = Class.forName("okhttp3.Protocol", false, classLoader)
        val mtClass = Class.forName("okhttp3.MediaType", false, classLoader)
        val urlClass = Class.forName("okhttp3.HttpUrl", false, classLoader)

        val reqUrl = reqClass.getMethod("url")
        val urlHost = urlClass.getMethod("host")
        val urlPath = urlClass.getMethod("encodedPath")
        val newBuilder = builderClass.getConstructor()
        val bReq = builderClass.getMethod("request", reqClass)
        val bProto = builderClass.getMethod("protocol", protocolClass)
        val bCode = builderClass.getMethod("code", Int::class.java)
        val bMsg = builderClass.getMethod("message", String::class.java)
        val bBody = builderClass.getMethod("body", bodyClass)
        val bBuild = builderClass.getMethod("build")
        val http11 = protocolClass.getField("HTTP_1_1").get(null)
        val parseMT = mtClass.getMethod("parse", String::class.java)
        val textMT = parseMT.invoke(null, "text/plain")
        val bodyCreate = bodyClass.getMethod("create", mtClass, String::class.java)

        // Paths that detect/report premium attribute mismatches
        val detectionPaths = setOf(
            "/v3/dual-sync/",       // Attribute mismatch detection
            "/dual-sync/",          // Alternative sync path
            "/v1/social-connect/",  // Social connect verification
            "/melody/v1/check"      // Session validation checks
        )

        fun isSpclient(host: String): Boolean {
            val h = host.lowercase()
            return h.contains("spclient") && h.endsWith(".spotify.com")
        }

        fun isDetectionPath(path: String): Boolean {
            val p = path.lowercase()
            return detectionPaths.any { p.startsWith(it) }
        }

        val proceed = chainClass.getMethod("proceed", reqClass)
        XposedBridge.hookMethod(proceed, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val req = param.args[0] ?: return
                    val url = reqUrl.invoke(req) ?: return
                    val host = urlHost.invoke(url) as? String ?: return
                    val path = urlPath.invoke(url) as? String ?: return

                    if (isSpclient(host) && isDetectionPath(path)) {
                        Log.i(TAG, "★ DETECTION BLOCKED: $host$path")
                        val emptyBody = bodyCreate.invoke(null, textMT, "")
                        val builder = newBuilder.newInstance()
                        bReq.invoke(builder, req)
                        bProto.invoke(builder, http11)
                        bCode.invoke(builder, 204)
                        bMsg.invoke(builder, "Blocked by RVX")
                        bBody.invoke(builder, emptyBody)
                        param.result = bBuild.invoke(builder)
                    }
                } catch (_: Throwable) { /* don't crash */ }
            }
        })
        Log.i(TAG, "✓ Layer 2: Detection path blocking hooked")
        hooks++
    } catch (e: Throwable) {
        Log.w(TAG, "✗ Layer 2: ${e.message}")
    }

    // ══════════════════════════════════════════════════════════════
    // LAYER 3: Protect stored auth credentials
    // ══════════════════════════════════════════════════════════════
    //
    // When Spotify force-logs-out, it clears stored tokens from
    // SharedPreferences. By intercepting remove() and clear(),
    // we keep credentials alive for the next app startup.

    try {
        val protectedKeywords = setOf(
            "access_token", "refresh_token", "token",
            "session", "auth", "credential", "login",
            "bearer", "oauth", "account"
        )

        fun isAuthKey(key: String?): Boolean {
            if (key == null) return false
            val k = key.lowercase()
            return protectedKeywords.any { k.contains(it) }
        }

        val editorClass = android.content.SharedPreferences.Editor::class.java

        // Prevent remove() of auth-related keys
        val removeMethod = editorClass.getMethod("remove", String::class.java)
        XposedBridge.hookMethod(removeMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val key = param.args[0] as? String ?: return
                if (isAuthKey(key)) {
                    Log.i(TAG, "★ PROTECTED auth key from removal: $key")
                    param.result = param.thisObject
                }
            }
        })
        Log.i(TAG, "✓ Layer 3A: SharedPreferences.Editor.remove() hooked")
        hooks++

        // Prevent clear() that would wipe all credentials
        val clearMethod = editorClass.getMethod("clear")
        XposedBridge.hookMethod(clearMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                Log.i(TAG, "★ BLOCKED SharedPreferences.Editor.clear()")
                param.result = param.thisObject
            }
        })
        Log.i(TAG, "✓ Layer 3B: SharedPreferences.Editor.clear() hooked")
        hooks++
    } catch (e: Throwable) {
        Log.w(TAG, "✗ Layer 3: ${e.message}")
    }

    Log.i(TAG, "═══ SessionProtection v1 complete: $hooks hooks ═══")
}
