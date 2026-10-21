# Technical validation and external resources

## Source-of-truth order

1. Microsoft Learn Minecraft Creator documentation and the official Creator Tools validation reference are the primary references for manifest structure, content schemas, Script API behavior, and supported formats.
2. Creator Tools Inspector/CLI checks should be used as an independent validation pass when available. Blockline's local validator is not a replacement for those checks.
3. Bedrock Wiki and other community references are useful for examples and practical explanations, but examples must be checked against the target Minecraft version and official documentation.
4. Third-party editors and generators are external tools. Blockline links to them; it does not embed or control their behavior.

## What Blockline validation means

Blockline currently performs local static checks, including JSON syntax, selected manifest fields, UUID shape and duplicates, script entry paths, dependency metadata, and selected resource references. These checks are designed to catch common issues early.

A clean report does **not** prove that a pack matches every Mojang schema, loads successfully in Minecraft, or behaves correctly in a world. Preview features and version-specific schema changes can also outpace the local rules. For release-quality pack validation, compare findings with the current Microsoft documentation and run Creator Tools validation where possible, then test the pack in the intended Minecraft version.

## Template and asset policy

- Prefer original examples and templates authored for Blockline.
- Do not bundle or redistribute third-party templates, textures, models, or pack contents unless the applicable license or terms explicitly allow that use.
- For Bedrock Tweaks and other external resources, link users to the source and review the current terms before copying, modifying, or redistributing content.
- Keep attribution and license information alongside any approved third-party asset. Do not assume that a public download is public domain.

## Resource directory

The app and README may link to Microsoft Creator documentation, Creator Tools, bridge., Bedrock Wiki, VS Code Web, Blockbench, Bedrock Tweaks, MC-Tools, MakeBead, and other community references. These links are independent resources, not built-in Blockline features. Their availability and behavior can change without a Blockline release.

## Change and verification checklist

When adding a new validation rule:

1. Cite the official documentation or schema that supports the rule.
2. Decide whether a finding should be an error, warning, or informational note; avoid errors for uncertain community conventions.
3. Add unit tests for valid and invalid examples, including legacy and current manifest formats where relevant.
4. Run `gradle testDebugUnitTest` and the complete GitHub Actions build workflow.
5. Check English and Vietnamese issue messages if user-visible text changes.
6. Record remaining limits; automated checks are not a substitute for testing on a real Android device and the target Minecraft version.

## Reference links

- [Microsoft Minecraft Creator documentation](https://learn.microsoft.com/en-us/minecraft/creator/?view=minecraft-bedrock-stable)
- [Manifest validation rules](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/mctoolsvalreference/chkmanif?view=minecraft-bedrock-stable)
- [Pack manifest reference](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/addonsreference/packmanifest?view=minecraft-bedrock-stable)
- [Creator Tools](https://mctools.dev/)
- [Bedrock Tweaks terms](https://bedrocktweaks.net/terms)