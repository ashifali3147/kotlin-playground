// Shape of recaps/<ID>/script.<lang>.json plus the timings recap.py measures from the voice track.

export type Tone = "bad" | "good" | "neutral";

export type TimelineDiagram = {
  kind: "timeline";
  lanes: string[];
  steps: { lane: number; label: string; tone?: Tone }[];
  result?: { text: string; tone?: Tone };
};

export type TreeDiagram = {
  kind: "tree";
  nodes: { id: string; label: string; parent?: string }[];
  events: { id: string; state: "running" | "done" | "failed" | "cancelled"; label?: string }[];
  result?: { text: string; tone?: Tone };
};

export type PipelineDiagram = {
  kind: "pipeline";
  stages: string[];
  items: { label: string; drop_at?: number }[];
  result?: { text: string; tone?: Tone };
};

export type MemoryDiagram = {
  kind: "memory";
  boxes: { id: string; label: string; kind: "var" | "object"; lines?: string[] }[];
  arrows: { from: string; to: string; label?: string; tone?: Tone }[];
  result?: { text: string; tone?: Tone };
};

export type LocksDiagram = {
  kind: "locks";
  threads: string[];
  locks: string[];
  steps: { thread: number; lock: number; action: "hold" | "wait" | "release" }[];
  result?: { text: string; tone?: Tone };
};

export type Diagram = TimelineDiagram | TreeDiagram | PipelineDiagram | MemoryDiagram | LocksDiagram;

// narration is spoken; caption (English) is what the subtitles show when the audio is Hinglish.
type Base = { narration: string; caption?: string; kicker?: string };

export type Scene =
  | (Base & { type: "title"; heading: string; sub?: string })
  | (Base & { type: "text"; heading: string; body?: string; icon?: string })
  | (Base & { type: "analogy"; heading: string; items: { icon: string; label: string }[]; body?: string })
  | (Base & { type: "code"; tone?: Tone; file?: string; code: string; highlight?: number[] })
  | (Base & { type: "diff"; file?: string; before: string; after: string })
  | (Base & { type: "diagram"; heading?: string; diagram: Diagram })
  | (Base & { type: "outro"; heading: string; sub?: string });

export type Script = {
  id: string;
  lang?: "en" | "hi";
  title?: string;
  scenes: Scene[];
};

export type Timing = { start: number; speech: number; duration: number };

export type RecapProps = {
  script: Script;
  timings: Timing[];
  total: number;
  audio: string;
  channel?: string;
};
