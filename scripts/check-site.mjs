import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const html = readFileSync(path.join(root, "index.html"), "utf8");
const cssPath = path.join(root, "assets/css/style.css");
const jsPath = path.join(root, "assets/js/main.js");
const css = readFileSync(cssPath, "utf8");
const js = readFileSync(jsPath, "utf8");

assert.match(html, /<html\b[^>]*\blang=["']vi["']/i, "The document declares its default language.");
assert.match(html, /name=["']viewport["']/i, "The page declares a mobile viewport.");
assert.match(html, /href=["']assets\/css\/style\.css(?:\?[^"']*)?["']/i, "The external stylesheet is linked, optionally with a cache-busting query.");
assert.match(html, /src=["']assets\/js\/main\.js(?:\?[^"']*)?["'][^>]*defer\b/i, "The external interaction script is deferred, optionally with a cache-busting query.");
assert.ok(existsSync(path.join(root, "favicon.svg")), "The declared favicon file exists.");

const localResources = [...html.matchAll(/\b(?:href|src)=["']([^"']+)["']/gi)]
  .map((match) => match[1].trim())
  .filter((value) => value && !/^(?:[a-z][a-z0-9+.-]*:|\/\/|#)/i.test(value));
for (const resource of new Set(localResources)) {
  let pathname;
  try {
    pathname = decodeURIComponent(new URL(resource, "https://blockline.local/").pathname);
  } catch {
    assert.fail(`A local resource URL is malformed: ${resource}`);
  }
  const resolved = path.resolve(root, `.${pathname.startsWith("/") ? pathname : `/${pathname}`}`);
  assert.ok(
    resolved.startsWith(root + path.sep) && existsSync(resolved),
    `The local HTML resource exists and stays inside the site root: ${resource}`
  );
}
assert.ok(/<main\b/i.test(html), "The page has a main landmark.");
assert.ok(/id=["']main["']/.test(html), "The skip-link target exists.");
assert.ok(/prefers-reduced-motion:\s*reduce/.test(css), "Reduced-motion styles are present.");
assert.ok(/prefers-reduced-motion/.test(js), "JavaScript checks the reduced-motion preference.");

const markup = html.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, "");
const ids = [...markup.matchAll(/\bid=["']([^"']+)["']/g)].map((match) => match[1]);
assert.equal(new Set(ids).size, ids.length, "HTML id attributes must be unique.");
const idSet = new Set(ids);
const missingAnchors = [...markup.matchAll(/\bhref=["']#([^"']+)["']/g)]
  .map((match) => match[1])
  .filter((id) => id.length > 0 && !idSet.has(id));
assert.deepEqual(missingAnchors, [], "Every in-page link must point to an existing HTML id.");

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

checkBalancedBraces(css, "External stylesheet");
assert.doesNotThrow(() => new Function(js), "The external interaction script must parse.");
console.log("Blockline site checks passed: external assets, landmarks, anchors, CSS/JS syntax and reduced motion.");
