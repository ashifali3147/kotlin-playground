import React from "react";
import { AbsoluteFill, Html5Audio, Sequence, staticFile, useCurrentFrame, useVideoConfig } from "remotion";
import { Captions } from "./components/Captions";
import { Background, Header, SceneShell } from "./components/Chrome";
import { SceneView } from "./scenes/Scenes";
import type { RecapProps } from "./types";

export const Recap: React.FC<RecapProps & { showCaptions?: boolean }> = ({ script, timings, audio, channel, showCaptions = true }) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();

  return (
    <AbsoluteFill>
      <Background />
      {audio && <Html5Audio src={staticFile(audio)} />}
      {script.scenes.map((scene, i) => {
        const t = timings[i];
        const from = Math.round(t.start * fps);
        const frames = Math.max(1, Math.round(t.duration * fps));
        return (
          <Sequence key={i} from={from} durationInFrames={frames} name={`${i + 1}. ${scene.type}`}>
            <SceneShell frames={frames}>
              <SceneView scene={scene} frames={frames} channel={channel} />
            </SceneShell>
            {showCaptions && <Captions text={scene.caption || scene.narration} speechFrames={Math.round(t.speech * fps)} />}
          </Sequence>
        );
      })}
      <Header id={script.id} progress={frame / durationInFrames} />
    </AbsoluteFill>
  );
};
