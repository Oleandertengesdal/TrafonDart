/**
 * Trafon, Klæbuveien 1 – the red 1910 building in Høyskoleparken, drawn from photos of the front, with the
 * proportions measured from a straight-on picture: the low arched gable with its white trim and oval window in a
 * burst of rays, the pilasters, the banded ground floor, two black arched doors with fanlights and a lamp over each,
 * the ornamented plaques with their row of insulators, the grey stone base and the steps. At dusk, in snow, with the
 * lights on. The plaques carry a dartboard where the original has the old power company's monogram.
 *
 * Original artwork. Every colour is a step on the scale (Tailwind fill/stroke classes, see index.css).
 */

// Measured from the photo (wall width 300 units).
const L = 50; // left edge of the wall
const R = 350; // right edge
const CORNICE = 170; // top of the cornice
const WALL_TOP = 180;
const PLINTH = 335; // top of the stone base
const GROUND = 365;
const ARCH_C = 247; // centre of the gable arches (below the cornice: a low, wide arch)
const DOORS = [131, 269];

const archAt = (r: number) => {
  const dx = Math.sqrt(r * r - (ARCH_C - CORNICE) ** 2);
  return `M${200 - dx} ${CORNICE}A${r} ${r} 0 0 1 ${200 + dx} ${CORNICE}Z`;
};

