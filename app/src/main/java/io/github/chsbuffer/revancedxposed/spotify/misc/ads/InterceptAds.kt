package io.github.chsbuffer.revancedxposed.spotify.misc.ads

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.chsbuffer.revancedxposed.spotify.SpotifyHook
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.net.InetAddress

private const val TAG = "DNS_REDIRECT"

// 👉 TON DNS ICI
private const val CUSTOM_DNS = "1.2.3.4"

fun SpotifyHook.InterceptAds() {
    Log.i(TAG, "═══ DNS Redirect Mode (ALL traffic) ═══")

    var hooks = 0

    // ═══════════════════════════════════════
    // DNS RESOLVER (centralisé)
    // ═══════════════════════════════════════
    fun resolve(host: String): InetAddress? {
        return try {
            val lookup = Lookup(host, Type.A)
            val resolver = SimpleResolver(CUSTOM_DNS)
            resolver.setTimeout(2) // évite freeze

            lookup.setResolver(resolver)
            val result = lookup.run()

            if (result != null && result.isNotEmpty()) {
                val ip = result[0].rdataToString()
                val addr = InetAddress.getByName(ip)
                Log.i(TAG, "DNS: $host -> $ip")
                addr
            } else {
                Log.w(TAG, "DNS FAIL (empty): $host")
                null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "DNS ERROR: $host (${e.message})")
            null
        }
    }

    // ═══════════════════════════════════════
    // LAYER 1: InetAddress hooks
    // ═══════════════════════════════════════

    // getByName
    try {
        val method = InetAddress::class.java.getMethod("getByName", String::class.java)
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val host = param.args[0] as? String ?: return

                val resolved = resolve(host)
                if (resolved != null) {
                    param.result = resolved
                }
            }
        })
        hooks++
        Log.i(TAG, "✓ InetAddress.getByName hooked")
    } catch (e: Throwable) {
        Log.w(TAG, "✗ getByName: ${e.message}")
    }

    // getAllByName
    try {
        val method = InetAddress::class.java.getMethod("getAllByName", String::class.java)
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val host = param.args[0] as? String ?: return

                val resolved = resolve(host)
                if (resolved != null) {
                    param.result = arrayOf(resolved)
                }
            }
        })
        hooks++
        Log.i(TAG, "✓ InetAddress.getAllByName hooked")
    } catch (e: Throwable) {
        Log.w(TAG, "✗ getAllByName: ${e.message}")
    }

    // ═══════════════════════════════════════
    // LAYER 2: OkHttp DNS (TRÈS IMPORTANT)
    // ═══════════════════════════════════════

    try {
        val dnsInterface = Class.forName("okhttp3.Dns", false, classLoader)
        val systemDnsField = dnsInterface.getField("SYSTEM")
        val systemDns = systemDnsField.get(null)

        val lookupMethod = systemDns.javaClass.getMethod("lookup", String::class.java)

        XposedBridge.hookMethod(lookupMethod, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val host = param.args[0] as? String ?: return

                val resolved = resolve(host)
                if (resolved != null) {
                    param.result = listOf(resolved)
                }
            }
        })

        hooks++
        Log.i(TAG, "✓ OkHttp DNS hooked")
    } catch (e: Throwable) {
        Log.w(TAG, "✗ OkHttp DNS: ${e.message}")
    }

    Log.i(TAG, "═══ DNS Redirect active ($hooks hooks) ═══")
}
