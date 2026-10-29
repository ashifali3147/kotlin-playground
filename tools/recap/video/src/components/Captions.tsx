import React from "react";
import { useCurrentFrame, useVideoConfig } from "remotion";
import { C, PAD, SANS, pop } from "../theme";

const MAX_WORDS = 5;

/** Splits narration into short chunks, breaking after punctuation (including the Hindi danda) when it can. */
const chunk = (words: string[]) => {
  const chunks: number[][] = [];
  let cur: number[] = [];
  words.forEach((w, i) => {
    cur.push(i);
    if (cur.length >= MAX_WORDS || (cur.length >= 2 && /[.,;:!?।]$/.test(w))) {
      chunks.push(cur);
      cur = [];
    }
  });
  if (cur.length) chunks.push(cur);
  return chunks;
};

/**
 * Karaoke-style captions. Word timings are estimated from word length across the scene's speech,
 * since the voice track has no word timestamps.
 */
export const Captions: React.FC<{ text: string; speechFrames: number }> = ({ text, speechFrames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const words = text.split(/\s+/).filter(Boolean);
  if (!words.length) return null;

  const weights = words.map((w) => w.length + 2);
  const total = weights.reduce((a, b) => a + b, 0);
  const starts: number[] = [];
  let acc = 0;
  for (const w of weights) {
    starts.push((acc / total) * speechFrames);
    acc += w;
  }
  let active = starts.findIndex((s, i) => frame >= s && (i === starts.length - 1 || frame < starts[i + 1]));
  if (active < 0) active = frame < 0 ? 0 : words.length - 1;

  const chunks = chunk(words);
  const current = chunks.find((c) => c.includes(active)) ?? chunks[chunks.length - 1];
  const chunkStart = starts[current[0]];
  const p = pop(frame, fps, chunkStart, 16);

  return (
    <div
      style={{
        position: "absolute", left: PAD - 10, right: PAD - 10, top: 1440, display: "flex", justifyContent: "center",
      }}
    >
      <div
        style={{
          display: "flex", flexWrap: "wrap", justifyContent: "center", gap: "4px 18px",
          padding: "22px 34px", borderRadius: 28, background: "rgba(5,8,14,0.72)",
          border: `2px solid ${C.border}`, fontFamily: SANS, fontWeight: 700, fontSize: 60, lineHeight: 1.35,
          transform: `scale(${0.92 + 0.08 * p})`, opacity: Math.min(1, p * 1.5),
        }}
      >
        {current.map((i) => {
          const isActive = i === active;
          const spoken = i < active;
          return (
            <span
              key={i}
              style={{
                color: isActive ? C.accent : spoken ? C.fg : "rgba(232,238,247,0.55)",
                transform: `scale(${isActive ? 1.08 : 1})`, display: "inline-block",
              }}
            >
              {words[i]}
            </span>
          );
        })}
      </div>
    </div>
  );
};
