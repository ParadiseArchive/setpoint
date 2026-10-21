// Renders Setpoint's blocks as isometric pixel-art PNGs for the website, straight from the real
// block models and textures (no Minecraft needed). Run from the project root:
//   node tools/render-showcase.mjs <output folder>
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { deflateSync, inflateSync } from 'node:zlib';

const ASSETS = 'src/main/resources/assets/setpoint';
const OUT = process.argv[2] || 'build/showcase';
mkdirSync(OUT, { recursive: true });

// ---------- PNG read / write (RGBA 8 bit) ----------
function readPng(path) {
  const buf = readFileSync(path);
  let o = 8, w = 0, h = 0, type = 6;
  const idat = [];
  while (o < buf.length) {
    const len = buf.readUInt32BE(o), kind = buf.toString('ascii', o + 4, o + 8), data = buf.subarray(o + 8, o + 8 + len);
    if (kind === 'IHDR') { w = data.readUInt32BE(0); h = data.readUInt32BE(4); type = data[9]; }
    if (kind === 'IDAT') idat.push(data);
    o += 12 + len;
  }
  const bpp = type === 6 ? 4 : 3, stride = w * bpp, raw = inflateSync(Buffer.concat(idat));
  const px = Buffer.alloc(w * h * 4), line = Buffer.alloc(stride), prev = Buffer.alloc(stride);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)];
    for (let i = 0; i < stride; i++) {
      const x = raw[y * (stride + 1) + 1 + i], a = i >= bpp ? line[i - bpp] : 0, b = prev[i], c = i >= bpp ? prev[i - bpp] : 0;
      const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
      line[i] = (x + [0, a, b, (a + b) >> 1, pa <= pb && pa <= pc ? a : pb <= pc ? b : c][f]) & 255;
    }
    for (let x = 0; x < w; x++) {
      for (let k = 0; k < 3; k++) px[(y * w + x) * 4 + k] = line[x * bpp + k];
      px[(y * w + x) * 4 + 3] = bpp === 4 ? line[x * bpp + 3] : 255;
    }
    line.copy(prev);
  }
  return { w, h, px };
}
const CRC = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = (buf) => { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type), data]), crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function writePng(path, w, h, px) {
  const raw = Buffer.alloc(h * (1 + w * 4));
  for (let y = 0; y < h; y++) px.copy(raw, y * (1 + w * 4) + 1, y * w * 4, (y + 1) * w * 4);
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
  writeFileSync(path, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]));
}

// ---------- models ----------
const textures = new Map();
const texture = (id) => {
  if (!textures.has(id)) textures.set(id, readPng(join(ASSETS, 'textures', id.replace('setpoint:', '') + '.png')));
  return textures.get(id);
};
const cube = (t) => [{ from: [0, 0, 0], to: [16, 16, 16], faces: Object.fromEntries(Object.entries(t).map(([f, v]) => [f, { texture: v }])) }];
const PARENTS = { // the vanilla parents the mod uses, as plain cubes
  'minecraft:block/cube': (t) => cube({ north: t.north, south: t.south, east: t.east, west: t.west, up: t.up, down: t.down }),
  'minecraft:block/cube_all': (t) => cube({ north: t.all, south: t.all, east: t.all, west: t.all, up: t.all, down: t.all }),
  'minecraft:block/cube_bottom_top': (t) => cube({ north: t.side, south: t.side, east: t.side, west: t.side, up: t.top, down: t.bottom }),
  'minecraft:block/orientable_with_bottom': (t) => cube({ north: t.front, south: t.side, east: t.side, west: t.side, up: t.top, down: t.bottom }),
};
function model(name) {
  const m = JSON.parse(readFileSync(join(ASSETS, 'models/block', name + '.json'), 'utf8'));
  const t = m.textures || {};
  const resolve = (ref) => { while (ref && ref.startsWith('#')) ref = t[ref.slice(1)]; return ref; };
  const refs = Object.fromEntries(Object.keys(t).map((k) => [k, '#' + k]));
  const elements = m.elements || (PARENTS[m.parent] ? PARENTS[m.parent](refs) : []);
  return elements.map((e) => ({ ...e, faces: Object.fromEntries(Object.entries(e.faces).map(([f, v]) => [f, { ...v, texture: resolve(v.texture) }])) }));
}

// face corners: origin at (u1, v1), then the u edge and the v edge, following Minecraft's uv layout
function faceQuad(face, [x1, y1, z1], [x2, y2, z2]) {
  switch (face) {
    case 'north': return { o: [x2, y2, z1], u: [x1, y2, z1], v: [x2, y1, z1], uv: [16 - x2, 16 - y2, 16 - x1, 16 - y1] };
    case 'south': return { o: [x1, y2, z2], u: [x2, y2, z2], v: [x1, y1, z2], uv: [x1, 16 - y2, x2, 16 - y1] };
    case 'east': return { o: [x2, y2, z2], u: [x2, y2, z1], v: [x2, y1, z2], uv: [16 - z2, 16 - y2, 16 - z1, 16 - y1] };
    case 'west': return { o: [x1, y2, z1], u: [x1, y2, z2], v: [x1, y1, z1], uv: [z1, 16 - y2, z2, 16 - y1] };
    case 'up': return { o: [x1, y2, z1], u: [x2, y2, z1], v: [x1, y2, z2], uv: [x1, z1, x2, z2] };
    case 'down': return { o: [x1, y1, z2], u: [x2, y1, z2], v: [x1, y1, z1], uv: [x1, 16 - z2, x2, 16 - z1] };
  }
}

