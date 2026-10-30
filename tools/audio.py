"""Makes the sound clips in app/src/main/assets/audio for kana, words, kanji examples, sentences and grammar.

Usage: python tools/audio.py [--voice jvnv-F1-jp] [--only-missing] [--recheck]
Needs: pip install style-bert-vits2 pyopenjtalk-plus   (and ffmpeg with libopus on the PATH)
       pip install faster-whisper scipy                  (for --recheck)

Words and sentences come from Style-Bert-VITS2 with a JVNV voice (CC BY-SA 4.0), which takes pitch
accent from OpenJTalk. Single kana come from OpenJTalk's own HTS voice: the neural voice turns a lone
syllable into a sigh, and speech recognition could make out the plain voice far better. Each clip is named like the app does it (data/Audio.kt): the first 16 hex digits of the
SHA-1 of what is said ("written|reading" for words). When OpenJTalk would read a word or sentence
differently from our reading (一日 as いちにち instead of ついたち), the clip is made from the kana instead.

The neural voice sometimes slurs a short word. --recheck lets a speech recognizer (Whisper small) listen
to every clip; when it can't make one out, the clip is made again with other random seeds and, for words,
finally with the plain voice, and the first take the recognizer gets right is kept.
"""
import argparse
import hashlib
import os
import re
import subprocess

import numpy as np
import pyopenjtalk
import torch
from huggingface_hub import hf_hub_download
from style_bert_vits2.constants import Languages
from style_bert_vits2.nlp import bert_models
from style_bert_vits2.tts_model import TTSModel

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
OUT = os.path.join(ASSETS, "audio")
KANA_KT = os.path.join(ROOT, "app", "src", "main", "java", "io", "github", "realkhfan", "kotoba", "data", "Kana.kt")
VOICES = {
    "jvnv-F1-jp": "jvnv-F1-jp_e160_s14000.safetensors",
    "jvnv-F2-jp": "jvnv-F2_e166_s20000.safetensors",
    "jvnv-M1-jp": "jvnv-M1-jp_e158_s14000.safetensors",
    "jvnv-M2-jp": "jvnv-M2-jp_e159_s17000.safetensors",
}


def key(text):
    return hashlib.sha1(text.encode("utf-8")).hexdigest()[:16]


def lines(name):
    for line in open(os.path.join(ASSETS, name), encoding="utf-8"):
        line = line.strip()
        if line and not line.startswith("#"):
            yield line


def pron(s):
    return re.sub(r"[、。？！?!…「」\s]", "", pyopenjtalk.g2p(s, kana=True))


def sounds(s):
    """Kana as heard, for comparing with a recognizer: hiragana, long vowels and を folded together."""
    k = pron(s).replace("ヲ", "オ")
    out = []
    for c in (chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in k):
        if c == "ー" or (out and c in "いう" and out[-1] in "えけげせぜてでねへべぺめれおこごそぞとどのほぼぽもよろょ"):
            continue
        out.append(c)
    return "".join(out)


def spoken(text, reading):
    """What to feed the voice: the text itself if it is read the way we read it, else our kana."""
    return text if reading is None or pron(text) == pron(reading) else reading


def clips():
    """(key text, text to say, is a single kana) for everything the app can play."""
    out = {}
    spec = open(KANA_KT, encoding="utf-8").read().split('"""')[1]
    for row in spec.strip().splitlines():
        part, cells = row.split()[0], row.split()[2:]
        for cell in cells:
            if cell == "-":
                continue
            hira = cell.split(":")[0]
            kata = "".join(chr(ord(c) + 0x60) for c in hira)
            if part != "E":
                out[hira] = (hira, True)
            out[kata] = (hira, True)
    for line in lines("words.txt"):
        if line.startswith("## "):
            continue
        f = line.split("|")
        written, reading = (f[0], f[0]) if len(f) == 2 else (f[0], f[1])
        out[f"{written}|{reading}"] = (spoken(written, reading), False)
    for line in lines("kanji.txt"):
        if line.startswith("## "):
            continue
        for example in line.split("|")[4].split("; "):
            word, reading, _ = example.split(" ", 2)
            out.setdefault(f"{word}|{reading}", (spoken(word, reading), False))
    for line in lines("sentences.txt"):
        if line.startswith("## "):
            continue
        ja, reading, _ = line.split("|")
        out[ja.replace(" ", "")] = (spoken(ja.replace(" ", ""), reading.replace(" ", "")), False)
    for name in ("grammar-n5.txt", "grammar-n4.txt"):
        for line in lines(name):
            if line.startswith("ex: "):
                ja, reading, _ = [x.strip() for x in line[4:].split("|")]
                out.setdefault(ja, (spoken(ja, reading), False))
            elif line.startswith("q: "):
                f = line[3:].split("|")
                filled = f[0].replace("＿＿", f[2]).split("(")[0].strip()
                out.setdefault(filled, (filled, False))
    return out


