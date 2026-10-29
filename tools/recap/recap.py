"""Turn a solved dojo challenge into a 1080x1920 short video with narration and captions.

    recap.py prompt <ID>              print the script-writing prompt (diff + journal + tests)
    recap.py save <ID> <raw-file>     extract and validate the JSON script, write recaps/<ID>/script.json
    recap.py render <script.json>     render <dir of script>/<ID>.mp4, cover.png, post.txt

Normally driven by `./dojo recap <ID>`. Voice is picked by DOJO_TTS (say | openai | elevenlabs).
"""

import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
import wave
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from pygments.lexers import KotlinLexer
from pygments.token import Comment, Keyword, Name, Number, String, Token

ROOT = Path(__file__).resolve().parents[2]
RECAPS = ROOT / "recaps"
WORK = ROOT / "build" / "recap"

W, H = 1080, 1920
MARGIN = 72
FPS = 30
SCENE_GAP = 0.35  # seconds of silence after each scene's narration

BG = "#0D1117"
PANEL = "#161B22"
FG = "#E6EDF3"
MUTED = "#8B949E"
ACCENT = "#F5C451"
RED = "#FF6B6B"
GREEN = "#3FB950"
BLUE = "#58A6FF"
TONES = {"bad": RED, "good": GREEN, "neutral": BLUE}

SANS = "/System/Library/Fonts/HelveticaNeue.ttc"
MONO = "/System/Library/Fonts/Menlo.ttc"
SANS_REGULAR, SANS_BOLD, SANS_BLACK, SANS_MEDIUM = 0, 1, 9, 10


def die(msg):
    print(f"  ✘ {msg}", file=sys.stderr)
    sys.exit(1)


def run(cmd, **kw):
    return subprocess.run(cmd, check=True, capture_output=True, text=True, cwd=ROOT, **kw).stdout


# ---------------------------------------------------------------- context for the script writer

def find_challenge(cid):
    """Reads the challenge tables in CHALLENGES.md: | K01 | [Title](path/README.md) | Concept | ..."""
    for line in (ROOT / "CHALLENGES.md").read_text().splitlines():
        m = re.match(r"^\| (%s) \| \[(.+?)\]\((.+?)\) \|(.*)\|\s*$" % re.escape(cid), line)
        if m:
            cols = [c.strip() for c in m.group(4).split("|")]
            concept = cols[-2] if len(cols) > 2 else cols[0]  # boss rows have an extra "Bugs" column
            return {"id": cid, "title": m.group(2), "dir": str(Path(m.group(3)).parent), "concept": concept}
    die(f"No challenge {cid} in CHALLENGES.md")


def journal_entry(cid):
    text = (ROOT / "JOURNAL.md").read_text()
    m = re.search(r"^## %s\b.*?(?=^## |\Z)" % re.escape(cid), text, re.S | re.M)
    return m.group(0).strip() if m else None


def test_names(cid):
    prefix = "C" + cid[1:] if cid.startswith("T") else cid
    names = []
    for f in (ROOT / "src/test/kotlin/challenges").rglob(f"{prefix}*Test.kt"):
        names += re.findall(r"fun `(.+?)`", f.read_text())
    return names


def build_prompt(cid):
    ch = find_challenge(cid)
    entry = journal_entry(cid)
    if not entry:
        die(f"No journal entry for {cid} yet. Write it in JOURNAL.md first (## {cid} · ...):\n"
            f"    the video is built from your explanation, that's the part that makes it stick.")

    base = run(["git", "log", "--diff-filter=A", "--format=%H", "--", ch["dir"]]).split()
    if not base:
        die(f"Can't find the commit that added {ch['dir']}")
    base = base[-1]
    diff = run(["git", "diff", base, "--", ch["dir"], f":(exclude){ch['dir']}/*.md"])
    if not diff.strip():
        die(f"No changes to {ch['dir']} since the starting code, nothing to recap.")

    changed = run(["git", "diff", "--name-only", base, "--", ch["dir"]]).split()
    files = []
    for path in [p for p in changed if p.endswith(".kt")]:
        before = run(["git", "show", f"{base}:{path}"])
        after = (ROOT / path).read_text() if (ROOT / path).exists() else "(deleted)"
        files.append(f"### {path}\n\nBEFORE (starting code):\n```kotlin\n{before}```\n\n"
                     f"AFTER (learner's fix):\n```kotlin\n{after}```")

    readme = (ROOT / ch["dir"] / "README.md").read_text()
    tests = "\n".join(f"- {t}" for t in test_names(cid))
    return PROMPT.format(
        id=cid, title=ch["title"], concept=ch["concept"], readme=readme, tests=tests,
        journal=entry, diff=diff, files="\n\n".join(files),
    )


