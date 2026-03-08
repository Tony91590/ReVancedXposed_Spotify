# Changelog

All notable changes to this project will be documented in this file.

This project follows the [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format and semantic versioning.

---

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