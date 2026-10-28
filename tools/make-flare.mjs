// Makes the flare's roar (assets/setpoint/sounds/block/flare_roar.ogg): the soft, steady rush of a big gas flame with a
// low rumble under it, as a seamless twelve-second loop. Every filter runs round the sound twice, so its end flows into its
// start. The swell comes from slow waves in whole cycles per loop that never line up, and a few hundred faint crackles fall
// at random, so the ear finds no beat to lock on to. The flare plays it on a loop from its tip.
// Needs the npm package wasm-media-encoders@0.7.0: install it in a scratch folder, put this file there and run
//   node make-flare.mjs <out.ogg>
import { createOggEncoder } from 'wasm-media-encoders';
import { writeFileSync } from 'node:fs';

const out = process.argv[2];
const rate = 44100, n = rate * 12;
let seed = 20261028;
const random = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff * 2 - 1; };
const noise = Float32Array.from({ length: n }, random);

// a one-pole low-pass, round in a circle
function lowpass(x, alpha) {
  const y = new Float32Array(n);
  let v = 0;
  for (let pass = 0; pass < 2; pass++) for (let i = 0; i < n; i++) { v += (x[i] - v) * alpha; if (pass) y[i] = v; }
  return y;
}
// the rush: noise between about 100 and 700 Hz; the rumble: a soft bed below that
const rush = lowpass(lowpass(noise, 0.09), 0.09), floor = lowpass(noise, 0.014);
const rumble = lowpass(lowpass(noise, 0.006), 0.012);
const crackles = new Float32Array(n);
for (let k = 0; k < 300; k++) crackles[Math.floor((random() + 1) / 2 * n) % n] = random() * (0.5 + Math.abs(random()));
const crackle = lowpass(crackles, 0.25);

// slow swells: 5, 7, 11 and 17 cycles in twelve seconds, at random phases
const waves = [5, 7, 11, 17].map((c) => ({ c, phase: (random() + 1) * Math.PI, depth: 0.06 + Math.abs(random()) * 0.05 }));

const pcm = new Float32Array(n);
let peak = 0;
for (let i = 0; i < n; i++) {
  const t = i / n; // 0 to 1 round the loop
  let swell = 1;
  for (const w of waves) swell += w.depth * Math.sin(2 * Math.PI * w.c * t + w.phase);
  pcm[i] = (rush[i] - floor[i]) * 2.2 * swell + rumble[i] * 14 + crackle[i] * 0.25;
  peak = Math.max(peak, Math.abs(pcm[i]));
}
for (let i = 0; i < n; i++) pcm[i] = Math.tanh(pcm[i] / peak * 1.1) * 0.45;

const encoder = await createOggEncoder();
encoder.configure({ channels: 1, sampleRate: rate, vbrQuality: 4 });
const parts = [encoder.encode([pcm]).slice(), encoder.finalize().slice()];
writeFileSync(out, Buffer.concat(parts.map((p) => Buffer.from(p))));
console.log('wrote', out, n, 'samples');
