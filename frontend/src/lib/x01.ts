/** X01 start scores the server accepts, for every picker (matches, party X01, tournaments, challenges, Trafo). */
export const START_SCORES = [301, 401, 501, 701] as const;

export const startScoreOptions = START_SCORES.map((v) => ({ value: v as number, label: String(v) }));
