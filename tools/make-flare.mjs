// Makes the flare's roar (assets/setpoint/sounds/block/flare_roar.ogg): the deep rumble of a big flame with a softer
// rushing on top and now and then a crackle, as a seamless four-second loop. Every filter runs round the sound twice, so
// its end flows into its start, and the flutter is in whole cycles per loop. The flare plays it on a loop from its tip.
// Needs the npm package wasm-media-encoders@0.7.0: install it in a scratch folder, put this file there and run
//   node make-flare.mjs <out.ogg>
import { createOggEncoder } from 'wasm-media-encoders';
import { writeFileSync } from 'node:fs';

const out = process.argv[2];
const rate = 44100, n = rate * 4;
let seed = 20261022;
const random = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff * 2 - 1; };
const noise = Float32Array.from({ length: n }, random);

// a one-pole low-pass, round in a circle
function lowpass(x, alpha) {
  const y = new Float32Array(n);
  let v = 0;
  for (let pass = 0; pass < 2; pass++) for (let i = 0; i < n; i++) { v += (x[i] - v) * alpha; if (pass) y[i] = v; }
  return y;
}
const deep = lowpass(lowpass(noise, 0.015), 0.03);
const mid = lowpass(noise, 0.1), low = lowpass(noise, 0.025);
const crackles = new Float32Array(n);
for (let k = 0; k < 40; k++) crackles[Math.floor((random() + 1) / 2 * n) % n] = random() * 6;
const crackle = lowpass(crackles, 0.3);

const pcm = new Float32Array(n);
let peak = 0;
for (let i = 0; i < n; i++) {
  const t = i / n; // 0 to 1 round the loop
  const flutter = 1 + 0.2 * Math.sin(2 * Math.PI * 3 * t) + 0.12 * Math.sin(2 * Math.PI * 7 * t + 1);
  pcm[i] = deep[i] * 9 + (mid[i] - low[i]) * 1.6 * flutter + crackle[i] * 0.5;
  peak = Math.max(peak, Math.abs(pcm[i]));
}
for (let i = 0; i < n; i++) pcm[i] = Math.tanh(pcm[i] / peak * 1.4) * 0.6;

const encoder = await createOggEncoder();
encoder.configure({ channels: 1, sampleRate: rate, vbrQuality: 4 });
const parts = [encoder.encode([pcm]).slice(), encoder.finalize().slice()];
writeFileSync(out, Buffer.concat(parts.map((p) => Buffer.from(p))));
console.log('wrote', out, n, 'samples');
