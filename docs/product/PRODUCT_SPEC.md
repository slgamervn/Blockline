# Blockline — Product specification

**Status:** Product baseline and target direction  
**Applies to:** Android application and official website  
**Reviewed against repository:** 2026-11-04

## 1. Product definition

Blockline is a focused, mobile-first workspace for creating, inspecting, editing, validating, and packaging Minecraft Bedrock add-ons. The Android app is the primary creation environment. The website is the official product front door, download destination, documentation index, and support surface.

The product should feel like a practical creator tool, not a generic AI dashboard or a desktop IDE squeezed onto a phone. Every screen should make the next useful action obvious and keep the creator's files under the creator's control.

## 2. Product principles

1. **Local-first by default.** Normal pack editing, browsing, search, validation, and export happen on the device. A network connection is required only for explicitly online features such as refreshing remote Script API references or checking releases.
2. **Phone-first, not phone-only.** The Android experience must be efficient in portrait, remain usable in landscape, and adapt to tablets without copying a desktop IDE literally.
3. **One clear primary action.** Prioritize the task in front of the creator. Keep secondary actions in contextual toolbars, menus, and overflow areas.
4. **Trustworthy feedback.** Distinguish errors, warnings, informational notes, external dependencies, unsupported formats, and checks that were not performed.
5. **Safe file operations.** Preview destructive or batch changes, prevent path traversal and collisions, and provide recovery where practical.
6. **Fast by measurement.** Profile on representative Android devices and real packs. Avoid adding dependencies or visual effects without measurable benefit.
7. **Honest feature claims.** A local static check is not a Minecraft runtime test. A preview is not an in-game renderer. A roadmap item is not a shipped capability.
8. **No forced account.** Do not introduce sign-in, cloud sync, uploads, or telemetry as prerequisites for ordinary editing.

## 3. Audience and primary jobs

### New creators
- Import a sample or existing add-on.
- Understand Behavior Pack and Resource Pack structure.
- Find a manifest problem and understand how to fix it.
- Generate a starter manifest without overwriting existing work.

### Experienced creators
- Navigate large project trees quickly.
- Edit JSON, JavaScript, and mcfunction files without losing cursor, selection, undo history, or viewport position.
- Search across files, compare changes, batch rename or replace safely, and inspect dependencies.
- Export a pack in the correct format and check compatibility against a target Minecraft version.

### Maintainers
- Review a pack before release.
- Inspect UUID and manifest relationships.
- Verify generated output, file paths, references, and external dependencies.
- Keep project backups and understand what Blockline did or did not validate.

## 4. Product boundaries

### In scope now
- Native Android pack workspace.
- Local project copies, import/export, file explorer, search, editor, local validation, generators, snippets, comparison, activity history, Script API reference, and in-app update flow as implemented and verified.
- Official static website with Vietnamese/English content, product information, downloads, resources, FAQ, and support entry points.
- Stable and test release channels with explicit separation.

### Not part of the current release scope
- A browser-based full IDE or web editor.
- User accounts, cloud storage, collaborative editing, or cross-device project synchronization.
- Automatic upload of pack contents, source files, or diagnostics.
- A guarantee that a pack will load or behave correctly in Minecraft.
- Bundling third-party packs, textures, models, templates, or proprietary editor assets without permission.

Future Web Studio work must be proposed as a separate product phase with its own storage, browser support, performance, security, and import/export acceptance criteria. It must not delay core Android reliability.

## 5. Android application information architecture

The current product navigation documents five destinations. Keep this familiar structure unless usability evidence shows a concrete problem:

| Destination | Main responsibility | Primary actions |
|---|---|---|
| Projects | Project overview and recent work | Open, create, import, duplicate, rename, backup, delete |
| Explorer | Browse the selected project's files | Open, create, rename, move, favorite, preview, delete |
| Search | Find paths and text across a project | Filter, inspect matches, jump to file and line |
| Tools | Focused creator utilities | Validate, compare, generate, inspect dependencies, snippets |
| More | Preferences, references, updates, help | Settings, Script API reference, release notes, update channel, support |

The active project and active file must be visible whenever their context matters. Avoid duplicating the same action in multiple top-level destinations; contextual shortcuts may link to the canonical action.

### Projects
The first screen should answer three questions immediately: which project is active, what was opened recently, and what can be done next. A project row or card should prioritize its name, pack type, last activity, and a concise health/validation state only when that state is current.

Required states:
- First launch with no projects: one obvious Import action and a small Create sample action.
- Existing projects: recent list, search/filter when useful, and a clearly visible active project.
- Loading, import failure, invalid archive, empty project, and permission failure.
- Confirmation before irreversible deletion; explain whether the project backup is available.

### Explorer
- Show the pack tree with clear BP/RP grouping and stable path labels.
- Keep file operations close to the selected file; do not crowd the screen with persistent action buttons.
- Sort and filter predictably.
- Use stable item keys and lazy lists for unbounded trees or search results.
- Distinguish file preview from editable source and unsupported binary content.
- Preserve the current project context when navigating into and back out of a file.

### Editor
The editor is the highest-priority workflow and should receive the strongest performance and regression coverage.

**Editor layout**
- Compact top bar: back/navigation, file name, unsaved state, and overflow actions.
- Horizontally scrollable tab strip with a visible active tab and a bounded number of open tabs.
- A small breadcrumb/path line when it clarifies context.
- Main text surface with line numbers, syntax color, selection, cursor, and native keyboard behavior.
- Contextual toolbar for undo/redo, find/replace, go to line, format JSON, and save when applicable.
- Compact diagnostics state: current issue count or current validation state, never a stale green success indicator.
- Bottom/status details only when useful: file type, line/column, indentation, read-only status.

