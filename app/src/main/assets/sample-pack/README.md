# Blockline sample pack

This folder contains a minimal Behavior Pack (BP) and Resource Pack (RP) used by the in-app **Create sample project** action.

## Contents

- `BP/manifest.json` — data and Script API modules
- `BP/scripts/main.js` — small Script API example
- `BP/functions/hello.mcfunction` — basic function example
- `RP/manifest.json` — resource pack manifest
- `RP/texts/` — sample localization files

The manifest UUIDs use template tokens. Blockline replaces each token with a fresh UUID when creating a sample project, so separate sample projects do not share the same manifest identifiers.

The sample is a learning scaffold, not a production-ready add-on. Script API compatibility depends on the Minecraft Bedrock version used to run it.
