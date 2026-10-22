import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

const schemaPath = resolve("app/src/main/assets/addon_completion_schema.json");

function fail(message) {
  throw new Error(`Invalid add-on completion schema: ${message}`);
}

function assertStringArray(value, label) {
  if (!Array.isArray(value) || value.some((item) => typeof item !== "string" || item.trim() === "")) {
    fail(`${label} must be an array of non-empty strings`);
  }
  if (new Set(value).size !== value.length) {
    fail(`${label} must not contain duplicate entries`);
  }
}

const schema = JSON.parse(await readFile(schemaPath, "utf8"));

if (schema.version !== 1) {
  fail("version must be 1");
}

assertStringArray(schema.javascriptKeywords, "javascriptKeywords");

const requiredEntries = {
  "manifest.json": {
    keys: ["format_version", "header", "modules", "uuid"],
    valueArrays: ["format_version", "type", "module_type"],
  },
  "entities/": {
    keys: ["minecraft:entity", "description", "components"],
    valueArrays: ["format_version", "components"],
  },
  "items/": {
    keys: ["minecraft:item", "description", "components"],
    valueArrays: ["format_version", "components"],
  },
  "blocks/": {
    keys: ["minecraft:block", "description", "components"],
    valueArrays: ["format_version", "components"],
  },
};

for (const [path, requirements] of Object.entries(requiredEntries)) {
  const entry = schema.files?.[path];
  if (!entry || typeof entry !== "object") {
    fail(`files.${path} is missing`);
  }

  assertStringArray(entry.keys, `files.${path}.keys`);
  for (const key of requirements.keys) {
    if (!entry.keys.includes(key)) {
      fail(`files.${path}.keys is missing required key ${key}`);
    }
  }

  for (const key of requirements.valueArrays) {
    assertStringArray(entry.values?.[key], `files.${path}.values.${key}`);
  }

  if (path === "manifest.json") {
    const contexts = {
      root: ["format_version", "header", "modules"],
      header: ["name", "description", "uuid", "version"],
      modules: ["type", "uuid", "version"],
      dependencies: ["uuid", "version"],
    };
    for (const [context, requiredKeys] of Object.entries(contexts)) {
      assertStringArray(entry.keysByContext?.[context], `files.manifest.json.keysByContext.${context}`);
      for (const key of requiredKeys) {
        if (!entry.keysByContext[context].includes(key)) {
          fail(`files.manifest.json.keysByContext.${context} is missing required key ${key}`);
        }
      }
    }
  }
}

assertStringArray(schema.js?.globals, "js.globals");
for (const owner of ["world", "system", "Player", "Entity", "Dimension", "Block", "BlockPermutation", "ItemStack"]) {
  if (!schema.js.globals.includes(owner)) {
    fail(`js.globals is missing ${owner}`);
  }
  assertStringArray(schema.js[owner], `js.${owner}`);
}

const requiredApiMembers = {
  world: ["getAimAssist"],
  Player: ["getAimAssist", "setPropertyOverrideForEntity"],
  Entity: ["getBlockStandingOn"],
  Dimension: ["getBlock", "getEntities", "setWeather"],
  Block: ["getLightLevel", "setPermutation"],
  BlockPermutation: ["resolve", "withState"],
  ItemStack: ["clone", "getLore"],
};

for (const [owner, members] of Object.entries(requiredApiMembers)) {
  for (const member of members) {
    if (!schema.js[owner]?.includes(member)) {
      fail(`js.${owner} is missing required API member ${member}`);
    }
  }
}

const previewOnlyApiMembers = {
  world: ["broadcastClientMessage", "getClock", "playMusic"],
  Dimension: ["getWeather", "getGeneratedStructures", "stopAllSounds", "stopSound", "spawnXp"],
  Player: [
    "chatDisplayName",
    "chatMessagePrefix",
    "chatNamePrefix",
    "chatNameSuffix",
    "persistentId",
    "eatItem",
    "getPing",
    "getSplitScreenSlot",
    "playMusic",
    "postClientMessage",
    "stopAllSounds",
    "stopSound",
  ],
};
for (const [owner, members] of Object.entries(previewOnlyApiMembers)) {
  for (const member of members) {
    if (schema.js[owner]?.includes(member)) {
      fail(`js.${owner}.${member} is preview-only and must not be suggested as stable`);
    }
  }
}

console.log("Validated Minecraft Bedrock add-on completion schema and stable API suggestions.");
