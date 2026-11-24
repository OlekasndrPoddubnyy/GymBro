// zone.txt -> FigureMuscoli.kt + punti.json (un punto ben interno per ogni zona, per la prova dei tocchi)
// Uso: node genera.js <zone.txt> <uscita.kt> <punti.json>
const fs = require('fs');
const [src, out, punti] = process.argv.slice(2);
const MUSCOLI = ('PETTO DELTOIDI_ANT DELTOIDI_LAT DELTOIDI_POST CUFFIA BICIPITI TRICIPITI AVAMBRACCI ADDOME OBLIQUI ' +
  'TRAPEZIO DORSALI LOMBARI GLUTEI QUADRICIPITI ADDUTTORI FEMORALI POLPACCI').split(' ');

function curva(p) {
  // Catmull-Rom chiusa -> Bezier
  const r = [];
  for (let i = 0; i < p.length; i++) {
    const [a, b, c, d] = [-1, 0, 1, 2].map(j => p[(i + j + p.length) % p.length]);
    for (let t = 0; t < 1; t += 1 / 8) {
      r.push([0, 1].map(k => .5 * (2 * b[k] + (c[k] - a[k]) * t + (2 * a[k] - 5 * b[k] + 4 * c[k] - d[k]) * t * t
        + (-a[k] + 3 * b[k] - 3 * c[k] + d[k]) * t * t * t)));
    }
  }
  return r;
}
function svg(p) {
  const f = v => +v.toFixed(1);
  let s = `M${p[0][0]},${p[0][1]}`;
  for (let i = 0; i < p.length; i++) {
    const [a, b, c, d] = [-1, 0, 1, 2].map(j => p[(i + j + p.length) % p.length]);
    s += ` C${f(b[0] + (c[0] - a[0]) / 6)},${f(b[1] + (c[1] - a[1]) / 6)}` +
      ` ${f(c[0] - (d[0] - b[0]) / 6)},${f(c[1] - (d[1] - b[1]) / 6)} ${c[0]},${c[1]}`;
  }
  return s + ' Z';
}
function dentro(q, x, y) {
  let r = false;
  for (let i = 0, j = q.length - 1; i < q.length; j = i++) {
    if ((q[i][1] > y) !== (q[j][1] > y) && x < (q[j][0] - q[i][0]) * (y - q[i][1]) / (q[j][1] - q[i][1]) + q[i][0]) r = !r;
  }
  return r;
}
const dist = (q, x, y) => Math.min(...q.map(([a, b]) => Math.hypot(a - x, b - y)));

const viste = {};
let v;
for (const riga of fs.readFileSync(src, 'utf8').split(/\r?\n/)) {
  if (!riga || riga[0] === '#') continue;
  if (riga[0] === '@') { v = viste[riga.slice(1)] = []; continue; }
  const [m, resto] = riga.split(': ');
  if (!MUSCOLI.includes(m)) throw 'muscolo ignoto ' + m;
  for (const t of resto.split(' | ')) v.push({ m, p: t.trim().split(/\s+/).map(c => c.split(',').map(Number)) });
}
for (const sesso of ['UOMO', 'DONNA']) {
  const visti = new Set([...viste[sesso + '_FRONTE'], ...viste[sesso + '_RETRO']].map(z => z.m));
  const manca = MUSCOLI.filter(m => !visti.has(m));
  if (manca.length) throw sesso + ' senza ' + manca;
}

const prova = {};
for (const [nome, zone] of Object.entries(viste)) {
  zone.forEach(z => { z.q = curva(z.p); });
  prova[nome] = zone.map((z, i) => {
    let best = null;
    const xs = z.q.map(a => a[0]), ys = z.q.map(a => a[1]);
    for (let x = Math.min(...xs); x < Math.max(...xs); x += 1.5) {
      for (let y = Math.min(...ys); y < Math.max(...ys); y += 2) {
        if (!dentro(z.q, x, y) || zone.slice(i + 1).some(o => dentro(o.q, x, y))) continue;
        const d = dist(z.q, x, y);
        if (!best || d > best.d) best = { x, y, d };
      }
    }
    if (!best) throw nome + ' zona coperta ' + z.m;
    return { m: z.m, x: best.x, y: best.y };
  });
}
fs.writeFileSync(punti, JSON.stringify(prova));

let k = `package com.my.gymbro.ui

import com.my.gymbro.ui.Muscolo.*

/*
 * Zone muscolari ricalcate sui render in res/drawable-nodpi/figura_*.webp.
 * Coordinate del render intero 896 x 1600; ogni tracciato descrive la metà destra (x >= 448)
 * e l'altra si ottiene specchiando. L'ordine conta: le zone successive vincono nel tocco.
 */
`;
for (const [nome, zone] of Object.entries(viste)) {
  k += `\ninternal val ZONE_${nome}: List<Zona> = listOf(\n`;
  for (const z of zone) k += `    Zona(${z.m}, "${svg(z.p)}"),\n`;
  k += ')\n';
}
fs.writeFileSync(out, k);
console.log('ok', Object.entries(viste).map(([n, z]) => `${n} ${z.length}`).join(', '));
