// Draws the Setpoint logo: "SETPOINT" in yellow block letters in front of a Minecraft-style factory at dusk.
// 256x144 pixel art (16:9). Run from the project root:
//   node tools/draw-logo.mjs [extra output path ...]
// Always writes src/main/resources/logo.png (the mod list logo); extra paths get a copy (the website).
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { deflateSync } from 'node:zlib';

const W = 256, H = 144;
const px = new Uint8Array(W * H * 4);
const hex = (c) => [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)];
function set(x, y, c, alpha = 1) {
  x = Math.round(x); y = Math.round(y);
  if (x < 0 || y < 0 || x >= W || y >= H) return;
  const [r, g, b] = hex(c), i = (y * W + x) * 4;
  px[i] = Math.round(px[i] * (1 - alpha) + r * alpha);
  px[i + 1] = Math.round(px[i + 1] * (1 - alpha) + g * alpha);
  px[i + 2] = Math.round(px[i + 2] * (1 - alpha) + b * alpha);
  px[i + 3] = 255;
}
const rect = (x, y, w, h, c, a) => { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) set(i, j, c, a); };
let seed = 7;
const rand = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
const pick = (list) => list[Math.floor(rand() * list.length)];
/** A Minecraft-like textured area: every pixel picks from a small palette. */
const textured = (x, y, w, h, palette) => { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) set(i, j, pick(palette)); };

// ---------- sky: dusk in hard bands, with stars ----------
const SKY = ['#0a0c13', '#0d1020', '#12142c', '#191838', '#231c45', '#311f52', '#43225c', '#5a2664', '#772b68', '#953466', '#b2435f'];
for (let y = 0; y < 116; y++) rect(0, y, W, 1, SKY[Math.min(SKY.length - 1, Math.floor(y / 116 * SKY.length))]);
for (let i = 0; i < 40; i++) { const x = Math.floor(rand() * W), y = Math.floor(rand() * 50); set(x, y, rand() < 0.2 ? '#2de2e6' : '#d9deea', 0.5 + rand() * 0.5); }
// a square setting sun, Minecraft style
rect(228, 84, 18, 18, '#ff8a4c'); rect(230, 86, 14, 14, '#ffb547'); rect(233, 89, 8, 8, '#ffd27a');
// far hills
for (let x = 0; x < W; x++) {
  const h = Math.round(8 + 5 * Math.sin(x / 19) + 3 * Math.sin(x / 7 + 1));
  rect(x, 116 - h, 1, h, '#2a1940');
}

// ---------- the factory ----------
const STONE = ['#7a7a7a', '#6f6f6f', '#858585', '#6a6a6a', '#7f7f7f'];
const BRICK = ['#8e3b2a', '#9c4530', '#843424', '#a14a33'];
const IRON = ['#b9bec7', '#aeb3bc', '#c5cad2', '#a7acb5'];
const COPPER = ['#c06b47', '#d07a52', '#b5603e'];
const DARK = '#14161c';

