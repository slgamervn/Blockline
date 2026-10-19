# Blockline — Phase 1

Blockline is an Android-native, mobile-first workspace for Minecraft Bedrock pack files. The app stores imported projects in app-private storage and does not upload pack contents.

## Build
Open this repository in Android Studio and sync Gradle, or use **Actions → Build APK**. The workflow builds a debug APK and uploads it as the `blockline-debug-apk` artifact.

## Phase 1 implemented
- Import .mcpack/.mcaddon archives through Android's document picker; nested archives are expanded and behavior/resource packs are separated by manifest module type.
- Zip-slip protection during extraction.
- Project list, recent ordering, sample project, rename, duplicate and delete.
- Manifest overview, file explorer, create/rename/delete files, favorites, editor tabs, undo/redo, autosave and JSON formatting that preserves comments.
- Read-only editor mode for files larger than 1 MiB.
- Local backups and latest-backup restore.
- Export of the selected project to a .mcaddon archive.
- Dark/light/system themes and adjustable editor font size.
- Core pack operations work offline.

## Known limitations
- Project metadata uses a small private JSON index rather than Room; Hilt and Clean Architecture layers are not yet introduced.
- The Phase 1 editor has basic regex-based highlighting rather than viewport-only syntax parsing.
- Export currently provides a combined .mcaddon action; individual BP-only and RP-only export choices are not exposed in the UI yet.
- Favorite/recent metadata is stored in private preferences.
- Search currently filters file paths only; content/identifier/UUID indexing belongs to Phase 2.
- The included sample project is generated locally by the app; the repository does not yet bundle a separate sample pack under assets.

## Security and privacy
- No analytics or tracking dependencies.
- No internet permission is requested in Phase 1.
- Imports are copied into app-private storage; archive entries are checked against the extraction root before writing.
