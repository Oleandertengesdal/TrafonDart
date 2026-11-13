// The design system, enforced (see docs/DESIGN.md). These are static checks over the source: they keep the colour
// scale, the spacing scale, the fonts, the icons and the tab titles honest as the app grows.
import { strict as assert } from "node:assert";
import { readFileSync, readdirSync, statSync, existsSync } from "node:fs";
import { join, relative } from "node:path";
import { describe, it } from "node:test";

const root = new URL("..", import.meta.url).pathname;
const src = join(root, "src");
const read = (p) => readFileSync(p, "utf8");

function walk(dir, exts) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) out.push(...walk(p, exts));
    else if (exts.some((e) => name.endsWith(e))) out.push(p);
  }
  return out;
}

const isTest = (p) => /\.test\.(ts|tsx|mjs)$/.test(p);
const code = walk(src, [".ts", ".tsx"]).filter((p) => !isTest(p));
const tsx = code.filter((p) => p.endsWith(".tsx"));
const css = read(join(src, "index.css"));
const PALETTE_FILE = join(src, "lib", "palette.ts");

// ------------------------------------------------------------------------------------------------ colour helpers

function tokens() {
  const theme = css.slice(css.indexOf("@theme"), css.indexOf("@layer base"));
  const raw = {};
  for (const m of theme.matchAll(/--color-([a-z0-9-]+):\s*([^;]+);/g)) raw[m[1]] = m[2].trim();
  const resolve = (v, depth = 0) => {
    const ref = /^var\(--color-([a-z0-9-]+)\)$/.exec(v);
    if (ref && depth < 5) return resolve(raw[ref[1]], depth + 1);
    return v;
  };
  const out = {};
  for (const [k, v] of Object.entries(raw)) out[k] = resolve(v);
  return { raw, colors: out };
}

