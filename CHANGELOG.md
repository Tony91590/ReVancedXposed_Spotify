# Changelog

All notable changes to this project will be documented in this file.

This project follows the [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format and semantic versioning.

---

## [v260318] - 2026-03-18

### Added
- **Multi-layer Session Persistence** — a defense-in-depth strategy to prevent forced logouts and session invalidation.

| Layer | Functionality | Implementation |
| :--- | :--- | :--- |
| **Layer 1: Token Replay** | Caches the last successful auth response from `login5.spotify.com`. When the server returns a 401 (token revoked), it replays the cached 200 response to keep the session alive. | OkHttp `afterHookedMethod` on `proceed()` |
| **Layer 2: Detection Blocking** | Blocks `spclient` paths that report attribute mismatches to the server (`/v3/dual-sync/`, `/dual-sync/`, `/melody/v1/check`). | OkHttp `beforeHookedMethod` returning fake 204 |
| **Layer 3: Credential Protection** | Hooks `SharedPreferences.Editor.remove()` and `clear()` to prevent the app from deleting stored auth tokens, ensuring credentials survive forced logout attempts. | Android `SharedPreferences` hooks |

## [v260317] - 2026-03-17


### Added
- **Anti-logout protection** — blocks `dealer.g2.spotify.com` and its regional variants (gew4, guc3, etc.). These domains deliver real-time server-side "kill session" commands. By blocking the dealer WebSocket channel, the server can no longer push forced logout signals to the client even if modification is detected.

- **Consolidated blocklist** — refined the ad domain list to use subdomain matching for `dealer` and `spclient` regions, keeping the list clean and future-proof.

## [v260312] - 2026-03-12

### Added
- **Path-level ad blocking (Layer 2B)** — implemented an OkHttp interceptor to block ad-specific URL paths (`/ads/`, `/ad-logic/`, etc.) on shared `spclient` domains. This handles cases where DNS blocking is too broad because the domain also carries legitimate traffic (playlists, profiles). Returns empty 204 responses to ensure app stability.
- **Initial Path-level interceptor** — research and implementation of OkHttp hooks for URL-level inspection.

## [v260308] - 2026-03-08

### Added
- **DNS-level ad blocking** — blocks all Spotify ad-serving domains directly within the module by sinkholing DNS resolution to `127.0.0.1`. This is the same proven technique used by AdGuard DNS and Pi-hole, but runs entirely inside the Xposed module with no external DNS configuration required. Blocks 25 ad-related domains including `ads.spotify.com`, `spclient.wg.spotify.com`, `analytics.spotify.com`, sponsored content endpoints, and third-party tracking services.
- **DNS Domain Discovery Mode** — a built-in diagnostic tool for finding new ad domains when Spotify changes them. Toggle `DNS_DISCOVERY_MODE = true` in `InterceptAds.kt`, rebuild, and run `adb logcat -s AD_DIAG | grep "DNS_LOG"` to log every domain Spotify resolves. Identify new ad domains and add them to the blocklist.
- **Multi-layer defense-in-depth** — in addition to DNS blocking, the module hooks OkHttp's `Dns.SYSTEM.lookup()` and `URL.openConnection()` to cover all network paths. Also retains player-state sanitization (`PlayerState.Builder.adBreakContext` → skip, `deserializeAdBreakContext` → null), ad track URI filtering (`spotify:ad:` tracks removed from player/queue state), and ad display suppression (`DisplayAdActivity` and `InAppBrowserActivity` → `finish()` on creation).

### Design Notes
- The `ads` account attribute is intentionally **not** overridden. Setting `ads=FALSE` triggers Spotify's server-side dual-sync detection, causing forced logouts every 60-120 seconds. The DNS blocking approach avoids this entirely by operating at the network layer rather than modifying account state.
- The ad domain blocklist is maintained as a `Set<String>` in `InterceptAds.kt` with exact-match and subdomain matching support (e.g., blocking `ads.spotify.com` also blocks `foo.ads.spotify.com`).

---

## [v260225] - 2026-02-25

### Added
- Debug build variant (`assembleUniversalDebug`) with `.debug` package suffix and "RVX Spotify (Test)" label for side-by-side installation with the release build.

### Changed
- Premium attribute override now creates a defensive copy of the attributes map with cloned `AccountAttribute` objects (via `Unsafe.allocateInstance` reflection), leaving the original protobuf data untouched to prevent server-side detection through state serialization.
- Product state hook changed from `before` (in-place mutation) to `after` (return value replacement) to avoid modifying the protobuf backing store.
- Home and browse section ad filtering now returns a new `ArrayList` copy instead of mutating the original protobuf list via `iterator.remove()`, preventing detection through protobuf integrity checks.
- DexKit native library loading now copies `libdexkit.so` to a temp file with a randomized 12-character name before loading, preventing detection via `/proc/self/maps` inspection.
- DexKit cache filename changed from a bare SHA-256 hash to `com.spotify.music.scf.<hash prefix>` to blend with Spotify's own cache files.
- Initialization toast ("ReVanced Xposed is initializing") now only shows in debug builds.

### Fixed
- Fix forced logouts every 40–60 seconds caused by server-side detection of mutated protobuf attribute values.
- Wrap `AutoValue_PlayerOptionOverrides$Builder` hook in `runCatching` to prevent crashes if the hardcoded class name changes between Spotify versions.

---

## [v1.0.33] - 2026-02-24
### Changed
- Strip `Logger` calls.
- Use application ID instead of build type for cache filename.
- Implement cache using ProtoBuf serialization.

---

### Release process (copy-paste friendly)
1. Update the `Unreleased` section moving appropriate entries under a new header `## [vX.Y.Z] - YYYY-MM-DD`.
2. Update `CHANGELOG.md` and commit the change.
3. Tag the release:
   ```bash
   git tag -a vX.Y.Z -m "Release vX.Y.Z"
   git push origin vX.Y.Z