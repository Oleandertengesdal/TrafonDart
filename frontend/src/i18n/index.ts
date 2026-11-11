import i18n from "i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import { initReactI18next } from "react-i18next";
import en from "./locales/en.json";
import nb from "./locales/nb.json";

export const LANGUAGES = [
  { code: "nb", label: "Norsk", short: "NO" },
  { code: "en", label: "English", short: "EN" },
] as const;

export type LanguageCode = (typeof LANGUAGES)[number]["code"];

/** Norwegian variants (nn, no, nb-NO …) all map to bokmål; everything else falls back to English. */
function normalise(lng: string | undefined): LanguageCode {
  if (!lng) return "nb";
  const base = lng.toLowerCase().split("-")[0];
  return base === "nb" || base === "nn" || base === "no" ? "nb" : "en";
}

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: { en: { translation: en }, nb: { translation: nb } },
    supportedLngs: ["nb", "en"],
    nonExplicitSupportedLngs: false,
    fallbackLng: "en",
    detection: {
      order: ["localStorage", "navigator"],
      lookupLocalStorage: "trafondart.lang",
      caches: ["localStorage"],
      convertDetectedLanguage: (lng: string) => normalise(lng),
    },
    interpolation: { escapeValue: false }, // React already escapes
    returnNull: false,
  });

i18n.on("languageChanged", (lng) => {
  document.documentElement.lang = normalise(lng);
});
document.documentElement.lang = normalise(i18n.language);

export function currentLanguage(): LanguageCode {
  return normalise(i18n.resolvedLanguage ?? i18n.language);
}

/** BCP-47 locale for Intl formatting. */
export function intlLocale(): string {
  return currentLanguage() === "nb" ? "nb-NO" : "en-GB";
}

export default i18n;
