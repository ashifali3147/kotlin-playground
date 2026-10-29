import React from "react";
import { Highlight, type PrismTheme } from "prism-react-renderer";
import { interpolate, useCurrentFrame } from "remotion";
import { C, MONO, PAD, SANS } from "../theme";

const theme: PrismTheme = {
  plain: { color: C.fg, backgroundColor: "transparent" },
  styles: [
    { types: ["comment"], style: { color: "#6B7890", fontStyle: "italic" } },
    { types: ["keyword", "builtin"], style: { color: "#FF7B72" } },
    { types: ["string", "char"], style: { color: "#A5D6FF" } },
    { types: ["number", "boolean"], style: { color: "#79C0FF" } },
    { types: ["function"], style: { color: "#D2A8FF" } },
    { types: ["class-name", "annotation"], style: { color: "#FFA657" } },
    { types: ["operator", "punctuation"], style: { color: "#C9D1D9" } },
  ],
};

/** Font size that fits the longest line and the line count into the panel. */
const fitSize = (lines: string[], maxHeight: number) => {
  const longest = Math.max(1, ...lines.map((l) => l.length));
  const byWidth = (1080 - 2 * PAD - 2 * 40 - 70) / (longest * 0.6);
  const byHeight = maxHeight / (lines.length * 1.55);
  return Math.max(22, Math.min(40, byWidth, byHeight));
};

const Panel: React.FC<{ file?: string; children: React.ReactNode; glow?: string }> = ({ file, children, glow }) => (
  <div
    style={{
      position: "absolute", left: PAD, right: PAD, top: 370, borderRadius: 30, background: C.surface,
      border: `2px solid ${C.border}`, boxShadow: glow ? `0 0 80px ${glow}33` : "0 30px 80px rgba(0,0,0,0.45)",
      overflow: "hidden",
    }}
  >
    <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "22px 30px", borderBottom: `2px solid ${C.border}` }}>
      {[C.red, C.accent, C.green].map((c) => (
        <div key={c} style={{ width: 20, height: 20, borderRadius: 10, background: c }} />
      ))}
      <div style={{ flex: 1 }} />
      {file && <div style={{ fontFamily: SANS, fontSize: 28, fontWeight: 500, color: C.muted }}>{file}</div>}
    </div>
    <div style={{ padding: "26px 0" }}>{children}</div>
  </div>
);

/**
 * Code that types itself out over the first part of the scene, then sweeps a highlight over the given lines.
 */
