<div align="center">

# Blockline

### A mobile-first workspace for Minecraft Bedrock creators.

**Import · Explore · Edit · Back up · Export**

[![Build APK](https://github.com/slgamervn/Blockline/actions/workflows/build-apk.yml/badge.svg)](https://github.com/slgamervn/Blockline/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/slgamervn/Blockline?display_name=tag&color=3dba7a)](https://github.com/slgamervn/Blockline/releases/latest)

[Download APK](https://github.com/slgamervn/Blockline/releases/latest/download/Blockline.apk) · [Project website](https://slgamervn.github.io/Blockline/) · [Report a bug](https://github.com/slgamervn/Blockline/issues)

[Tiếng Việt](README.vi.md)

</div>

---

## Overview

Blockline is an Android-native studio for working with **Minecraft Bedrock add-on files** directly on your phone. It is designed around a simple principle: your packs stay on your device, while the tools you need to inspect and edit them stay close at hand.

The project is in active development. The current build focuses on a reliable local project workflow and a practical editor foundation. Advanced validation, generators, dependency analysis, and Script API reference browsing are planned for later phases.

## Highlights

- **Local-first workflow** — imported pack contents are copied to app-private storage; Phase 1 does not require an account or network access.
- **Pack import** — open `.mcpack` and `.mcaddon` archives through Android's system file picker.
- **Project management** — create a sample project, open, rename, duplicate, and delete local projects.
- **Pack overview** — inspect manifest metadata and jump to a manifest for editing.
- **File explorer** — browse folders, create, rename, move, and delete files; mark files as favorites and reopen the most recently opened file.
- **Editor foundation** — multi-tab editing, basic syntax coloring, undo/redo, JSON formatting that preserves line and block comments, find, and autosave.
- **Safer archive handling** — archive extraction checks paths against the extraction root to guard against ZIP-slip.
- **Backup and restore** — create a local project backup and restore the latest backup.
- **Export choices** — export a behavior pack, resource pack, or combined add-on when the project contains the corresponding packs.
- **Mobile-friendly themes** — dark, light, and system theme options with adjustable editor text size.
- **Bundled sample pack** — a small BP/RP template under `app/src/main/assets/sample-pack/`; the app generates fresh UUIDs for new sample projects.

## Project status

Blockline is being built in phases. The table below describes the current scope and roadmap; it does not mean every planned feature is already available.

| Phase | Scope | Status |
| --- | --- | --- |
| **1 — Foundation** | Local projects, import/export, explorer, editor, backup, core navigation | In progress |
| **2 — Search & validation** | Content index, UUID/dependency/reference checks, health report, error jump | Planned |
| **3 — Generators & snippets** | Pack resource generators, reusable snippets, templates | Planned |
| **4 — Advanced workflow** | Diff, batch replace, dependency viewer, Script API reference | Planned |

### Known limitations

- Project metadata currently uses a private JSON index and preferences; Room, Hilt, and the full Clean Architecture separation are not yet implemented.
- Syntax highlighting is a lightweight first pass, not a full language parser or viewport-only renderer.
- Content search currently scans supported text files up to 1 MB each and shows matching line numbers; identifier/UUID indexing and direct error-to-editor navigation remain future work.
- Resource-specific previews and exhaustive reference resolution for entities, items, blocks, recipes, loot tables, functions, scripts, and language files are not yet complete.
- Validation is an initial static analysis pass, not a complete Mojang schema validator. Automated tests and device-level verification are still needed before treating this as a production release.

## Download and install

1. Open the [latest release](https://github.com/slgamervn/Blockline/releases/latest).
2. Download **Blockline.apk**.
3. Open the downloaded APK on your Android device and follow the system installation prompts. Android may ask you to allow installation from the app used to download the file.
4. If you prefer not to install an APK, build the app from source using Android Studio.

**Release note:** current GitHub Actions releases contain a debug APK. This is an early-access build, not a Play Store release.

## Build from source

### Requirements

- Android Studio with Android SDK Platform 35
- JDK 17
- Gradle 8.14.6 (the GitHub Actions workflow provisions this version)
- Android 8.0 (API 26) or newer to run on a device

### Build

Clone the repository:

```bash
git clone https://github.com/slgamervn/Blockline.git
cd Blockline
```

Open the project in Android Studio, sync Gradle, and run the `app` configuration.

To build a debug APK from a terminal with Gradle 8.14.6 installed:

```bash
gradle assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

You can also open **Actions → Build APK** on GitHub to inspect build logs and download the generated artifact.

## Technology

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Kotlin Coroutines
- Android Storage Access Framework (SAF)
- Android private app storage

The dependency set is intentionally small while the foundation is being stabilized.

## Privacy and security

- Pack contents are kept in app-private storage after import.
- Phase 1 does not request the `INTERNET` permission and does not upload project files.
- There are no analytics or tracking integrations.
- Archive extraction validates destination paths to help prevent ZIP-slip.
- Export creates an archive from the selected local pack content.

When reporting a security concern, avoid posting sensitive details or private pack contents in a public issue.

## Contributing

Bug reports, reproducible test cases, and focused pull requests are welcome.

When reporting a bug, include:
- Android version and device model
- Blockline build or release tag
- Steps to reproduce
- Expected behavior and actual behavior
- Relevant logs or a minimal sample pack, after removing private information

For feature requests, describe the problem first and the smallest workflow that would solve it.

## License

A license file is not currently included. Until the maintainer adds one, do not assume that public visibility grants permission to redistribute or reuse the source.

---

<div align="center">

Made for creators building Minecraft Bedrock add-ons on mobile.

[Website](https://slgamervn.github.io/Blockline/) · [Releases](https://github.com/slgamervn/Blockline/releases) · [Issues](https://github.com/slgamervn/Blockline/issues)

</div>
