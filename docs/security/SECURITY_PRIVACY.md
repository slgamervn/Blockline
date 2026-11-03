# Blockline — Security and privacy requirements

**Status:** Product security baseline and implementation checklist  
**Applies to:** Android app, website, CI, release assets, support reports

## 1. Privacy commitment

Normal Blockline project work should remain on the user's device. The app should not upload pack contents, source files, manifests, or diagnostic results as part of ordinary editing, validation, backup, or export.

Any future data collection, remote processing, crash-report submission, or improvement program must be:
- off by default,
- described in plain language,
- limited to the minimum required data,
- explicitly enabled by the user,
- revocable,
- documented with purpose, destination, retention, and deletion behavior.

Do not introduce analytics or telemetry merely because a library makes it easy.

## 2. Data classification

| Data | Default handling | Network behavior |
|---|---|---|
| Pack files and source code | Local project storage | Never uploaded by normal workflows |
| Project names and local metadata | Local app storage | Not uploaded by default |
| Validation findings | Local to the project | Not uploaded by default |
| Script API package metadata/declarations | Cached locally after explicit online fetch | Request only the selected public source |
| Release metadata and APK | Public release source | Download only after user action or an explicitly initiated update check |
| Bug report and logs | User-reviewed before submission | User chooses what to share |
| Preferences | Local app storage | No synchronization by default |

A support form must not silently attach a project, file tree, source code, private paths, or full logs.

## 3. Android file security

### Import and archives
- Treat every archive entry name and file payload as untrusted.
- Normalize relative paths before writing.
- Reject absolute paths, traversal segments, invalid destination paths, and entries escaping the selected project root.
- Enforce configurable limits for archive size, entry count, nesting depth, decompressed bytes, and per-file size.
- Detect duplicate paths and case-collision behavior on the target filesystem.
- Avoid following archive-provided links or special file types.
- Surface a clear error and leave the existing project untouched when import fails.

### Editing and writes
- Validate the target path again at write time; do not rely on validation performed only when the file was opened.
- Use temporary files and atomic replacement where supported.
- Keep a known-good backup before destructive multi-file operations.
- Handle insufficient storage, permission loss, provider failure, and process termination without falsely reporting success.
- Ensure a read-only or partially loaded document can never replace the original full file.
- Avoid logging source contents or full private paths.

### SAF permissions
- Request only the access flags needed for the user's selected action.
- Persist URI permission only when the provider offers it and the product truly needs ongoing direct access.
- Explain when a moved/deleted external document requires the user to pick it again.
- Respect provider capability flags instead of assuming every provider supports the same operations.

## 4. Update and release security

- Fetch release data only from the configured official repository/release source.
- Validate the release channel and reject a test asset for a stable update flow.
- Verify the expected Android package/application identity.
- Enforce a valid version transition and reject downgrade unless an explicit supported recovery policy exists.
- Verify the published SHA-256 digest when available.
- Verify signing identity through Android package/install behavior and the app's expected signing policy; a digest alone does not prove publisher identity.
- Do not install or bypass Android's confirmation flow automatically.
- Fail closed on malformed metadata, package mismatch, missing required verification data, or unexpected asset name.
- Never put signing credentials in source, website JavaScript, logs, or public artifacts.

Stable and test release channels must remain visibly and technically separate. CI must not silently publish a debug-signed artifact as the stable release.

## 5. Website security

- Serve over HTTPS.
- Keep browser JavaScript free of secrets and private credentials.
- Avoid inline event handlers and unsafe dynamic HTML insertion.
- Escape untrusted strings before inserting them into the DOM.
- Validate external links and use safe new-window behavior.
- Keep third-party scripts and remote resources to a minimum.
- Evaluate a Content Security Policy compatible with the actual GitHub Pages hosting model. Do not add a configuration file or response header that the host does not support.
- Keep dependency and script sources explicit; remove unused external runtime dependencies.
- Do not let a failure to fetch release metadata block access to the official Releases page.

The site is static and does not currently need authentication or server-side input handling. If that changes, perform a new threat model before implementation.

## 6. CI and repository security

- Keep release secrets only in GitHub Actions secrets.
- Limit workflow permissions to the minimum required for checkout, build, artifact upload, and release publication.
- Pin or review third-party actions and monitor upstream changes.
- Validate workflow YAML when workflows change.
- Run website checks, Android unit tests, resource/localization checks, and APK build in the appropriate CI jobs.
- Make the stable release job conditional on valid tag/channel and signing configuration.
- Do not print secret values or encode them into artifacts.
- Review dependency licenses before adding editor, parser, or archive libraries.

## 7. User-facing privacy and support copy

Explain the following in the app and website:
- projects are stored locally by default,
- which features require internet,
- when data leaves the device (only when the user explicitly submits/shares it),
- how update verification works and what each channel means,
- what diagnostic reports include,
- how to remove local projects and backups.

Bug-report guidance should ask for app version, Android version/device model, reproduction steps, expected/actual behavior, and relevant redacted logs. Warn users not to publish private packs, signing credentials, access tokens, or personal files.

## 8. Security acceptance checklist

- [ ] Archive extraction rejects traversal and invalid destinations.
- [ ] Failed imports and interrupted writes do not corrupt an existing project.
- [ ] Destructive and batch operations preview scope and provide recovery where practical.
- [ ] Large/read-only documents cannot be saved back as truncated data.
- [ ] Update channel, package identity, version, digest, and signing policy are checked.
- [ ] Website contains no secrets and handles release lookup failure.
- [ ] No project content is uploaded by normal workflows.
- [ ] Any future data collection is opt-in and covered by updated privacy documentation.