PROMPT = """\
You write the script for a 45 to 60 second vertical short (YouTube Shorts / Instagram Reels) that
recaps a concurrency challenge the learner just solved: what was breaking, what they changed, and the
concept behind it.

Build the story from the learner's JOURNAL ENTRY: their explanation, their wording, their "aha".
Use the diff and code only to be accurate. Never invent behaviour the code and tests don't show.
If the journal gets something technically wrong, correct it gently in the WHY IT WORKS scene.

Scenes, in order:
1. "title": the hook. A relatable app symptom phrased as something viewers have seen.
2. "text", kicker "THE SYMPTOM": what the user of the app experienced (the failing tests tell you).
3. "code", kicker "THE BUG", tone "bad": the smallest snippet of the BEFORE code that shows the bug.
4. "code", kicker "THE FIX", tone "good": the matching snippet of the learner's AFTER code.
5. "text", kicker "WHY IT WORKS": the concept, in one idea.
6. optional "text", kicker "INTERVIEW TIP": the follow-up question an interviewer would ask.
7. "outro": a one-line takeaway worth remembering.

Narration (read aloud by text-to-speech):
- Spoken English, short sentences, 110 to 150 words in total across all scenes.
- No code symbols, backticks, brackets or abbreviations a voice would mangle. Say identifiers as
  words: "with context", "Global Scope", "coroutine scope". Never read code out line by line.
On screen:
- heading at most 8 words, body at most 30 words.
- code: at most 12 lines, at most 44 characters per line, trimmed to the essentials,
  "// ..." for omitted parts. highlight = 1-based line numbers within the snippet.
post: a title (max 70 chars), a 1 to 3 sentence caption, 5 to 8 hashtags.

Reply with ONLY this JSON, no prose, no code fences:
{{
  "id": "{id}",
  "title": "<short video title>",
  "scenes": [
    {{"type": "title", "heading": "...", "sub": "...", "narration": "..."}},
    {{"type": "text", "kicker": "THE SYMPTOM", "heading": "...", "body": "...", "narration": "..."}},
    {{"type": "code", "kicker": "THE BUG", "tone": "bad", "file": "X.kt", "code": "...", "highlight": [2], "narration": "..."}},
    {{"type": "code", "kicker": "THE FIX", "tone": "good", "file": "X.kt", "code": "...", "highlight": [2, 3], "narration": "..."}},
    {{"type": "text", "kicker": "WHY IT WORKS", "heading": "...", "body": "...", "narration": "..."}},
    {{"type": "outro", "heading": "...", "sub": "...", "narration": "..."}}
  ],
  "post": {{"title": "...", "caption": "...", "hashtags": ["#kotlin", "..."]}}
}}

=== CHALLENGE {id}: {title} ===
Concept: {concept}

--- README ---
{readme}

--- TESTS (all passing now) ---
{tests}

--- JOURNAL ENTRY ---
{journal}

--- DIFF (starting code -> learner's fix) ---
{diff}

--- FULL FILES ---
{files}
"""


def save_script(cid, raw_path):
    raw = Path(raw_path).read_text()
    m = re.search(r"\{.*\}", raw, re.S)
    if not m:
        die(f"The script writer didn't return JSON. Raw reply: {raw_path}")
    try:
        script = json.loads(m.group(0))
    except json.JSONDecodeError as e:
        die(f"Script JSON is malformed ({e}). Raw reply: {raw_path}")
    validate(script)
    out = RECAPS / cid / "script.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(script, indent=2, ensure_ascii=False) + "\n")
    print(out)


def validate(script):
    scenes = script.get("scenes")
    if not scenes:
        die("script.json has no scenes")
    for i, s in enumerate(scenes, 1):
        if s.get("type") not in ("title", "text", "code", "outro"):
            die(f"scene {i}: unknown type {s.get('type')!r}")
        if not s.get("narration", "").strip():
            die(f"scene {i}: narration is empty")
        if s["type"] == "code" and not s.get("code", "").strip():
            die(f"scene {i}: code scene without code")


# ---------------------------------------------------------------- voice

