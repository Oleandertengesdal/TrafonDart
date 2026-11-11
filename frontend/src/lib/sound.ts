import { currentLanguage } from "../i18n";

/**
 * Audio callouts, generated in the browser – no audio files, nothing licensed.
 * - chimes: short Web Audio tone sequences
 * - voice:  the browser's own speech synthesis in the active language
 * Both are opt-out (sound) / opt-in (caller) from Settings and stored in localStorage.
 */

const SOUND_KEY = "trafondart.sound";
const CALLER_KEY = "trafondart.caller";

function readFlag(key: string, fallback: boolean): boolean {
  try {
    const v = localStorage.getItem(key);
    return v === null ? fallback : v === "1";
  } catch {
    return fallback;
  }
}

function writeFlag(key: string, value: boolean) {
  try {
    localStorage.setItem(key, value ? "1" : "0");
  } catch {
    // storage unavailable (private mode) – keep in memory only
  }
}

export const soundPrefs = {
  get sound() {
    return readFlag(SOUND_KEY, true);
  },
  set sound(v: boolean) {
    writeFlag(SOUND_KEY, v);
  },
  get caller() {
    return readFlag(CALLER_KEY, false);
  },
  set caller(v: boolean) {
    writeFlag(CALLER_KEY, v);
  },
};

let ctx: AudioContext | null = null;

function audio(): AudioContext | null {
  try {
    ctx ??= new AudioContext();
    if (ctx.state === "suspended") void ctx.resume();
    return ctx;
  } catch {
    return null;
  }
}

function tone(freq: number, start: number, duration: number, type: OscillatorType = "triangle", gain = 0.18) {
  const ac = audio();
  if (!ac) return;
  const osc = ac.createOscillator();
  const g = ac.createGain();
  osc.type = type;
  osc.frequency.value = freq;
  const t0 = ac.currentTime + start;
  g.gain.setValueAtTime(0, t0);
  g.gain.linearRampToValueAtTime(gain, t0 + 0.02);
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + duration);
  osc.connect(g).connect(ac.destination);
  osc.start(t0);
  osc.stop(t0 + duration + 0.05);
}

export type Chime = "tap" | "oneEighty" | "finish" | "bust" | "leg";

export function chime(kind: Chime) {
  if (!soundPrefs.sound) return;
  switch (kind) {
    case "tap":
      tone(880, 0, 0.05, "sine", 0.05);
      break;
    case "bust":
      tone(220, 0, 0.18, "sawtooth", 0.08);
      tone(165, 0.16, 0.28, "sawtooth", 0.08);
      break;
    case "oneEighty":
      [523, 659, 784, 1047].forEach((f, i) => tone(f, i * 0.09, 0.5));
      break;
    case "finish":
      [392, 523, 659, 784, 1047].forEach((f, i) => tone(f, i * 0.07, 0.45));
      break;
    case "leg":
      [523, 784].forEach((f, i) => tone(f, i * 0.12, 0.4));
      break;
  }
}

export function speak(text: string) {
  if (!soundPrefs.sound || typeof window === "undefined" || !("speechSynthesis" in window)) return;
  const lang = currentLanguage() === "nb" ? "nb-NO" : "en-GB";
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = lang;
  const voice = window.speechSynthesis.getVoices().find((v) => v.lang.toLowerCase().startsWith(lang.slice(0, 2)));
  if (voice) utterance.voice = voice;
  utterance.rate = 1.0;
  utterance.pitch = 0.9;
  window.speechSynthesis.cancel();
  window.speechSynthesis.speak(utterance);
}

export function vibrate(ms = 10) {
  try {
    navigator.vibrate?.(ms);
  } catch {
    // not supported
  }
}
