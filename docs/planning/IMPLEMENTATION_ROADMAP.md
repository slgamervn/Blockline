# Blockline — Implementation roadmap

**Baseline reviewed:** 2026-11-04  
**Delivery rule:** branch → test/CI → device/browser QA → promotion PR → main  
**Priority order:** reliability first, feature completeness second, visual polish third

## 1. Repository findings that affect the plan

- The default branch is main.
- The test branch is currently divergent: it is one commit ahead of main and four commits behind main in the GitHub compare result. Do not force-push or replace either branch.
- Current main already contains the checkpointed syntax document and per-file editor viewport state. Treat the older editor PRs as overlapping candidates, not as missing work.
- The current website on main already uses a flat palette, system fonts, no gradients, and no decorative animation. A newer main commit titled as a website visual-effects cleanup supersedes the older direction in PR #18.
- PR #18 has passing Website validation and Pull Request validation runs on its own commit, but those checks do not make its older styling branch a better candidate than current main.
- PR #13 is a narrower editor patch and overlaps the newer checkpoint-based syntax approach.
- PR #16 contains a larger editor fix set. Before closing or merging it, compare its exact file tree against current main and verify whether any unique changes remain. The current branch/PR state is not itself proof of missing code.
- Do not merge a stale PR just to clear the PR list. Resolve each by inspecting changed files, base/head commits, CI, and current-main parity.

## 2. Phases

### P0 — Establish a trustworthy product baseline
**Goal:** keep app, website, and release information consistent.

Tasks:
- Adopt the product specification, design system, architecture, security baseline, and roadmap in this directory.
- Confirm each claimed feature against source code and tests.
- Reconcile open PRs #13, #16, and #18 using exact file-level comparison.
- Record stable/test channel behavior and signing requirements in the release checklist.
- Establish a representative Android device and sample-pack QA matrix.

Exit criteria:
- No ambiguous ownership between main and test.
- The test branch is synchronized through a reviewed PR, not force-pushed.
- Every release-critical behavior has an owner, test, and evidence requirement.
- The website describes only current stable features.

### P1 — Editor correctness and performance
**Goal:** remove data-loss and interaction regressions before adding editor features.

Required work:
- Verify tab switching, native text synchronization, cursor/selection direction, viewport restoration, and undo/redo isolation.
- Verify stale syntax/validation results are discarded by document identity and revision.
- Validate checkpoint tokenization for minified JSON, long strings, escapes, comments, Unicode, JS, and mcfunction.
- Confirm highlighting stays viewport-bounded and does not rebuild full-document annotated text during scroll.
- Verify find/replace, go-to-line, formatting, command palette, and tab close flush the current native buffer in the correct order.
- Test save cancellation, rapid edits, app backgrounding, orientation change, low-storage, and process recreation.
- Benchmark typing latency, scroll frame time, heap use, open time, save time, and validation latency against representative files.

Exit criteria:
- No lost or cross-tab text.
- No scroll rewind caused by stale restoration.
- No stale diagnostics shown as current.
- Large-file behavior follows documented size policy and never silently truncates.
- Measured performance has a recorded baseline and no critical regression.

### P2 — Project and explorer reliability
**Goal:** make everyday pack operations predictable and reversible.

Required work:
- Verify import of mcpack, mcaddon, and ZIP-compatible archives.
- Exercise malformed archives, duplicate paths, path traversal, large entries, empty projects, and low storage.
- Verify create/rename/move/delete/favorite/recent/duplicate/backup/restore and export.
- Confirm file operations are scoped to the selected project.
- Keep large trees and search results lazy and keyed by stable identity.

Exit criteria:
- Failed operations preserve the previous project state.
- User-visible progress and error messages are accurate.
- Batch operations preview their effects and recover safely.

### P3 — Validation and creator-reference quality
**Goal:** improve trust in findings, not merely increase the number of warnings.

Required work:
- Map rules to authoritative Minecraft versioned references.
- Label unsupported or skipped checks.
- Test UUID duplication, module/dependency matching, entry paths, selected resources, and malformed manifests.
- Verify distinction between in-project, vanilla, and external dependencies.
- Include source/version in Script API reference results and test offline cache fallback.
- Test local validation against Creator Tools when available and verify samples in the intended Minecraft version.