// ---------- isometric rasteriser ----------
const VSCALE = 1.125; // vertical squash, 2:1 pixel-art isometric
const project = ([x, y, z]) => [x - z, (x + z) / 2 - y * VSCALE];
const VIEW = [1, 1 / VSCALE, 1];
const depth = ([x, y, z]) => x * VIEW[0] + y * VIEW[1] + z * VIEW[2];
const sub = (a, b) => a.map((v, i) => v - b[i]);
const rotY = ([x, y, z], deg) => { // blockstate y rotation, clockwise seen from above
  for (let r = 0; r < ((deg / 90) % 4 + 4) % 4; r++) [x, z] = [16 - z, x];
  return [x, y, z];
};
const SHADE = { up: 1, down: 0.5, north: 0.8, south: 0.8, east: 0.6, west: 0.6 };

/** parts: [{ model, at: [bx, by, bz] (blocks), y: rotation }] */
function render(file, parts, scale = 4, pad = 2) {
  const quads = [];
  for (const { model: name, at = [0, 0, 0], y = 0 } of parts) {
    for (const e of model(name)) {
      for (const [face, f] of Object.entries(e.faces)) {
        const q = faceQuad(face, e.from, e.to);
        const place = (p) => rotY(p, y).map((v, i) => v + at[i] * 16);
        const o = place(q.o), u = place(q.u), v = place(q.v);
        const n = [ // normal from the edges, to cull back faces
          (u[1] - o[1]) * (v[2] - o[2]) - (u[2] - o[2]) * (v[1] - o[1]),
          (u[2] - o[2]) * (v[0] - o[0]) - (u[0] - o[0]) * (v[2] - o[2]),
          (u[0] - o[0]) * (v[1] - o[1]) - (u[1] - o[1]) * (v[0] - o[0]),
        ];
        // faces wind so that the normal points inwards; flip it
        if (-(n[0] * VIEW[0] + n[1] * VIEW[1] + n[2] * VIEW[2]) <= 1e-9) continue;
        const axis = Math.abs(n[1]) > Math.abs(n[0]) && Math.abs(n[1]) > Math.abs(n[2]) ? (n[1] < 0 ? 'up' : 'down')
          : Math.abs(n[0]) > Math.abs(n[2]) ? (n[0] < 0 ? 'east' : 'west') : (n[2] < 0 ? 'south' : 'north');
        quads.push({ o, u, v, uv: f.uv || q.uv, tex: texture(f.texture), shade: SHADE[axis] });
      }
    }
  }
  // bounds
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  for (const q of quads) for (const p of [q.o, q.u, q.v, q.u.map((c, i) => c + q.v[i] - q.o[i])]) {
    const [sx, sy] = project(p);
    minX = Math.min(minX, sx); maxX = Math.max(maxX, sx); minY = Math.min(minY, sy); maxY = Math.max(maxY, sy);
  }
  const W = Math.ceil(maxX - minX) + pad * 2, H = Math.ceil(maxY - minY) + pad * 2;
  const px = Buffer.alloc(W * H * 4), zbuf = new Float64Array(W * H).fill(-Infinity);
  for (const q of quads) {
    const s0 = project(q.o), su = sub(project(q.u), s0), sv = sub(project(q.v), s0);
    const det = su[0] * sv[1] - su[1] * sv[0];
    if (Math.abs(det) < 1e-9) continue;
    const xs = [0, su[0], sv[0], su[0] + sv[0]].map((v) => v + s0[0] - minX + pad), ys = [0, su[1], sv[1], su[1] + sv[1]].map((v) => v + s0[1] - minY + pad);
    const [u1, v1, u2, v2] = q.uv, du = sub(q.u, q.o), dv = sub(q.v, q.o);
    for (let py = Math.floor(Math.min(...ys)); py <= Math.ceil(Math.max(...ys)); py++) {
      for (let pxx = Math.floor(Math.min(...xs)); pxx <= Math.ceil(Math.max(...xs)); pxx++) {
        if (pxx < 0 || py < 0 || pxx >= W || py >= H) continue;
        const dx = pxx + 0.5 - (s0[0] - minX + pad), dy = py + 0.5 - (s0[1] - minY + pad);
        const a = (dx * sv[1] - dy * sv[0]) / det, b = (su[0] * dy - su[1] * dx) / det;
        if (a < 0 || a >= 1 || b < 0 || b >= 1) continue;
        const tex = (val, lo, hi) => Math.min(15, Math.max(0, Math.floor(hi >= lo ? val : val - 1e-6)));
        const tx = tex(u1 + a * (u2 - u1), u1, u2), ty = tex(v1 + b * (v2 - v1), v1, v2);
        const t = q.tex, ti = ((ty * t.h / 16 | 0) * t.w + (tx * t.w / 16 | 0)) * 4;
        if (t.px[ti + 3] < 128) continue;
        const d = depth(q.o.map((c, i) => c + a * du[i] + b * dv[i])), i = py * W + pxx;
        if (d <= zbuf[i]) continue;
        zbuf[i] = d;
        for (let k = 0; k < 3; k++) px[i * 4 + k] = Math.round(t.px[ti + k] * q.shade);
        px[i * 4 + 3] = 255;
      }
    }
  }
  save(file, W, H, px, scale);
}

