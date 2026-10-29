import React from "react";
import { interpolate, useCurrentFrame, useVideoConfig } from "remotion";
import { CodeBlock, DiffBlock } from "../components/CodeBlock";
import { Kicker } from "../components/Chrome";
import { DiagramView } from "../diagrams/Diagrams";
import { C, PAD, SANS, fade, pop, stepStart, toneColor } from "../theme";
import type { Scene } from "../types";

const Heading: React.FC<{ text?: string; top: number; size?: number; delay?: number }> = ({ text, top, size = 76, delay = 4 }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  if (!text) return null;
  // Words rise in one after another.
  return (
    <div
      style={{
        position: "absolute", left: PAD, right: PAD, top, fontFamily: SANS, fontWeight: 800, fontSize: size,
        lineHeight: 1.15, color: C.fg, display: "flex", flexWrap: "wrap", gap: `0 ${size * 0.28}px`,
      }}
    >
      {text.split(" ").map((w, i) => {
        const p = pop(frame, fps, delay + i * 3);
        return (
          <span key={i} style={{ display: "inline-block", opacity: p, transform: `translateY(${(1 - p) * 40}px)` }}>
            {w}
          </span>
        );
      })}
    </div>
  );
};

const Body: React.FC<{ text?: string; top: number; delay?: number }> = ({ text, top, delay = 18 }) => {
  const frame = useCurrentFrame();
  if (!text) return null;
  return (
    <div
      style={{
        position: "absolute", left: PAD, right: PAD, top, fontFamily: SANS, fontWeight: 500, fontSize: 48,
        lineHeight: 1.45, color: "#C5CEDC", opacity: fade(frame, delay, 14),
      }}
    >
      {text}
    </div>
  );
};

const Title: React.FC<{ s: Extract<Scene, { type: "title" }> }> = ({ s }) => {
  const frame = useCurrentFrame();
  const bar = fade(frame, 14, 16);
  return (
    <>
      <Heading text={s.heading} top={560} size={104} />
      <div style={{ position: "absolute", left: PAD, top: 1000, height: 14, width: 200 * bar, borderRadius: 7, background: C.accent }} />
      <Body text={s.sub} top={1060} delay={20} />
    </>
  );
};

const Text: React.FC<{ s: Extract<Scene, { type: "text" }> }> = ({ s }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const bounce = pop(frame, fps, 2, 8);
  return (
    <>
      <Kicker text={s.kicker} />
      {s.icon && (
        <div style={{ position: "absolute", left: PAD, top: 440, fontSize: 160, transform: `scale(${bounce}) rotate(${(1 - bounce) * -20}deg)` }}>
          {s.icon}
        </div>
      )}
      <Heading text={s.heading} top={s.icon ? 680 : 520} />
      <Body text={s.body} top={s.icon ? 940 : 800} />
    </>
  );
};

const Analogy: React.FC<{ s: Extract<Scene, { type: "analogy" }>; frames: number }> = ({ s, frames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const n = s.items.length;
  const cols = Math.min(3, n);
  return (
    <>
      <Kicker text={s.kicker} color={C.purple} />
      <Heading text={s.heading} top={370} size={68} />
      <div style={{ position: "absolute", left: PAD, right: PAD, top: 640, display: "flex", flexWrap: "wrap", justifyContent: "center", gap: 30 }}>
        {s.items.map((it, i) => {
          const p = pop(frame, fps, stepStart(i, n, frames, 0.1, 0.5), 10);
          const wobble = Math.sin((frame - i * 7) / 9) * 4;
          return (
            <div
              key={i}
              style={{
                width: (1080 - 2 * PAD - 30 * (cols - 1)) / cols, padding: "28px 10px", borderRadius: 28,
                background: C.surface, border: `2px solid ${C.border}`, textAlign: "center",
                opacity: p, transform: `scale(${0.6 + 0.4 * p}) translateY(${wobble}px)`,
              }}
            >
              <div style={{ fontSize: 120 }}>{it.icon}</div>
              <div style={{ fontFamily: SANS, fontWeight: 600, fontSize: 34, color: C.fg, marginTop: 10 }}>{it.label}</div>
            </div>
          );
        })}
      </div>
      <Body text={s.body} top={1080} delay={Math.round(frames * 0.55)} />
    </>
  );
};

const Code: React.FC<{ s: Extract<Scene, { type: "code" }>; frames: number }> = ({ s, frames }) => {
  const color = toneColor(s.tone);
  return (
    <>
      <Kicker text={s.kicker} color={color} />
      <CodeBlock code={s.code} file={s.file} highlight={s.highlight} color={color} frames={frames} />
    </>
  );
};

const Diff: React.FC<{ s: Extract<Scene, { type: "diff" }>; frames: number }> = ({ s, frames }) => (
  <>
    <Kicker text={s.kicker} color={C.green} />
    <DiffBlock before={s.before} after={s.after} file={s.file} frames={frames} />
  </>
);

const DiagramScene: React.FC<{ s: Extract<Scene, { type: "diagram" }>; frames: number }> = ({ s, frames }) => (
  <>
    <Kicker text={s.kicker} color={C.blue} />
    {s.heading && <Heading text={s.heading} top={340} size={52} />}
    <DiagramView diagram={s.diagram} frames={frames} />
  </>
);

const Outro: React.FC<{ s: Extract<Scene, { type: "outro" }>; channel?: string }> = ({ s, channel }) => {
  const frame = useCurrentFrame();
  const shine = interpolate(frame, [10, 40], [-30, 130], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  return (
    <>
      <Kicker text="TAKEAWAY" top={420} />
      <div
        style={{
          position: "absolute", left: PAD - 36, right: PAD - 36, top: 490, padding: "40px 20px", borderRadius: 34,
          background: `linear-gradient(110deg, ${C.surface} ${shine - 20}%, #2A3550 ${shine}%, ${C.surface} ${shine + 20}%)`,
          border: `3px solid ${C.accent}`, minHeight: 360,
        }}
      />
      <Heading text={s.heading} top={545} size={74} />
      <Body text={s.sub} top={960} delay={20} />
      {channel && (
        <div style={{ position: "absolute", left: PAD, top: 1180, fontFamily: SANS, fontWeight: 700, fontSize: 42, color: C.accent, opacity: fade(frame, 30, 12) }}>
          Follow {channel} for more
        </div>
      )}
    </>
  );
};

export const SceneView: React.FC<{ scene: Scene; frames: number; channel?: string }> = ({ scene, frames, channel }) => {
  switch (scene.type) {
    case "title": return <Title s={scene} />;
    case "text": return <Text s={scene} />;
    case "analogy": return <Analogy s={scene} frames={frames} />;
    case "code": return <Code s={scene} frames={frames} />;
    case "diff": return <Diff s={scene} frames={frames} />;
    case "diagram": return <DiagramScene s={scene} frames={frames} />;
    case "outro": return <Outro s={scene} channel={channel} />;
  }
};