**Editor behavior**
- Preserve each tab's text snapshot, selection direction, cursor, scroll offsets, undo/redo history, and pending save state independently.
- Tab switching must not overwrite the new tab with the previous tab's native view state.
- Text input must remain responsive while syntax highlighting and validation are running.
- Perform file I/O on an I/O dispatcher and expensive parsing/tokenization on a background dispatcher.
- Cancel obsolete work and accept a result only when project ID, file path, and document revision still match.
- Highlight only the visible text window. Do not create an annotated representation of the entire large file just to render a viewport.
- Keep tokenization correct across long strings, escaped quotes, comments, Unicode, minified JSON, and viewport boundaries.
- Preserve native editing behavior: IME composition, selection handles, copy/paste, accessibility actions, hardware keyboard input, and undo/redo.
- Autosave must not report success before the write is complete. Use safe replacement and retain a recoverable prior copy for operations that can corrupt or replace data.
- For read-only or over-limit documents, explain why editing is unavailable and keep navigation/search behavior usable where supported.

**Editor commands**
- Find and replace operate on the active document first. Multi-file replacement must show a preview, affected file count, and collision/error summary before applying.
- JSON formatting must preserve valid content and provide a clear failure state for invalid JSON.
- Go to line must clamp to valid lines and must not discard unsaved editor state.
- Save and tab switching must flush the native editor snapshot before invoking ViewModel operations.
- No syntax highlighter or formatter should silently rewrite user text.

### Validation and pack health
Group findings by severity and file path. Each finding should contain:
- Stable issue code.
- Severity: error, warning, or information.
- File path and line/column when available.
- Short explanation.
- A concrete fix suggestion.
- Evidence or rule source, including target Minecraft version when relevant.

Validation must distinguish:
- Local structural checks.
- Version-specific schema checks.
- References resolved inside the current project.
- External references that may be supplied by Minecraft or another installed pack.
- Checks skipped because the file was too large, unsupported, missing, or unavailable.
- Independent Creator Tools validation and actual in-game testing.

A health score may summarize local findings for triage, but it must never be presented as a compatibility percentage or a guarantee. The issue list is the authoritative explanation.

### Generators and references
- Generators must create fresh identifiers and never overwrite an existing manifest without an explicit, reviewed confirmation.
- Keep built-in snippets short, original, and version-labeled where API behavior changes.
- Script API reference data should show package/version/source, cache status, and whether the data is online, cached, or bundled.
- Do not imply that the lightweight TypeScript declaration reader is a complete TypeScript compiler.
- Provide a useful offline fallback and an explicit refresh action.

### Settings and updates
- Keep settings grouped into Appearance, Editor, Language, and Updates/Help where the current IA allows.
- Use sensible defaults; do not expose low-value tuning switches.
- Separate Stable from Test clearly and explain the risk of test builds.
- Before installing an update, show version, channel, release notes, source, and any relevant verification result.
- Never claim a download is trusted solely because it was downloaded over HTTPS. Verify the expected package identity, version, digest when published, and signing identity before handing off to Android's installer.

## 6. Official website responsibilities

The website complements the app; it is not a second editor.

### Primary navigation
- Overview: what Blockline does and who it is for.
- Features: specific workflows with honest current-status labels.
- Experience: screenshots or short demonstrations that match the released app.
- Downloads: latest stable release and clearly separated test builds when publicly available.
- Documentation: installation, first project, editor, validation, import/export, troubleshooting, and FAQ.
- Resources: official Microsoft references first, then clearly labeled community tools.
- Support: issue reporting with a safe bug-report template.
- About and policies: project identity, license, privacy, and third-party notices.

The current single-page website can provide these sections through anchors. Split into separate pages only when content volume or navigation tests justify it.

### Website content rules
- Every download link should land on a release page, not an unverified direct APK URL.
- Feature descriptions must match the current stable app. Mark test-only, experimental, and planned items.
- Version, release date, channel, changelog, and asset links must come from one canonical release source.
- Keep Vietnamese and English content behaviorally equivalent, including labels, error states, metadata, and policy pages.
- If a resource is external, label it as an external resource and do not imply it is operated by Blockline.
- Give installation and update instructions that match Android's actual installer and permission behavior.

## 7. Content and localization

- Vietnamese is the default language for the app and website.
- English remains a supported language; do not translate technical identifiers, file extensions, API package names, or code examples.
- Use short, consistent terms for project, pack, file, folder, validation, dependency, and release channel.
- Avoid promotional claims such as “perfect,” “100% compatible,” or “professional IDE” unless objectively substantiated.
- Keep error messages actionable: what happened, what the user can do, and whether data is safe.

## 8. Product success criteria

Track quality rather than vanity metrics:
- Project import/export completes without data loss on representative pack archives.
- File browsing and search remain responsive on large project trees.
- Editor typing, cursor movement, tab switching, and scrolling remain stable on representative long JSON/JS/mcfunction files.
- No stale background result replaces a newer document's syntax or validation state.
- Autosave/recovery tests demonstrate that interrupted writes do not silently destroy the last good file.
- Stable and test update flows cannot install an unverified or wrong-channel package.
- Website users can find the latest stable release, correct setup instructions, and support entry point on a small phone screen.
- Website copy does not claim unimplemented or unverified behavior.

## 9. Evidence and product truth

The repository README describes current features, but each feature still needs the relevant automated and device-level checks. GitHub CI passing proves only the configured checks for that commit. It does not prove device compatibility, editor latency, correct behavior for every pack, or successful Minecraft runtime loading.

Use the implementation roadmap for acceptance gates and the technical validation policy for the Minecraft source hierarchy.