def tts(text, out_wav):
    """Writes 24 kHz mono 16-bit WAV."""
    provider = os.environ.get("DOJO_TTS", "say")
    raw = out_wav.with_suffix(".raw")
    if provider == "say":
        voice = os.environ.get("DOJO_VOICE", "Samantha")
        rate = os.environ.get("DOJO_VOICE_RATE", "190")
        aiff = out_wav.with_suffix(".aiff")
        subprocess.run(["say", "-v", voice, "-r", rate, "-o", str(aiff), text], check=True)
        to_wav(aiff, out_wav)
        return
    if provider == "openai":
        key = os.environ.get("OPENAI_API_KEY") or die("DOJO_TTS=openai needs OPENAI_API_KEY")
        body = {
            "model": os.environ.get("DOJO_OPENAI_MODEL", "gpt-4o-mini-tts"),
            "voice": os.environ.get("DOJO_VOICE", "onyx"),
            "input": text,
            "response_format": "pcm",
            "instructions": "Upbeat, clear tech-explainer voice for a short video. Natural pace.",
        }
        req = urllib.request.Request(
            "https://api.openai.com/v1/audio/speech", data=json.dumps(body).encode(),
            headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
    elif provider == "elevenlabs":
        key = os.environ.get("ELEVENLABS_API_KEY") or die("DOJO_TTS=elevenlabs needs ELEVENLABS_API_KEY")
        voice = os.environ.get("DOJO_VOICE", "JBFqnCBsd6RMkjVDRZzb")
        body = {"text": text, "model_id": os.environ.get("DOJO_ELEVENLABS_MODEL", "eleven_multilingual_v2")}
        req = urllib.request.Request(
            f"https://api.elevenlabs.io/v1/text-to-speech/{voice}?output_format=pcm_24000",
            data=json.dumps(body).encode(), headers={"xi-api-key": key, "Content-Type": "application/json"})
    else:
        die(f"Unknown DOJO_TTS={provider!r} (use say, openai or elevenlabs)")
    with urllib.request.urlopen(req, timeout=120) as resp:
        raw.write_bytes(resp.read())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", "24000", "-ac", "1",
                    "-i", str(raw), str(out_wav)], check=True)


def to_wav(src, out_wav):
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(src),
                    "-ar", "24000", "-ac", "1", "-sample_fmt", "s16", str(out_wav)], check=True)


def wav_seconds(path):
    with wave.open(str(path)) as w:
        return w.getnframes() / w.getframerate()


