import { useEffect } from "react";

/**
 * Tab titles: the page first and the app name last, so a row of open tabs can be told apart
 * ("Kamper – TrafonDart", "141–60 Kari mot Ola – TrafonDart").
 */
export const APP_NAME = "TrafonDart";
const SEPARATOR = " – ";
const MAX = 72;

export function pageTitle(page?: string | null): string {
  const text = (page ?? "").trim();
  if (!text) return APP_NAME;
  const room = MAX - SEPARATOR.length - APP_NAME.length;
  const shown = text.length > room ? `${text.slice(0, room - 1).trimEnd()}…` : text;
  return `${shown}${SEPARATOR}${APP_NAME}`;
}

/** "141–60 Kari mot Ola" while live, "Kari mot Ola" otherwise. `versus` is the translated word. */
export function matchTitle(names: [string, string], scores: [number, number], live: boolean, versus = "mot"): string {
  const pair = `${names[0]} ${versus} ${names[1]}`;
  return live ? `${scores[0]}–${scores[1]} ${pair}` : pair;
}

/** Sets the tab title while the page is shown. Pass nothing (or null while loading) for the bare app name. */
export function usePageTitle(page?: string | null) {
  useEffect(() => {
    document.title = pageTitle(page);
  }, [page]);
}
