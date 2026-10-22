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

The original roadmap has evolved into a feature-by-feature implementation. The website roadmap and feature list describe what is currently implemented; items marked as needing QA still require checks on real Android devices and representative Bedrock packs. Passing CI confirms only that the configured automated checks and APK build passed for that commit.

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

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions validates the website, the add-on completion schema and stable Script API suggestions, and matching Android translation keys, resource types, and formatting placeholders before running unit tests and building a versioned APK. A normal push to `main` uploads a workflow artifact; it does **not** automatically create a public GitHub Release. A stable Release is created for a valid stable `vX.Y.Z` tag or when **Actions → Build APK → Run workflow** is started on `main`, and only when all four signing secrets are configured. SemVer prerelease tags (for example `v1.2.3-rc.1`) are published as prereleases and do not replace the stable latest release. Required signing secrets: `BLOCKLINE_KEYSTORE_BASE64`, `BLOCKLINE_KEYSTORE_PASSWORD`, `BLOCKLINE_KEY_ALIAS`, and `BLOCKLINE_KEY_PASSWORD`. Without signing configuration, CI uploads a debug APK artifact and deliberately skips publishing it as the latest stable release. Release assets are named `Blockline-v<version>.apk`. A debug APK signed with a different key may not install as an update over another build. The website checks cover linked assets, HTML landmarks and anchors, CSS/JavaScript syntax, website translation coverage, responsive breakpoints, and reduced-motion behavior. The Android resource check is implemented in `scripts/check-android-translations.mjs`. GitHub Actions workflow files are also parsed as YAML whenever workflow files change and on pull requests, preventing common indentation/syntax errors from silently breaking CI. See the run summary and logs to confirm the actual build type and release outcome.

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

## External creator resources

These links are references to independently maintained tools and documentation. They are **not bundled with or integrated directly into Blockline**.

### Official documentation and tools
- [Microsoft Minecraft Creator Docs](https://learn.microsoft.com/vi-vn/minecraft/creator/?view=minecraft-bedrock-stable) — primary reference for creator features, Script API, schemas, and pack formats.
- [Minecraft Creator Tools](https://mctools.dev/) — Minecraft creator tooling for web and command-line workflows.

### Editors and technical references
- [bridge. Editor](https://editor.bridge-core.app/) — browser-based add-on editing.
- [VS Code Web](https://vscode.dev/) — browser-based code editor; available file access depends on the workspace.
- [Blockbench](https://blockbench.net/) — model, texture, and animation authoring.
- [Bedrock Wiki](https://wiki.bedrock.dev/) — community-maintained Bedrock technical documentation.
- [Minecraft Wiki](https://minecraft.wiki/) — community-maintained Minecraft reference; verify details against your Minecraft Bedrock version.

### Templates and utilities
- [Bedrock Tweaks](https://bedrocktweaks.net/) — browse resource-pack tweaks and templates; do not redistribute or bundle content without checking its license.
- [MC-Tools](https://mc-tools.net/) — browser utilities for Minecraft workflows.
- [MakeBead Pixel Art Generator](https://makebead.com/minecraft-pixel-art-generator/) — image-to-pixel-art pattern and material-list generation.

External services can change independently. Verify compatibility, license terms, and download sources before applying examples or reusing third-party content. See [the technical validation and external-resource policy](docs/technical-validation-policy.md) for Blockline's source priority, validation limits, and template licensing rules.

## Contributing and bug reports

When reporting a bug, include the Blockline version, Android version/device, reproduction steps, expected behavior, actual behavior, and relevant logs. Remove private pack contents and credentials before sharing.

## License

Blockline source code is licensed under the [MIT License](LICENSE). Third-party dependencies and assets may be subject to their own licenses.

---

<div align="center">

Made for creators building Minecraft Bedrock add-ons on mobile.

[Website](https://slgamervn.github.io/Blockline/) · [Releases](https://github.com/slgamervn/Blockline/releases) · [Issues](https://github.com/slgamervn/Blockline/issues)

</div>
