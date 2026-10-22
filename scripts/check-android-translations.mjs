import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const resourceFiles = [
  { locale: "en", file: path.join(root, "app/src/main/res/values/strings.xml") },
  { locale: "vi", file: path.join(root, "app/src/main/res/values-vi/strings.xml") }
];
const errors = [];

function parseResources({ locale, file }) {
  const xml = fs.readFileSync(file, "utf8").replace(/<!--[\\s\\S]*?-->/g, "");
  const entries = new Map();
  const pattern = /<(string|plurals|string-array|integer-array)\\b([^>]*?)(?:\\/>|>([\\s\\S]*?)<\\/\\1\\s*>)/g;

  for (const match of xml.matchAll(pattern)) {
    const [, type, attributes, body = ""] = match;
    const name = /\\bname\\s*=\\s*["']([^"']+)["']/.exec(attributes)?.[1];
    if (!name) {
      errors.push(`${locale}: <${type}> resource is missing its name attribute.`);
      continue;
    }
    if (entries.has(name)) {
      errors.push(`${locale}: duplicate resource name "${name}".`);
      continue;
    }
    const placeholders = (body.match(/%(?:\\d+\\$)?(?:\\.\\d+)?[sdf]|%%/g) || []).sort();
    entries.set(name, { type, placeholders });
  }
  return entries;
}

const english = parseResources(resourceFiles[0]);
const vietnamese = parseResources(resourceFiles[1]);

for (const name of english.keys()) {
  if (!vietnamese.has(name)) {
    errors.push(`vi: missing resource "${name}" from values/strings.xml.`);
    continue;
  }
  const en = english.get(name);
  const vi = vietnamese.get(name);
  if (en.type !== vi.type) errors.push(`"${name}": resource type differs (en=${en.type}, vi=${vi.type}).`);
  if (JSON.stringify(en.placeholders) !== JSON.stringify(vi.placeholders)) {
    errors.push(`"${name}": format placeholders differ (en=${en.placeholders.join(",") || "none"}, vi=${vi.placeholders.join(",") || "none"}).`);
  }
}
for (const name of vietnamese.keys()) {
  if (!english.has(name)) errors.push(`vi: resource "${name}" has no matching default resource.`);
}

if (errors.length) {
  console.error(`Android translation validation failed with ${errors.length} issue(s):`);
  errors.forEach((error) => console.error("✗ " + error));
  process.exitCode = 1;
} else {
  console.log(`Android translation validation passed: ${english.size} resource keys in English and Vietnamese; types and format placeholders match.`);
}
