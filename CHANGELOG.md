# Changelog

All notable changes to this project will be documented in this file.

This project follows the [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format and semantic versioning.

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