// Renders pictures for the website, straight from the real models and textures:
//  - isometric pixel-art renders of Setpoint's blocks and small plants
//  - item icons for every recipe ingredient and result, and for the items the machine screens show,
//    taken from the vanilla Minecraft client jar (flat items as their texture, blocks rendered like in the inventory)
//  - recipes.js: every Setpoint recipe as data, so the docs page draws its crafting grids from it
// Run from the project root after a build (the jar comes from the Gradle cache):
//   node tools/render-showcase.mjs <output folder>
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { deflateSync, inflateRawSync, inflateSync } from 'node:zlib';

const ASSETS = 'src/main/resources/assets/setpoint';
const OUT = process.argv[2] || 'build/showcase';
mkdirSync(OUT, { recursive: true });

// ---------- the vanilla client jar, read in place (a jar is a zip) ----------
function openJar(path) {
  const buf = readFileSync(path), entries = new Map();
  let end = buf.length - 22;
  while (buf.readUInt32LE(end) !== 0x06054b50) end--;
  let o = buf.readUInt32LE(end + 16);
  for (let i = 0, n = buf.readUInt16LE(end + 10); i < n; i++) {
    const nameLen = buf.readUInt16LE(o + 28), extraLen = buf.readUInt16LE(o + 30), commentLen = buf.readUInt16LE(o + 32);
    entries.set(buf.toString('utf8', o + 46, o + 46 + nameLen), {
      method: buf.readUInt16LE(o + 10), size: buf.readUInt32LE(o + 20), local: buf.readUInt32LE(o + 42),
    });
    o += 46 + nameLen + extraLen + commentLen;
  }
  return (name) => {
    const e = entries.get(name);
    if (!e) return null;
    const start = e.local + 30 + buf.readUInt16LE(e.local + 26) + buf.readUInt16LE(e.local + 28);
    const data = buf.subarray(start, start + e.size);
    return e.method === 8 ? inflateRawSync(data) : Buffer.from(data);
  };
}
const CACHE = join(homedir(), '.gradle/caches/neoformruntime/artifacts');
const JAR = existsSync(CACHE) && readdirSync(CACHE).filter((n) => /^minecraft_.*_client.jar$/.test(n)).sort().pop();
if (!JAR) throw new Error('Minecraft client jar not found in ' + CACHE + ': run ./gradlew build once');
const vanilla = openJar(join(CACHE, JAR));
const LANG = { ...JSON.parse(vanilla('assets/minecraft/lang/en_us.json').toString('utf8')),
  ...JSON.parse(readFileSync(join(ASSETS, 'lang/en_us.json'), 'utf8')) };

/** Bytes of an asset, from the mod or from the vanilla jar. */
function asset(ns, path) {
  if (ns === 'setpoint') return readFileSync(join(ASSETS, path));
  const data = vanilla('assets/' + ns + '/' + path);
  if (!data) throw new Error('missing vanilla asset ' + ns + ':' + path);
  return data;
}
const split = (id) => (id.includes(':') ? id.split(':') : ['minecraft', id]);