// main hall: stone brick walls with mortar lines
const HX = 70, HY = 70, HW = 120, HH = 50;
textured(HX, HY, HW, HH, STONE);
for (let y = HY + 3; y < HY + HH; y += 4) rect(HX, y, HW, 1, '#555555');
for (let y = HY, row = 0; y < HY + HH; y += 4, row++) for (let x = HX + (row % 2 ? 4 : 0); x < HX + HW; x += 8) rect(x, y, 1, 3, '#555555');
rect(HX, HY, HW, 1, '#9a9a9a'); rect(HX + HW - 1, HY, 1, HH, '#4a4a4a');
// sawtooth roof: each tooth has a glass north light
for (let t = 0; t < 5; t++) {
  const x0 = HX + t * 24;
  for (let i = 0; i < 24; i++) {
    const h = Math.floor(i * 12 / 24); // slope up to the right
    textured(x0 + i, HY - h, 1, h, ['#4a4f5a', '#555b67', '#434853']);
  }
  rect(x0 + 23, HY - 12, 1, 12, DARK);
  for (let j = 0; j < 11; j++) set(x0 + 22, HY - 11 + j, j % 3 ? '#2de2e6' : '#178a8d', 0.9); // glass
}
// windows with warm light, a few dark ones
for (let r = 0; r < 2; r++) for (let c = 0; c < 9; c++) {
  const x = HX + 6 + c * 13, y = HY + 8 + r * 16;
  rect(x - 1, y - 1, 9, 9, DARK);
  const lit = rand() < 0.75;
  rect(x, y, 7, 7, lit ? '#9ee7ff' : '#262a35');
  if (lit) { rect(x, y, 7, 1, '#e4f9ff'); rect(x + 3, y, 1, 7, '#4fa9c4'); rect(x, y + 3, 7, 1, '#4fa9c4'); }
}
// big door
rect(HX + 52, HY + 32, 16, 18, DARK); textured(HX + 53, HY + 33, 14, 17, ['#3a3f4a', '#424754', '#373c46']);
for (let y = HY + 35; y < HY + HH; y += 3) rect(HX + 53, y, 14, 1, '#2a2e37');
// cyan status lights on the hall
for (const x of [HX + 4, HX + 112]) { rect(x, HY + 4, 2, 2, '#2de2e6'); }

// chimneys: brick with a white band, smoke drifting right
for (const [cx, top] of [[88, 18], [106, 26]]) {
  textured(cx, top, 10, HY - top, BRICK);
  for (let y = top + 2; y < HY; y += 3) rect(cx, y, 10, 1, '#5e2418');
  rect(cx, top + 6, 10, 3, '#d9deea');
  rect(cx - 1, top, 12, 2, '#3b2a28');
  // blocky puffs, like Minecraft's smoke particles: growing, fading, drifting with the wind
  for (let i = 0; i < 5; i++) {
    const size = 4 + i * 2, sx = cx + 3 + i * 6 + i * i, sy = top - 6 - i * 5, a = 0.9 - i * 0.1;
    rect(sx - size / 2, sy - size / 2, size, size, '#c9ccd4', a);
    rect(sx - size / 2, sy - size / 2, size, 1, '#e6e8ee', a);
    rect(sx - size / 2, sy + size / 2 - 1, size, 1, '#8f94a0', a);
  }
}

// storage tank on the left: iron rings, a blue sight glass and a ladder
const TX = 14, TY = 66, TW = 40, TH = 54;
textured(TX, TY, TW, TH, IRON);
for (let x = TX; x < TX + TW; x++) { const shade = Math.abs(x - (TX + TW * 0.35)) / TW; rect(x, TY, 1, TH, '#000000', shade * 0.5); }
for (let y = TY + 8; y < TY + TH; y += 10) rect(TX, y, TW, 1, '#7d828b');
rect(TX - 2, TY - 3, TW + 4, 3, '#8e939c'); rect(TX + 4, TY - 5, TW - 8, 2, '#a3a8b1');
rect(TX + 26, TY + 6, 4, TH - 10, DARK); rect(TX + 27, TY + 26, 2, TH - 30, '#3a6fd8'); rect(TX + 27, TY + 26, 2, 1, '#6a9af0');
for (let y = TY + 2; y < TY + TH; y += 3) rect(TX + 6, y, 5, 1, '#4a4f5a');
rect(TX + 6, TY, 1, TH, '#4a4f5a'); rect(TX + 10, TY, 1, TH, '#4a4f5a');

// distillation column on the right, with platforms and a flare
const CX = 206, CW = 12;
textured(CX, 26, CW, 94, IRON);
for (let x = CX; x < CX + CW; x++) rect(x, 26, 1, 94, '#000000', Math.abs(x - (CX + 4)) / CW * 0.5);
for (let y = 40; y < 120; y += 16) { rect(CX - 4, y, CW + 8, 1, '#ffb547'); rect(CX - 4, y - 3, 1, 3, '#ffb547'); rect(CX + CW + 3, y - 3, 1, 3, '#ffb547'); }
rect(CX + 5, 14, 2, 12, '#7d828b');
rect(CX + 4, 9, 4, 5, '#ff8a4c'); rect(CX + 5, 6, 2, 4, '#ffd27a'); set(CX + 5, 5, '#fff3c4');