/** Integer upscale with a 1 px dark outline around the silhouette. */
function save(file, W, H, px, scale) {
  const out = Buffer.alloc(W * H * 4);
  px.copy(out);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = (y * W + x) * 4;
    if (px[i + 3]) continue;
    const near = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([ox, oy]) => {
      const nx = x + ox, ny = y + oy;
      return nx >= 0 && ny >= 0 && nx < W && ny < H && px[(ny * W + nx) * 4 + 3];
    });
    if (near) { out[i] = 5; out[i + 1] = 6; out[i + 2] = 10; out[i + 3] = 255; }
  }
  const big = Buffer.alloc(W * scale * H * scale * 4);
  for (let y = 0; y < H * scale; y++) for (let x = 0; x < W * scale; x++) {
    out.copy(big, (y * W * scale + x) * 4, (((y / scale) | 0) * W + ((x / scale) | 0)) * 4, (((y / scale) | 0) * W + ((x / scale) | 0)) * 4 + 4);
  }
  writePng(join(OUT, file + '.png'), W * scale, H * scale, big);
  console.log('wrote', file, W * scale + 'x' + H * scale);
}

// ---------- the pictures ----------
const S = 180, E = 90, W_ = 270; // fronts toward the camera
const pipe = (at, arms) => [{ model: 'pipe_core', at }, ...arms.map((y) => ({ model: 'pipe_arm', at, y }))];

render('pump', [{ model: 'pump_running', y: 90 }]);
render('pump_alarm', [{ model: 'pump_alarm', y: 90 }]);
render('tank', [{ model: 'tank_5' }]);
render('pipe', pipe([0, 0, 0], [E, W_, S]));
render('control_valve', [{ model: 'control_valve_3', y: 90 }]);
render('level_transmitter', [{ model: 'level_transmitter', y: S }]);
render('controller', [{ model: 'controller', y: S }]);
render('stirling_engine', [{ model: 'stirling_engine_on', y: S }]);
render('biogas_generator', [{ model: 'biogas_generator_on', y: S }]);
render('creative_generator', [{ model: 'creative_generator' }]);
render('boiler', [{ model: 'boiler_on', y: S }]);
render('steam_turbine', [{ model: 'steam_turbine_on', y: S }]);

// steam: feed water tank -> pump -> control valve -> boiler with a turbine on top and the drum level controller beside it
render('steam_plant', [
  { model: 'tank_6', at: [0, 0, 0] },
  ...pipe([1, 0, 0], [E, W_]),
  { model: 'pump_running', at: [2, 0, 0], y: 90 },
  { model: 'stirling_engine_on', at: [2, 1, 0], y: S },
  ...pipe([3, 0, 0], [E, W_]),
  { model: 'control_valve_2', at: [4, 0, 0], y: 90 },
  ...pipe([5, 0, 0], [E, W_]),
  { model: 'boiler_on', at: [6, 0, 0], y: S },
  { model: 'steam_turbine_on', at: [6, 1, 0], y: S },
  { model: 'controller', at: [7, 0, 0], y: S },
], 3);

// a small plant: tank -> pump -> control valve -> tank with its controller, powered by a Stirling engine
render('plant', [
  { model: 'tank_7', at: [0, 0, 0] },
  ...pipe([1, 0, 0], [E, W_]),
  { model: 'pump_running', at: [2, 0, 0], y: 90 },
  ...pipe([3, 0, 0], [E, W_]),
  { model: 'control_valve_2', at: [4, 0, 0], y: 90 },
  ...pipe([5, 0, 0], [E, W_]),
  { model: 'tank_4', at: [6, 0, 0] },
  { model: 'controller', at: [7, 0, 0], y: S },
  { model: 'stirling_engine_on', at: [2, 1, 0], y: S },
], 3);

// the field communicator is a flat item: just scale it up
{
  const t = readPng(join(ASSETS, 'textures/item/field_communicator.png'));
  const W = t.w + 2, H = t.h + 2, px = Buffer.alloc(W * H * 4);
  for (let y = 0; y < t.h; y++) t.px.copy(px, ((y + 1) * W + 1) * 4, y * t.w * 4, (y + 1) * t.w * 4);
  save('field_communicator', W, H, px, 8);
}
