# Contributing to ReVancedXposed Spotify

Thank you for your interest in contributing! This is a community-maintained fork of the original [chsbuffer/ReVancedXposed_Spotify](https://github.com/chsbuffer/ReVancedXposed_Spotify), which has been archived. Contributions of all kinds are welcome.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How to Contribute](#how-to-contribute)
- [Development Setup](#development-setup)
- [Branch Strategy](#branch-strategy)
- [Commit Guidelines](#commit-guidelines)
- [Pull Request Process](#pull-request-process)
- [Reporting Issues](#reporting-issues)
- [License](#license)

## Code of Conduct

- Be respectful and constructive in all interactions.
- Do not share or distribute Spotify APKs, proprietary code, or copyrighted material.
- Do not discuss piracy or circumventing paid services — this project is for educational and research purposes.

## Getting Started

1. **Fork** this repository and clone your fork locally.
2. Read [DEVELOPMENT.md](DEVELOPMENT.md) for the full project structure and patch authoring guide.
3. Read [CHANGELOG.md](CHANGELOG.md) for recent changes and context.

## How to Contribute

- **Bug fixes** — Spotify updates frequently; hooks break often. Fixing broken fingerprints or hooks is always valuable.
- **Anti-detection improvements** — Better stealth techniques to avoid client-side or server-side detection.
- **New patches** — Adding support for new Spotify features or behaviors.
- **Documentation** — Improving guides, code comments, or developer onboarding.
- **Testing** — Reporting detailed issues with logs, Spotify version, and Android version.

## Development Setup

### Prerequisites

- **Android SDK** (API 36+)
- **JDK 17** (OpenJDK recommended)
- **Rooted Android device** with LSPosed or compatible Xposed framework
- **ADB** for installing and debugging

### Building

```bash
# Release build (randomized package name, minified)
./gradlew :app:assembleUniversalRelease

# Debug build (side-by-side installable with release)
./gradlew :app:assembleUniversalDebug
```

APKs are output to `app/build/outputs/apk/universal/{release,debug}/`.

### Installing

```bash
adb install app/build/outputs/apk/universal/release/app-universal-release.apk
```

Then enable the module in LSPosed, set scope to `com.spotify.music`, and force-stop Spotify.

### Debugging

- Use `adb logcat -s LSPosed` or `adb logcat | grep -i revanced` to view hook logs.
- Debug builds show initialization toasts; release builds suppress them.

## Branch Strategy

| Branch | Purpose |
|--------|---------|
| `main` | Stable, tested code. All releases are tagged here. |
| `feature/*` | New features or significant changes. |
| `fix/*` | Bug fixes and fingerprint updates. |

Always branch off `main` for new work.

## Commit Guidelines

Write clear, descriptive commit messages:

```
<type>: <short summary>

<optional body explaining why, not just what>
```

**Types:** `fix`, `feat`, `refactor`, `docs`, `build`, `test`, `chore`

Examples:
- `fix: Update productStateProto fingerprint for Spotify 9.x`
- `feat: Add stealth native library loading`
- `docs: Improve DEVELOPMENT.md patch porting section`

## Pull Request Process

1. **One concern per PR** — don't bundle unrelated changes.
2. **Test on a real device** — emulators don't support Xposed. Include the Spotify version and Android version you tested on.
3. **Describe what and why** — explain the problem, how your change fixes it, and any detection/stealth implications.
4. **Keep diffs minimal** — avoid reformatting unrelated code.
5. **Update CHANGELOG.md** — add your changes under `[Unreleased]`.
6. **Ensure it builds** — `./gradlew :app:assembleUniversalRelease` must succeed with no errors.

### PR template

```markdown
## What does this PR do?
<!-- Brief description -->

## Why is this change needed?
<!-- Context: broken hook, new detection vector, etc. -->

## Testing
- Spotify version:
- Android version:
- Xposed framework:
- Tested behavior:

## Checklist
- [ ] Builds without errors
- [ ] Tested on a real rooted device
- [ ] CHANGELOG.md updated
- [ ] No unrelated formatting changes
```

## Reporting Issues

When opening an issue, include:

- **Spotify version** (e.g., 9.0.44.478)
- **Android version** and device model
- **Xposed framework** and version (LSPosed, etc.)
- **Module version** (commit hash or release tag)
- **Logs** from `adb logcat` around the time of the issue
- **Steps to reproduce** the problem
- **Expected vs. actual behavior**

Issues without sufficient detail may be closed.

## License

By contributing, you agree that your contributions will be licensed under the [GPL-3.0 License](LICENSE), consistent with the original project.
