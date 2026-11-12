# Design: "Trafon after dark"

The visual idea is a red-brick transformer station lit up at night, seen from the street. Its tall **arched windows** glow warm, and its round attic window doubles as a **dartboard**. The palette comes from the building itself (brick red, the salmon it was once painted, white window trim) and from a dartboard (board green, brass). No third-party logos or brands are used. The facade illustration and the logo mark are original SVGs.

## Tokens

Defined once in `frontend/src/index.css` (`@theme`) and available as Tailwind classes (`bg-trafon`, `text-trim-dim`, …).

| Token | Hex | Role |
|---|---|---|
| `night` | `#170D0C` | page background: warm near-black brick, not neutral grey |
| `masonry` / `masonry-2` | `#24140F` / `#321C17` | raised surfaces, hover |
| `mortar` | `#4A2C25` | borders, hairlines, chart grid |
| `trafon` / `-bright` / `-deep` | `#C8231F` / `#E0392C` / `#8E1B1F` | brand red: primary actions, the active thrower |
| `salmon` | `#E58A73` | the building's old colour: warnings, busts |
| `trim` / `-dim` / `-faint` | `#F3E9DC` / `#BFAEA3` / `#8C7A70` | text levels (faint is never used for body copy) |
| `board` / `-bright` | `#1F8A5B` / `#34B97F` | dartboard green: wins, checkout hints, doubles |
| `brass` | `#CDA349` | achievements: 180s, 100+ finishes, champions, legs won |

**Chart colours** were validated for colour-vision deficiency and contrast on the `masonry` surface: rating line `#E0392C`, 3-dart average `#D9735C`, first-9 average `#4C94CC`.

## Typography

- **Big Shoulders Display**, a condensed, industrial signage face, for headings and big score numerals. It's set in caps with tabular figures (the `.numerals` utility).
- **Schibsted Grotesk**, designed for the Norwegian media house Schibsted, for body text and small numbers (the `.tnum` utility gives tabular figures in the body face).
- Both are self-hosted via Fontsource, so no Google Fonts requests are made and the CSP stays strict.

## The signature element

`.arch` gives a box an arched-window shape: an elliptical top and square sill corners. It frames:

- each player's remaining score (`ArchScore`). The active thrower's window is "lit", with a red frame and warm glow
- live-match cards on the home page
- the rating plaque on a profile and the champion plaque on a tournament
- the reigning Player of the Month, in a brass window instead of a red one

### Award medals

Monthly awards reuse the window as a small medal (`features/awards/Medal.tsx`): a round-topped outline with one Lucide icon inside. Each award keeps one colour everywhere it appears (awards page, hall of fame, trophy cabinet):

| Award | Icon | Colour |
|---|---|---|
| Player of the Month | Crown | brass, the only champion colour |
| Climber of the Month | TrendingUp | board green |
| Most active | Activity | salmon |
| 180 master | Target | facade red |
| Best finish | Zap | window trim |

The medal colour is never the only cue: every medal has the award name next to it or as its accessible label. A crown after a name in a list always means "reigning Player of the Month" and nothing else.

Everything around the arches stays quiet: flat panels, hairline borders, no gradients beyond the lit window.

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
- Colour is never the only signal: wins and losses also show S/T or W/L, and a provisional rating also shows `~` plus screen-reader text.
- There is a skip-to-content link, and `<html lang>` follows the UI language.
