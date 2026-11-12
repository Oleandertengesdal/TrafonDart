// Test stand-in for src/i18n: the language is whatever the test sets, and t() returns the key (plus options).
const state = { language: "nb" as "nb" | "en" };

export function setTestLanguage(lang: "nb" | "en"): void {
  state.language = lang;
}

export function currentLanguage(): "nb" | "en" {
  return state.language;
}

export function intlLocale(): string {
  return state.language === "nb" ? "nb-NO" : "en-GB";
}

const i18n = {
  get language() {
    return state.language;
  },
  t(key: string, options?: Record<string, unknown>): string {
    return options && "count" in options ? `${key}(${String(options.count)})` : key;
  },
};

export default i18n;
