<div align="center">

# 🎵 RVX Spotify

<img src="https://img.shields.io/badge/Spotify-1DB954?style=for-the-badge&logo=spotify&logoColor=white" alt="Spotify" />
<img src="https://img.shields.io/badge/Xposed-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Xposed" />
<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
<img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />

<br>

[![GitHub release](https://img.shields.io/github/v/release/simoabid/RVX-Spotify?style=flat-square&color=1DB954&label=Latest%20Release)](https://github.com/simoabid/RVX-Spotify/releases/latest)
[![GitHub downloads](https://img.shields.io/github/downloads/simoabid/RVX-Spotify/total?style=flat-square&color=blue&label=Downloads)](https://github.com/simoabid/RVX-Spotify/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=flat-square)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub stars](https://img.shields.io/github/stars/simoabid/RVX-Spotify?style=flat-square&color=yellow)](https://github.com/simoabid/RVX-Spotify/stargazers)

**An Xposed/LSPosed module that unlocks premium features on Spotify — with full ad blocking.**

*No server-side detection. No forced logouts. Just music.*

<br>

[📥 Download Latest](#-download) • [✨ Features](#-features) • [📦 Installation](#-installation) • [🔧 Domain Discovery](#-dns-domain-discovery-mode) • [❓ FAQ](#-faq)

</div>

---

## 📌 About

> [!IMPORTANT]
> - This is **NOT** an official ReVanced project — do not ask ReVanced developers for help.
> - **Root access** (Magisk/KernelSU) with **LSPosed** is strictly **required**.

This is an actively maintained fork of the original [chsbuffer/ReVancedXposed_Spotify](https://github.com/chsbuffer/ReVancedXposed_Spotify), which was archived and is now read-only.

**What this fork brings:**
- ✅ Full ad blocking — audio & visual ads completely eliminated
- ✅ No forced logouts (avoids server-side detection traps)
- ✅ Active maintenance & updates
- ✅ Compatible with latest Spotify versions

All credit for the original implementation goes to the original author and contributors. This fork remains licensed under **GPL-3.0**.

---

## ✨ Features

| Feature | Status | Description |
|---------|--------|-------------|
| 🔓 **Unlock Premium** | ✅ Working | Unlocks premium UI features (shuffle, unlimited skips, etc.) |
| 🚫 **Block All Ads** | ✅ Working | DNS-level ad blocking — blocks 25+ ad-serving domains at the network layer |
| 🔗 **Sanitize Links** | ✅ Working | Removes tracking parameters from shared Spotify links |
| 🏠 **Fix Widgets** | ✅ Working | Fixes third-party launcher widgets |

### How Ad Blocking Works

The module implements a **DNS-level sinkhole** directly within Spotify's process — the same proven technique used by [AdGuard DNS](https://adguard-dns.io) and [Pi-hole](https://pi-hole.net):

```
Spotify tries to reach → ads.spotify.com
Module intercepts DNS  → returns 127.0.0.1 (loopback)
Connection fails       → no ads served ✅
```

This approach operates at the **network layer** rather than modifying account attributes, which means:
- ❌ ~~Forced logouts~~ — doesn't trigger server-side dual-sync detection
- ❌ ~~Account flags~~ — your account state remains untouched
- ✅ Works reliably without depending on Spotify's internal code structure

> [!NOTE]
> The `ads` account attribute is intentionally **not** overridden. Setting `ads=FALSE` triggers Spotify's server-side detection, causing forced logouts every 60–120 seconds. DNS blocking avoids this entirely.

---

## 📥 Download

| Build | Link |
|-------|------|
| **Latest Release** | [![Download](https://img.shields.io/github/v/release/simoabid/RVX-Spotify?style=for-the-badge&color=1DB954&label=Download)](https://github.com/simoabid/RVX-Spotify/releases/latest) |

> [!NOTE]
> The package name and signature of this build vary. You do **not** need to reinstall daily.

---

## 📦 Installation

### Prerequisites
- Rooted Android device (Magisk or KernelSU)
- [LSPosed](https://github.com/LSPosed/LSPosed) framework installed
- Spotify app installed from Play Store or APK

### Steps
1. Download the latest release APK from [Releases](https://github.com/simoabid/RVX-Spotify/releases/latest)
2. Install the APK on your device
3. Open **LSPosed Manager** → **Modules**
4. Enable **RVX Spotify** and select **Spotify** as the target app
5. Force stop Spotify and reopen it
6. Enjoy ad-free music 🎵

---

## 🔧 DNS Domain Discovery Mode

Spotify may change their ad-serving domains over time. The module includes a built-in **Domain Discovery Mode** to help you find new domains:

<details>
<summary><b>📖 Click to expand — How to discover new ad domains</b></summary>

<br>

1. Open `InterceptAds.kt` and set:
   ```kotlin
   private const val DNS_DISCOVERY_MODE = true
   ```
2. Rebuild and install:
   ```bash
   ./gradlew :app:assembleUniversalRelease
   ```
3. Play Spotify for a few minutes
4. Capture the domain log:
   ```bash
   adb logcat -s AD_DIAG | grep "DNS_LOG"
   ```
5. Review the output:
   ```
   DNS_LOG: ✓ ALLOWED  api-partner.spotify.com
   DNS_LOG: ★ BLOCKED  ads.spotify.com → 127.0.0.1
   DNS_LOG: ✓ ALLOWED  newdomain.spotify.com    ← is this an ad domain?
   ```
6. Look for domains with keywords like: `ad`, `track`, `analytics`, `sponsored`, `log`, `metric`
7. Add suspicious domains to the `blockedDomains` set in `InterceptAds.kt`
8. Set `DNS_DISCOVERY_MODE = false` and rebuild for daily use

**💡 Tip:** Save to file for easier review:
```bash
adb logcat -s AD_DIAG | grep "DNS_LOG" > ~/spotify_domains.txt
```

</details>

---

## 🛡️ Currently Blocked Domains

<details>
<summary><b>View all 25 blocked domains</b></summary>

<br>

| Domain | Category |
|--------|----------|
| `ads.spotify.com` | Spotify Ads |
| `spclient.wg.spotify.com` | Spotify Ad Client |
| `audio-ak-spotify-com.akamaized.net` | Ad Audio CDN |
| `audio2.spotify.com` | Ad Audio |
| `analytics.spotify.com` | Analytics |
| `adstats.spotify.com` | Ad Statistics |
| `adeventtracker.spotify.com` | Ad Event Tracking |
| `tracking.spotify.com` | Tracking |
| `log.spotify.com` | Logging |
| `crashdump.spotify.com` | Crash Reporting |
| `sponsored-recommendations.spotify.com` | Sponsored Content |
| `desktop.spotify.com` | Desktop Ads |
| `weblb-wg.gslb.spotify.com` | Load Balancer (Ads) |
| `redirect.spotify.net` | Ad Redirects |
| `firebaseinstallations.googleapis.com` | Firebase Tracking |
| `firebase-settings.crashlytics.com` | Crashlytics |
| `cdn.branch.io` | Branch.io Tracking |
| `api2.branch.io` | Branch.io API |
| `pagead2.googlesyndication.com` | Google Ads |
| `bs.serving-sys.com` | Serving Sys Ads |
| `bounceexchange.com` | Bounce Exchange |
| `sb.scorecardresearch.com` | Scorecard Research |
| `b.scorecardresearch.com` | Scorecard Research |
| `segment-data-us-east.zqtk.net` | Segment Tracking |
| `live.ravelin.click` | Ravelin Tracking |

</details>

---

## ❓ FAQ

<details>
<summary><b>Will this get my account banned?</b></summary>

> The module does not modify your Spotify account attributes or send modified data to Spotify's servers. DNS blocking is indistinguishable from network issues — it's the same as using AdGuard DNS or a Pi-hole on your network.
</details>

<details>
<summary><b>Why not just set ads=FALSE?</b></summary>

> Setting the `ads` attribute to `FALSE` triggers Spotify's server-side dual-sync detection, causing forced logouts every 60–120 seconds. DNS-level blocking avoids this by operating at the network layer instead.
</details>

<details>
<summary><b>Ads are showing again after a Spotify update?</b></summary>

> Spotify may introduce new ad-serving domains. Use the built-in [Domain Discovery Mode](#-dns-domain-discovery-mode) to identify and block them.
</details>

<details>
<summary><b>Does this work without root?</b></summary>

> No. This is an Xposed/LSPosed module and requires root access (Magisk or KernelSU).
</details>

---

## 🏗️ Building from Source

```bash
# Clone the repository
git clone https://github.com/simoabid/RVX-Spotify.git
cd RVX-Spotify

# Build release APK
./gradlew :app:assembleUniversalRelease

# Output: app/build/outputs/apk/universal/release/
```

---

## ⭐ Credits

| Project | Contribution |
|---------|-------------|
| [chsbuffer/ReVancedXposed_Spotify](https://github.com/chsbuffer/ReVancedXposed_Spotify) | Original implementation — all foundational credit |
| [DexKit](https://luckypray.org/DexKit/en/) | High-performance dex runtime parsing library |
| [ReVanced](https://revanced.app) | Continuing the legacy of Vanced |
| [AdGuard DNS](https://adguard-dns.io) | Inspiration for DNS-level ad blocking approach |

---

<div align="center">

**If this module helps you, consider giving it a ⭐**

[![Star History Chart](https://api.star-history.com/svg?repos=simoabid/RVX-Spotify&type=Date)](https://star-history.com/#simoabid/RVX-Spotify&Date)

</div>
