import React from "react";
import { interpolate, useCurrentFrame, useVideoConfig } from "remotion";
import { ResultBanner } from "../components/Chrome";
import { C, PAD, SANS, fade, pop, stepStart, toneColor } from "../theme";
import type { Diagram, LocksDiagram, MemoryDiagram, PipelineDiagram, TimelineDiagram, TreeDiagram } from "../types";
import { Arrow, Box, Svg } from "./parts";

const TOP = 440; // below kicker + heading
const BOTTOM = 1230; // result banner sits under this
const RESULT_AT = 0.84;

type Props<D> = { d: D; frames: number };

/** Lanes are columns, time flows down. Good for races, ordering, "who ran when". */
const Timeline: React.FC<Props<TimelineDiagram>> = ({ d, frames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const lanes = d.lanes.length;
  const colW = (1080 - 2 * PAD - 60) / lanes;
  const laneX = (i: number) => PAD + 60 + colW * i + colW / 2;
  const rowH = Math.min(120, (BOTTOM - TOP - 130) / Math.max(1, d.steps.length));
  const lineGrow = fade(frame, 4, 20);

  return (
    <>
      <Svg>
        <Arrow x1={PAD + 18} y1={TOP + 100} x2={PAD + 18} y2={TOP + 100 + (BOTTOM - TOP - 140) * lineGrow} progress={lineGrow} color={C.muted} width={4} />
        {d.lanes.map((_, i) => (
          <line key={i} x1={laneX(i)} y1={TOP + 90} x2={laneX(i)} y2={TOP + 90 + (BOTTOM - TOP - 120) * lineGrow} stroke={C.border} strokeWidth={4} strokeDasharray="10 10" />
        ))}
      </Svg>
      <div style={{ position: "absolute", left: PAD - 6, top: TOP + 40, fontFamily: SANS, fontSize: 24, color: C.muted, transform: "rotate(-90deg)", transformOrigin: "left top" }}>
        time
      </div>
      {d.lanes.map((lane, i) => (
        <Box key={i} x={laneX(i)} y={TOP} w={colW - 30} label={lane} color={C.blue} opacity={pop(frame, fps, 2 + i * 4)} fontSize={30} />
      ))}
      {d.steps.map((s, i) => {
        const start = stepStart(i, d.steps.length, frames, 0.15, 0.75);
        const p = pop(frame, fps, start);
        const color = toneColor(s.tone);
        return (
          <div key={i} style={{ position: "absolute", left: 0, top: (1 - p) * -30 }}>
            <Box
              x={laneX(s.lane)} y={TOP + 120 + i * rowH} w={colW - 40} label={s.label}
              color={s.tone ? color : C.border} fill={s.tone === "bad" ? "#3A1A22" : s.tone === "good" ? "#123327" : C.surface2}
              opacity={p} scale={0.8 + 0.2 * p} fontSize={Math.min(30, rowH * 0.32)}
            />
          </div>
        );
      })}
      <ResultBanner text={d.result?.text} tone={d.result?.tone} start={frames * RESULT_AT} top={BOTTOM + 30} />
    </>
  );
};

const STATE = {
  running: { color: C.blue, icon: "⏳" },
  done: { color: C.green, icon: "✓" },
  failed: { color: C.red, icon: "💥" },
  cancelled: { color: C.muted, icon: "✕" },
} as const;

/** Parent/child hierarchy (coroutine scopes, jobs). Events recolour nodes one by one. */
const Tree: React.FC<Props<TreeDiagram>> = ({ d, frames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const depth = (id: string, seen = 0): number => {
    const n = d.nodes.find((x) => x.id === id);
    return n?.parent && seen < 10 ? 1 + depth(n.parent, seen + 1) : 0;
  };
  const rows: string[][] = [];
  d.nodes.forEach((n) => (rows[depth(n.id)] ??= []).push(n.id));
  const rowGap = Math.min(250, (BOTTOM - TOP - 100) / Math.max(1, rows.length));
  const pos: Record<string, { x: number; y: number }> = {};
  rows.forEach((ids, r) =>
    ids.forEach((id, k) => (pos[id] = { x: PAD + ((1080 - 2 * PAD) * (k + 0.5)) / ids.length, y: TOP + 40 + r * rowGap })),
  );
  const boxW = Math.min(300, (1080 - 2 * PAD) / Math.max(...rows.map((r) => r.length)) - 30);

  // Latest event per node up to now.
  const state: Record<string, { state: keyof typeof STATE; label?: string; at: number }> = {};
  d.events.forEach((e, i) => {
    const at = stepStart(i, d.events.length, frames, 0.3, 0.78);
    if (frame >= at) state[e.id] = { state: e.state, label: e.label, at };
  });

  return (
    <>
      <Svg>
        {d.nodes.filter((n) => n.parent && pos[n.parent]).map((n, i) => {
          const a = pos[n.parent!];
          const b = pos[n.id];
          const s = state[n.id];
          const color = s ? STATE[s.state].color : C.border;
          return <Arrow key={n.id} x1={a.x} y1={a.y + 50} x2={b.x} y2={b.y - 50} progress={fade(frame, 8 + i * 4, 14)} color={color === C.border ? C.muted : color} width={4} />;
        })}
      </Svg>
      {d.nodes.map((n, i) => {
        const s = state[n.id];
        const st = s ? STATE[s.state] : null;
        const flash = s ? pop(frame, fps, s.at, 9) : 1;
        return (
          <Box
            key={n.id} x={pos[n.id].x} y={pos[n.id].y} h={100} w={boxW} label={`${st ? st.icon + " " : ""}${n.label}`}
            color={st?.color ?? C.border} opacity={pop(frame, fps, i * 4) * (s?.state === "cancelled" ? 0.6 : 1)}
            scale={s ? 0.9 + 0.1 * flash : 1} fontSize={28}
          >
            {s?.label && <div style={{ fontSize: 22, color: st!.color, marginTop: 6 }}>{s.label}</div>}
          </Box>
        );
      })}
      <ResultBanner text={d.result?.text} tone={d.result?.tone} start={frames * RESULT_AT} top={BOTTOM + 30} />
    </>
  );
};

/** Items flowing down through operator stages (Flow). Dropped items turn red and vanish. */
const Pipeline: React.FC<Props<PipelineDiagram>> = ({ d, frames }) => {
  const frame = useCurrentFrame();
  const n = d.stages.length;
  const collectorY = BOTTOM - 60;
  const stageY = (i: number) => TOP + 20 + (i * (collectorY - TOP - 120)) / Math.max(1, n - 1);
  const travel = Math.max(20, frames * 0.18);
  const survivors = d.items.filter((it) => it.drop_at === undefined);

  return (
    <>
      {d.stages.map((s, i) => (
        <div
          key={i}
          style={{
            position: "absolute", left: PAD, right: PAD, top: stageY(i) - 44, height: 88, borderRadius: 20,
            border: `2px dashed ${C.border}`, display: "flex", alignItems: "center", paddingLeft: 26,
            fontFamily: SANS, fontSize: 30, fontWeight: 600, color: C.muted, opacity: fade(frame, i * 5, 12),
          }}
        >
          {s}
        </div>
      ))}
      <div style={{ position: "absolute", left: PAD, right: PAD, top: collectorY - 10, height: 110, borderRadius: 22, background: `${C.green}14`, border: `2px solid ${C.green}66` }} />
      {d.items.map((it, i) => {
        const launch = stepStart(i, d.items.length, frames, 0.1, 0.62);
        const stop = it.drop_at !== undefined ? Math.min(it.drop_at, n - 1) : n - 1;
        const p = interpolate(frame, [launch, launch + travel * (stop + 1) / n], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
        if (frame < launch) return null;
        const dropped = it.drop_at !== undefined && p >= 1;
        const afterDrop = dropped ? fade(frame, launch + (travel * (stop + 1)) / n, 12) : 0;
        const slot = survivors.indexOf(it);
        let y = interpolate(p, [0, 1], [stageY(0), stageY(stop)]);
        let x = 760;
        if (!dropped && it.drop_at === undefined && p >= 1) {
          const settle = fade(frame, launch + travel, 10);
          y = interpolate(settle, [0, 1], [stageY(n - 1), collectorY + 45]);
          x = interpolate(settle, [0, 1], [760, PAD + 90 + slot * 150]);
        }
        const color = dropped ? C.red : C.accent;
        return (
          <div
            key={i}
            style={{
              position: "absolute", left: x - 62, top: y - 34, width: 124, height: 68, borderRadius: 34,
              background: color, color: C.bg, display: "flex", alignItems: "center", justifyContent: "center",
              fontFamily: SANS, fontWeight: 800, fontSize: 30, opacity: 1 - afterDrop * 0.85,
              transform: `scale(${1 - afterDrop * 0.3}) rotate(${afterDrop * 12}deg)`, boxShadow: `0 0 30px ${color}88`,
              textDecoration: dropped ? "line-through" : undefined,
            }}
          >
            {it.label}
          </div>
        );
      })}
      <ResultBanner text={d.result?.text} tone={d.result?.tone} start={frames * RESULT_AT} top={BOTTOM + 80} />
    </>
  );
};

/** Variables pointing at objects. Shows sharing, references, caches. */
const Memory: React.FC<Props<MemoryDiagram>> = ({ d, frames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const vars = d.boxes.filter((b) => b.kind === "var");
  const objs = d.boxes.filter((b) => b.kind === "object");
  const pos: Record<string, { x: number; y: number; w: number }> = {};
  const place = (list: typeof d.boxes, x: number, w: number) =>
    list.forEach((b, i) => (pos[b.id] = { x, w, y: TOP + 60 + ((BOTTOM - TOP - 180) * (i + 0.5)) / list.length }));
  place(vars, PAD + 150, 300);
  place(objs, 1080 - PAD - 200, 400);
  const appear = (id: string) => stepStart(d.boxes.findIndex((b) => b.id === id), d.boxes.length, frames, 0.05, 0.35);

  return (
    <>
      <div style={{ position: "absolute", left: PAD, top: TOP - 30, fontFamily: SANS, fontSize: 26, color: C.muted, letterSpacing: 2 }}>VARIABLES</div>
      <div style={{ position: "absolute", right: PAD, top: TOP - 30, fontFamily: SANS, fontSize: 26, color: C.muted, letterSpacing: 2 }}>OBJECTS IN MEMORY</div>
      <Svg>
        {d.arrows.map((a, i) => {
          const from = pos[a.from];
          const to = pos[a.to];
          if (!from || !to) return null;
          const start = stepStart(i, d.arrows.length, frames, 0.4, 0.75);
          const color = a.tone ? toneColor(a.tone) : C.fg;
          if (Math.abs(from.x - to.x) < 10) {
            // Same column (object -> object): curve through the gap on the left, the screen edge is on the right.
            const edge = from.x - from.w / 2;
            return (
              <Arrow
                key={i} x1={edge} y1={from.y + 40} x2={edge - 6} y2={to.y + 34} label={a.label}
                progress={fade(frame, start, 16)} color={color} bend={0.45 + 0.12 * (i % 2)}
              />
            );
          }
          const x1 = from.x + (from.x < to.x ? from.w / 2 : -from.w / 2);
          const x2 = to.x + (from.x < to.x ? -to.w / 2 : to.w / 2) - 10;
          return (
            <Arrow
              key={i} x1={x1} y1={from.y + 40} x2={x2} y2={to.y + 40} label={a.label}
              progress={fade(frame, start, 16)} color={color} bend={0.12 * (i % 2 ? -1 : 1)}
            />
          );
        })}
      </Svg>
      {d.boxes.map((b) => (
        <Box
          key={b.id} x={pos[b.id].x} y={pos[b.id].y} w={pos[b.id].w} label={b.label}
          color={b.kind === "object" ? C.purple : C.blue} opacity={pop(frame, fps, appear(b.id))} fontSize={36}
        >
          {b.lines?.map((l, k) => (
            <div key={k} style={{ fontSize: 30, color: C.muted, fontWeight: 500, marginTop: 6 }}>{l}</div>
          ))}
        </Box>
      ))}
      <ResultBanner text={d.result?.text} tone={d.result?.tone} start={frames * RESULT_AT} top={BOTTOM + 30} />
    </>
  );
};

/** Threads holding and waiting on locks. A cycle of waits is a deadlock. */
const Locks: React.FC<Props<LocksDiagram>> = ({ d, frames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const span = (n: number, i: number) => PAD + ((1080 - 2 * PAD) * (i + 0.5)) / n;
  const threadY = TOP + 40;
  const lockY = BOTTOM - 200;
  const boxW = Math.min(340, (1080 - 2 * PAD) / Math.max(d.threads.length, d.locks.length) - 40);

  const edges = new Map<string, { thread: number; lock: number; action: "hold" | "wait"; at: number }>();
  d.steps.forEach((s, i) => {
    const at = stepStart(i, d.steps.length, frames, 0.15, 0.72);
    if (frame < at) return;
    const key = `${s.thread}-${s.lock}`;
    if (s.action === "release") edges.delete(key);
    else edges.set(key, { thread: s.thread, lock: s.lock, action: s.action, at });
  });
  const deadlocked = d.result?.tone === "bad" && frame >= frames * RESULT_AT;
  const glow = deadlocked ? 0.5 + 0.5 * Math.sin(frame / 4) : 0;

  return (
    <>
      <Svg>
        {[...edges.values()].map((e) => {
          const tx = span(d.threads.length, e.thread);
          const lx = span(d.locks.length, e.lock);
          const hold = e.action === "hold";
          return (
            <Arrow
              key={`${e.thread}-${e.lock}`}
              x1={hold ? lx : tx} y1={hold ? lockY - 55 : threadY + 55} x2={hold ? tx : lx} y2={hold ? threadY + 60 : lockY - 60}
              progress={fade(frame, e.at, 12)} color={hold ? C.green : C.red} dashed={!hold}
              width={hold ? 6 : 5 + glow * 4} bend={hold ? -0.12 : 0.12} label={hold ? "holds" : "waits"}
            />
          );
        })}
      </Svg>
      {d.threads.map((t, i) => (
        <Box key={t} x={span(d.threads.length, i)} y={threadY} h={110} w={boxW} label={`🧵 ${t}`} color={C.blue} opacity={pop(frame, fps, i * 4)} fontSize={30} />
      ))}
      {d.locks.map((l, i) => (
        <Box key={l} x={span(d.locks.length, i)} y={lockY} h={110} w={boxW} label={`🔒 ${l}`} color={deadlocked ? C.red : C.accent} opacity={pop(frame, fps, 6 + i * 4)} fontSize={30} />
      ))}
      <ResultBanner text={d.result?.text} tone={d.result?.tone} start={frames * RESULT_AT} top={BOTTOM + 30} />
    </>
  );
};

export const DiagramView: React.FC<{ diagram: Diagram; frames: number }> = ({ diagram, frames }) => {
  switch (diagram.kind) {
    case "timeline": return <Timeline d={diagram} frames={frames} />;
    case "tree": return <Tree d={diagram} frames={frames} />;
    case "pipeline": return <Pipeline d={diagram} frames={frames} />;
    case "memory": return <Memory d={diagram} frames={frames} />;
    case "locks": return <Locks d={diagram} frames={frames} />;
  }
};
