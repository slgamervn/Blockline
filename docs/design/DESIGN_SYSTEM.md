# Blockline — Design system

**Status:** Shared design direction for Android and website  
**Visual direction:** Quiet, practical, creator-focused, and fast

## 1. Design intent

Blockline should look like a well-made utility used by people who work with structured files. The interface should prioritize content, paths, code, diagnostics, and actions. It must not look like a generic AI-generated dashboard.

The system deliberately avoids decorative visual effects. The current website on main uses a flat dark/light palette, system fonts, restrained borders, no decorative motion, and no gradient overlays. Preserve that direction rather than reintroducing visual effects through later redesigns.

## 2. Non-negotiable visual rules

- No gradients, radial glow, glassmorphism, backdrop blur, animated background, or floating decorative mockups.
- No decorative reveal animation, parallax, animated counters, or animated code cursor.
- No excessive shadows or stacked cards.
- Prefer solid surfaces, clear borders, simple icons, and readable text.
- Use system font stacks by default. Do not require remote font downloads for the page to render correctly.
- Motion is functional only. Respect reduced-motion preferences and keep transitions short.
- Keep density appropriate to the task: editor surfaces may be dense; project overview and settings should remain calm and spacious.

## 3. Shared semantic tokens

The website values below reflect the current main stylesheet and are the initial web reference. The Android implementation should map the same semantic roles to Material 3 ColorScheme values; it does not need to reuse every hexadecimal value blindly.

### Dark palette (website baseline)
| Token | Value | Intended use |
|---|---|---|
| background | #111411 | Main page background |
| surface | #171b17 | Raised panels and navigation surfaces |
| surface-subtle | #202620 | Selected rows and low-emphasis areas |
| text-primary | #e9ede7 | Main text |
| text-muted | #b0b8ae | Supporting text |
| text-subtle | #858e83 | Metadata and low-priority labels |
| border | #303830 | Default dividers and outlines |
| border-strong | #515d51 | Focused or higher-contrast boundaries |
| accent-primary | #478f5e | Main action and selected state |
| accent-secondary | #8bb991 | Emphasis and positive state |
| accent-warning | #bd965a | Warning and version metadata |

### Light palette (website baseline)
| Token | Value | Intended use |
|---|---|---|
| background | #f5f5f1 | Main page background |
| surface | #ffffff | Panels and navigation |
| surface-subtle | #edf0e9 | Selected rows and subtle backgrounds |
| text-primary | #202820 | Main text |
| text-muted | #525d52 | Supporting text |
| text-subtle | #707a70 | Metadata and low-priority labels |
| border | #d9dfd6 | Default dividers and outlines |
| border-strong | #a8b3a5 | Stronger boundaries |
| accent-primary | #34784a | Main action and selected state |
| accent-secondary | #467f52 | Emphasis and positive state |
| accent-warning | #8d682f | Warning and version metadata |

Use semantic names in application code. Never use the warning accent as the only signal of a warning; pair it with an icon or label.

## 4. Typography

- Website and general app UI: system UI stack with platform-native rendering.
- Code and file metadata: platform monospace stack.
- Avoid adding a display font solely for decoration.
- Use a small, consistent type scale: page title, section title, body, supporting text, label, metadata.
- Body text should prioritize legibility over tight tracking.
- Avoid excessive uppercase and overly compressed headlines.
- File names, paths, and code must remain legible at Android system font scaling settings.

Suggested roles:
| Role | Guidance |
|---|---|
| Screen title | 22–28sp app / responsive web heading |
| Section title | 18–22sp app |
| Body | 14–16sp app |
| Supporting text | 12–14sp app, with adequate contrast |
| Code | 13–15sp baseline; user-adjustable within safe bounds |
| Metadata | 11–12sp, never used for essential instructions |

## 5. Spacing, shape, and surfaces

Use a 4dp/4px base rhythm. Preferred spacing steps are 4, 8, 12, 16, 24, and 32. Use larger gaps only between major sections.

