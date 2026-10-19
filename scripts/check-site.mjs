import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const html = readFileSync(path.join(root, "index.html"), "utf8");
const markupEnd = html.search(/<script\b/i);
assert.notEqual(markupEnd, -1, "The page should contain its inline scripts.");
const markup = html.slice(0, markupEnd);

function checkBalancedBraces(source, label) {
  let depth = 0;
  let lowest = 0;
  let quote = "";
  let inComment = false;
  let escaped = false;
  for (let i = 0; i < source.length; i += 1) {
    const ch = source[i];
    const next = source[i + 1];
    if (inComment) {
      if (ch === "*" && next === "/") {
        inComment = false;
        i += 1;
      }
      continue;
    }
    if (quote) {
      if (escaped) escaped = false;
      else if (ch === "\\") escaped = true;
      else if (ch === quote) quote = "";
      continue;
    }
    if (ch === "/" && next === "*") {
      inComment = true;
      i += 1;
    } else if (ch === "'" || ch === '"') {
      quote = ch;
    } else if (ch === "{") {
      depth += 1;
    } else if (ch === "}") {
      depth -= 1;
      lowest = Math.min(lowest, depth);
    }
  }
  assert.equal(inComment, false, label + " has an unclosed comment.");
  assert.equal(quote, "", label + " has an unclosed string.");
  assert.ok(lowest >= 0, label + " closes a block before opening it.");
  assert.equal(depth, 0, label + " has unbalanced braces.");
}

const scripts = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)]
  .map((match) => match[1])
  .filter((source) => source.trim().length > 0);
assert.ok(scripts.length >= 2, "The language and interaction scripts should be present.");
scripts.forEach((source, index) => {
  assert.doesNotThrow(() => new Function(source), "Inline script " + (index + 1) + " must parse.");
});

const styleBlocks = [...html.matchAll(/<style\b[^>]*>([\s\S]*?)<\/style>/gi)].map((match) => match[1]);
assert.ok(styleBlocks.length > 0, "The page needs its styles.");
styleBlocks.forEach((source, index) => checkBalancedBraces(source, "Style block " + (index + 1)));

const ids = [...markup.matchAll(/\bid=["']([^"']+)["']/g)].map((match) => match[1]);
const uniqueIds = new Set(ids);
assert.equal(uniqueIds.size, ids.length, "HTML id attributes must be unique.");
const idSet = new Set(ids);
const missingAnchors = [...markup.matchAll(/\bhref="#([^"']+)"/g)]
  .map((match) => match[1])
  .filter((id) => id.length > 0 && !idSet.has(id));
assert.deepEqual(missingAnchors, [], "Every in-page navigation link must point to an existing id.");

assert.equal([...markup.matchAll(/data-story-go="/g)].length, 3, "The workflow needs exactly three step controls.");
assert.equal([...markup.matchAll(/data-story-step="/g)].length, 3, "The workflow needs exactly three step descriptions.");
assert.match(markup, /href=["']favicon\.svg["']/, "The site must declare its favicon.");
assert.ok(existsSync(path.join(root, "favicon.svg")), "The declared favicon file must exist.");

const messageScript = scripts.find((source) => source.includes("const messages = {"));
assert.ok(messageScript, "The bilingual website message table must exist.");
const enStart = messageScript.indexOf("    en: {");
const viStart = messageScript.indexOf("    vi: {", enStart);
assert.ok(enStart >= 0 && viStart > enStart, "English and Vietnamese message tables must both exist.");
const messageEnd = messageScript.indexOf("\n  };", viStart);
assert.ok(messageEnd > viStart, "The message table must close correctly.");

function messageKeys(source) {
  return [...source.matchAll(/^\s{6}([A-Za-z][A-Za-z0-9_]*):/gm)].map((match) => match[1]);
}
const enKeys = messageKeys(messageScript.slice(enStart, viStart));
const viKeys = messageKeys(messageScript.slice(viStart, messageEnd));
assert.deepEqual(viKeys, enKeys, "English and Vietnamese website translations must have the same keys.");

const bindingsStart = messageScript.indexOf("const bindings = [");
const bindingsEnd = messageScript.indexOf("\n  ];", bindingsStart);
assert.ok(bindingsStart >= 0 && bindingsEnd > bindingsStart, "The language bindings must be defined.");
const bindingKeys = [...messageScript.slice(bindingsStart, bindingsEnd).matchAll(/\[\s*'[^']+'\s*,\s*'([A-Za-z][A-Za-z0-9_]*)'/g)]
  .map((match) => match[1]);
const missingMessages = [...new Set(bindingKeys)].filter((key) => !enKeys.includes(key) || !viKeys.includes(key));
assert.deepEqual(missingMessages, [], "Every translated element must have an English and Vietnamese string.");

console.log("Blockline website checks passed: scripts, CSS blocks, anchors, favicon and bilingual strings.");
