/**
 * Illustration: a red-brick transformer-station gable at dusk, with lit arched windows and a dartboard
 * for an attic window. Drawn from scratch for TrafonDart.
 */
export function Facade({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 320 360" className={className} role="img" aria-label="Trafon">
      <defs>
        <linearGradient id="glow" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#f6d9a6" />
          <stop offset="1" stopColor="#e58a73" />
        </linearGradient>
        <pattern id="bricks" width="16" height="8" patternUnits="userSpaceOnUse">
          <path d="M0 7.5h16M8 0v3.5M0 4v3.5M16 4v3.5M0 3.75h16" stroke="#8e1b1f" strokeWidth="0.6" opacity="0.55" />
        </pattern>
        <clipPath id="facadeClip">
          <path d="M30 352V150L160 46l130 104v202z" />
        </clipPath>
      </defs>

      {/* body + gable */}
      <path d="M30 352V150L160 46l130 104v202z" fill="#c8231f" />
      <rect x="0" y="0" width="320" height="360" fill="url(#bricks)" clipPath="url(#facadeClip)" />
      {/* corner pilasters */}
      <rect x="30" y="150" width="14" height="202" fill="#b01d1a" />
      <rect x="276" y="150" width="14" height="202" fill="#b01d1a" />
      {/* cornice and string course */}
      <path d="M18 156L160 42l142 114" fill="none" stroke="#f3e9dc" strokeWidth="5" strokeLinejoin="round" />
      <rect x="30" y="170" width="260" height="5" fill="#f3e9dc" opacity="0.9" />

      {/* oculus = dartboard */}
      <circle cx="160" cy="112" r="28" fill="#f3e9dc" />
      <circle cx="160" cy="112" r="23" fill="#170d0c" />
      <circle cx="160" cy="112" r="19" fill="none" stroke="#c8231f" strokeWidth="3" />
      <circle cx="160" cy="112" r="12" fill="none" stroke="#1f8a5b" strokeWidth="3" />
      <circle cx="160" cy="112" r="4" fill="#c8231f" />
      <circle cx="160" cy="112" r="1.6" fill="#1f8a5b" />

      {/* lit arched windows */}
      {[64, 214].map((x) => (
        <g key={x}>
          <path d={`M${x} 300v-78a21 21 0 0 1 42 0v78z`} fill="url(#glow)" />
          <path d={`M${x} 300v-78a21 21 0 0 1 42 0v78z`} fill="none" stroke="#f3e9dc" strokeWidth="3" />
          <path d={`M${x + 21} 201v99M${x} 250h42M${x} 274h42`} stroke="#f3e9dc" strokeWidth="2" />
        </g>
      ))}

      {/* arched double door */}
      <path d="M134 352v-66a26 26 0 0 1 52 0v66z" fill="#170d0c" stroke="#f3e9dc" strokeWidth="3" />
      <path d="M160 262v90" stroke="#4a2c25" strokeWidth="2" />

      {/* ground */}
      <rect x="0" y="351" width="320" height="9" fill="#24140f" />
    </svg>
  );
}
