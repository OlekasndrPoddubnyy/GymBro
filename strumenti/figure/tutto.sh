#!/usr/bin/env bash
# Rigenera le quattro figure e le zone dei muscoli e le copia nell'app.
# Uso: bash tutto.sh
set -e
QUI="$(cd "$(dirname "$0")" && pwd)"
APP="$QUI/../../app/src/main"
mkdir -p "$QUI/out"
for chi in uomo donna; do
  for vista in fronte retro; do
    bash "$QUI/rendi.sh" "$chi" "$vista" "$QUI/out/${chi}_${vista}.png" '{"altezza":2000,"campioni":200}'
  done
done
# ritaglio 712 x 1530 in WebP (lo stesso riquadro usato in FiguraRealistica.kt)
bash "$QUI/blender.sh" -b -P "$QUI/esporta.py" -- "$QUI/out" "$APP/res/drawable-nodpi" 2>&1 | grep -E "WEBP|rror"
node "$QUI/genera.js" "$QUI/zone.txt" "$APP/java/com/my/gymbro/ui/FigureMuscoli.kt" "$QUI/out/punti.json"
