// Makes the vehicle horn sounds (assets/setpoint/sounds/vehicle/horn.ogg and horn_low.ogg): one steady two-tone
// note, exactly one second long, that loops without a seam (every tone fits a whole number of times in the second),
// so the horn holds the same note for as long as you hold H.
// Needs the npm package wasm-media-encoders@0.7.0: install it in a scratch folder, put this file there and run
//   node make-horn.mjs <out.ogg> [low]      (no second argument for the pallet truck, "low" for the forklift)
import { createOggEncoder } from 'wasm-media-encoders';
import { writeFileSync } from 'node:fs';

const out = process.argv[2], low = process.argv[3] === 'low';
const rate = 44100, n = rate; // one second
const tones = low ? [296, 373] : [370, 466]; // a major third, like a car's two horns; whole hertz, so the loop is seamless
const raw = (i) => {
  const t = (i % n) / rate;
  let s = 0;
  for (const f of tones) s += (2 * ((t * f) % 1) - 1) * 0.6 + Math.sin(2 * Math.PI * f * t) * 0.4; // part saw, part sine
  return Math.tanh(2.2 * s) * 0.8; // driven hard: brassy
};
const pcm = new Float32Array(n);
let lp = 0;
for (let i = 0; i < 2 * n; i++) { // a little softened; the first second only warms the filter up, so the end meets the start
  lp += (raw(i) - lp) * 0.35;
  if (i >= n) pcm[i - n] = lp;
}
let peak = 0;
for (const v of pcm) peak = Math.max(peak, Math.abs(v));
for (let i = 0; i < n; i++) pcm[i] *= 0.7 / peak;

const encoder = await createOggEncoder();
encoder.configure({ channels: 1, sampleRate: rate, vbrQuality: 5 });
const parts = [encoder.encode([pcm]).slice(), encoder.finalize().slice()];
writeFileSync(out, Buffer.concat(parts.map((p) => Buffer.from(p))));
console.log('wrote', out, n, 'samples');
