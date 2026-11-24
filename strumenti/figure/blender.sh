#!/usr/bin/env bash
# Avvia Blender con un profilo separato in ./profilo: non usa né modifica il profilo Blender personale.
# Il percorso di Blender si può cambiare con la variabile BLENDER.
QUI="$(cd "$(dirname "$0")" && pwd)"
BLENDER="${BLENDER:-/c/Program Files/Blender Foundation/Blender 5.3/blender.exe}"
export BLENDER_USER_RESOURCES="$QUI/profilo"
cd "$QUI" && exec "$BLENDER" "$@"
