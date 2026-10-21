<div align="center">

# Blockline

### A mobile-first workspace for Minecraft Bedrock creators.

**Import · Explore · Edit · Validate · Generate · Compare · Export**

[![Build APK](https://github.com/slgamervn/Blockline/actions/workflows/build-apk.yml/badge.svg?branch=main)](https://github.com/slgamervn/Blockline/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/slgamervn/Blockline?display_name=tag&color=3dba7a)](https://github.com/slgamervn/Blockline/releases/latest)

[Download APK](https://github.com/slgamervn/Blockline/releases/latest) · [Project website](https://slgamervn.github.io/Blockline/) · [Report a bug](https://github.com/slgamervn/Blockline/issues)

[Tiếng Việt](README.vi.md)

</div>

---

## Overview

Blockline is an Android-native workspace for inspecting and editing **Minecraft Bedrock add-on packs** on a phone. It uses Kotlin, Jetpack Compose, and Material 3, with local project storage and Android's Storage Access Framework for importing and exporting files.

The repository has working implementations across the original four development phases. This is still an actively developed project: passing CI means the automated checks and APK build passed for that commit; it does not replace testing on real Android devices or validating every possible Bedrock schema.

## Features

### Project workflow
- Import `.mcpack`, `.mcaddon`, and ZIP-compatible pack archives through Android's document picker.
- Keep project copies in app-private storage; create sample projects, switch projects, rename, duplicate, and delete them.
- Browse pack manifests and metadata, and create local backups or restore the latest backup.
- Export a Behavior Pack, Resource Pack, or combined add-on, with a backup before export.
- ZIP extraction validates destination paths to mitigate ZIP-slip and handles archive failures without trusting archive paths.

### Explorer and editor
- Browse and manage project files and folders; create, rename, move, delete, favorite, and revisit recent files.
- Preview recognized Bedrock resources and source files without pretending to render them in-game.
- Edit JSON, JavaScript, and `.mcfunction` files with multiple tabs, undo/redo, autosave, search, and comment-preserving JSON formatting.
- Large-file safeguards: files above 1 MiB are opened read-only; files above the app's configured hard limit are not loaded into the editor.
- The editor uses lightweight syntax coloring, not a complete language server or full IDE parser.

### Search, validation, and pack health
- Search project paths and text contents with file-type filters; inspect matching lines and open results in the editor.
- Validate JSON syntax, manifest fields, UUID shape and duplication, script entry paths, dependency metadata, and selected resource references.
- Review localized issue reports and a heuristic pack-health score. This score is a triage aid, not a guarantee that Minecraft will load a pack.
- Dependency analysis distinguishes references found inside the current project from external dependencies that may be provided by Minecraft or another installed pack.

### Generators and snippets
- Generate a Behavior Pack or Resource Pack manifest with fresh UUIDs; existing manifest files are not overwritten by the generator.
- Browse and copy built-in JavaScript Script API and `.mcfunction` snippets.
- Use the bundled sample pack as a starting point.

### Advanced workflow and Script API reference
- Compare text using a line-diff engine and review a local per-project activity log. Check for stable APK updates from the official GitHub Releases page.
- Preview literal batch file-name renames and text replacements before applying them, with collision checks, backups, and rollback-aware project operations.
- Inspect manifest dependencies, their source and target, version mismatches, and unresolved external UUIDs.
- Browse Script API TypeScript declarations for supported npm packages. The reference repository can fetch package metadata from npm and declarations from jsDelivr, cache versions and source for offline reuse, and fall back to bundled snapshots. Parsed symbols and members retain raw declarations for constructs the lightweight parser does not recognize.

## Navigation

The app uses five main destinations: **Projects, Explorer, Search, Tools, and More**. Theme and editor-size settings are available in the app. Most pack editing tasks work locally; Script API online refresh requires an internet connection, while cached and bundled reference data can be used offline.

## Build and test

Requirements:
- Android Studio with Android SDK Platform 35
- JDK 17
- Gradle 8.14.6
- Android 8.0 (API 26) or newer to run the app

```bash
git clone https://github.com/slgamervn/Blockline.git
cd Blockline
gradle testDebugUnitTest
gradle assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions runs unit tests before building and publishing a versioned APK. Open **Actions → Build APK** to inspect logs and download build artifacts. Release assets are named `Blockline-v<version>.apk`; a signed release APK requires repository signing secrets, otherwise the workflow publishes a debug-signed APK. A debug APK signed with a different key may not install as an update over another build.

## Performance and reliability principles

Blockline follows Android Compose guidance where it fits the app: lazy layouts for potentially large lists, stable keys for mutable collections, moving expensive work away from frequently recomposed UI, and explicit limits for large editor files. These practices reduce avoidable work but are not a substitute for profiling on representative devices.

## Privacy and security

- Imported project contents are copied to app-private storage and are not uploaded as part of normal pack editing.
- Script API refresh is an explicit network feature for npm metadata and declaration files.
- Archive extraction checks destination paths to mitigate ZIP-slip.
- Do not share private packs or sensitive logs in public bug reports.

## Current limitations

- Static validation is not a complete Mojang schema validator and cannot guarantee in-game compatibility.
- The resource preview is a metadata/source preview, not an in-game renderer.
- Script API declaration parsing is intentionally tolerant rather than a full TypeScript compiler; raw declarations are retained when the parser cannot interpret a construct.
- Device-level QA, performance profiling, and regression testing across a broad range of Android versions and pack samples remain important before treating any build as production-ready.

## Contributing and bug reports

When reporting a bug, include the Blockline version, Android version/device, reproduction steps, expected behavior, actual behavior, and relevant logs. Remove private pack contents and credentials before sharing.

## License

Blockline source code is licensed under the [MIT License](LICENSE). Third-party dependencies and assets may be subject to their own licenses.

---

<div align="center">

Made for creators building Minecraft Bedrock add-ons on mobile.

[Website](https://slgamervn.github.io/Blockline/) · [Releases](https://github.com/slgamervn/Blockline/releases) · [Issues](https://github.com/slgamervn/Blockline/issues)

</div>