export function Facade({ className = "", scene = false, label = "Trafon" }: { className?: string; scene?: boolean; label?: string }) {
  return (
    <svg viewBox={scene ? "0 0 400 400" : "30 80 340 300"} className={className} role="img" aria-label={label}>
      <defs>
        <pattern id="trafon-hatch" width="4" height="4" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
          <path d="M0 0v4" className="stroke-brick-700" strokeWidth="1.2" />
        </pattern>
      </defs>

      {scene && <Scenery />}

      {/* the roof behind the gable, its ends showing at the shoulders, with snow */}
      <path d={`M${L - 8} ${CORNICE}L${L + 6} ${CORNICE - 9}H${R - 6}L${R + 8} ${CORNICE}Z`} className="fill-stone-800" />
      <path d={`M${L - 2} ${CORNICE - 5}L${L + 6} ${CORNICE - 10}H${L + 26}V${CORNICE - 5}ZM${R - 26} ${CORNICE - 10}H${R - 6}L${R + 2} ${CORNICE - 5}H${R - 26}Z`} className="fill-stone-50" />

      {/* the gable: metal cap, white trim, red face */}
      <path d={archAt(153)} className="fill-stone-800" />
      <path d={archAt(149)} className="fill-stone-100" />
      <path d={archAt(139)} className="fill-brick-500" />
      {/* recessed hatched panels either side of the window */}
      <path d="M100 168H166V124Q128 136 100 168Z" fill="url(#trafon-hatch)" />
      <path d="M300 168H234V124Q272 136 300 168Z" fill="url(#trafon-hatch)" />
      {/* the burst of rays round the oval window */}
      <g className="stroke-brick-700" strokeWidth="1.1">
        {Array.from({ length: 13 }, (_, i) => {
          const a = (Math.PI * (i + 1)) / 14;
          return <path key={i} d={`M${200 + Math.cos(a) * 24} ${136 - Math.sin(a) * 16}L${200 + Math.cos(a) * 44} ${136 - Math.sin(a) * 30}`} />;
        })}
      </g>
      {/* the oval window, lit */}
      <ellipse cx="200" cy="136" rx="19" ry="12.5" className="fill-stone-900" />
      <ellipse cx="200" cy="136" rx="15.5" ry="9.5" className="fill-lamp-200" />
      <path d="M200 126.5v19" className="stroke-stone-900" strokeWidth="1.8" />

      {/* the wall and the cornice */}
      <rect x={L} y={WALL_TOP - 2} width={R - L} height={PLINTH - WALL_TOP + 2} className="fill-brick-500" />
      <rect x={L - 8} y={CORNICE} width={R - L + 16} height="9" className="fill-stone-100" />
      <rect x={L - 8} y={CORNICE + 9} width={R - L + 16} height="1.6" className="fill-stone-300" />

      {/* pilasters, each with a shadowed edge */}
      {[
        [L, 15],
        [156, 13],
        [231, 13],
        [R - 15, 15],
      ].map(([x, w]) => (
        <g key={x}>
          <rect x={x} y={WALL_TOP} width={w} height="88" className="fill-brick-600" />
          <rect x={x + w - 1.4} y={WALL_TOP} width="1.4" height="88" className="fill-brick-700" />
        </g>
      ))}
      {/* small vents under the cornice */}
      <g className="fill-brick-800">
        <rect x="70" y="186" width="3.5" height="3.5" />
        <rect x="77" y="186" width="3.5" height="3.5" />
        <rect x="319.5" y="186" width="3.5" height="3.5" />
        <rect x="326.5" y="186" width="3.5" height="3.5" />
      </g>

      {/* upper floor: the middle and right windows are lit */}
      <Window x={101} y={189} w={30} h={33} lit={false} />
      <Window x={183.5} y={187} w={33} h={45} rows={3} lit dartboard />
      <Window x={269} y={189} w={30} h={33} lit />

      {/* the banded ground floor */}
      <g className="fill-brick-700">
        {Array.from({ length: 9 }, (_, i) => (
          <rect key={i} x={L} y={272 + i * 7.4} width={R - L} height="1.2" />
        ))}
      </g>

      {/* the half-round window between the doors, lit */}
      <path d="M182 286a18 18 0 0 1 36 0z" className="fill-stone-900" />
      <path d="M185.5 286a14.5 14.5 0 0 1 29 0z" className="fill-lamp-300" />
      <g className="stroke-stone-900" strokeWidth="1.4">
        {[36, 72, 108, 144].map((d) => (
          <path key={d} d={`M200 286L${200 + Math.cos((d * Math.PI) / 180) * 14.5} ${286 - Math.sin((d * Math.PI) / 180) * 14.5}`} />
        ))}
      </g>
      <rect x="180" y="286" width="40" height="2.5" className="fill-stone-100" />

      {DOORS.map((c) => (
        <Door key={c} c={c} />
      ))}

      {/* the stone base, with a cellar grille */}
      <rect x={L - 1} y={PLINTH} width={R - L + 2} height={GROUND - PLINTH} className="fill-stone-600" />
      <rect x={L - 1} y={PLINTH} width={R - L + 2} height="1.6" className="fill-stone-500" />
      <rect x="189" y="344" width="22" height="9" className="fill-stone-800" />
      <g className="fill-stone-600">
        {[194, 199.5, 205].map((x) => (
          <rect key={x} x={x} y="344" width="1.3" height="9" />
        ))}
      </g>

      {/* steps up to each door: snow on the treads, lamplight on the snow */}
      {DOORS.map((c) => (
        <g key={c}>
          {[0, 1, 2, 3].map((i) => (
            <g key={i}>
              <rect x={c - 20 - i * 6} y={PLINTH + i * 7.5} width={40 + i * 12} height="7.5" className="fill-stone-500" />
              <rect x={c - 20 - i * 6} y={PLINTH + i * 7.5} width={40 + i * 12} height="1.8" className="fill-stone-50" />
            </g>
          ))}
          <ellipse cx={c} cy={PLINTH + 6} rx="24" ry="8" className="fill-lamp-200" opacity="0.2" />
        </g>
      ))}
      {!scene && <rect x="30" y={GROUND} width="340" height="5" rx="2.5" className="fill-stone-800" />}
    </svg>
  );
}