- App touch targets should normally be at least 48dp.
- Website controls should have a comfortable tap area, especially on narrow screens.
- App cards are reserved for meaningful groups or summaries, not every list item.
- Use flat surfaces with one clear border level.
- Keep corner rounding modest and consistent; the existing website baseline is approximately 8px.
- Use shadows only when needed to explain elevation. The default is no shadow.
- Dividers should be subtle but visible; do not use borders around every line of text.

## 6. Component patterns

### Navigation
- Keep the five-destination app navigation stable unless usability tests demonstrate a better information architecture.
- Keep the selected destination obvious through color and/or an indicator, not motion.
- Website navigation should remain short, keyboard accessible, and easy to operate with one hand on mobile.
- Mobile navigation must have a reliable close action, focus handling, Escape behavior where relevant, and no hidden page-scroll trap.

### Buttons
- One primary action per screen or workflow step.
- Primary buttons use the primary accent with a readable foreground color in both themes.
- Secondary actions use outline or neutral surfaces.
- Destructive actions must be visually distinct and require confirmation when the result is irreversible.
- Disabled, loading, success, and error states must be explicit. Prevent duplicate submissions while an operation is running.

### Lists and project rows
- Prefer clear rows over ornamental cards.
- Keep leading icons simple and consistent.
- Show the information needed to choose an item: name, type, path, and relevant status.
- Preserve selection and scroll position during refreshes and navigation.

### Editor
- Use a neutral editor surface with syntax colors limited to token meaning.
- Keep selection, cursor, current line, and diagnostic markers distinct.
- Do not communicate syntax meaning by color alone; maintain readable contrast and preserve text selection.
- Use toolbars that adapt to width; move infrequent actions into an overflow menu.
- Landscape should create more editor room without turning the interface into a desktop-only multi-pane layout.

### Validation
- Error, warning, and information each use both an icon/label and an accessible text description.
- Put file path and line information close to the message.
- Use plain language for the suggested fix.
- A score or badge must never replace the actual issue list.

### Website sections
- Use a consistent content width and generous but controlled section spacing.
- Keep the first viewport focused on the product promise, real app evidence, and one primary download action.
- Avoid large decorative phone mockups that conceal the true product UI.
- Use real, current screenshots only; label concept mockups as concepts.

## 7. Responsive behavior

### Android phone portrait
- Single primary content column.
- Bottom navigation remains reachable and does not obscure the keyboard or editor controls.
- Use horizontal scrolling for tabs and compact toolbars rather than shrinking labels to unreadable sizes.
- Long paths and file names wrap or ellipsize without pushing primary actions off-screen.
- Handle status/navigation bars and safe areas.

### Android landscape and tablets
- Preserve the active workflow and editor state through configuration changes.
- Allow wider editor controls and optional side-by-side panes only when they add clear value.
- Do not make landscape a required mode for basic editing.
- Avoid rebuilding a screen in a way that discards editor contents or cursor/viewport state.

### Website
- Mobile: one-column sections, navigation menu that can be opened and dismissed reliably, no horizontal page overflow.
- Tablet: use the extra width for readable columns, not denser decoration.
- Desktop: use a bounded content width and stable text line length.
- Verify at 320px, 360px, 390px, tablet portrait, and common desktop widths.

## 8. Accessibility and interaction

- Provide visible keyboard focus.
- Use semantic HTML landmarks, ordered headings, labels, and descriptive link text.
- All website functions must work without a pointer.
- Support reduced motion; current design direction is to avoid decorative animation entirely.
- Respect Android font scaling and content descriptions.
- Do not rely on color alone for selection, warning, error, or enabled state.
- Maintain readable contrast for text, focus rings, and controls in both themes.
- Test dialogs, menus, bottom sheets, and mobile navigation with screen readers and keyboard navigation.

## 9. Design QA checklist

- [ ] No gradients, blur, glass effects, or decorative motion have been reintroduced.
- [ ] Dark and light themes preserve contrast and action hierarchy.
- [ ] Primary action is obvious and not duplicated by competing CTAs.
- [ ] Mobile screens have no horizontal overflow or clipped actions.
- [ ] Editor state, selection, and scroll remain stable through navigation and rotation.
- [ ] Tap targets and focus indicators are usable.
- [ ] Empty, loading, read-only, error, and success states are designed.
- [ ] Screenshots and website demos represent the released app.
