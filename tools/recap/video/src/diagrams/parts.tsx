import React from "react";
import { C, SANS } from "../theme";

/** A rounded label box centred on (x, y). */
export const Box: React.FC<{
  x: number;
  y: number;
  w: number;
  h?: number;
  label: string;
  color?: string;
  fill?: string;
  opacity?: number;
  scale?: number;
  fontSize?: number;
  children?: React.ReactNode;
}> = ({ x, y, w, h, label, color = C.border, fill = C.surface2, opacity = 1, scale = 1, fontSize = 32, children }) => (
  <div
    style={{
      position: "absolute", left: x - w / 2, top: h ? y - h / 2 : y, width: w, minHeight: h,
      display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center",
      padding: "14px 18px", boxSizing: "border-box", borderRadius: 20, background: fill, border: `3px solid ${color}`,
      fontFamily: SANS, fontWeight: 600, fontSize, color: C.fg, textAlign: "center", lineHeight: 1.25,
      opacity, transform: `scale(${scale})`, boxShadow: color !== C.border ? `0 0 36px ${color}55` : undefined,
    }}
  >
    {label}
    {children}
  </div>
);

/** Full-size SVG layer for arrows drawn under/over the boxes. */
export const Svg: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <svg width={1080} height={1920} style={{ position: "absolute", left: 0, top: 0, overflow: "visible" }}>
    <defs>
      {[C.fg, C.red, C.green, C.blue, C.muted, C.accent].map((c) => (
        <marker key={c} id={`head-${c.slice(1)}`} viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <path d="M0,0 L10,5 L0,10 z" fill={c} />
        </marker>
      ))}
    </defs>
    {children}
  </svg>
);

/**
 * An arrow from (x1,y1) to (x2,y2) that draws itself as `progress` goes 0 to 1.
 * `bend` curves it sideways; `dashed` is used for "waiting on".
 */
export const Arrow: React.FC<{
  x1: number; y1: number; x2: number; y2: number;
  progress: number; color?: string; bend?: number; dashed?: boolean; width?: number; label?: string;
}> = ({ x1, y1, x2, y2, progress, color = C.fg, bend = 0, dashed, width = 5, label }) => {
  if (progress <= 0) return null;
  const mx = (x1 + x2) / 2 - (y2 - y1) * bend;
  const my = (y1 + y2) / 2 + (x2 - x1) * bend;
  const d = `M${x1},${y1} Q${mx},${my} ${x2},${y2}`;
  const len = Math.hypot(x2 - x1, y2 - y1) * (1 + Math.abs(bend));
  return (
    <g opacity={Math.min(1, progress * 2)}>
      <path
        d={d} fill="none" stroke={color} strokeWidth={width} strokeLinecap="round"
        strokeDasharray={dashed ? "14 12" : `${len} ${len}`}
        strokeDashoffset={dashed ? 0 : len * (1 - progress)}
        markerEnd={progress > 0.95 ? `url(#head-${color.slice(1)})` : undefined}
      />
      {label && progress > 0.6 && (
        <text x={mx} y={my - 14} fill={color} fontFamily={SANS} fontSize={28} fontWeight={600} textAnchor="middle">
          {label}
        </text>
      )}
    </g>
  );
};