function rgb(hex) {
  const h = hex.replace("#", "");
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16));
}
function luminance(hex) {
  const [r, g, b] = rgb(hex).map((c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
function contrast(a, b) {
  const [x, y] = [luminance(a), luminance(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
}
function hue(hex) {
  const [r, g, b] = rgb(hex).map((c) => c / 255);
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const d = max - min;
  if (d === 0) return { h: 0, s: 0 };
  let h;
  if (max === r) h = ((g - b) / d) % 6;
  else if (max === g) h = (b - r) / d + 2;
  else h = (r - g) / d + 4;
  h = (h * 60 + 360) % 360;
  const l = (max + min) / 2;
  const s = d / (1 - Math.abs(2 * l - 1));
  return { h, s };
}

const SCALES = ["brick", "stone", "lamp", "board", "dusk"];
const STEPS = ["50", "100", "200", "300", "400", "500", "600", "700", "800", "900", "950"];

// ------------------------------------------------------------------------------------------------ colour

describe("colour scale", () => {
  const { raw, colors } = tokens();

  it("has a full scale (50–950) for every hue, lightest to darkest", () => {
    for (const s of SCALES) {
      const lum = STEPS.map((step) => {
        const v = raw[`${s}-${step}`];
        assert.match(v ?? "", /^#[0-9a-f]{6}$/i, `--color-${s}-${step} must be a hex value`);
        return luminance(v);
      });
      for (let i = 1; i < lum.length; i++) assert.ok(lum[i] < lum[i - 1], `${s}-${STEPS[i]} must be darker than ${s}-${STEPS[i - 1]}`);
    }
  });

  it("defines every role colour as a step on a scale, never a loose hex", () => {
    const roles = ["night", "masonry", "masonry-2", "mortar", "trafon", "trafon-bright", "trafon-deep", "salmon", "trim", "trim-dim", "trim-faint", "board", "board-bright", "brass"];
    for (const r of roles) {
      assert.match(raw[r] ?? "", new RegExp(`^var\\(--color-(${SCALES.join("|")})-(${STEPS.join("|")})\\)$`), `--color-${r} must point at a scale step`);
    }
  });

  it("keeps text readable (WCAG AA 4.5:1) on every surface it is used on", () => {
    const surfaces = ["night", "masonry", "masonry-2"];
    for (const fg of ["trim", "trim-dim", "trim-faint", "brass", "board-bright", "salmon", "trafon-bright"]) {
      for (const bg of surfaces) {
        const c = contrast(colors[fg], colors[bg]);
        assert.ok(c >= 4.5, `${fg} on ${bg} is ${c.toFixed(2)}:1, needs 4.5:1`);
      }
    }
    // white text on the red buttons and on the lit (red) score arch
    for (const bg of ["trafon", "trafon-deep"]) assert.ok(contrast(colors.trim, colors[bg]) >= 4.5, `trim on ${bg}`);
    // dark text on the brass and green badges
    assert.ok(contrast(colors.night, colors.brass) >= 4.5, "night on brass");
  });

  it("has no purple anywhere: no violet/indigo/fuchsia/purple/pink classes, no purple hues", () => {
    for (const f of [...code, join(src, "index.css")]) {
      assert.doesNotMatch(read(f), /\b(?:bg|text|border|from|via|to|fill|stroke|ring|outline|shadow|decoration)-(?:purple|violet|indigo|fuchsia|pink)-/, relative(root, f));
    }
    const hexes = [...css.matchAll(/#[0-9a-f]{6}\b/gi)].map((m) => m[0]);
    for (const f of [PALETTE_FILE, join(root, "public", "favicon.svg"), join(root, "public", "manifest.webmanifest")]) {
      if (existsSync(f)) hexes.push(...[...read(f).matchAll(/#[0-9a-f]{6}\b/gi)].map((m) => m[0]));
    }
    for (const h of hexes) {
      const { h: deg, s } = hue(h);
      assert.ok(!(s > 0.15 && deg >= 255 && deg <= 335), `${h} is purple (hue ${deg.toFixed(0)})`);
    }
  });

  it("keeps colours out of components: no hex, rgb() or hsl() outside lib/palette.ts", () => {
    for (const f of code) {
      if (f === PALETTE_FILE) continue;
      const text = read(f);
      assert.doesNotMatch(text, /#[0-9a-fA-F]{6}\b|#[0-9a-fA-F]{3}\b(?![0-9a-fA-F])/, `${relative(root, f)} has a hex colour; use a token or lib/palette.ts`);
      assert.doesNotMatch(text, /\b(?:rgba?|hsla?)\(/, `${relative(root, f)} has an rgb()/hsl() colour`);
    }
  });

  it("uses no gradients (a data legend may opt out with an `allow-gradient` comment)", () => {
    const files = [...code, join(src, "index.css"), join(root, "public", "favicon.svg")];
    for (const f of files) {
      read(f)
        .split("\n")
        .forEach((line, i) => {
          if (line.includes("allow-gradient")) return;
          const where = `${relative(root, f)}:${i + 1}`;
          assert.doesNotMatch(line, /(?:linear|radial|conic)-gradient\(|<(?:linear|radial)Gradient|\bbg-(?:gradient|linear|radial|conic)-/, where);
          assert.doesNotMatch(line, /(?<=[\s"'`:])(?:from|via|to)-(?:[a-z]+(?:-[a-z0-9]+)*)(?:\/\d+)?(?=[\s"'`])/, where);
        });
    }
  });

  it("matches the browser chrome to the page: theme-color and the manifest use the page colour", () => {
    const { colors } = tokens();
    const html = read(join(root, "index.html"));
    const theme = /<meta name="theme-color" content="(#[0-9a-fA-F]{6})"/.exec(html)?.[1];
    assert.equal(theme?.toLowerCase(), colors.night.toLowerCase());
    const manifest = JSON.parse(read(join(root, "public", "manifest.webmanifest")));
    assert.equal(manifest.background_color.toLowerCase(), colors.night.toLowerCase());
    assert.equal(manifest.theme_color.toLowerCase(), colors.night.toLowerCase());
  });
});

// ------------------------------------------------------------------------------------------------ spacing & type

// The 4-point spacing scale: 2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64, 80, 96 px (and 0 / 1px).
const SPACING = new Set(["0", "px", "0.5", "1", "2", "3", "4", "5", "6", "8", "10", "12", "16", "20", "24"]);
const SPACING_UTIL = /(?<=[\s"'`:])-?(?:p|px|py|pt|pr|pb|pl|ps|pe|m|mx|my|mt|mr|mb|ml|ms|me|gap|gap-x|gap-y|space-x|space-y)-([0-9.]+|px)(?=[\s"'`])/g;

describe("spacing and type scales", () => {
  it("only uses steps from the spacing scale for padding, margins and gaps", () => {
    const bad = [];
    for (const f of tsx) {
      for (const m of read(f).matchAll(SPACING_UTIL)) if (!SPACING.has(m[1])) bad.push(`${relative(root, f)}: ${m[0]}`);
    }
    assert.deepEqual(bad, [], "spacing off the scale");
  });

  it("only uses font sizes from the type scale (no text-[…] one-offs)", () => {
    for (const f of tsx) assert.doesNotMatch(read(f), /\btext-\[(?:\d|clamp)/, relative(root, f));
  });

  it("declares the three families once, each with its own job", () => {
    assert.match(css, /@import "@fontsource\/young-serif/);
    assert.match(css, /@import "@fontsource-variable\/schibsted-grotesk"/);
    assert.match(css, /@import "@fontsource-variable\/big-shoulders-display"/);
    assert.match(css, /--font-display:\s*"Young Serif"/);
    assert.match(css, /--font-sans:\s*"Schibsted Grotesk Variable"/);
    assert.match(css, /--font-score:\s*"Big Shoulders Display Variable"/);
    assert.equal((css.match(/@import "@fontsource/g) ?? []).length, 3, "no other web fonts");
  });

  it("keeps the shouting out: no all-caps letter-spaced labels", () => {
    for (const f of tsx) {
      assert.doesNotMatch(read(f), /\btracking-(?:wide|wider|widest)\b/, `${relative(root, f)}: letter-spaced caps`);
    }
  });
});

// ------------------------------------------------------------------------------------------------ icons, title, favicon

// Emoji and dingbats used as icons (✓ ★ ⊗ → …). Typographic dashes, quotes, × and the like are fine.
const PICTO = /[\p{Extended_Pictographic}←-⇿⊕-⊡①-⓿■-➿⤀-⯿️]/u;

describe("icons, tab titles and favicon", () => {
  it("uses real icons, never emoji or dingbat characters", () => {
    const files = [...code, ...walk(join(src, "i18n", "locales"), [".json"]), join(root, "index.html"), join(root, "public", "manifest.webmanifest")];
    for (const f of files) {
      read(f)
        .split("\n")
        // Code comments may use an arrow (S20 → 20); only text that can reach the screen is checked.
        .forEach((line, i) => /^\s*(\*|\/\*|\/\/)/.test(line) || assert.doesNotMatch(line, PICTO, `${relative(root, f)}:${i + 1}`));
    }
  });

  it("gives every page its own tab title", () => {
    const pages = walk(join(src, "pages"), [".tsx"]);
    for (const p of pages) assert.match(read(p), /usePageTitle\(/, `${relative(root, p)} must call usePageTitle`);
  });

  it("has a tab title, an SVG favicon with a PNG fallback and an Apple touch icon", () => {
    const html = read(join(root, "index.html"));
    assert.match(html, /<title>TrafonDart<\/title>/);
    assert.match(html, /<link rel="icon" type="image\/svg\+xml" href="\/favicon\.svg"/);
    assert.match(html, /<link rel="icon" type="image\/png" sizes="32x32" href="\/icons\/favicon-32\.png"/);
    for (const f of ["favicon.svg", "icons/favicon-32.png", "icons/apple-touch-icon.png"]) assert.ok(statSync(join(root, "public", f)).size > 0, f);
    const svg = read(join(root, "public", "favicon.svg"));
    assert.match(svg, /viewBox="0 0 \d+ \d+"/);
    assert.doesNotMatch(svg, /<text|<image|href="http/, "the favicon is pure shapes, nothing external");
  });

  it("draws the favicon with colours from the scale only", () => {
    const { colors } = tokens();
    const allowed = new Set(Object.values(colors).map((c) => c.toLowerCase()));
    for (const m of read(join(root, "public", "favicon.svg")).matchAll(/#[0-9a-fA-F]{6}\b/g)) {
      assert.ok(allowed.has(m[0].toLowerCase()), `${m[0]} in favicon.svg is not on the colour scale`);
    }
  });
});