// ---------- PNG read (any non-interlaced PNG: grey, RGB, palette, with alpha, 1 to 16 bit) / write (RGBA 8 bit) ----------
function readPng(buf) {
  let o = 8, w = 0, h = 0, depth = 8, type = 6, palette = null, trns = null;
  const idat = [];
  while (o < buf.length) {
    const len = buf.readUInt32BE(o), kind = buf.toString('ascii', o + 4, o + 8), data = buf.subarray(o + 8, o + 8 + len);
    if (kind === 'IHDR') { w = data.readUInt32BE(0); h = data.readUInt32BE(4); depth = data[8]; type = data[9]; }
    if (kind === 'PLTE') palette = data;
    if (kind === 'tRNS') trns = data;
    if (kind === 'IDAT') idat.push(data);
    o += 12 + len;
  }
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[type], bits = channels * depth;
  const bpp = Math.max(1, bits >> 3), stride = Math.ceil(w * bits / 8), raw = inflateSync(Buffer.concat(idat));
  const px = Buffer.alloc(w * h * 4), line = Buffer.alloc(stride), prev = Buffer.alloc(stride);
  const max = (1 << Math.min(depth, 8)) - 1;
  const sample = (x, c) => { // channel c of pixel x, scaled to 0..255 (palette indices left as they are)
    if (depth === 16) return line[(x * channels + c) * 2];
    if (depth === 8) return line[x * channels + c];
    const bit = (x * channels + c) * depth, v = (line[bit >> 3] >> (8 - depth - (bit & 7))) & max;
    return type === 3 ? v : Math.round(v * 255 / max);
  };
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)];
    for (let i = 0; i < stride; i++) {
      const x = raw[y * (stride + 1) + 1 + i], a = i >= bpp ? line[i - bpp] : 0, b = prev[i], c = i >= bpp ? prev[i - bpp] : 0;
      const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
      line[i] = (x + [0, a, b, (a + b) >> 1, pa <= pb && pa <= pc ? a : pb <= pc ? b : c][f]) & 255;
    }
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4;
      if (type === 3) {
        const k = sample(x, 0);
        palette.copy(px, i, k * 3, k * 3 + 3);
        px[i + 3] = trns && k < trns.length ? trns[k] : 255;
      } else if (type === 0 || type === 4) {
        px[i] = px[i + 1] = px[i + 2] = sample(x, 0);
        // grey with tRNS: one grey value is transparent
        px[i + 3] = type === 4 ? sample(x, 1) : trns && sample(x, 0) === Math.round(trns.readUInt16BE(0) * 255 / ((1 << depth) - 1)) ? 0 : 255;
      } else {
        for (let k = 0; k < 3; k++) px[i + k] = sample(x, k);
        // RGB with tRNS: one colour is transparent
        const key = trns && type === 2 && [0, 1, 2].every((k) => sample(x, k) === (depth === 16 ? trns[k * 2] : trns.readUInt16BE(k * 2)));
        px[i + 3] = type === 6 ? sample(x, 3) : key ? 0 : 255;
      }
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
  const [ns, path] = split(id);
  if (!textures.has(ns + ':' + path)) textures.set(ns + ':' + path, readPng(asset(ns, 'textures/' + path + '.png')));
  return textures.get(ns + ':' + path);
};
/** A model with its parents merged: textures, elements, and whether it is a flat (generated) item. */
function loadModel(id) {
  const [ns, path] = split(id);
  if (path === 'builtin/generated') return { textures: {}, elements: null, generated: true };
  const m = JSON.parse(asset(ns, 'models/' + path + '.json').toString('utf8'));
  const parent = m.parent ? loadModel(m.parent) : { textures: {}, elements: null, generated: false };
  const own = Object.fromEntries(Object.entries(m.textures || {}).map(([k, v]) => [k, typeof v === 'object' ? v.sprite : v]));
  return { textures: { ...parent.textures, ...own }, elements: m.elements || parent.elements, generated: parent.generated };
}
/** Tinted faces (the liquid in a tank's sight glass) are drawn in water blue, like in a plain world. */
const TINT = [0x3f, 0x76, 0xe4];

/** Elements of a block model with every face's texture resolved. Short names mean a Setpoint block model. */
function model(name) {
  const m = loadModel(name.includes('/') ? name : 'setpoint:block/' + name);
  const resolve = (ref) => { while (ref && ref.startsWith('#')) ref = m.textures[ref.slice(1)]; return ref; };
  return (m.elements || []).map((e) => ({ ...e, faces: Object.fromEntries(Object.entries(e.faces).map(([f, v]) => [f, { ...v, texture: resolve(v.texture) }])) }));
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
function render(file, parts, scale = 4, pad = 2, outline = true) {
  const quads = [];
  for (const { model: name, at = [0, 0, 0], y = 0, tints = [TINT, [255, 255, 255]] } of parts) {
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
        quads.push({ o, u, v, uv: f.uv || q.uv, tex: texture(f.texture), shade: SHADE[axis], tint: f.tintindex === undefined ? null : tints[f.tintindex] || TINT });
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
        const t = q.tex, ti = ((ty * t.w / 16 | 0) * t.w + (tx * t.w / 16 | 0)) * 4; // animated textures: first frame
        if (t.px[ti + 3] < 128) continue;
        const d = depth(q.o.map((c, i) => c + a * du[i] + b * dv[i])), i = py * W + pxx;
        if (d < zbuf[i] - 1e-6) continue;
        zbuf[i] = d;
        for (let k = 0; k < 3; k++) px[i * 4 + k] = Math.round(t.px[ti + k] * q.shade * (q.tint ? q.tint[k] / 255 : 1));
        px[i * 4 + 3] = 255;
      }
    }
  }
  save(file, W, H, px, scale, outline);
}

