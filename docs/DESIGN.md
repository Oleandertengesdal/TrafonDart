# Design: "Trafon at dusk"

TrafonDart is named after Trafon, the red transformer building from 1910 in Høyskoleparken. The look is that building on a winter darts night: the red facade with white trim, the grey stone base, the black doors and the warm lamps over them, the dusk sky behind. Everything is flat colour from one set of scales. There are no gradients, no glows and no purple.

The illustration of the building (`components/Facade.tsx`) and the logo mark (`components/Logo.tsx`) are original SVGs, drawn from photos of the real front. The only change from the building is on the two door plaques: the original has the old power company's monogram, and the drawing has a dartboard instead. No third-party logos or brands are used.

`frontend/scripts/design.test.mjs` checks the rules on this page (see [TESTING.md](TESTING.md)), so they hold as the app grows.

## Colour

### Scales

Five scales from 50 (lightest) to 950 (darkest), defined once in `frontend/src/index.css` (`@theme`). Every colour in the app is a step on one of them.

| Scale | Where it comes from | Used for |
|---|---|---|
| `brick` | the facade paint: 500 is the sunlit wall, 600 the wall in shade | primary actions, the active thrower, red text, warnings |
| `stone` | white trim (50) down to the charcoal plinth (950) | text and every surface. Warm, never red-tinted |
| `lamp` | the bulbs over the doors | highlights, 180s, high finishes, champions |
| `board` | the dartboard's green (and the park) | wins, doubles, checkouts |
| `dusk` | the winter sky | the illustration, and quiet information states |

Each scale gets darker step by step; the test checks it.

### Roles

Components use role names, never raw steps or hex values (Tailwind classes such as `bg-trafon` and `text-trim-dim`). Each role points at a scale step:

| Role | Step | Used for |
|---|---|---|
| `night` | stone-950 | the page |
| `masonry` / `masonry-2` | stone-900 / stone-800 | cards and panels / raised: hover, inputs, keys |
| `mortar` | stone-700 | borders and hairlines |
| `trafon` / `-bright` / `-deep` | brick-600 / brick-400 / brick-700 | primary actions / red text and hover / pressed and danger |
| `salmon` | brick-300 | warnings, busts |
| `trim` / `-dim` / `-faint` | stone-50 / stone-300 / stone-400 | main text / secondary text / labels and meta |
| `board` / `-bright` | board-500 / board-300 | wins / green text |
| `brass` | lamp-300 | lamplight: achievements and champions |

Every text colour has a contrast of at least **4.5:1** (WCAG AA) on `night`, `masonry` and `masonry-2`, and white text on `trafon` passes too. That is why `trafon` is brick-600 and not the lighter wall colour (white on brick-500 is only 4.2:1).

Translucent tints of a role (`bg-trafon/15`, `border-brass/40`) are fine for soft panels. A lit live match is the facade itself: a solid `trafon` arch with a white `trim` top edge.

### Charts and the heatmap

SVG charts and the dartboard heatmap need colour values in code, so they take them from `frontend/src/lib/palette.ts` and nowhere else. The chart colours (rating `#d95c48`, 3-dart average `#b8892a`, first-9 average `#4a90d9`) were checked for colour-vision deficiency and contrast on the `masonry` surface. The heatmap uses one hue in five stepped swatches, not a gradient.

## Type

| Face | Role | Notes |
|---|---|---|
| **Young Serif** (`font-display`) | headings | a sturdy serif with the warmth of 1910 lettering. One weight only (400), so headings are never bold. Sentence case, never letter-spaced |
| **Schibsted Grotesk** (`font-sans`) | everything else | designed for the Norwegian media house Schibsted. `.tnum` gives tabular figures |
| **Big Shoulders Display** (`font-score`) | the scoreboard | condensed signage numerals for scores, ratings and the keypad (`.numerals`) |

All three are self-hosted through Fontsource, so there are no Google Fonts requests and the CSP stays strict.

Sizes come from Tailwind's type scale. The only extra sizes are fluid ones for the scoreboard (`text-score-sm` to `text-score-xl`, from a phone to a TV) and `text-key` for the keypad. One-off sizes (`text-[…]`) are not allowed, and neither is letter-spaced all-caps text: labels are written in sentence case.

## Spacing and shape

- **Spacing** follows a 4-point scale: 2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64, 80 and 96 px (Tailwind steps 0.5, 1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24). Padding, margins and gaps use only these.
- **Radii**: 4 px for small things, 8 px for controls, 12 px for cards (`.panel`), 16 px for large cards.
- **Shadows** aren't used. Depth comes from the surface steps (`night` → `masonry` → `masonry-2`) and hairline `mortar` borders.

## Icons