Exit criteria:
- Findings contain path, severity, explanation, and a practical remedy.
- No local clean result is described as guaranteed Minecraft compatibility.
- Version-specific behavior is traceable to a source.

### P4 — Website information and support quality
**Goal:** make the website a reliable companion to the app.

Required work:
- Maintain one clear overview and short, accurate feature explanations.
- Add or improve task-based documentation: install/update, create/import a project, navigate the pack, edit, validate, export, troubleshoot.
- Make stable download the primary action and test builds secondary with explicit labels.
- Keep Vietnamese and English content aligned.
- Add a concise privacy/security explanation and safe bug-report template.
- Keep system font fallback and the flat visual direction.

Exit criteria:
- A new user can find the stable release and complete the first-project workflow from a phone.
- No dead or misleading links.
- No page overflow at supported widths.
- Website validation, localization, and keyboard/focus checks pass.

### P5 — Release integration and operational quality
**Goal:** reduce mismatches between app, website, and release assets.

Required work:
- Ensure tag, version, channel, release notes, APK asset, and digest are consistent.
- Verify stable/test filtering in the app update flow.
- Confirm signing continuity and test installation/upgrade on physical Android devices.
- Ensure the website retains a working Releases fallback when remote metadata is unavailable.
- Keep release notes and website version data sourced from GitHub Releases or generated metadata.

Exit criteria:
- Stable and test channels cannot be confused.
- Release install/upgrade verification is complete on a device.
- Signing and digest checks are evidenced.
- Rollback/recovery steps are documented.

### P6 — Optional Web Studio discovery
Do not begin implementation until P1–P5 are stable and there is clear evidence that a browser editor solves a real user need. First produce a separate brief covering storage, browser support, offline behavior, memory limits, security, mobile layout, export, and maintenance cost.

## 3. Cross-surface acceptance checklist

### Android
- [ ] Import/export, backups, and restore preserve content.
- [ ] Editor typing, scrolling, selection, tab switching, and saves pass regression tests.
- [ ] Large-file limits and degradation are explicit and safe.
- [ ] Local validation and external references label their source and limits.
- [ ] Stable/test update flows remain separated and verify package identity.
- [ ] Portrait, landscape, small screens, font scaling, and accessibility are checked.

### Website
- [ ] Stable download and test-channel links are clearly distinguished.
- [ ] All visible content has Vietnamese and English equivalents.
- [ ] Feature claims match the released app.
- [ ] No gradients, blur, decorative animation, or remote-font dependency.
- [ ] Mobile navigation, keyboard access, focus, contrast, and overflow are checked.
- [ ] Releases fallback works if optional metadata lookup fails.
- [ ] Privacy, license, and external-resource information is easy to find.

### CI and release
- [ ] Website validation passes.
- [ ] Android unit tests pass.
- [ ] Android resource/translation checks pass.
- [ ] Workflow YAML validation passes when workflows change.
- [ ] Debug/QA/release build configuration is correct.
- [ ] Signing secrets are available only to authorized release jobs.
- [ ] Physical-device install/update and real-pack QA are recorded separately from CI.

## 4. Measurement plan

Do not introduce arbitrary “fast” claims. Record baselines and then compare:
- Editor open latency by file size.
- Input-to-render latency while typing.
- Scroll frame time and dropped frames.
- Peak memory during open, highlight, validation, and search.
- Autosave completion latency and recovery behavior.
- Archive import/export time and failure rate on representative archives.
- Search time for large project trees.
- Website Core Web Vitals on representative mobile and desktop visits.

For the website, use the current Core Web Vitals “good” thresholds as targets at the 75th percentile: LCP at or below 2.5 seconds, INP below 200 milliseconds, and CLS below 0.1. These are measurement targets, not claims that the current page already meets them.

## 5. Working agreement

- Make focused changes; avoid broad dependency upgrades during reliability fixes.
- Do not edit main directly.
- Use a dedicated feature/design branch and route changes through the test branch first.
- Before a code edit, inspect the relevant implementation and tests, then verify current official documentation and relevant design/engineering references.
- After a change, reread the complete affected files and surrounding code, run the appropriate checks, and repeat after fixes.
- Do not report a check as passed unless the command or workflow result is available.
- Keep comments short and explain non-obvious invariants, not obvious syntax.
- Do not add TODO-only implementations, fake features, or empty handlers.
- Preserve the current simple visual language; add features only when they clearly improve a real creator workflow.
