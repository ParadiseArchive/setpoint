// Makes the control room's alarm horn (assets/setpoint/sounds/block/alarm_horn.ogg): the two-tone call of a plant
// alarm, high-low-high-low, soft at the edges so it is clear but not harsh. The alarm beacon plays it every two seconds.
// Needs the npm package wasm-media-encoders@0.7.0: install it in a scratch folder, put this file there and run
//   node make-alarm.mjs <out.ogg>
import { createOggEncoder } from 'wasm-media-encoders';
import { writeFileSync } from 'node:fs';

const out = process.argv[2];
const rate = 44100, note = 0.32, gap = 0.04, tones = [660, 523, 660, 523]; // a falling major third, twice
const n = Math.round(rate * (tones.length * (note + gap) + 0.1));
const pcm = new Float32Array(n);
tones.forEach((f, k) => {
  const start = Math.round(k * (note + gap) * rate), length = Math.round(note * rate), fade = Math.round(0.025 * rate);
  for (let i = 0; i < length; i++) {
    const t = i / rate;
    let s = 0;
    for (const [h, a] of [[1, 1], [3, 0.33], [5, 0.16], [7, 0.08]]) s += a * Math.sin(2 * Math.PI * f * h * t); // a mellow square: a horn, not a beep
    const envelope = Math.min(1, i / fade, (length - i) / fade);
    pcm[start + i] += Math.tanh(1.6 * s) * envelope;
  }
});
let lp = 0, peak = 0;
for (let i = 0; i < n; i++) { lp += (pcm[i] - lp) * 0.45; pcm[i] = lp; peak = Math.max(peak, Math.abs(lp)); }
for (let i = 0; i < n; i++) pcm[i] *= 0.6 / peak;

const encoder = await createOggEncoder();
encoder.configure({ channels: 1, sampleRate: rate, vbrQuality: 5 });
const parts = [encoder.encode([pcm]).slice(), encoder.finalize().slice()];
writeFileSync(out, Buffer.concat(parts.map((p) => Buffer.from(p))));
console.log('wrote', out, n, 'samples');
