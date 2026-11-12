/** Fun facts for the year recap, kept pure so they can be tested. */

/** The oche is 2.37 m from the board; darts are fetched three at a time, there and back. */
export function walkedKm(darts: number): number {
  return Math.round(((darts / 3) * 2 * 2.37) / 100) / 10;
}

export function winRate(won: number, played: number): number {
  return played === 0 ? 0 : won / played;
}

/** The years a recap can be shown for: from the year the member joined up to this year, newest first. */
export function recapYears(memberSince: string | null | undefined, now = new Date()): number[] {
  const current = now.getFullYear();
  const first = memberSince ? Math.max(2000, new Date(memberSince).getFullYear()) : current;
  const out: number[] = [];
  for (let y = current; y >= Math.min(first, current); y--) out.push(y);
  return out;
}

/** The recap is promoted on the home page in December and January (looking back on the year just gone). */
export function recapPromo(now = new Date()): { year: number } | null {
  const m = now.getMonth();
  if (m === 11) return { year: now.getFullYear() };
  if (m === 0) return { year: now.getFullYear() - 1 };
  return null;
}
