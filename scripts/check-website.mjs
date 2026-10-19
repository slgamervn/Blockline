import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";

const root = path.resolve(new URL("..", import.meta.url).pathname);
const errors = [];
const passed = [];
const assert = (condition, message) => (condition ? passed : errors).push(message);
const read = (file) => fs.readFileSync(path.join(root, file), "utf8");

const html = read("index.html");
const css = read("assets/css/style.css");
const js = read("assets/js/main.js");
assert(/<html lang="vi">/.test(html), "HTML declares Vietnamese language");
assert(/<h1\b/.test(html) && (html.match(/<h1\b/g) || []).length === 1, "Page has exactly one h1");
assert(/name="description"/.test(html) && /property="og:title"/.test(html), "SEO and Open Graph metadata exist");
assert(/assets\/css\/style\.css/.test(html) && /assets\/js\/main\.js/.test(html), "External CSS and deferred JavaScript are linked");
assert(/href="https:\/\/github\.com\/slgamervn\/Blockline\/releases\/latest\/download\/Blockline\.apk"[^>]*download/.test(html), "Latest APK release download link is present");
const apkAnchors = [...html.matchAll(/<a\b[^>]*href="https:\/\/github\.com\/slgamervn\/Blockline\/releases\/latest\/download\/Blockline\.apk"[^>]*>/g)].map((match) => match[0]);
assert(apkAnchors.length === 4 && apkAnchors.every((tag) => /\bdata-apk-link\b/.test(tag)), "All APK links support embedded-browser guidance");
assert(!css.includes("\\n.site-header{") && /\.site-header\s*\{\s*position:fixed/.test(css), "Fixed header selector is not corrupted by a literal escape");
assert(/body\.menu-open\{overflow:hidden\}/.test(css) && !/body\.menu-open\{[^}]*touch-action/.test(css), "Mobile menu locks page scrolling without blocking menu touch gestures");
assert(/\.mobile-menu\{position:fixed;[^}]*padding:calc\(88px \+ env\(safe-area-inset-top\)\)/.test(css), "Mobile menu clears the fixed header and safe-area inset");
assert(/prefers-reduced-motion:\s*reduce/.test(css), "Reduced-motion styles are present");
assert(/IntersectionObserver/.test(js), "Scroll reveal uses IntersectionObserver");
assert(/data-language-toggle/.test(html) && /const englishText = \{/.test(js), "Vietnamese and English language toggle is present");

assert(/localStorage\.setItem\('blockline-language'/.test(js) && /localStorage\.getItem\('blockline-language'/.test(js), "Language choice persists between visits");
const englishDictionary = js.match(/const englishText = \{([\s\S]*?)\n  \};/);
assert(Boolean(englishDictionary) && (englishDictionary[1].match(/"[^"\n]+"\s*:/g) || []).length >= 140, "English translation covers the website copy");
const englishKeys = new Set([...(englishDictionary?.[1] || "").matchAll(/"([^"]+)"\s*:/g)].map((match) => match[1]));
const bodyMarkup = (html.match(/<body\b[^>]*>([\s\S]*?)<\/body>/i) || [null, ""])[1];
const visibleTextNodes = [...bodyMarkup.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, "").replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, "").matchAll(/>([^<>]+)</g)]
  .map((match) => match[1].replace(/&nbsp;/g, " ").replace(/&amp;/g, "&").trim())
  .filter((value) => value && !["×", "ƒ"].includes(value) && /[À-ỹ]/.test(value));
const untranslatedVisibleText = [...new Set(visibleTextNodes)].filter((value) => !englishKeys.has(value));
assert(untranslatedVisibleText.length === 0, "All visible Vietnamese copy has an English translation");
assert(/og:locale/.test(html) && /ogLocale\.content = currentLanguage === 'en'/.test(js), "Social metadata follows the selected language");
assert(/filter:blur\(4px\)/.test(css) && /filter:none!important/.test(css), "Reveal blur respects reduced-motion preferences");
assert(/classList\.remove\('is-visible'\)/.test(js) && !/observer\.unobserve\(entry\.target\)/.test(js), "Scroll reveal exits and re-enters when scrolling in either direction");
assert(/--reveal-y/.test(css) && /scrollDirection > 0/.test(js), "Scroll reveal direction is motion-aware");

assert(/aria-expanded/.test(js) && /Escape/.test(js), "Accessible mobile navigation has keyboard support");
assert(/data-demo-tab/.test(html) && /activateDemoTab/.test(js), "Interactive demo tabs are present");
assert(/data-reading-copy/.test(html) && /reading-progress/.test(js), "Manifesto scroll highlight is present");
assert(/@media\s*\(max-width:\s*380px\)/.test(css) && /@media\s*\(max-width:\s*560px\)/.test(css), "Narrow mobile breakpoints are present");
assert(/@media\s*\(min-width:\s*1600px\)/.test(css), "Wide desktop breakpoint is present");
assert(/env\(safe-area-inset-bottom\)/.test(css) && /100svh|100dvh/.test(css), "Safe-area and dynamic viewport support are present");
assert(/zip-slip/i.test(html) || /zip-slip/i.test(js), "Import security capability is described");
assert(/1 MB/.test(html) && /5 MB/.test(html), "Editor file-size limitations are disclosed");
assert(/Không liên kết với Mojang hay Microsoft/.test(html), "Minecraft trademark disclaimer is present");
assert(!/lorem ipsum|TODO|placeholder text/i.test(html + css + js), "No lorem ipsum or TODO placeholders remain");
const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map((m) => m[1]);
assert(ids.length === new Set(ids).size, "HTML IDs are unique");
const hashes = [...html.matchAll(/href="#([^"]+)"/g)].map((m) => m[1]);
hashes.forEach((id) => assert(ids.includes(id), `Internal anchor #${id} exists`));
const scriptBlocks = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)];
scriptBlocks.forEach((match, i) => {
  try { new vm.Script(match[1], { filename: `inline-script-${i + 1}.js` }); assert(true, `Inline script ${i + 1} parses`); }
  catch (error) { assert(false, `Inline script ${i + 1} parses: ${error.message}`); }
});
try { new vm.Script(js, { filename: "assets/js/main.js" }); assert(true, "Main JavaScript parses"); }
catch (error) { assert(false, `Main JavaScript parses: ${error.message}`); }
let braces = 0, quote = "", escaped = false, comment = false;
for (let i = 0; i < css.length; i++) {
  const c = css[i], next = css[i + 1];
  if (comment) { if (c === "*" && next === "/") { comment = false; i++; } continue; }
  if (quote) { if (escaped) escaped = false; else if (c === "\\") escaped = true; else if (c === quote) quote = ""; continue; }
  if (c === "/" && next === "*") { comment = true; i++; }
  else if (c === "'" || c === '"') quote = c;
  else if (c === "{") braces++;
  else if (c === "}") braces--;
  if (braces < 0) break;
}
assert(braces === 0 && quote === "" && !comment, "CSS braces, strings, and comments are balanced");
if (errors.length) {
  console.error(`Website checks failed: ${errors.length} error(s)`);
  errors.forEach((error) => console.error("✗ " + error));
  console.log(`Passed: ${passed.length}`);
  process.exitCode = 1;
} else {
  console.log(`Website checks passed: ${passed.length}`);
  passed.forEach((message) => console.log("✓ " + message));
}
