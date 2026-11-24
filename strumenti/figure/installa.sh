#!/usr/bin/env bash
# Prepara il profilo Blender separato con MPFB e le risorse MakeHuman.
# Uso: bash installa.sh <add-on-mpfb-v2.0.18.zip> <makehuman_system_assets_cc0.zip>
set -e
QUI="$(cd "$(dirname "$0")" && pwd)"
MPFB="$1"
RISORSE="$2"
if [ ! -f "$MPFB" ] || [ ! -f "$RISORSE" ]; then
  echo "Uso: bash installa.sh <add-on-mpfb-v2.0.18.zip> <makehuman_system_assets_cc0.zip>"
  exit 1
fi
bash "$QUI/blender.sh" --command extension install-file -r user_default -e "$MPFB"
DATI="$QUI/profilo/extensions/.user/user_default/mpfb/data"
mkdir -p "$DATI"
unzip -qn "$RISORSE" -d "$DATI"
echo "Pronto: profilo in $QUI/profilo"
