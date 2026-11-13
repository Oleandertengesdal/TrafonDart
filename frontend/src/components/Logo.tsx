/**
 * The mark: Trafon itself, simplified until it still reads at 16 px – the red building with its low arched gable and
 * white trim, a dartboard where the oval attic window is, two dark doors with lit fanlights and the grey stone base.
 * Original artwork. Same geometry as public/favicon.svg; keep the two in step.
 */
export function LogoMark({ className = "size-9" }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" className={className} aria-hidden="true">
      {/* gable: dark cap edge, white trim, red face */}
      <path d="M3.6 17.5A17.2 17.2 0 0 1 36.4 17.5Z" className="fill-stone-800" />
      <path d="M4.6 17.5A16.2 16.2 0 0 1 35.4 17.5Z" className="fill-stone-50" />
      <path d="M6.6 17.5A14.2 14.2 0 0 1 33.4 17.5Z" className="fill-brick-500" />
      {/* the attic window, as a dartboard */}
      <circle cx="20" cy="12.6" r="3.9" className="fill-stone-50" />
      <circle cx="20" cy="12.6" r="2.9" className="fill-stone-950" />
      <circle cx="20" cy="12.6" r="2" className="fill-board-500" />
      <circle cx="20" cy="12.6" r="0.9" className="fill-brick-500" />
      {/* body and cornice */}
      <rect x="6" y="18" width="28" height="15.5" className="fill-brick-500" />
      <rect x="4" y="16.5" width="32" height="2.4" rx="0.6" className="fill-stone-50" />
      {/* doors with lit fanlights */}
      <path d="M10.5 33.5V26.5a2.5 2.5 0 0 1 5 0V33.5ZM24.5 33.5V26.5a2.5 2.5 0 0 1 5 0V33.5Z" className="fill-stone-950" />
      <path d="M11.4 26.5a1.6 1.6 0 0 1 3.2 0ZM25.4 26.5a1.6 1.6 0 0 1 3.2 0Z" className="fill-lamp-200" />
      {/* stone base */}
      <rect x="5" y="33.5" width="30" height="3" rx="0.6" className="fill-stone-500" />
    </svg>
  );
}

export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <span className="flex items-center gap-3">
      <LogoMark />
      {!compact && (
        <span className="font-display text-2xl leading-none text-trim">
          TrafonDart
        </span>
      )}
    </span>
  );
}
