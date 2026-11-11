/**
 * The mark: the gable of a red-brick transformer station whose round attic window is a dartboard.
 * Original artwork – no third-party logos.
 */
export function LogoMark({ className = "size-9" }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" className={className} aria-hidden="true">
      <path d="M3 37V17.5L20 5l17 12.5V37z" fill="#c8231f" />
      <path d="M3 17.5L20 5l17 12.5" fill="none" stroke="#f3e9dc" strokeWidth="1.6" strokeLinejoin="round" />
      <circle cx="20" cy="19" r="7.4" fill="#f3e9dc" />
      <circle cx="20" cy="19" r="6" fill="#170d0c" />
      <circle cx="20" cy="19" r="4.1" fill="none" stroke="#1f8a5b" strokeWidth="1.3" />
      <circle cx="20" cy="19" r="1.5" fill="#c8231f" />
      <path d="M11 37v-6.5a3 3 0 0 1 6 0V37M23 37v-6.5a3 3 0 0 1 6 0V37" fill="#170d0c" />
    </svg>
  );
}

export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <span className="flex items-center gap-2.5">
      <LogoMark />
      {!compact && (
        <span className="font-display text-2xl uppercase leading-none tracking-wide text-trim">
          <span className="font-extrabold">Trafon</span>
          <span className="font-normal">Dart</span>
        </span>
      )}
    </span>
  );
}
