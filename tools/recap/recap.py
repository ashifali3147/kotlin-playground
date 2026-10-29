"""Turn a solved dojo challenge into a 1080x1920 animated short with narration and captions.

    recap.py prompt <ID> [en|hi]           print the script-writing prompt (diff + journal + tests)
    recap.py save <ID> <raw-file> [en|hi]  extract + validate the JSON, write recaps/<ID>/script.<lang>.json
    recap.py render <script.json>          render <ID>.<lang>.mp4, cover.<lang>.png, post.<lang>.txt next to it

Normally driven by `./dojo recap <ID>`. Voice is picked by DOJO_TTS (voicestudio | say | openai | elevenlabs);
unset means VoiceStudio when its local backend is running, else macOS say. The video itself is drawn by the
Remotion project in tools/recap/video.
"""

import json
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RECAPS = ROOT / "recaps"
WORK = ROOT / "build" / "recap"
VIDEO = Path(__file__).resolve().parent / "video"

SCENE_GAP = 0.45  # seconds of silence after each scene's narration
LANGS = ("en", "hi")


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


def build_prompt(cid, lang):
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
        id=cid, lang=lang, language=LANGUAGE[lang], title=ch["title"], concept=ch["concept"],
        readme=readme, tests=tests, journal=entry, diff=diff, files="\n\n".join(files),
    )


LANGUAGE = {
    "en": """\
Write all viewer-facing text (narration, heading, sub, body, analogy labels, diagram labels, result
text) in plain spoken English. Leave out "caption": the subtitles show the narration.""",
    "hi": """\
The AUDIO is Hinglish, everything ON SCREEN is English.
- "narration" (spoken only, never shown): natural HINGLISH, the way an Indian senior developer explains
  to a friend: Hindi grammar written in Devanagari, with technical and common English words kept in
  English (Latin script): coroutine, thread, lock, scope, cancel, list, screen, user, bug, fix...
  Example: "Data class का copy shallow होता है. Snapshot को नया cart मिलता है, पर list वही पुरानी है."
  Avoid formal or textbook Hindi (no "समवर्ती", "अनुरोध").
- "caption" (shown as subtitles): a faithful plain-English version of that scene's narration, same
  meaning, same order, similar length.
- heading, sub, body, analogy labels, diagram labels and result text: plain English.""",
}