/** Winter dusk in the park: sky, bare trees, falling snow, snow on the ground. */
function Scenery() {
  const flakes = [
    [30, 40], [84, 22], [146, 30], [262, 18], [322, 38], [372, 70], [20, 120], [374, 140], [62, 90], [340, 100],
    [14, 190], [388, 210], [44, 160], [352, 180], [110, 56], [292, 52], [196, 20], [228, 44], [168, 62],
  ];
  return (
    <g>
      <rect width="400" height="400" className="fill-dusk-700" />
      <g className="stroke-dusk-900" strokeLinecap="round" fill="none">
        {/* left tree */}
        <path d="M20 372V200" strokeWidth="5" />
        <path d="M20 300L4 270M20 282L38 250M20 258L6 222M20 236L34 196M20 214L12 180M38 250L48 236M4 270L-4 258M34 196L44 184M6 222L0 206" strokeWidth="2.6" />
        <path d="M38 250L36 234M48 236L56 232M34 196L26 186M12 180L18 168M44 184L50 172M0 206L-6 198" strokeWidth="1.4" />
        {/* right tree */}
        <path d="M380 372V186" strokeWidth="5" />
        <path d="M380 292L398 262M380 270L362 238M380 246L394 210M380 222L366 190M380 204L386 172M362 238L352 226M398 262L408 250M366 190L356 178M394 210L404 198" strokeWidth="2.6" />
        <path d="M362 238L364 222M352 226L344 222M366 190L374 180M386 172L380 160M356 178L350 168M404 198L410 188" strokeWidth="1.4" />
      </g>
      <g className="fill-dusk-200" opacity="0.75">
        {flakes.map(([x, y]) => (
          <circle key={`${x}-${y}`} cx={x} cy={y} r="1.5" />
        ))}
      </g>
      <rect y={GROUND} width="400" height={400 - GROUND} className="fill-dusk-100" />
      <rect y={GROUND} width="400" height="3" className="fill-dusk-200" />
    </g>
  );
}

function Window({ x, y, w, h, rows = 2, lit, dartboard = false }: { x: number; y: number; w: number; h: number; rows?: number; lit: boolean; dartboard?: boolean }) {
  const cols = 3;
  return (
    <g>
      <rect x={x - 2.5} y={y - 2.5} width={w + 5} height={h + 5} className="fill-stone-900" />
      <rect x={x} y={y} width={w} height={h} className={lit ? "fill-lamp-200" : "fill-dusk-600"} />
      {lit && dartboard && (
        <g>
          <circle cx={x + w / 2} cy={y + h * 0.5} r="6.5" className="fill-stone-900" />
          <circle cx={x + w / 2} cy={y + h * 0.5} r="4.8" className="fill-brick-600" />
          <circle cx={x + w / 2} cy={y + h * 0.5} r="3" className="fill-board-500" />
          <circle cx={x + w / 2} cy={y + h * 0.5} r="1.2" className="fill-brick-500" />
        </g>
      )}
      <g className="stroke-stone-900" strokeWidth="1.8">
        {Array.from({ length: cols - 1 }, (_, i) => (
          <path key={`c${i}`} d={`M${x + ((i + 1) * w) / cols} ${y}v${h}`} />
        ))}
        {Array.from({ length: rows - 1 }, (_, i) => (
          <path key={`r${i}`} d={`M${x} ${y + ((i + 1) * h) / rows}h${w}`} />
        ))}
      </g>
      <rect x={x - 4} y={y + h + 2.5} width={w + 8} height="2.2" className="fill-stone-100" />
    </g>
  );
}