/** Integer upscale, with a 1 px dark outline around the silhouette unless asked not to. */
function save(file, W, H, px, scale, outline = true) {
  const out = Buffer.alloc(W * H * 4);
  px.copy(out);
  for (let y = 0; outline && y < H; y++) for (let x = 0; x < W; x++) {
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
  mkdirSync(join(OUT, file, '..'), { recursive: true });
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
render('dcs_console', [{ model: 'dcs_console', y: S }]);
render('hand_valve', [{ model: 'hand_valve_open', y: 90 }], 6);
render('water_intake', [{ model: 'water_intake' }]);
render('flow_transmitter', [{ model: 'flow_transmitter', y: 90 }], 6);
render('pressure_transmitter', [{ model: 'pressure_transmitter', y: S }]);
render('safety_valve', [{ model: 'safety_valve' }], 6);
render('drain_valve', [{ model: 'drain_valve_open', y: 90 }], 6);
render('check_valve', [{ model: 'check_valve', y: 90 }], 6);
render('pid_board', [{ model: 'pid_board', y: S }]);
render('drum', [{ model: 'drum', y: S, tints: [[0x3b, 0x79, 0xde]] }], 6);
render('drum_colours', [
  { model: 'drum', y: S, at: [0, 0, 0], tints: [[0xb0, 0x2e, 0x26]] }, { model: 'drum', y: S, at: [1, 0, 0], tints: [[0xfe, 0xd8, 0x3d]] },
  { model: 'drum', y: S, at: [2, 0, 0], tints: [[0x5e, 0x7c, 0x16]] }, { model: 'drum', y: S, at: [3, 0, 0], tints: [[0x1d, 0x1d, 0x21]] },
], 4);
render('ibc_black', [{ model: 'ibc_5', y: S, tints: [[0x2a, 0x2c, 0x2f], [0x2a, 0x2c, 0x2f]] }], 4);
render('ibc', [{ model: 'ibc_5', y: S }], 4);
// gravity: a full tall tank runs into a low one through a check valve, until both stand at the same level
render('gravity_line', [
  { model: 'tank_8', at: [0, 0, 0] }, { model: 'tank_4', at: [0, 1, 0] },
  ...pipe([1, 0, 0], [E, W_]),
  { model: 'check_valve', at: [2, 0, 0], y: 90 },
  ...pipe([3, 0, 0], [E, W_]),
  { model: 'tank_3', at: [4, 0, 0] },
], 3);
// a protected boiler: turbine, boiler with a safety valve on top, pressure transmitter and its PIC
render('pressure_plant', [
  { model: 'steam_turbine_on', at: [0, 0, 0], y: S },
  { model: 'boiler_on', at: [1, 0, 0], y: S },
  { model: 'safety_valve_lifting', at: [1, 1, 0] },
  { model: 'pressure_transmitter', at: [2, 0, 0], y: S },
  { model: 'controller', at: [3, 0, 0], y: S },
], 3);
render('tall_tank', [{ model: 'tank_8', at: [0, 0, 0] }, { model: 'tank_8', at: [0, 1, 0] }, { model: 'tank_5', at: [0, 2, 0] }], 3);
render('hookonium_ore', [{ model: 'hookonium_ore' }]);
render('deepslate_hookonium_ore', [{ model: 'deepslate_hookonium_ore' }]);
render('hookonium_block', [{ model: 'hookonium_block' }]);

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
  const t = texture('setpoint:item/field_communicator');
  const W = t.w + 2, H = t.h + 2, px = Buffer.alloc(W * H * 4);
  for (let y = 0; y < t.h; y++) t.px.copy(px, ((y + 1) * W + 1) * 4, y * t.w * 4, (y + 1) * t.w * 4);
  save('field_communicator', W, H, px, 8);
}

// ---------- item icons and recipes for the docs ----------
// Items the machine screens on the docs page show (fuel, buckets). Add any new ones here.
const SCREEN_ITEMS = ['minecraft:coal', 'minecraft:bucket', 'minecraft:water_bucket', 'setpoint:pid'];

/** Writes items/<namespace>/<name>.png: a flat item at 16x16, a block item rendered like in the inventory. */
function icon(id) {
  const [ns, name] = split(id);
  const definition = JSON.parse(asset(ns, 'items/' + name + '.json').toString('utf8'));
  const modelId = definition.model.model;
  const m = loadModel(modelId);
  const file = 'items/' + ns + '/' + name;
  if (m.generated) {
    const t = texture(m.textures.layer0), px = Buffer.alloc(16 * 16 * 4);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const from = ((y * t.w / 16 | 0) * t.w + (x * t.w / 16 | 0)) * 4;
      t.px.copy(px, (y * 16 + x) * 4, from, from + 4);
    }
    save(file, 16, 16, px, 1, false);
  } else {
    render(file, [{ model: modelId, y: 180 }], 1, 0, false); // front to the left, like the inventory
  }
  const key = ['item', 'block'].map((k) => k + '.' + ns + '.' + name).find((k) => LANG[k]);
  return { name: key ? LANG[key] : name, icon: file + '.png' };
}

