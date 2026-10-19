import fs from "node:fs";

const html = fs.readFileSync(new URL("../index.html", import.meta.url), "utf8");
const errors = [];
const passed = [];
const assert = (condition, message) => {
  if (condition) passed.push(message);
  else errors.push(message);
};

const ids = [...html.matchAll(/\bid=["']([^"']+)["']/g)].map(match => match[1]);
const idSet = new Set(ids);
const duplicateIds = [...new Set(ids.filter((id, index) => ids.indexOf(id) !== index))];
assert(
  duplicateIds.length === 0,
  duplicateIds.length ? "Duplicate IDs: " + duplicateIds.join(", ") : "HTML IDs are unique"
);

const hashLinks = [...html.matchAll(/\bhref=["']#([^"']*)["']/g)]
  .map(match => match[1])
  .filter(Boolean);
const missingLinks = [...new Set(hashLinks.filter(id => !idSet.has(id)))];
assert(
  missingLinks.length === 0,
  missingLinks.length ? "Broken in-page links: " + missingLinks.join(", ") : "In-page links resolve to existing IDs"
);

const scripts = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)];
assert(scripts.length >= 2, "Expected inline interaction and language scripts are present");
for (const [index, match] of scripts.entries()) {
  try {
    new Function(match[1]);
    passed.push("Inline script " + (index + 1) + " parses");
  } catch (error) {
    errors.push("Inline script " + (index + 1) + " failed to parse: " + error.message);
  }
}

function cssBracesAreBalanced(css) {
  let depth = 0;
  let quote = "";
  let escaped = false;
  let comment = false;
  for (let index = 0; index < css.length; index++) {
    const char = css[index];
    const next = css[index + 1];
    if (comment) {
      if (char === "*" && next === "/") {
        comment = false;
        index++;
      }
      continue;
    }
    if (quote) {
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === quote) quote = "";
      continue;
    }
    if (char === "/" && next === "*") {
      comment = true;
      index++;
    } else if (char === '"' || char === "'") {
      quote = char;
    } else if (char === "{") {
      depth++;
    } else if (char === "}") {
      if (--depth < 0) return false;
    }
  }
  return depth === 0 && !quote && !comment;
}

const styleBlocks = [...html.matchAll(/<style\b[^>]*>([\s\S]*?)<\/style>/gi)];
assert(styleBlocks.length >= 2, "Stylesheets are present");
for (const [index, match] of styleBlocks.entries()) {
  assert(cssBracesAreBalanced(match[1]), "CSS block " + (index + 1) + " has balanced braces and strings");
}

const requiredSections = [
  ['id="product-story"', "Workflow story section remains in the page"],
  ['id="features"', "Features section remains in the page"],
  ['id="workflow"', "Workflow section remains in the page"],
  ['class="downloadCard"', "Download section remains in the page"],
  ["<footer>", "Footer remains in the page"]
];
for (const [needle, label] of requiredSections) assert(html.includes(needle), label);

assert(
  /content-visibility:\s*auto/.test(html) && /contain-intrinsic-size/.test(html),
  "Off-screen sections use native content-visibility with intrinsic sizing"
);
assert(
  /IntersectionObserver/.test(html) && /!\('IntersectionObserver' in window\)/.test(html),
  "Viewport observer has a safe unsupported-browser fallback"
);
assert(/prefers-reduced-motion:\s*reduce/.test(html), "Reduced-motion accessibility rules are present");
assert(
  /storyTrack\?\.addEventListener\('scroll',\s*syncStorySceneFromScroll/.test(html),
  "Mobile story swipe updates the preview"
);
assert(
  /\.hero \.eyebrow\.in-viewport[\s\S]*?animation-play-state:\s*running\s*!important/.test(html),
  "Viewport-aware motion has an explicit visible state"
);

const externalLinks = [...html.matchAll(/<a\b[^>]*>/gi)].map(match => match[0])
  .filter(tag => /\btarget=["']_blank["']/i.test(tag));
const unsafeExternalLinks = externalLinks.filter(tag => !/\brel=["'][^"']*\bnoopener\b[^"']*["']/i.test(tag));
assert(
  unsafeExternalLinks.length === 0,
  unsafeExternalLinks.length ? "New-tab links missing rel=noopener: " + unsafeExternalLinks.length : "External new-tab links include noopener"
);


assert(
  /id="premium-motion-refresh"/.test(html) &&
    /@keyframes hero-aurora/.test(html) &&
    /@keyframes orbit-breathe/.test(html),
  "Premium hero lighting and ambient depth effects are present"
);
assert(
  /:focus-visible\s*\{/.test(html) &&
    /prefers-reduced-motion:\s*reduce/.test(html),
  "Enhanced interactions retain keyboard focus and reduced-motion support"
);
assert(
  /\.mockWrap:hover \.device/.test(html) &&
    /\.feature:hover/.test(html),
  "Product mockup and feature cards provide restrained hover feedback"
);


assert(
  /id="blockline-experience-polish"/.test(html) &&
    /id="blockline-scroll-progress"/.test(html) &&
    /requestAnimationFrame\(updateProgress\)/.test(html),
  "Blockline scroll progress is requestAnimationFrame-throttled"
);
assert(
  /IntersectionObserver/.test(html) &&
    /data-bl-reveal/.test(html) &&
    /is-revealed/.test(html),
  "Viewport reveal effects use IntersectionObserver"
);
assert(
  /prefers-reduced-motion:\s*no-preference/.test(html) &&
    /prefers-reduced-motion:\s*reduce/.test(html),
  "New motion effects respect reduced-motion preferences"
);

if (errors.length) {
  console.error("Website validation failed:");
  for (const error of errors) console.error("- " + error);
  process.exitCode = 1;
} else {
  console.log("Website validation passed (" + passed.length + " checks):");
  for (const item of passed) console.log("✓ " + item);
}


assert(
  /@media\s*\(min-width:\s*1101px\)/.test(html) &&
    /@media\s*\(min-width:\s*821px\)\s*and\s*\(max-width:\s*1100px\)/.test(html) &&
    /@media\s*\(max-width:\s*820px\)/.test(html),
  "Desktop, compact laptop/tablet, and phone layouts have dedicated breakpoints"
);
assert(
  /\.links \.button\s*\{\s*display:\s*none;\s*\}/.test(html) &&
    /\.nav\.is-menu-open \.links \.button\s*\{[\s\S]*?display:\s*inline-flex/.test(html),
  "Mobile navigation keeps the header uncluttered and reveals the download action in the menu"
);
assert(
  /@media\s*\(max-width:\s*380px\)/.test(html) &&
    /\.stage-files\s*\{\s*grid-template-columns:\s*minmax\(64px,\s*\.7fr\)\s*minmax\(0,\s*1\.3fr\)/.test(html),
  "Narrow phones receive additional layout safeguards"
);
assert(
  /@media\s*\(prefers-reduced-motion:\s*reduce\)\s*\{\s*#blockline-scroll-progress\s*\{\s*display:\s*none/.test(html),
  "Decorative scroll progress is disabled for reduced-motion preferences"
);
