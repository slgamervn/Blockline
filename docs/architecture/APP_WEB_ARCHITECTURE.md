# Blockline — App and website architecture

**Status:** Architecture baseline and target guardrails  
**Repository baseline:** 2026-11-04

## 1. System overview

Blockline consists of two independently delivered surfaces:

1. **Android app:** the primary local-first environment for pack projects, file browsing, editing, validation, and export.
2. **Official website:** a static public site for product information, documentation, downloads, resources, and support.

GitHub is the source-control and release platform. Do not add a backend, account system, or cloud storage unless a future product decision demonstrates a concrete need.

## 2. Android architecture

### Current technical direction
- Kotlin.
- Jetpack Compose and Material 3 for app UI.
- ViewModel-owned screen/business state where it spans multiple UI elements.
- Repository and focused data utilities for projects, updates, validation, references, and file operations.
- Android native document picker / Storage Access Framework at import and export boundaries.
- App-private project copies for normal local editing, as described in the project README.

### Logical layers
| Layer | Responsibility | Must not do |
|---|---|---|
| Compose UI | Render state, collect user events, manage local presentation state | Perform blocking file or parsing work |
| ViewModel / state holders | Coordinate screen state and user actions; cancel obsolete work | Become the permanent home for every domain rule |
| Domain logic | Validation rules, pack relationships, file operation policy, generators | Depend on Android UI classes |
| Repositories | Project data, local files, preferences, cached references, release metadata | Leak raw mutable storage state into composables |
| Platform adapters | SAF, Android installer, package/signature checks, archive/file APIs | Bypass validation and error handling |

This is a target boundary, not an instruction to perform a large rewrite at once. Refactor only when a focused change reduces real coupling, risk, or performance cost.

## 3. Project storage and file operations

- Imported archives are copied into Blockline's project storage for normal editing. The original external file is not treated as the live working copy.
- Use the Storage Access Framework for explicit user-selected import/export and other supported external document operations.
- If a future workflow edits a provider URI directly, verify document-provider flags and persist only the URI permissions actually granted by the user.
- Never assume every document provider supports rename, delete, move, or write.
- Centralize path normalization and validate every destination before extraction, rename, move, import, restore, or batch replacement.
- Reject path traversal, absolute paths, unexpected separators, collisions, and archive entries that exceed configured safety limits.
- Keep file mutations atomic where possible. For a multi-file operation, retain a rollback plan or a recoverable backup.
- Do not load unbounded directory trees or file contents onto the main thread.

## 4. Editor pipeline

### Data flow
1. A file open request resolves the project ID and relative path.
2. Repository code checks existence, file type, encoding, and configured size policy.
3. The ViewModel publishes an immutable text snapshot and document identity.
4. The editor view receives the snapshot and maintains native text-input behavior.
5. Text changes update the active document state and schedule a debounced save.
6. Background work receives an immutable snapshot plus document identity/revision.
7. Highlighting and validation return results tagged with the source identity/revision.
8. The UI accepts a result only if project, path, and revision still match.

### Performance rules
- File reads and writes use Dispatchers.IO.
- Parsing, indexing, diffing, and validation use a background dispatcher.
- Text input, selection, scrolling, and IME composition remain on the UI thread.
- Never stringify or copy the entire editable buffer during each recomposition or scroll event.
- Build syntax checkpoints incrementally or once per debounced revision, then create spans only for the visible window.
- Long strings and comments must retain lexical context across checkpoints; do not tokenize an arbitrary middle substring as though it starts at a token boundary.
- Cancel obsolete jobs and reject stale asynchronous output.
- Keep viewport and selection state per project/file identity. Scroll events should not trigger broad screen recomposition.
- Measure memory, frame time, typing latency, scroll responsiveness, and save latency on real devices before increasing file limits.

### State ownership
- Shared editing/business state belongs in a state holder appropriate to its lifetime.
- Purely visual state can remain close to the composable that consumes it.
- Expose immutable state and events rather than a mutable repository object.
- Keep per-tab history, viewport, selection, and pending-save state aligned to one document identity.
- Define how state behaves on tab close, project switch, process death, orientation change, and file rename.

### Limits and degradation
The current README documents a read-only mode for files above 1 MiB and a configurable hard limit above which files are not loaded into the editor. Preserve explicit limits until profiling demonstrates a safe change. Do not increase limits just to make a feature appear more capable.

For large documents, degrade progressively:
- Keep project navigation available.
- Open a read-only preview when supported.
- Skip expensive syntax coloring before making input lag.
- Report which analysis was skipped.
- Avoid silently truncating content.
- Never allow a partially loaded buffer to overwrite the complete source file.

## 5. Validation and Script API references

- Keep rules versioned and explain their Minecraft target version.
- Prefer official Microsoft Creator documentation and Creator Tools for authoritative reference and independent checks.
- Keep community sources secondary and label them as such.
- Treat local static validation as diagnostic assistance, not runtime validation.
- Treat external dependencies as unresolved only after checking the supported source set and version context.
- Cache remote Script API references for offline use, record their source/version, and provide an explicit refresh path.
- Parse declarations tolerantly, preserve raw source where unsupported syntax exists, and do not claim compiler-level semantic analysis.

## 6. Website architecture

### Current deployment model
- Static HTML, CSS, and JavaScript.
- GitHub Pages hosting.
- Vietnamese and English content.
- Dark and light theme support.
- Links to GitHub Releases and support channels.

Keep this static model until a specific product requirement justifies a server or framework migration. A build system is not an improvement by itself.

### Website responsibilities
- Display accurate product features and current stable/test release links.
- Provide onboarding and troubleshooting information.
- Explain local data handling and permissions.
- Link to official Minecraft references and label community tools.
- Provide a predictable bug-report template.
- Remain usable when optional remote release metadata is unavailable.

### Runtime constraints
- No secrets or private API keys in browser JavaScript.
- No upload endpoint for user packs.
- Avoid third-party runtime dependencies and remote fonts when native browser features are sufficient.
- Defer non-critical scripts and keep the first viewport usable without waiting for optional API calls.
- If release metadata cannot load, show a safe GitHub Releases link rather than a blank download area.
- Keep query-string/cache-busting behavior intentional and tested.

## 7. App–website release integration

Use GitHub Releases and the release workflow as the canonical release source.

Required release fields:
- version and tag,
- channel: stable or test,
- publication date,
- prerelease/stable status,
- release notes,
- APK asset URL,
- published SHA-256 digest where available.

Integration rules:
- Stable website CTA points to the latest stable release page.
- Test builds are visibly labeled as test and never replace stable download links.
- The Android update flow filters to the selected channel and validates package identity, version, digest when available, and signing identity.
- Release notes should not be manually retyped in multiple places. Generate a small public metadata file from the release workflow only if direct release metadata lookup proves unreliable.
- Missing metadata must fail safely: keep the release page link available and do not fabricate a version or digest.
- Never put signing keys, passwords, tokens, or private CI variables in the website repository output.

## 8. Future Web Studio boundary

A browser-based editor is an optional later phase, not an extension of the current landing page.

Before starting it, require:
- Explicit product approval and a separate feature specification.
- Local-first browser storage and a clear export/backup model.
- Browser capability detection for file-system access, with import/export fallback.
- Web Worker processing for large archives and syntax analysis.
- Import validation and archive resource limits.
- No automatic upload or account requirement.
- A tested offline story if a PWA is justified.
- Representative mobile browser performance and memory tests.

Do not mirror the Android editor in the browser by reusing screens or assuming native filesystem access.