export const CodeBlock: React.FC<{
  code: string;
  file?: string;
  highlight?: number[];
  color: string;
  frames: number;
}> = ({ code, file, highlight = [], color, frames }) => {
  const frame = useCurrentFrame();
  const text = code.replace(/\t/g, "    ").replace(/\n+$/, "");
  const lines = text.split("\n");
  const size = fitSize(lines, 900);
  const lineH = size * 1.55;

  const typed = Math.floor(interpolate(frame, [4, frames * 0.4], [0, text.length], { extrapolateLeft: "clamp", extrapolateRight: "clamp" }));
  const sweep = interpolate(frame, [frames * 0.42, frames * 0.55], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const pulse = 0.5 + 0.5 * Math.sin(frame / 6);
  let offset = 0;

  return (
    <Panel file={file} glow={sweep > 0 && highlight.length ? color : undefined}>
      <Highlight code={text} language="kotlin" theme={theme}>
        {({ tokens, getTokenProps }) => (
          <div style={{ fontFamily: MONO, fontSize: size, color: C.fg }}>
            {tokens.map((line, n) => {
              const lit = highlight.includes(n + 1);
              const dim = highlight.length > 0 && sweep > 0 && !lit;
              const lineStart = offset;
              offset += lines[n].length + 1;
              return (
                <div key={n} style={{ position: "relative", height: lineH, display: "flex", alignItems: "center" }}>
                  {lit && (
                    <div
                      style={{
                        position: "absolute", left: 0, top: 0, bottom: 0, width: `${sweep * 100}%`,
                        background: `${color}${Math.round(40 + 30 * pulse).toString(16)}`, borderLeft: `8px solid ${color}`,
                      }}
                    />
                  )}
                  <span style={{ position: "relative", width: 80, textAlign: "right", paddingRight: 24, color: C.muted, opacity: dim ? 0.35 : 0.8 }}>
                    {n + 1}
                  </span>
                  <span style={{ position: "relative", whiteSpace: "pre", opacity: dim ? 0.35 : 1 }}>
                    {(() => {
                      let col = lineStart;
                      return line.map((token, k) => {
                        const props = getTokenProps({ token });
                        const visible = Math.max(0, Math.min(token.content.length, typed - col));
                        col += token.content.length;
                        return <span key={k} {...props}>{token.content.slice(0, visible)}</span>;
                      });
                    })()}
                    {typed >= lineStart && typed < lineStart + lines[n].length + 1 && typed < text.length && (
                      <span style={{ background: C.accent, opacity: frame % 16 < 8 ? 1 : 0 }}>&nbsp;</span>
                    )}
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </Highlight>
    </Panel>
  );
};

type Op = { type: "same" | "del" | "add"; text: string };

/** Line diff via longest common subsequence. Snippets are short, so O(n*m) is fine. */
const diffLines = (a: string[], b: string[]): Op[] => {
  const dp = Array.from({ length: a.length + 1 }, () => new Array(b.length + 1).fill(0));
  for (let i = a.length - 1; i >= 0; i--)
    for (let j = b.length - 1; j >= 0; j--)
      dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
  const ops: Op[] = [];
  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      ops.push({ type: "same", text: a[i] });
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) ops.push({ type: "del", text: a[i++] });
    else ops.push({ type: "add", text: b[j++] });
  }
  while (i < a.length) ops.push({ type: "del", text: a[i++] });
  while (j < b.length) ops.push({ type: "add", text: b[j++] });
  return ops;
};

/**
 * The bug turning into the fix: removed lines flash red and collapse, added lines grow in green.
 */
export const DiffBlock: React.FC<{ before: string; after: string; file?: string; frames: number }> = ({ before, after, file, frames }) => {
  const frame = useCurrentFrame();
  const clean = (s: string) => s.replace(/\t/g, "    ").replace(/\n+$/, "").split("\n");
  const ops = diffLines(clean(before), clean(after));
  const size = fitSize(ops.map((o) => o.text), 900);
  const lineH = size * 1.55;

  const flag = interpolate(frame, [frames * 0.12, frames * 0.25], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const morph = interpolate(frame, [frames * 0.35, frames * 0.6], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });
  const settle = interpolate(frame, [frames * 0.75, frames * 0.9], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp" });

  return (
    <Panel file={file} glow={morph > 0.5 ? C.green : flag > 0 ? C.red : undefined}>
      <Highlight code={ops.map((o) => o.text).join("\n")} language="kotlin" theme={theme}>
        {({ tokens, getTokenProps }) => (
          <div style={{ fontFamily: MONO, fontSize: size, color: C.fg }}>
            {tokens.map((line, n) => {
              const op = ops[n];
              const grow = op.type === "del" ? 1 - morph : op.type === "add" ? morph : 1;
              const tint = op.type === "del" ? C.red : op.type === "add" ? C.green : null;
              const tintAlpha = op.type === "del" ? flag : op.type === "add" ? 1 - settle * 0.6 : 0;
              return (
                <div
                  key={n}
                  style={{
                    position: "relative", height: lineH * grow, opacity: grow, overflow: "hidden",
                    display: "flex", alignItems: "center",
                    background: tint ? `${tint}${Math.round(tintAlpha * 70).toString(16).padStart(2, "0")}` : undefined,
                    borderLeft: tint && tintAlpha > 0.05 ? `8px solid ${tint}` : "8px solid transparent",
                  }}
                >
                  <span style={{ width: 64, textAlign: "center", color: tint ?? C.muted, fontWeight: 700 }}>
                    {op.type === "del" ? "−" : op.type === "add" ? "+" : ""}
                  </span>
                  <span style={{ whiteSpace: "pre", textDecoration: op.type === "del" && morph > 0 ? "line-through" : undefined }}>
                    {line.map((token, k) => (
                      <span key={k} {...getTokenProps({ token })} />
                    ))}
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </Highlight>
    </Panel>
  );
};
