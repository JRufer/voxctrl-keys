#!/usr/bin/env python3
"""
Builds app/src/main/assets/words_en.txt, the typing keyboard's English word
list. Run it again only to change the list; the output is committed.

    pip install wordfreq==3.1.1
    npm pack wordlist-english@1.2.1 && tar xzf wordlist-english-1.2.1.tgz
    python3 scripts/build-wordlist.py package/ > app/src/main/assets/words_en.txt

Frequencies come from wordfreq (data CC BY-SA 4.0, Robyn Speer); the SCOWL
word lists (Kevin Atkinson, permissive licence) remove misspellings, fragments
and other noise. A word is kept if SCOWL has it, if it is a contraction of
words SCOWL has, or if it is very common (Zipf 4 or more: names, places,
"okay", "gonna"). See docs/typing.md. The output is CC BY-SA 4.0, like
wordfreq's data; app/src/main/assets/words_en.LICENSE.txt carries both
sources' notices and must ship with it.

Each line is `word<TAB>zipf`, where zipf is the Zipf frequency times ten
(about 10 to 80), most frequent first.
"""
import json, re, sys
from pathlib import Path
from wordfreq import top_n_list, zipf_frequency

SIZE = 50_000
scowl_dir = Path(sys.argv[1])
scowl = set()
for level in (10, 20, 35, 40, 50, 55, 60):
    for variant in ("english", "american", "british"):
        scowl.update(w.lower() for w in json.loads((scowl_dir / f"{variant}-words-{level}.json").read_text()))

WORD = re.compile(r"^[a-z]+('[a-z]+)?$")
SUFFIXES = ("'s", "'t", "'re", "'ll", "'ve", "'d", "'m")
CAPITALISED = {
    "i", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
    "january", "february", "april", "june", "july", "august", "september", "october",
    "november", "december", "english", "american", "british", "christmas", "easter",
}

def keep(word, zipf):
    if not WORD.match(word):
        return False
    if len(word) == 1 and word not in ("a", "i"):
        return False
    if word in scowl or zipf >= 4.0:
        return True
    if "'" in word:
        stem, _, rest = word.partition("'")
        if "'" + rest in SUFFIXES and (stem in scowl or stem == "i" or word in ("won't", "can't", "shan't")):
            return True
    return False

def display(word):
    stem, apostrophe, rest = word.partition("'")
    if stem in CAPITALISED:
        stem = stem.capitalize()
    return stem + apostrophe + rest

out = []
seen = set()
for word in top_n_list("en", 200_000):
    word = word.replace("’", "'")
    zipf = zipf_frequency(word, "en")
    if word in seen or not keep(word, zipf):
        continue
    seen.add(word)
    out.append((display(word), round(zipf * 10)))
    if len(out) == SIZE:
        break
for word, z in out:
    print(f"{word}\t{z}")