def tidy(audio, sr):
    """Trims silence, leaves a short pause on both ends and evens out the loudness."""
    a = audio.astype(np.float32)
    if np.abs(a).max() > 2.0:  # int16-scaled samples (OpenJTalk gives them as floats)
        a = a / 32768.0
    frame = sr // 100
    energy = np.array([np.sqrt(np.mean(a[i:i + frame] ** 2)) for i in range(0, len(a), frame)])
    loud = np.where(energy > max(energy.max() * 0.03, 1e-4))[0]
    if len(loud):
        a = a[max(0, loud[0] * frame - frame * 3): min(len(a), (loud[-1] + 4) * frame)]
    a = a / max(np.abs(a).max(), 1e-6) * 0.89
    pad = np.zeros(int(sr * 0.06), dtype=np.float32)
    return np.concatenate([pad, a, pad])


def encode(audio, sr, path):
    pcm = (np.clip(audio, -1, 1) * 32767).astype("<i2").tobytes()
    subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-y", "-f", "s16le", "-ar", str(sr), "-ac", "1", "-i", "-",
         "-ar", "24000", "-c:a", "libopus", "-b:a", "24k", "-application", "voip",
         "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", path],
        input=pcm, check=True,
    )


def make(model, say, single, seed=0):
    if single:
        audio, sr = pyopenjtalk.tts(say, speed=0.8)
    else:
        torch.manual_seed(seed)
        # The voice's text front end trips over ！, … and quote marks in the middle of a sentence.
        text = say.replace("！", "。").replace("…", "、").replace("「", "").replace("」", "")
        sr, audio = model.infer(text=text, length=1.1 if len(say) <= 6 else 1.0)
    return tidy(audio, sr), sr


def distance(a, b):
    d = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        prev, d[0] = d[0], i
        for j, cb in enumerate(b, 1):
            prev, d[j] = d[j], min(d[j] + 1, d[j - 1] + 1, prev + (ca != cb))
    return d[-1]


def understood(asr, audio, sr, k, say):
    """Words must be heard exactly; a sentence may have a slip or two (recognizers guess at particles)."""
    import scipy.signal
    a16 = scipy.signal.resample_poly(audio, 16000, sr).astype(np.float32)
    heard = sounds("".join(s.text for s in asr.transcribe(a16, language="ja", beam_size=5)[0]))
    targets = {sounds(t) for t in (say, *k.split("|"))}
    if "|" in k:
        return heard in targets
    return min(distance(heard, t) / max(len(t), 1) for t in targets) <= 0.15


def recheck(model, todo):
    from faster_whisper import WhisperModel
    asr = WhisperModel("small", device="cpu", compute_type="int8")
    fixed, left = [], []
    for k, (say, single) in sorted(todo.items()):
        if single:
            continue  # one syllable is too little for a recognizer
        path = os.path.join(OUT, key(k) + ".ogg")
        raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", path, "-f", "s16le", "-ac", "1", "-ar", "16000", "-"],
                             capture_output=True, check=True).stdout
        if understood(asr, np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0, 16000, k, say):
            continue
        takes = [(say, False, seed) for seed in (1, 2, 3)] + ([(say, True, 0)] if "|" in k else [])
        for args in takes:
            audio, sr = make(model, *args)
            if understood(asr, audio, sr, k, say):
                encode(audio, sr, path)
                fixed.append(k)
                print(f"remade {k} ({'plain voice' if args[1] else f'seed {args[2]}'})", flush=True)
                break
        else:
            left.append(k)
            print("kept", k, flush=True)
    print(f"recheck: {len(fixed)} clips made again, {len(left)} kept as they were")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--voice", default="jvnv-M2-jp", choices=VOICES)
    ap.add_argument("--only-missing", action="store_true")
    ap.add_argument("--recheck", action="store_true", help="remake clips a speech recognizer can't make out")
    args = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    todo = clips()
    wanted = {key(k) for k in todo}
    for f in os.listdir(OUT):  # clips for content that no longer exists
        if f.removesuffix(".ogg") not in wanted:
            os.remove(os.path.join(OUT, f))
    bert = "ku-nlp/deberta-v2-large-japanese-char-wwm"
    bert_models.load_model(Languages.JP, bert)
    bert_models.load_tokenizer(Languages.JP, bert)
    bert_models.load_model(Languages.JP).float()  # newer transformers load it as float16, the voice wants float32
    files = [hf_hub_download("litagin/style_bert_vits2_jvnv", f"{args.voice}/{f}")
             for f in (VOICES[args.voice], "config.json", "style_vectors.npy")]
    model = TTSModel(model_path=files[0], config_path=files[1], style_vec_path=files[2], device="cpu")
    made = {}  # the same speech for several keys (あ and ア) is made once
    os.makedirs(os.path.join(ROOT, "build"), exist_ok=True)
    # What each clip says, for checking (not shipped).
    with open(os.path.join(ROOT, "build", "audio-list.txt"), "w", encoding="utf-8") as listing:
        for i, (k, (say, single)) in enumerate(sorted(todo.items())):
            path = os.path.join(OUT, key(k) + ".ogg")
            listing.write(f"{key(k)}\t{k}\t{say}\n")
            if args.only_missing and os.path.exists(path):
                continue
            if say not in made:
                made[say] = make(model, say, single)
            encode(*made[say], path)
            if i % 100 == 0:
                print(i, "/", len(todo), k, "→", say, flush=True)
    print("done:", len(todo), "clips")
    if args.recheck:
        recheck(model, todo)


if __name__ == "__main__":
    main()