// copper pipe rack between tank, hall and column
for (const y of [104, 110]) { textured(54, y, 16, 3, COPPER); textured(190, y, 16, 3, COPPER); rect(54, y + 2, 16, 1, '#7a3f28'); rect(190, y + 2, 16, 1, '#7a3f28'); }
// a pump by the tank
textured(56, 112, 8, 8, ['#4f5562', '#636a79']); rect(57, 113, 2, 2, '#2de2e6');

// ---------- ground: a row of grass blocks over dirt ----------
for (let x = 0; x < W; x += 8) {
  textured(x, 120, 8, 24, ['#79553a', '#8b5f3f', '#6a4a32', '#966c4a']);
  textured(x, 120, 8, 3, ['#5d9b3a', '#6aab42', '#4f8a31']);
  for (let i = 0; i < 8; i++) if (rand() < 0.5) set(x + i, 123, pick(['#5d9b3a', '#4f8a31']));
}

// ---------- SETPOINT in yellow block letters ----------
const GLYPHS = {
  S: ['.###.', '#...#', '#....', '.###.', '....#', '#...#', '.###.'],
  E: ['#####', '#....', '#....', '####.', '#....', '#....', '#####'],
  T: ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '..#..'],
  P: ['####.', '#...#', '#...#', '####.', '#....', '#....', '#....'],
  O: ['.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
  I: ['###', '.#.', '.#.', '.#.', '.#.', '.#.', '###'],
  N: ['#...#', '##..#', '#.#.#', '#..##', '#...#', '#...#', '#...#'],
};
const S = 4, WORD = 'SETPOINT';
const widths = [...WORD].map((c) => GLYPHS[c][0].length);
const total = (widths.reduce((a, b) => a + b, 0) + WORD.length - 1) * S;
const ox = Math.floor((W - total) / 2), oy = 98;
const cells = [];
let gx = ox;
for (const c of WORD) {
  GLYPHS[c].forEach((row, j) => [...row].forEach((ch, i) => { if (ch === '#') cells.push([gx + i * S, oy + j * S]); }));
  gx += (GLYPHS[c][0].length + 1) * S;
}
const filled = new Set(cells.map(([x, y]) => x + ',' + y));
// deep drop shadow, then a dark outline, then shaded yellow blocks
for (const [x, y] of cells) rect(x + 3, y + 3, S, S, '#1a0f00', 0.85);
for (const [x, y] of cells) rect(x - 2, y - 2, S + 4, S + 4, '#2a1600');
for (const [x, y] of cells) {
  rect(x, y, S, S, '#ffd23f');
  if (!filled.has(x + ',' + (y - S))) rect(x, y, S, 1, '#fff08a');       // lit top edge
  if (!filled.has(x + ',' + (y + S))) rect(x, y + S - 1, S, 1, '#d98e1e'); // shaded bottom edge
  if (!filled.has((x + S) + ',' + y)) rect(x + S - 1, y, 1, S, '#e8a62a');
}

// ---------- write ----------
const CRC = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = (buf) => { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type), data]), crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function png(scale) {
  const w = W * scale, h = H * scale, raw = Buffer.alloc(h * (1 + w * 4));
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const from = ((Math.floor(y / scale)) * W + Math.floor(x / scale)) * 4;
    raw.set(px.subarray(from, from + 4), y * (1 + w * 4) + 1 + x * 4);
  }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
for (const [path, scale] of [['src/main/resources/logo.png', 2], ...process.argv.slice(2).map((p) => [p, 1])]) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, png(scale));
  console.log('wrote', path);
}