PROMPT = """\
You write the script for a 60 to 80 second animated vertical short (YouTube Shorts / Instagram Reels)
that recaps a concurrency challenge the learner just solved: what was breaking, what happens inside
the runtime, what they changed, and the concept behind it. It must make sense to a beginner, so
pair every technical idea with something they can picture.

Build the story from the learner's JOURNAL ENTRY: their explanation, their wording, their "aha".
Use the diff and code only to be accurate. Never invent behaviour the code and tests don't show.
If the journal gets something technically wrong, correct it gently in the WHY IT WORKS scene.

LANGUAGE ({lang}):
{language}

SCENES, in this order:
1. "title": the hook. A relatable app symptom phrased as something viewers have seen.
2. "text", kicker "THE SYMPTOM", with an emoji "icon": what the app's user experienced (from the tests).
3. "analogy", kicker "THINK OF IT LIKE": a real-world picture of the bug with 2 to 4 emoji items
   (e.g. two cooks writing on one order slip).
4. "diagram", kicker "WHAT'S HAPPENING INSIDE": animate the broken mechanism (result tone "bad").
5. "code", kicker "THE BUG", tone "bad": the smallest BEFORE snippet that shows the bug.
6. "diff", kicker "THE FIX": "before" = that snippet, "after" = the learner's fixed version of it.
   The video morphs one into the other, so keep unchanged lines identical in both.
7. optional "diagram", kicker "AFTER THE FIX": the same diagram kind, now behaving (result tone "good").
8. "text", kicker "WHY IT WORKS": the concept, in one idea.
9. optional "text", kicker "INTERVIEW TIP": the follow-up question an interviewer would ask.
10. "outro": a one-line takeaway worth remembering.

DIAGRAMS: pick the kind that best shows the mechanism. Steps animate one by one in order, synced
to the narration, so order them the way the narration tells the story. Labels max 4 words.
- "timeline": lanes are threads/coroutines side by side, time flows down. Races, ordering, interleaving.
  {{"kind":"timeline","lanes":["Riya","Kabir"],
   "steps":[{{"lane":0,"label":"seat 7 free?"}},{{"lane":1,"label":"seat 7 free?"}},
            {{"lane":0,"label":"book seat 7"}},{{"lane":1,"label":"book seat 7","tone":"bad"}}],
   "result":{{"text":"one seat, two tickets","tone":"bad"}}}}
- "tree": parent/child jobs or scopes; events recolour nodes (running|done|failed|cancelled).
  Failure and cancellation propagation, structured concurrency.
  {{"kind":"tree","nodes":[{{"id":"p","label":"album job"}},{{"id":"a","label":"resize","parent":"p"}}],
   "events":[{{"id":"a","state":"failed","label":"out of disk"}},{{"id":"p","state":"cancelled"}}],
   "result":{{"text":"...","tone":"bad"}}}}
- "pipeline": Flow operators as stages top to bottom; items flow down; "drop_at" = stage index where
  an item is dropped (debounce, conflate, distinctUntilChanged, filter). Survivors reach the collector.
  {{"kind":"pipeline","stages":["sensor","filter > 30°","alert"],
   "items":[{{"label":"24°","drop_at":1}},{{"label":"31°"}},{{"label":"28°","drop_at":1}}],
   "result":{{"text":"only hot readings alert","tone":"good"}}}}
- "memory": variables (kind "var") pointing at objects (kind "object", optional "lines" of fields).
  Shared mutable state, references, caches, leaks.
  {{"kind":"memory","boxes":[{{"id":"a","label":"cart","kind":"var"}},{{"id":"l","label":"MutableList","kind":"object","lines":["burger"]}}],
   "arrows":[{{"from":"a","to":"l"}}],"result":{{"text":"...","tone":"bad"}}}}
- "locks": threads (top) holding/waiting on locks (bottom); steps "hold" | "wait" | "release".
  Deadlocks, contention, lock ordering. A "bad" result makes a wait cycle pulse.
  {{"kind":"locks","threads":["Robot 1","Robot 2"],"locks":["Paint","Brush"],
   "steps":[{{"thread":0,"lock":0,"action":"hold"}},{{"thread":1,"lock":1,"action":"hold"}},
            {{"thread":0,"lock":1,"action":"wait"}},{{"thread":1,"lock":0,"action":"wait"}}],
   "result":{{"text":"both robots stuck forever","tone":"bad"}}}}
Indices ("lane", "thread", "lock", "drop_at") are 0-based into their lists.

NARRATION (read aloud by text-to-speech):
- Short spoken sentences, 140 to 190 words in total across all scenes.
- Each scene's narration must describe what that scene shows, in the same order.
- Every scene has "narration" and, when the language rules ask for it, "caption".
- No code symbols, backticks or brackets. Say identifiers as words ("with context", "Global Scope").
  Never read code out line by line.
ON SCREEN: heading max 8 words, body max 25 words. Code max 12 lines, max 44 characters per line,
"// ..." for omitted parts; "highlight" = 1-based line numbers within the snippet.
POST: a title (max 70 chars), a 1 to 3 sentence caption, 5 to 8 hashtags.

Reply with ONLY the JSON, no prose, no code fences:
{{
  "id": "{id}",
  "lang": "{lang}",
  "title": "<short video title>",
  "scenes": [
    {{"type": "title", "heading": "...", "sub": "...", "narration": "...", "caption": "..."}},
    {{"type": "text", "kicker": "THE SYMPTOM", "icon": "😤", "heading": "...", "body": "...", "narration": "..."}},
    {{"type": "analogy", "kicker": "THINK OF IT LIKE", "heading": "...", "items": [{{"icon": "👨‍🍳", "label": "..."}}], "body": "...", "narration": "..."}},
    {{"type": "diagram", "kicker": "WHAT'S HAPPENING INSIDE", "heading": "...", "diagram": {{...}}, "narration": "..."}},
    {{"type": "code", "kicker": "THE BUG", "tone": "bad", "file": "X.kt", "code": "...", "highlight": [2], "narration": "..."}},
    {{"type": "diff", "kicker": "THE FIX", "file": "X.kt", "before": "...", "after": "...", "narration": "..."}},
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


def save_script(cid, raw_path, lang):
    raw = Path(raw_path).read_text()
    m = re.search(r"\{.*\}", raw, re.S)
    if not m:
        die(f"The script writer didn't return JSON. Raw reply: {raw_path}")
    try:
        script = json.loads(m.group(0))
    except json.JSONDecodeError as e:
        die(f"Script JSON is malformed ({e}). Raw reply: {raw_path}")
    script["lang"] = lang
    problems = validate(script)
    if problems:
        die("The script has problems:\n    " + "\n    ".join(problems) + f"\n    Raw reply: {raw_path}")
    out = RECAPS / cid / f"script.{lang}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(script, indent=2, ensure_ascii=False) + "\n")
    print(out)


def validate(script):
    """Returns a list of problems; the renderer would crash or draw nonsense on any of them."""
    problems = []
    scenes = script.get("scenes") or []
    if not scenes:
        return ["no scenes"]
    for i, s in enumerate(scenes, 1):
        where = f"scene {i} ({s.get('type')})"
        kind = s.get("type")
        if not str(s.get("narration", "")).strip():
            problems.append(f"{where}: narration is empty")
        if script.get("lang") == "hi" and not str(s.get("caption", "")).strip():
            problems.append(f"{where}: missing English caption")
        if kind in ("title", "text", "analogy", "outro") and not s.get("heading"):
            problems.append(f"{where}: missing heading")
        if kind == "analogy" and not s.get("items"):
            problems.append(f"{where}: analogy without items")
        elif kind == "code" and not str(s.get("code", "")).strip():
            problems.append(f"{where}: no code")
        elif kind == "diff" and not (s.get("before") and s.get("after")):
            problems.append(f"{where}: diff needs before and after")
        elif kind == "diagram":
            problems += [f"{where}: {p}" for p in validate_diagram(s.get("diagram") or {})]
        elif kind not in ("title", "text", "analogy", "code", "diff", "diagram", "outro"):
            problems.append(f"{where}: unknown scene type")
    return problems


def validate_diagram(d):
    kind = d.get("kind")
    out = []

    def indices(items, field, size, name):
        for k, it in enumerate(items):
            v = it.get(field)
            if v is not None and not (isinstance(v, int) and 0 <= v < size):
                out.append(f"{name} {k + 1}: {field}={v!r} is not a valid index (0..{size - 1})")

    if kind == "timeline":
        lanes = d.get("lanes") or []
        if not lanes or not d.get("steps"):
            out.append("timeline needs lanes and steps")
        indices(d.get("steps") or [], "lane", len(lanes), "step")
        if any(s.get("lane") is None for s in d.get("steps") or []):
            out.append("every timeline step needs a lane")
    elif kind == "tree":
        ids = {n.get("id") for n in d.get("nodes") or []}
        if not ids:
            out.append("tree needs nodes")
        out += [f"node {n.get('id')}: unknown parent {n['parent']!r}" for n in d.get("nodes") or []
                if n.get("parent") and n["parent"] not in ids]
        out += [f"event for unknown node {e.get('id')!r}" for e in d.get("events") or [] if e.get("id") not in ids]
    elif kind == "pipeline":
        stages = d.get("stages") or []
        if not stages or not d.get("items"):
            out.append("pipeline needs stages and items")
        indices(d.get("items") or [], "drop_at", len(stages), "item")
    elif kind == "memory":
        ids = {b.get("id") for b in d.get("boxes") or []}
        if not ids:
            out.append("memory needs boxes")
        out += [f"arrow {a.get('from')}->{a.get('to')}: unknown box" for a in d.get("arrows") or []
                if a.get("from") not in ids or a.get("to") not in ids]
    elif kind == "locks":
        if not d.get("threads") or not d.get("locks") or not d.get("steps"):
            out.append("locks needs threads, locks and steps")
        indices(d.get("steps") or [], "thread", len(d.get("threads") or []), "step")
        indices(d.get("steps") or [], "lock", len(d.get("locks") or []), "step")
    else:
        out.append(f"unknown diagram kind {kind!r}")
    return out


# ---------------------------------------------------------------- voice

VOICESTUDIO_URL = os.environ.get("DOJO_VOICESTUDIO_URL", "http://127.0.0.1:3900").rstrip("/")


def voicestudio_up():
    try:
        # A closed port fails instantly; the long timeout only covers a backend that's still warming up.
        with urllib.request.urlopen(f"{VOICESTUDIO_URL}/v1/audio/voices", timeout=20) as resp:
            return resp.status == 200
    except OSError:
        return False


# A designed voice saved as a clone profile, so every scene is the same speaker. Without a profile,
# OmniVoice invents a new speaker per request.
NARRATOR = "Dojo Narrator"
NARRATOR_STYLE = "male, middle-aged, low pitch, indian accent"
NARRATOR_SEED = 42
NARRATOR_REF = ("नमस्ते, मैं आपका dojo narrator हूँ. आज हम एक bug समझेंगे, आराम से, step by step. "
                "Thread, coroutine और Flow, सब simple तरीके से.")


def voicestudio(path, data=None, headers=None, timeout=300):
    req = urllib.request.Request(f"{VOICESTUDIO_URL}{path}", data=data, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.read()
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        try:
            body = json.loads(body)["error"]["message"]
        except (ValueError, KeyError, TypeError):
            body = body[:300]
        die(f"VoiceStudio said HTTP {e.code}: {body}")


def ensure_narrator():
    """Creates the narrator profile on first use: design the voice once, then clone it for every scene."""
    profiles = json.loads(voicestudio("/profiles", timeout=30))
    if any(p.get("name") == NARRATOR for p in profiles):
        return
    print(f"  (creating the '{NARRATOR}' voice in VoiceStudio, one time)")
    ref = voicestudio("/v1/audio/speech", json.dumps({
        "model": "tts-1", "voice": "alloy", "input": NARRATOR_REF, "response_format": "wav",
        "instructions": NARRATOR_STYLE, "seed": NARRATOR_SEED,
    }).encode(), {"Content-Type": "application/json"})
    boundary = "dojo-narrator-boundary"
    fields = {"name": NARRATOR, "kind": "clone", "language": "Hindi", "ref_text": NARRATOR_REF}
    body = b"".join(
        f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode() for k, v in fields.items()
    ) + (f'--{boundary}\r\nContent-Disposition: form-data; name="ref_audio"; filename="narrator.wav"\r\n'
         f"Content-Type: audio/wav\r\n\r\n").encode() + ref + f"\r\n--{boundary}--\r\n".encode()
    voicestudio("/profiles", body, {"Content-Type": f"multipart/form-data; boundary={boundary}"}, timeout=60)


def tts_provider():
    chosen = os.environ.get("DOJO_TTS")
    if chosen:
        if chosen == "voicestudio" and not voicestudio_up():
            die(f"VoiceStudio isn't answering at {VOICESTUDIO_URL}. Open the VoiceStudio app first.")
        return chosen
    if voicestudio_up():
        return "voicestudio"
    print("  (VoiceStudio isn't running, using macOS say. Open the app for a better voice.)")
    return "say"


def tts(text, out_wav, provider):
    """Writes 24 kHz mono 16-bit WAV."""
    raw = out_wav.with_suffix(".raw")
    if provider == "voicestudio":
        # OpenAI-compatible endpoint of the local VoiceStudio backend; loopback needs no key.
        body = {"model": "tts-1", "voice": os.environ.get("DOJO_VOICE", NARRATOR), "input": text, "response_format": "wav"}
        raw.write_bytes(voicestudio("/v1/audio/speech", json.dumps(body).encode(), {"Content-Type": "application/json"}))
        to_wav(raw, out_wav)
        return
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
        die(f"Unknown DOJO_TTS={provider!r} (use voicestudio, say, openai or elevenlabs)")
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




# ---------------------------------------------------------------- render

def remotion(*args):
    subprocess.run(["npx", "--no-install", "remotion", *args], check=True, cwd=VIDEO)


def render(script_path):
    script_path = Path(script_path).resolve()
    script = json.loads(script_path.read_text())
    problems = validate(script)
    if problems:
        die("script has problems:\n    " + "\n    ".join(problems))
    if not shutil.which("ffmpeg"):
        die("ffmpeg is missing: brew install ffmpeg")
    if not (VIDEO / "node_modules").exists():
        die(f"Video toolkit not installed: (cd {VIDEO.relative_to(ROOT)} && npm ci)")

    cid = script.get("id", script_path.parent.name)
    lang = script.get("lang", "en")
    out_dir = script_path.parent
    work = WORK / f"{cid}.{lang}"
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)

    provider = tts_provider()
    if provider == "voicestudio" and "DOJO_VOICE" not in os.environ:
        ensure_narrator()
    print(f"  🎙  voice ({provider})")
    audio_parts, timings, start = [], [], 0.0
    for i, scene in enumerate(script["scenes"]):
        wav = work / f"scene{i:02}.wav"
        tts(scene["narration"], wav, provider)
        speech = wav_seconds(wav)
        audio_parts.append((wav, SCENE_GAP))
        timings.append({"start": round(start, 3), "speech": round(speech, 3), "duration": round(speech + SCENE_GAP, 3)})
        start += speech + SCENE_GAP
    concat_audio(audio_parts, work / "voice.wav")

    props = {"script": script, "timings": timings, "total": round(start, 3), "audio": "voice.wav",
             "channel": os.environ.get("DOJO_CHANNEL")}
    (work / "props.json").write_text(json.dumps(props, ensure_ascii=False))
    (work / "cover-props.json").write_text(json.dumps({**props, "showCaptions": False}, ensure_ascii=False))

    print("  🎬 animating")
    mp4 = out_dir / f"{cid}.{lang}.mp4"
    common = [f"--props={work / 'props.json'}", f"--public-dir={work}", "--log=error"]
    remotion("render", "src/index.ts", "Recap", str(mp4), *common)
    cover = out_dir / f"cover.{lang}.png"
    remotion("still", "src/index.ts", "Recap", str(cover), "--frame=40",
             f"--props={work / 'cover-props.json'}", f"--public-dir={work}", "--log=error")

    post = script.get("post") or {}
    (out_dir / f"post.{lang}.txt").write_text(
        f"{post.get('title', script.get('title', cid))}\n\n{post.get('caption', '')}\n\n"
        + " ".join(post.get("hashtags", [])) + "\n")
    print(f"\n  ✔ {short(mp4)}  ({start:.0f}s)")
    print(f"    cover:   {short(cover)}")
    print(f"    caption: {short(out_dir / f'post.{lang}.txt')}\n")


def short(path):
    return path.relative_to(ROOT) if path.is_relative_to(ROOT) else path


def lang_arg(args, i):
    lang = args[i] if len(args) > i else "hi"
    if lang not in LANGS:
        die(f"Unknown language {lang!r} (use {' or '.join(LANGS)})")
    return lang


if __name__ == "__main__":
    args = sys.argv[1:]
    if len(args) in (2, 3) and args[0] == "prompt":
        print(build_prompt(args[1].upper(), lang_arg(args, 2)))
    elif len(args) in (3, 4) and args[0] == "save":
        save_script(args[1].upper(), args[2], lang_arg(args, 3))
    elif len(args) == 2 and args[0] == "render":
        render(args[1])
    else:
        print(__doc__)
        sys.exit(2)