const items = {}, recipes = {}, shapeless = [], cooking = [];
const need = (id) => { if (!items[id]) items[id] = icon(id); };
const RECIPES = 'src/main/resources/data/setpoint/recipe';
for (const fileName of readdirSync(RECIPES).sort()) {
  const r = JSON.parse(readFileSync(join(RECIPES, fileName), 'utf8'));
  if (r.type === 'minecraft:smelting' || r.type === 'minecraft:blasting') {
    need(r.ingredient); need(r.result.id);
    cooking.push({ type: r.type.slice(10), input: r.ingredient, result: r.result.id, xp: r.experience, seconds: r.cookingtime / 20 });
    continue;
  }
  if (r.type === 'minecraft:crafting_shapeless') {
    r.ingredients.forEach(need); need(r.result.id);
    shapeless.push({ inputs: r.ingredients, result: r.result.id, count: r.result.count || 1 });
    continue;
  }
  if (r.type !== 'minecraft:crafting_shaped') continue;
  const ingredient = (v) => (Array.isArray(v) ? v[0] : v);
  const rows = r.pattern.length === 1 ? ['   ', r.pattern[0], '   '] : r.pattern; // a one-row recipe sits in the middle
  const grid = [];
  for (let y = 0; y < 3; y++) for (let x = 0; x < 3; x++) {
    const c = (rows[y] || '')[x];
    const id = c && c !== ' ' ? ingredient(r.key[c]) : null;
    if (id) need(id);
    grid.push(id);
  }
  need(r.result.id);
  recipes[r.result.id] = { grid, count: r.result.count || 1 };
}
SCREEN_ITEMS.forEach(need);
['minecraft:furnace', 'minecraft:blast_furnace', 'minecraft:diamond_pickaxe', 'minecraft:iron_pickaxe'].forEach(need); // shown with the ores
writeFileSync(join(OUT, 'recipes.js'), '// Generated by setpoint/tools/render-showcase.mjs: do not edit by hand.\n'
  + 'window.SETPOINT_ITEMS = ' + JSON.stringify(items, null, 1) + ';\n'
  + 'window.SETPOINT_RECIPES = ' + JSON.stringify(recipes, null, 1) + ';\n'
  + 'window.SETPOINT_SHAPELESS = ' + JSON.stringify(shapeless, null, 1) + ';\n'
  + 'window.SETPOINT_COOKING = ' + JSON.stringify(cooking, null, 1) + ';\n');
console.log('wrote recipes.js:', Object.keys(recipes).length, 'shaped,', shapeless.length, 'shapeless,', cooking.length, 'cooking recipes,', Object.keys(items).length, 'items');
