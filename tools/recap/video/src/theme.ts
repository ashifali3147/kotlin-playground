import { loadFont as loadPoppins } from "@remotion/google-fonts/Poppins";
import { loadFont as loadMono } from "@remotion/google-fonts/JetBrainsMono";
import { interpolate, spring } from "remotion";
import type { Tone } from "./types";

// Poppins covers Latin and Devanagari, so Hinglish captions render in one typeface.
export const SANS = loadPoppins("normal", {
  weights: ["400", "500", "600", "700", "800"],
  subsets: ["latin", "devanagari"],
}).fontFamily;
export const MONO = loadMono("normal", { weights: ["400", "700"], subsets: ["latin"] }).fontFamily;

export const C = {
  bg: "#0B0F17",
  surface: "#131A26",
  surface2: "#1A2333",
  border: "#263042",
  fg: "#E8EEF7",
  muted: "#8A97AD",
  accent: "#FFC857",
  red: "#FF6B6B",
  green: "#3DDC97",
  blue: "#5AB0FF",
  purple: "#B392F0",
};

export const W = 1080;
export const H = 1920;
export const PAD = 72;
export const CONTENT_TOP = 260;

export const toneColor = (tone?: Tone) => (tone === "bad" ? C.red : tone === "good" ? C.green : C.blue);

/** Spring from 0 to 1 starting at `delay` frames. */
export const pop = (frame: number, fps: number, delay = 0, damping = 14) =>
  spring({ frame: frame - delay, fps, config: { damping, mass: 0.7 } });

/**
 * Spreads `n` steps across a scene: step i starts at a fraction of the scene between `from` and `to`.
 * Returns the frame where step i begins.
 */
export const stepStart = (i: number, n: number, frames: number, from = 0.12, to = 0.8) =>
  Math.round(frames * (n <= 1 ? from : from + ((to - from) * i) / (n - 1)));

export const fade = (frame: number, start: number, length = 10) =>
  interpolate(frame, [start, start + length], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
