# Blockline (Phase 1)

Android app (Kotlin + Jetpack Compose + Material 3) for reading, editing and exporting Minecraft Bedrock packs on a phone.

## Build the APK with GitHub Actions
1. Push this whole folder to the root of a GitHub repo (include the hidden `.github` folder).
2. Open the repo's **Actions** tab, run **Build APK** (it also runs on every push to main/master).
3. When it finishes, open the run and download the artifact **blockline-debug-apk**. Unzip it and install `app-debug.apk`.

No Gradle wrapper is committed: the workflow installs Gradle 8.10.2 itself. For local builds, open the folder in Android Studio and sync.

## Phase 1 contents
- Projects: import `.mcpack` / `.mcaddon` (SAF, zip-slip safe, nested packs split into BP/RP), sample project, recent, rename, duplicate, delete, project switcher
- Overview: parsed manifests (comments allowed), quick actions, backup / restore, export BP / RP / both
- Explorer: collapsible tree, create / rename / move / delete files and folders, favorites, recent files
- Editor: JSON / `.mcfunction` / JS highlight, multi-tab, undo / redo, comment-preserving JSON format, find, autosave
- Settings: theme (dark / light / system), editor font size; UI in English and Vietnamese

## Known limits in Phase 1
- Project metadata is stored in a small JSON index (not Room/Hilt yet).
- Highlighting runs over the whole text (disabled above 200 KB); files over 1 MB open read-only.
- Search and Tools tabs are placeholders until Phases 2 and 3.
