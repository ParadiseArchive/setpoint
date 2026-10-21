// Makes the vehicle horn sounds (assets/setpoint/sounds/vehicle/horn.ogg and horn_low.ogg).
// Needs the npm package wasm-media-encoders@0.7.0: install it in a scratch folder, put this file there and run
//   node make-horn.mjs <out.ogg> [pitch]      (pitch 1 for the pallet truck, 0.8 for the forklift)
import { createOggEncoder } from 'wasm-media-encoders';
import { writeFileSync } from 'node:fs';

const out = process.argv[2], pitch = +(process.argv[3] || 1);
const rate = 44100;
const honks = [[0, 0.24], [0.33, 0.62]]; // start and end of each honk, in seconds
const total = 0.75, n = Math.round(total * rate);
const tones = [370 * pitch, 466 * pitch]; // a major third, like a car's two horns
const pcm = new Float32Array(n);
let lp = 0;
for (let i = 0; i < n; i++) {
  const t = i / rate;
  let env = 0;
  for (const [a, b] of honks) {
    if (t < a || t > b) continue;
    env = Math.min(1, (t - a) / 0.012, (b - t) / 0.04); // quick attack, short release
  }
  if (env <= 0) { pcm[i] = lp *= 0.9; continue; }
  const wobble = 1 + 0.004 * Math.sin(2 * Math.PI * 7 * t); // the diaphragm is not quite steady
  let s = 0;
  for (const f of tones) {
    const ph = (t * f * wobble) % 1;
    s += (2 * ph - 1) * 0.6 + Math.sin(2 * Math.PI * f * wobble * t) * 0.4; // part saw, part sine
  }
  s = Math.tanh(2.2 * s) * 0.8;     // driven hard: brassy
  lp += (s - lp) * 0.35;            // and a little softened
  pcm[i] = lp * env;
}
let peak = 0;
for (const v of pcm) peak = Math.max(peak, Math.abs(v));
for (let i = 0; i < n; i++) pcm[i] *= 0.7 / peak;

const encoder = await createOggEncoder();
encoder.configure({ channels: 1, sampleRate: rate, vbrQuality: 5 });
const parts = [encoder.encode([pcm]).slice(), encoder.finalize().slice()];
writeFileSync(out, Buffer.concat(parts.map((p) => Buffer.from(p))));
console.log('wrote', out, n, 'samples');
