import React from "react";
import { Composition } from "remotion";
import { Recap } from "./Recap";
import { H, W } from "./theme";
import type { RecapProps } from "./types";

const FPS = 30;

// Placeholder so `npm run studio` opens; real renders pass --props from recap.py.
const sample: RecapProps = {
  script: {
    id: "DEMO",
    scenes: [{ type: "title", heading: "Recap preview", sub: "Render with ./dojo recap <ID>", narration: "Recap preview" }],
  },
  timings: [{ start: 0, speech: 2, duration: 3 }],
  total: 3,
  audio: "",
};

export const Root: React.FC = () => (
  <Composition
    id="Recap"
    component={Recap}
    width={W}
    height={H}
    fps={FPS}
    durationInFrames={FPS * 3}
    defaultProps={sample}
    calculateMetadata={({ props }) => ({ durationInFrames: Math.max(1, Math.ceil(props.total * FPS)) })}
  />
);