Icons are [Lucide](https://lucide.dev) components, always with a text label next to them or an accessible name. Emoji and symbol characters (✓, ⊗, arrows) are never used in the interface: the test refuses them in code, translations and the manifest. Where darts needs its own sign, it is drawn: Cricket marks are a slash, a cross and a circled cross in SVG.

## The signature element

`.arch` gives a box the shape of Trafon's arched doors and gable: a round top and square corners at the bottom. It frames:

- each player's remaining score (`ArchScore`). The active thrower's arch is the lit facade: red with a thick white top edge
- live-match cards on the home page
- the rating plaque on a profile and the champion on a tournament
- the reigning Player of the Month, in brass

### Award medals

Monthly awards reuse the arch as a small medal (`features/awards/Medal.tsx`): a round-topped outline with one Lucide icon inside. Each award keeps one colour everywhere it appears (awards page, hall of fame, trophy cabinet):

| Award | Icon | Colour |
|---|---|---|
| Player of the Month | Crown | brass, the only champion colour |
| Climber of the Month | TrendingUp | board green |
| Most active | Activity | salmon |
| 180 master | Target | facade red |
| Best finish | Zap | window trim |

The medal colour is never the only cue: every medal has the award name next to it or as its accessible label. A crown after a name in a list always means "reigning Player of the Month" and nothing else.

## The illustration and the icons

`<Facade />` draws the front of the building: the low arched gable with its oval window in a burst of rays, the cornice and pilasters, three upper windows (two lit), the banded ground floor, the half-round window, two black doors with lit fanlights and a lamp each, the plaques with their row of insulators, the stone base and the steps. `<Facade scene />` adds the winter dusk: sky, bare trees, falling snow and snow on the ground. It appears on the login page, the "waking the server" screen, the home page when nothing is live, and the 404 pages.

The logo mark is the same building simplified until it reads at 16 px, with a dartboard where the oval window is. `public/favicon.svg` is the same drawing; `public/icons/` holds the PNG versions (32 px favicon, 180 px Apple touch icon, 192 and 512 px app icons on `night`, and a maskable 512 px icon with the mark inside the safe zone). To redraw the PNGs, render `favicon.svg` at those sizes, for example with Playwright, and keep the two in step.

## Tab titles

Every page sets its tab title with `usePageTitle` (`lib/pageTitle.ts`): the page first, the app last, so a row of tabs can be told apart. "Spillere – TrafonDart", "Kari Nordmann – TrafonDart", and a live match shows its score: "141–60 Kari mot Ola – TrafonDart".

## Errors and "not found"

Every error is explained by kind (`lib/errors.ts`), each with its own icon, title, explanation and way out (`ErrorNotice` in `components/ui.tsx`):

| Kind | When | Way out |
|---|---|---|
| offline | this device has no network | try again |
| waking | the free server is asleep or restarting (network error while online, 502–504) | try again (queries also retry by themselves) |
| session | the session has run out | log in |
| forbidden / notFound | 403 / 404, or a malformed id in the address | go home |
| rateLimited | too many tries | try again later |
| server | the server crashed on the request (reported automatically) | try again, report the bug |
| conflict / invalid | someone changed it meanwhile / the request was wrong | the error's own sentence |
| unknown | anything else | try again, report the bug |

Errors that pass on their own (offline, waking, session …) are shown calmly on `masonry`; real failures get a red tint. In forms the notice is one line under the form; when something fails to load, it is a block with buttons.

- **Session expiry:** the first request refused as "not logged in" forgets the user and sends them to the login page, which says the session ran out and brings them back to where they were.
- **Not found:** an unknown address, and a match, game, player or tournament that doesn't exist, get a "Bom på skiva" page with the illustration, a way home and a few places to go instead.
- **Offline banner:** while the device is offline, a line under the header says what you see may be out of date. On the scorer, darts are queued as before.
- **Crashes:** a page that crashes shows "Noe gikk i stykker" with reload, home and report buttons, and the crash is reported. After a new deploy an open tab can miss a code chunk; that shows "Ny versjon er ute" with a reload button instead, and is not reported as a bug.

## Motion

Motion is used only when it carries meaning:

| Moment | Treatment |
|---|---|
| Key press on the scorepad | press-scale + 8 ms haptic tick + soft click |
| Bust | the player's arch shakes, a "Bust" flash appears, a low two-tone chime plays |
| 180 / 100+ finish | brass flash card, rising chime, spoken callout |
| Leg / match won | "Game shot" card, chime, spoken callout |

`prefers-reduced-motion` disables all animation, and sound can be turned off in Settings.

## Layouts

- **Tablet landscape (the board-side device):** scores on the left, the keypad on the right, both full height. Keys are at least 56 px.
- **Phone portrait:** arches side by side at the top, then the turn strip, then a full-width keypad.
- **Desktop:** a broad dashboard: profile stat tiles, rating and average charts, head-to-head tables, the bracket.
- Bottom tab bar on phones and tablets. Top navigation from `lg` up.

## Accessibility

- All interactive elements are real buttons or links, with visible brass focus rings.
- Segmented controls use `role="radiogroup"`, toggles use `role="switch"`, and mode buttons use `aria-pressed`.
- The active player's arch has `aria-current`, and score numbers have labels.
- Colour is never the only signal: wins and losses also show S/T or W/L, a provisional rating also shows `~` plus screen-reader text, and every icon has a label.
- Errors use `role="alert"`, status lines `role="status"`.
- There is a skip-to-content link, and `<html lang>` follows the UI language.
