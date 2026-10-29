import React from "react";
import { AbsoluteFill, interpolate, useCurrentFrame, useVideoConfig } from "remotion";
import { C, PAD, SANS, fade, pop, toneColor } from "../theme";
import type { Tone } from "../types";

/** Slowly drifting colour blobs over a faint dot grid. */
export const Background: React.FC = () => {
  const frame = useCurrentFrame();
  const t = frame / 30;
  const blob = (x: number, y: number, r: number, color: string) => (
    <div
      style={{
        position: "absolute", left: x - r, top: y - r, width: r * 2, height: r * 2, borderRadius: "50%",
        background: `radial-gradient(circle, ${color}33 0%, ${color}00 70%)`,
      }}
    />
  );
  return (
    <AbsoluteFill style={{ background: C.bg, overflow: "hidden" }}>
      <AbsoluteFill
        style={{
          backgroundImage: `radial-gradient(${C.border} 1.5px, transparent 1.5px)`,
          backgroundSize: "44px 44px",
          opacity: 0.45,
        }}
      />
      {blob(200 + Math.sin(t * 0.4) * 80, 500 + Math.cos(t * 0.3) * 60, 520, C.blue)}
      {blob(880 + Math.cos(t * 0.35) * 90, 1250 + Math.sin(t * 0.25) * 80, 560, C.purple)}
      {blob(540 + Math.sin(t * 0.2) * 120, 1750, 420, C.accent)}
    </AbsoluteFill>
  );
};

export const Header: React.FC<{ id: string; progress: number }> = ({ id, progress }) => (
  <div style={{ position: "absolute", left: PAD, right: PAD, top: 96, fontFamily: SANS }}>
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
      <div style={{ color: C.muted, fontSize: 30, fontWeight: 700, letterSpacing: 4 }}>CONCURRENCY DOJO</div>
      <div
        style={{
          background: C.accent, color: C.bg, fontSize: 30, fontWeight: 800, padding: "6px 22px", borderRadius: 999,
        }}
      >
        {id}
      </div>
    </div>
    <div style={{ marginTop: 28, height: 8, borderRadius: 4, background: "#1E2636" }}>
      <div style={{ width: `${Math.max(1, progress * 100)}%`, height: "100%", borderRadius: 4, background: C.accent }} />
    </div>
  </div>
);

/** Fades and lifts a scene in, and fades it out over its last frames. */
export const SceneShell: React.FC<{ frames: number; children: React.ReactNode }> = ({ frames, children }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const enter = pop(frame, fps, 0, 18);
  const exit = interpolate(frame, [frames - 8, frames], [1, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <AbsoluteFill style={{ opacity: Math.min(enter, exit), transform: `translateY(${(1 - enter) * 40}px)` }}>
      {children}
    </AbsoluteFill>
  );
};

export const Kicker: React.FC<{ text?: string; color?: string; top?: number }> = ({ text, color = C.accent, top = 270 }) => {
  const frame = useCurrentFrame();
  if (!text) return null;
  return (
    <div
      style={{
        position: "absolute", left: PAD, top, display: "flex", alignItems: "center", gap: 16,
        fontFamily: SANS, fontWeight: 800, fontSize: 38, letterSpacing: 3, color,
        opacity: fade(frame, 2, 10), transform: `translateX(${(1 - fade(frame, 2, 10)) * -30}px)`,
      }}
    >
      <div style={{ width: 18, height: 18, borderRadius: 9, background: color, boxShadow: `0 0 24px ${color}` }} />
      {text}
    </div>
  );
};

/** The punchline under a diagram ("one seat, two tickets"). */
export const ResultBanner: React.FC<{ text?: string; tone?: Tone; start: number; top: number }> = ({ text, tone, start, top }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  if (!text) return null;
  const p = pop(frame, fps, start, 12);
  const color = tone ? toneColor(tone) : C.accent;
  return (
    <div
      style={{
        position: "absolute", left: PAD, right: PAD, top, padding: "22px 30px", borderRadius: 22,
        background: `${color}22`, border: `3px solid ${color}`, color: C.fg,
        fontFamily: SANS, fontSize: 40, fontWeight: 700, textAlign: "center",
        opacity: p, transform: `scale(${0.85 + 0.15 * p})`,
      }}
    >
      {text}
    </div>
  );
};
