#!/usr/bin/env bash
# Un render: bash rendi.sh <uomo|donna> <fronte|retro> <uscita.png> [json con opzioni]
# Opzioni utili: {"altezza":2000,"campioni":200} per la qualità finale, {"ritratto":true} per il primo piano.
QUI="$(cd "$(dirname "$0")" && pwd)"
bash "$QUI/blender.sh" -b -P "$QUI/persona.py" -- "$@" 2>&1 | grep -E "FATTO|rror|Traceback|mancante|File \".*figure" | head