/** An arched black door with a lit fanlight and the lamp above it, under its ornamented plaque. */
function Door({ c }: { c: number }) {
  const spring = 285; // where the door's arch starts
  const top = 228; // top of the plaque
  return (
    <g>
      {/* the plaque, in its recessed panel: a frame that narrows downwards, a row of insulators under the top bar,
          zigzag rays (the power station's lightning) round a half-round medallion, which holds a dartboard here */}
      <rect x={c - 24} y={top - 4} width="48" height="36" className="fill-brick-600" />
      <path
        d={`M${c - 21} ${top}H${c + 21}L${c + 17} ${top + 28}Q${c + 13} ${top + 25} ${c + 10} ${top + 24}A10 10 0 0 0 ${c - 10} ${top + 24}Q${c - 13} ${top + 25} ${c - 17} ${top + 28}Z`}
        className="fill-brick-500 stroke-stone-50"
        strokeWidth="2"
        strokeLinejoin="round"
      />
      <g className="stroke-stone-50" strokeWidth="0.8" fill="none" strokeLinejoin="round">
        {[-168, -150, -132, -114, -66, -48, -30, -12].map((d) => {
          const a = (d * Math.PI) / 180;
          const p = (r: number, k = 0) => `${c + Math.cos(a + k) * r} ${top + 24 + Math.sin(a + k) * r * 0.85}`;
          return <path key={d} d={`M${p(12.5)}L${p(15.5, 0.09)}L${p(17.5, -0.05)}L${p(21)}`} />;
        })}
      </g>
      <path d={`M${c - 10} ${top + 24}A10 10 0 0 1 ${c + 10} ${top + 24}`} className="stroke-stone-50" strokeWidth="2" fill="none" />
      <circle cx={c} cy={top + 20} r="6" className="fill-stone-50" />
      <circle cx={c} cy={top + 20} r="4.6" className="fill-stone-900" />
      <circle cx={c} cy={top + 20} r="3.4" className="fill-board-500" />
      <circle cx={c} cy={top + 20} r="1.3" className="fill-brick-500" />
      <rect x={c - 21} y={top - 1.5} width="42" height="3" rx="1.5" className="fill-stone-50" />
      <g className="fill-stone-50">
        {Array.from({ length: 8 }, (_, i) => (
          <ellipse key={i} cx={c - 15.75 + i * 4.5} cy={top + 3.4} rx="2" ry="1.9" />
        ))}
        {[
          [c - 21, top],
          [c + 21, top],
          [c - 17, top + 28],
          [c + 17, top + 28],
        ].map(([x, y]) => (
          <circle key={`${x}-${y}`} cx={x} cy={y} r="2.6" />
        ))}
      </g>

      {/* the arched recess, the door and the fanlight */}
      <path d={`M${c - 16.5} ${PLINTH}V${spring - 6}A16.5 16.5 0 0 1 ${c + 16.5} ${spring - 6}V${PLINTH}Z`} className="fill-brick-700" />
      <path d={`M${c - 13.5} ${PLINTH}V${spring}A13.5 13.5 0 0 1 ${c + 13.5} ${spring}V${PLINTH}Z`} className="fill-stone-950" />
      <path d={`M${c - 11.5} ${spring}A11.5 11.5 0 0 1 ${c + 11.5} ${spring}Z`} className="fill-lamp-200" />
      <g className="stroke-stone-950" strokeWidth="1.2">
        {[45, 90, 135].map((d) => (
          <path key={d} d={`M${c} ${spring}L${c + Math.cos((d * Math.PI) / 180) * 11.5} ${spring - Math.sin((d * Math.PI) / 180) * 11.5}`} />
        ))}
      </g>
      <rect x={c - 13.5} y={spring} width="27" height="2.2" className="fill-stone-950" />
      <g className="fill-stone-800">
        {[-7, -0.5, 6].map((dx) => (
          <rect key={dx} x={c + dx} y={spring + 6} width="0.9" height={PLINTH - spring - 8} />
        ))}
      </g>
      <rect x={c + 7} y={spring + 22} width="3.5" height="1.8" className="fill-stone-400" />

      {/* the lamp and its pool of light */}
      <circle cx={c} cy={spring - 18.5} r="9" className="fill-lamp-200" opacity="0.14" />
      <circle cx={c} cy={spring - 18.5} r="5" className="fill-lamp-200" opacity="0.3" />
      <circle cx={c} cy={spring - 18.5} r="2.6" className="fill-lamp-50" />
    </g>
  );
}
