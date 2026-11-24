# Figure della mappa muscoli

Script che generano in Blender le quattro figure della mappa muscoli: uomo e donna, fronte e retro.
Producono anche le zone toccabili dei muscoli in `FigureMuscoli.kt`.

## Cosa serve

- **Blender 5.3**, nel percorso standard; un altro percorso si indica con la variabile `BLENDER`.
- **Node.js**, per generare le zone.
- **Due file da scaricare una volta sola:**
  - `add-on-mpfb-v2.0.18.zip`, il plugin MPFB, da [Blender Extensions](https://extensions.blender.org/add-ons/mpfb/) (GPL v3, circa 46 MB);
  - `makehuman_system_assets_cc0.zip`, le risorse MakeHuman, da [MakeHuman Community](https://static.makehumancommunity.org/assets/assetpacks/makehuman_system_assets.html) (CC0, circa 281 MB).
    Il secondo mirror (`files.makehumancommunity.org`) è molto più veloce del primo.

Le risorse MakeHuman sono CC0 e le immagini prodotte si possono usare senza restrizioni
([FAQ MPFB](https://static.makehumancommunity.org/mpfb/faq/use_in_closed_source.html)).

## Preparazione

```bash
bash strumenti/figure/installa.sh percorso/add-on-mpfb-v2.0.18.zip percorso/makehuman_system_assets_cc0.zip
```

Plugin e risorse finiscono in `strumenti/figure/profilo/`, escluso da git.
Il profilo Blender personale non viene toccato.

## Rigenerare tutto

```bash
bash strumenti/figure/tutto.sh
```

Lo script fa i quattro render in `out/` (pochi secondi l'uno con una GPU NVIDIA, tramite OptiX)
e li ritaglia in WebP 712 × 1530 dentro `app/src/main/res/drawable-nodpi/`.
Poi rigenera `app/src/main/java/com/my/gymbro/ui/FigureMuscoli.kt` da `zone.txt`.

Per una prova veloce di una sola vista:

```bash
bash strumenti/figure/rendi.sh donna fronte strumenti/figure/out/prova.png '{"altezza":1100,"campioni":32}'
```

## Dove cambiare le cose

| Cosa | Dove |
|---|---|
| Corpo, età, viso, capelli, carnagione, posa delle braccia | `CONF` in `persona.py` |
| Pantaloncini e top: quote di orlo, vita, elastico e top, in frazioni dell'altezza | `short` e `top` in `CONF` |
| Forma del top (scollo, spalline, vogatore) | `taglio_top` in `forme.py` |
| Barba corta, attaccatura dei capelli, abbronzatura | `colora` in `forme.py` |
| Rilievo muscolare (solchi e gonfiori) | `R` in `forme.py` e `rilievi_extra.py` |
| Luci | funzione `luce` in fondo a `persona.py` |
| Zone toccabili e colorate dei muscoli | `zone.txt` |

Il rilievo e le zone usano le coordinate del render intero 896 × 1600 e descrivono solo la metà destra
(x ≥ 448); l'altra metà è speculare. `griglia.py` sovrappone una griglia ogni 50 px ai render,
per ricalcare le zone:

```bash
bash strumenti/figure/blender.sh -b -P "$PWD/strumenti/figure/griglia.py" -- griglia.png strumenti/figure/out/uomo_fronte.png
```

Se si sposta la figura nel render, vanno aggiornati insieme `zone.txt`, il rilievo e il riquadro
di ritaglio, che compare sia in `esporta.py` sia in `FiguraRealistica.kt` (`RITAGLIO_*`).

## Regole da mantenere

- **Età fissa di 28 anni** (`age=0.523` in `persona.py`): le figure sono sempre adulte.
- **Figure sempre vestite.** Sotto i vestiti la pelle è dipinta del colore della stoffa e levigata,
  così la stoffa non lascia vedere dettagli anatomici. Se cambi le quote dei vestiti, prima fai una prova
  a bassa risoluzione con una pelle già vestita, per esempio `young_caucasian_female_special_suit`.