def concat_audio(parts, out_wav):
    """parts: list of (wav path, seconds of silence to append)."""
    with wave.open(str(out_wav), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(24000)
        for path, gap in parts:
            with wave.open(str(path)) as w:
                out.writeframes(w.readframes(w.getnframes()))
            out.writeframes(b"\x00\x00" * int(24000 * gap))


# ---------------------------------------------------------------- drawing

_fonts = {}


def font(path, size, index=0):
    key = (path, size, index)
    if key not in _fonts:
        _fonts[key] = ImageFont.truetype(path, size, index=index)
    return _fonts[key]


def wrap(draw, text, fnt, max_w):
    lines = []
    for para in text.split("\n"):
        line = ""
        for word in para.split():
            trial = f"{line} {word}".strip()
            if draw.textlength(trial, font=fnt) <= max_w or not line:
                line = trial
            else:
                lines.append(line)
                line = word
        lines.append(line)
    return lines


def draw_block(draw, text, fnt, y, color, max_w=W - 2 * MARGIN, spacing=1.18, align="left"):
    """Draws wrapped text starting at y, returns the y below it."""
    line_h = int(fnt.size * spacing)
    for line in wrap(draw, text, fnt, max_w):
        x = MARGIN if align == "left" else (W - draw.textlength(line, font=fnt)) / 2
        draw.text((x, y), line, font=fnt, fill=color)
        y += line_h
    return y


def rgb(hex_color):
    return tuple(int(hex_color[i:i + 2], 16) for i in (1, 3, 5))


def blend(hex_a, hex_b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(rgb(hex_a), rgb(hex_b)))


def token_color(ttype):
    for kind, color in [
        (Comment, MUTED), (Keyword, "#FF7B72"), (String, "#A5D6FF"), (Number, "#79C0FF"),
        (Name.Function, "#D2A8FF"), (Name.Class, "#FFA657"), (Name.Builtin, "#FFA657"),
        (Name.Decorator, "#FFA657"),
    ]:
        if ttype in kind:
            return color
    return FG


def draw_code(img, draw, scene, top):
    code = scene["code"].expandtabs(4).rstrip("\n")
    lines = code.split("\n")
    tone = TONES.get(scene.get("tone", "neutral"), BLUE)
    highlight = set(scene.get("highlight") or [])

    avail_w = W - 2 * MARGIN - 2 * 40 - 70  # panel padding + line-number gutter
    longest = max(len(l) for l in lines) or 1
    size = 38
    while size > 20 and (font(MONO, size).getlength("M") * longest > avail_w or size * 1.5 * len(lines) > 980):
        size -= 1
    mono = font(MONO, size)
    line_h = int(size * 1.5)

    tab_h = 64
    panel_h = tab_h + 32 + line_h * len(lines) + 32
    x0, x1 = MARGIN, W - MARGIN
    draw.rounded_rectangle((x0, top, x1, top + panel_h), radius=28, fill=PANEL, outline="#30363D", width=2)
    for i, c in enumerate([RED, ACCENT, GREEN]):
        draw.ellipse((x0 + 32 + i * 34, top + 24, x0 + 52 + i * 34, top + 44), fill=c)
    if scene.get("file"):
        f = font(SANS, 30, SANS_MEDIUM)
        draw.text((x1 - 32 - draw.textlength(scene["file"], font=f), top + 18), scene["file"], font=f, fill=MUTED)

    # Per-line token runs, so highlighted lines stay bright and the rest are dimmed.
    runs = [[]]
    for ttype, value in KotlinLexer().get_tokens(code):
        parts = value.split("\n")
        for j, part in enumerate(parts):
            if j:
                runs.append([])
            if part:
                runs[-1].append((ttype, part))

    y = top + tab_h + 32
    for n, line_runs in enumerate(runs[:len(lines)], 1):
        lit = not highlight or n in highlight
        if n in highlight:
            overlay = Image.new("RGBA", (x1 - x0 - 4, line_h), (*rgb(tone), 46))
            img.paste(overlay, (x0 + 2, y - int(size * 0.2)), overlay)
            draw.rectangle((x0 + 2, y - int(size * 0.2), x0 + 9, y - int(size * 0.2) + line_h), fill=tone)
        draw.text((x0 + 40, y), f"{n:>2}", font=mono, fill=MUTED if lit else blend(MUTED, PANEL, 0.6))
        x = x0 + 40 + 70
        for ttype, part in line_runs:
            color = token_color(ttype)
            draw.text((x, y), part, font=mono, fill=color if lit else blend(color, PANEL, 0.62))
            x += mono.getlength(part)
        y += line_h
    return top + panel_h


def frame(script, scene, caption, progress, show_caption=True):
    img = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(img)
    tone = TONES.get(scene.get("tone"), ACCENT) if scene["type"] == "code" else ACCENT

    # Header + progress bar.
    draw.text((MARGIN, 104), "CONCURRENCY DOJO", font=font(SANS, 32, SANS_BOLD), fill=MUTED)
    pill = script.get("id", "")
    pf = font(SANS, 32, SANS_BOLD)
    pw = draw.textlength(pill, font=pf)
    draw.rounded_rectangle((W - MARGIN - pw - 40, 94, W - MARGIN, 148), radius=27, fill=ACCENT)
    draw.text((W - MARGIN - pw - 20, 104), pill, font=pf, fill=BG)
    draw.rounded_rectangle((MARGIN, 182, W - MARGIN, 190), radius=4, fill="#21262D")
    draw.rounded_rectangle((MARGIN, 182, MARGIN + max(8, (W - 2 * MARGIN) * progress), 190), radius=4, fill=ACCENT)

    kind = scene["type"]
    if kind == "title":
        y = 520
        y = draw_block(draw, scene.get("heading", ""), font(SANS, 112, SANS_BLACK), y, FG, spacing=1.05)
        draw.rectangle((MARGIN, y + 30, MARGIN + 140, y + 42), fill=ACCENT)
        draw_block(draw, scene.get("sub", ""), font(SANS, 52, SANS_MEDIUM), y + 90, MUTED)
    elif kind == "outro":
        draw.text((MARGIN, 420), "TAKEAWAY", font=font(SANS, 40, SANS_BOLD), fill=ACCENT)
        y = draw_block(draw, scene.get("heading", ""), font(SANS, 92, SANS_BLACK), 490, FG, spacing=1.08)
        y = draw_block(draw, scene.get("sub", ""), font(SANS, 50, SANS_MEDIUM), y + 50, MUTED)
        handle = os.environ.get("DOJO_CHANNEL")
        if handle:
            draw.text((MARGIN, y + 70), f"Follow {handle} for more", font=font(SANS, 44, SANS_BOLD), fill=ACCENT)
    else:
        draw.text((MARGIN, 300), scene.get("kicker", ""), font=font(SANS, 42, SANS_BOLD), fill=tone)
        if kind == "code":
            draw_code(img, draw, scene, 390)
        else:
            y = draw_block(draw, scene.get("heading", ""), font(SANS, 80, SANS_BOLD), 380, FG, spacing=1.1)
            draw_block(draw, scene.get("body", ""), font(SANS, 52, SANS_REGULAR), y + 50, "#C9D1D9", spacing=1.3)

    if show_caption and caption:
        cf = font(SANS, 64, SANS_BOLD)
        lines = wrap(draw, caption, cf, W - 2 * MARGIN - 40)
        if len(lines) == 2:  # balance the two lines instead of leaving one orphan word
            words = caption.split()
            cut = min(range(1, len(words)), key=lambda k: abs(
                draw.textlength(" ".join(words[:k]), font=cf) - draw.textlength(" ".join(words[k:]), font=cf)))
            lines = [" ".join(words[:cut]), " ".join(words[cut:])]
        y = 1520 - len(lines) * 80 // 2
        for line in lines:
            x = (W - draw.textlength(line, font=cf)) / 2
            draw.text((x, y), line, font=cf, fill="#FFFFFF", stroke_width=7, stroke_fill="#000000")
            y += 80
    return img


def caption_chunks(text, max_words=6):
    """Splits narration into short caption chunks, breaking at punctuation when it can."""
    chunks, cur = [], []
    for word in text.split():
        cur.append(word)
        if len(cur) >= max_words or (len(cur) >= 3 and word[-1] in ".,;:!?"):
            chunks.append(" ".join(cur))
            cur = []
    if cur:
        chunks.append(" ".join(cur))
    return chunks


# ---------------------------------------------------------------- render

def render(script_path):
    script_path = Path(script_path).resolve()
    script = json.loads(script_path.read_text())
    validate(script)
    if not shutil.which("ffmpeg"):
        die("ffmpeg is missing: brew install ffmpeg")

    cid = script.get("id", script_path.parent.name)
    out_dir = script_path.parent
    work = WORK / cid
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)

    print(f"  🎙  voice ({os.environ.get('DOJO_TTS', 'say')})")
    audio_parts, timings = [], []
    for i, scene in enumerate(script["scenes"]):
        wav = work / f"scene{i:02}.wav"
        tts(scene["narration"], wav)
        audio_parts.append((wav, SCENE_GAP))
        timings.append(wav_seconds(wav))
    concat_audio(audio_parts, work / "voice.wav")
    total = sum(t + SCENE_GAP for t in timings)

    print("  🎨 frames")
    entries, elapsed = [], 0.0
    for i, (scene, speech) in enumerate(zip(script["scenes"], timings)):
        chunks = caption_chunks(scene["narration"])
        weights = [len(c) for c in chunks]
        for j, chunk in enumerate(chunks):
            dur = speech * weights[j] / sum(weights) + (SCENE_GAP if j == len(chunks) - 1 else 0)
            png = work / f"f{i:02}_{j:02}.png"
            frame(script, scene, chunk, (elapsed + dur / 2) / total).save(png)
            entries.append((png, dur))
            elapsed += dur
    frame(script, script["scenes"][0], None, 0, show_caption=False).save(out_dir / "cover.png")

    # The concat demuxer wants the last image listed twice.
    listing = "".join(f"file '{p}'\nduration {d:.3f}\n" for p, d in entries) + f"file '{entries[-1][0]}'\n"
    (work / "frames.txt").write_text(listing)

    print("  🎬 video")
    mp4 = out_dir / f"{cid}.mp4"
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error",
        "-f", "concat", "-safe", "0", "-i", str(work / "frames.txt"),
        "-i", str(work / "voice.wav"),
        "-fps_mode", "cfr", "-r", str(FPS), "-c:v", "libx264", "-preset", "medium", "-crf", "20",
        "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-shortest", "-movflags", "+faststart",
        str(mp4),
    ], check=True)

    post = script.get("post") or {}
    (out_dir / "post.txt").write_text(
        f"{post.get('title', script.get('title', cid))}\n\n{post.get('caption', '')}\n\n"
        + " ".join(post.get("hashtags", [])) + "\n")
    print(f"\n  ✔ {short(mp4)}  ({total:.0f}s)")
    print(f"    cover:   {short(out_dir / 'cover.png')}")
    print(f"    caption: {short(out_dir / 'post.txt')}\n")


def short(path):
    return path.relative_to(ROOT) if path.is_relative_to(ROOT) else path


if __name__ == "__main__":
    args = sys.argv[1:]
    if len(args) == 2 and args[0] == "prompt":
        print(build_prompt(args[1].upper()))
    elif len(args) == 3 and args[0] == "save":
        save_script(args[1].upper(), args[2])
    elif len(args) == 2 and args[0] == "render":
        render(args[1])
    else:
        print(__doc__)
        sys.exit(2)
