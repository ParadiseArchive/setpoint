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

/** parts: [{ model, at: [bx, by, bz] (blocks), y: rotation, xf: (pixel point) => moved point, after the rotation }] */
function render(file, parts, scale = 4, pad = 2, outline = true) {
  const quads = [];
  for (const { model: name, at = [0, 0, 0], y = 0, tints = [TINT, [255, 255, 255]], xf = (p) => p } of parts) {
    for (const e of model(name)) {
      for (const [face, f] of Object.entries(e.faces)) {
        const q = faceQuad(face, e.from, e.to);
        const turn = (p) => { // an element turned about its origin, about x, then y, then z
          if (!e.rotation) return p;
          const [ox, oy, oz] = e.rotation.origin;
          let [x, y, z] = [p[0] - ox, p[1] - oy, p[2] - oz];
          if (e.rotation.x) { const a = e.rotation.x * Math.PI / 180; [y, z] = [Math.cos(a) * y - Math.sin(a) * z, Math.sin(a) * y + Math.cos(a) * z]; }
          if (e.rotation.y) { const a = e.rotation.y * Math.PI / 180; [x, z] = [Math.cos(a) * x + Math.sin(a) * z, -Math.sin(a) * x + Math.cos(a) * z]; }
          if (e.rotation.z) { const a = e.rotation.z * Math.PI / 180; [x, y] = [Math.cos(a) * x - Math.sin(a) * y, Math.sin(a) * x + Math.cos(a) * y]; }
          return [x + ox, y + oy, z + oz];
        };
        const place = (p) => xf(rotY(turn(p), y)).map((v, i) => v + at[i] * 16);
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
// gases: the machines with their product side toward the camera (front east, so your left is south)
const GAS_TINT = { hydrogen: [0xc7, 0x32, 0x2f], oxygen: [0xf2, 0xf4, 0xf7], nitrogen: [0x2c, 0x2e, 0x33], steam: [0xd8, 0xdd, 0xe3], ammonia: [0xe8, 0xc5, 0x47] };
const gasPipe = (at, arms) => [{ model: 'gas_pipe_core', at }, ...arms.map((y) => ({ model: 'gas_pipe_arm', at, y }))];
render('electrolyser', [{ model: 'electrolyser_on', y: E }]);
render('nitrogen_generator', [{ model: 'nitrogen_generator_on', y: S }]);
render('compressor', [{ model: 'compressor_on', y: E }]);
for (const name of ['fuel_cell', 'ammonia_reactor', 'fertilizer_mixer']) render(name, [{ model: name + '_on', y: S }]);
render('gas_cylinder_ammonia', [{ model: 'gas_cylinder', tints: [[0xe8, 0xc5, 0x47]] }], 6);
render('gas_pipe', gasPipe([0, 0, 0], [E, W_, S]));
const powerCable = (at, arms) => [{ model: 'power_cable_core', at }, ...arms.map((y) => ({ model: 'power_cable_arm', at, y }))];
render('power_cable', powerCable([0, 0, 0], [E, W_, S]));
render('gas_cylinder', [{ model: 'gas_cylinder', tints: [GAS_TINT.hydrogen] }], 6);
render('gas_cylinders', Object.values(GAS_TINT).map((t, i) => ({ model: 'gas_cylinder', at: [i * 0.75, 0, -i * 0.75], tints: [t] })), 4);
// an electrolyser filling a cylinder of hydrogen through a gas pipe
render('gas_line', [{ model: 'electrolyser_on', y: E }, ...gasPipe([0, 0, 1], [0, S]), ...gasPipe([0, 0, 2], [0, S]),
  { model: 'gas_cylinder', at: [0, 0, 3], tints: [GAS_TINT.hydrogen] }], 3);
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
render('pallet_4', [{ model: 'pallet_4', y: S, tints: [[0xb0, 0x2e, 0x26], [0x3b, 0x79, 0xde], [0xfe, 0xd8, 0x3d], [0x3b, 0x79, 0xde]] }], 4);
render('pallet_3', [{ model: 'pallet_3', y: S, tints: [[0x3b, 0x79, 0xde], [0x3b, 0x79, 0xde], [0x3b, 0x79, 0xde]] }], 4);
render('pallet', [{ model: 'pallet_0', y: S }], 4);
render('pallet_truck', [{ model: 'pallet_truck', y: 90 }], 4);
// a truck carrying four drums: the pallet's middle over the middle of the forks (8, 12.5), riding 4 pixels up
render('pallet_truck_loaded', [{ model: 'pallet_truck', y: 90 }, { model: 'pallet_4', y: 90, at: [-0.03125, 0.25, -0.5],
  tints: [[0xb0, 0x2e, 0x26], [0x3b, 0x79, 0xde], [0xfe, 0xd8, 0x3d], [0x5e, 0x7c, 0x16]] }], 3);
// the forklift from its part models (see FORKLIFT_SHIFT in gen-assets.mjs), turned so its forks point at the right;
// lift: fork height in blocks. The carriage rises first, then the inner mast, the middle one half as far.
const forklift = (lift, load, pallet = 'pallet_4') => {
  const extend = Math.max(0, lift - 1.25);
  return [
    { model: 'forklift_body', y: 90, at: [0, 2 / 16, 0] },
    { model: 'forklift_rear_wheel', y: 90, at: [-0.75, 0, 0.375] }, { model: 'forklift_rear_wheel', y: 90, at: [-0.75, 0, -0.375] },
    { model: 'forklift_mast_outer', y: 90, at: [0, 14 / 16, 0] },
    { model: 'forklift_mast_middle', y: 90, at: [0, 14 / 16 + extend / 2, 0] },
    { model: 'forklift_mast_inner', y: 90, at: [0, 14 / 16 + extend, 0] },
    { model: 'forklift_carriage', y: 90, at: [26 / 16, lift, 0] },
    ...(load ? [{ model: pallet, y: 270, at: [2, lift, 0.5], tints: load }] : []),
  ];
};
render('forklift', forklift(0), 3);
render('forklift_lifted', forklift(2.5, [[0xb0, 0x2e, 0x26], [0x3b, 0x79, 0xde], [0xfe, 0xd8, 0x3d], [0x5e, 0x7c, 0x16]]), 3);
// an IBC on a wooden pallet (tint 0 its liquid, tint 1 its tote), and one on the forklift
render('pallet_ibc', [{ model: 'pallet_ibc_6', y: S, tints: [TINT, [255, 255, 255]] }], 4);
render('forklift_ibc', forklift(1.5, [TINT, [255, 255, 255]], 'pallet_ibc_6'), 3);
// the floating roof tank, put together like FloatingRoofTankRenderer does (TANK_SHIFT in gen-assets.mjs), its front
// (the nozzle, the ladder in the front-right corner) toward the camera. deck: the roof's height in pixels.
const TANK_SHIFT = { shell: [0, 0, -60], top: [0, -76, -56], rail: [0, -96, -56], pontoon: [0, 2, -40], deck: [0, 0, 0], manhole: [0, 0, 0],
  rolling_ladder: [0, 0, 16], platform: [0, -80, -60], ladder: [0, 0, -66], cage: [0, 0, -66], nozzle: [0, 0, -52] };
const floatingRoofTank = (deck) => {
  const parts = [];
  const spin = (deg) => { const a = deg * Math.PI / 180; return ([x, y, z]) => [x * Math.cos(a) + z * Math.sin(a), y, -x * Math.sin(a) + z * Math.cos(a)]; };
  // a part model turned theta about the middle, after moving it (x, y, z) pixels out; then a final move, for the ladder
  const piece = (name, theta, [x, y, z] = [0, 0, 0], then = (p) => p) => {
    const sh = TANK_SHIFT[name], turn = spin(theta);
    parts.push({ model: 'floating_roof_tank_' + name, xf: (p) => then(turn([p[0] + x - 8 - sh[0], p[1] + y - sh[1], p[2] + z - 8 - sh[2]])) });
  };
  for (let k = 0; k < 24; k++) {
    for (const course of [0, 32, 64]) piece('shell', k * 15, [0, course, 0]);
    piece('top', k * 15);
    if (k !== 3) piece('rail', k * 15);
    piece('pontoon', k * 15, [0, deck, 0]);
  }
  for (let x = -60; x < 60; x += 8) for (let z = -60; z < 60; z += 8) {
    if (Math.hypot(Math.max(Math.abs(x), Math.abs(x + 8)), Math.max(Math.abs(z), Math.abs(z + 8))) < 60) piece('deck', 0, [x + 4, deck, z + 4]);
  }
  for (const [x, z] of [[0, 0], [-22, 14], [18, -24]]) piece('manhole', 0, [x, deck, z]);
  for (const course of [0, 32, 64]) { piece('ladder', 45, [0, course, 0]); if (course) piece('cage', 45, [0, course, 0]); }
  piece('platform', 45);
  piece('nozzle', 0, [8, 0, 0]);
  const pitch = Math.asin(Math.min(1, (97 - deck) / 84)), hinge = (p) => { // about x, then out to the hinge and round to the corner
    const [x, y, z] = p, c = Math.cos(-pitch), sn = Math.sin(-pitch);
    return spin(45)([x, y * c - z * sn + 97, y * sn + z * c + 58]);
  };
  for (let n = 0; n < 3; n++) piece('rolling_ladder', 0, [0, 0, -28 * n], hinge);
  return parts;
};
// the gas sphere, put together like GasSphereRenderer does (SPHERE_SHIFT in gen-assets.mjs), the corner with its
// ladder toward the camera. Each step below is one call on the renderer's pose, applied to a model point, last first.
const SPHERE_SHIFT = { plate: [0, 8, 0], leg: [0, 0, 0], foot: [0, 0, 0], rod: [0, 8, 0], bridge: [0, 1, 16], platform: [0, 1, 0],
  pipe_up: [0, 0, 0], pipe_along: [0, 0, 0], station: [-8, 0, -8], ladder: [0, 0, -66], cage: [0, 0, -66] };
const gasSphere = () => {
  const parts = [];
  const ry = (deg) => { const a = deg * Math.PI / 180; return ([x, y, z]) => [x * Math.cos(a) + z * Math.sin(a), y, -x * Math.sin(a) + z * Math.cos(a)]; };
  const rx = (deg) => { const a = deg * Math.PI / 180; return ([x, y, z]) => [x, y * Math.cos(a) - z * Math.sin(a), y * Math.sin(a) + z * Math.cos(a)]; };
  const mv = (dx, dy, dz) => ([x, y, z]) => [x + dx, y + dy, z + dz];
  const sc = (kx, ky, kz) => ([x, y, z]) => [x * kx, y * ky, z * kz];
  // a part at (x, y, z) pixels in a frame made of these steps (outermost first)
  const piece = (name, [x, y, z], ...frame) => {
    const sh = SPHERE_SHIFT[name], model = name === 'ladder' || name === 'cage' ? 'floating_roof_tank_' + name : 'gas_sphere_' + name;
    const steps = [...frame, mv(x - 8 - sh[0], y - sh[1], z - 8 - sh[2])];
    parts.push({ model, xf: (p) => steps.reduceRight((q, step) => step(q), p) });
  };
  const R = 60, EQ = 88, step = 15, turn = 15, h = 2 * R * Math.sin(step / 2 * Math.PI / 180);
  for (let band = 0; band < 12; band++) {
    const low = -90 + band * step, high = low + step, mid = (low + high) / 2;
    const wide = Math.max(Math.cos(low * Math.PI / 180), Math.cos(high * Math.PI / 180)), w = 2 * R * wide * Math.tan(turn / 2 * Math.PI / 180);
    for (let k = 0; k < 24; k++) piece('plate', [0, 0, 0], ry(k * turn), mv(0, EQ, 0), rx(-mid), mv(0, 0, R), sc(w / 16, h / 16, 1));
  }
  const rod = (x1, y1, z1, x2, y2, z2) => {
    const dx = x2 - x1, dy = y2 - y1, dz = z2 - z1, flat = Math.hypot(dx, dz), len = Math.hypot(flat, dy);
    piece('rod', [0, 0, 0], mv(x1, y1, z1), ry(Math.atan2(dx, dz) * 180 / Math.PI), rx(-Math.atan2(dy, flat) * 180 / Math.PI), sc(1, 1, len / 16));
  };
  for (let leg = 0; leg < 8; leg++) {
    const a = (22.5 + 45 * leg) * Math.PI / 180, b = (22.5 + 45 * (leg + 1)) * Math.PI / 180;
    const x = 62 * Math.sin(a), z = 62 * Math.cos(a), nx = 62 * Math.sin(b), nz = 62 * Math.cos(b);
    piece('foot', [x, 0, z]);
    for (const y of [0, 32, 56]) piece('leg', [x, y, z]);
    rod(x, 10, z, nx, 80, nz); rod(nx, 10, nz, x, 80, z);
  }
  piece('pipe_up', [8, 0, 8]); piece('pipe_along', [8, 0, 8]); piece('pipe_along', [8, 0, 32]); piece('station', [0, 0, 48]);
  for (let c = 0; c < 5; c++) { piece('ladder', [0, c * 32, 0], ry(45)); if (c) piece('cage', [0, c * 32, 0], ry(45)); }
  for (let n = 0; n < 3; n++) piece('bridge', [0, 152, 72 - 24 * n], ry(45));
  piece('platform', [0, 152, 0], ry(45));
  return parts;
};
render('gas_sphere', gasSphere(), 2);
// the flare, put together like FlareRenderer does (FLARE_SHIFT in gen-assets.mjs), its front and ladder side toward the
// camera, burning with the wind blowing its flame to the left
const FLARE_SHIFT = { stack: [0, 0, 0], tip: [0, -16, 0], leg: [0, 8, 0], leg_red: [0, 8, 0], rod: [0, 8, 0], rail: [0, 8, 0], deck: [0, 2, 0],
  drum: [0, 0, 0], head: [0, 0, 0], pipe: [0, 8, 0], station: [-8, 0, -8], flame: [0, 0, 0], flame_core: [0, 0, 0], lamp: [0, 0, 0],
  ladder: [0, 0, -66], cage: [0, 0, -66] };
const flareStack = (load) => {
  const parts = [];
  const ry = (deg) => { const a = deg * Math.PI / 180; return ([x, y, z]) => [x * Math.cos(a) + z * Math.sin(a), y, -x * Math.sin(a) + z * Math.cos(a)]; };
  const rx = (deg) => { const a = deg * Math.PI / 180; return ([x, y, z]) => [x, y * Math.cos(a) - z * Math.sin(a), y * Math.sin(a) + z * Math.cos(a)]; };
  const mv = (dx, dy, dz) => ([x, y, z]) => [x + dx, y + dy, z + dz];
  const sc = (kx, ky, kz) => ([x, y, z]) => [x * kx, y * ky, z * kz];
  const piece = (name, [x, y, z], ...frame) => {
    const sh = FLARE_SHIFT[name], model = name === 'ladder' || name === 'cage' ? 'floating_roof_tank_' + name : 'flare_stack_' + name;
    const steps = [...frame, mv(x - 8 - sh[0], y - sh[1], z - 8 - sh[2])];
    parts.push({ model, xf: (p) => steps.reduceRight((q, step) => step(q), p) });
  };
  const rod = (name, x1, y1, z1, x2, y2, z2, thick = 1) => {
    const dx = x2 - x1, dy = y2 - y1, dz = z2 - z1, flat = Math.hypot(dx, dz), len = Math.hypot(flat, dy);
    piece(name, [0, 0, 0], mv(x1, y1, z1), ry(Math.atan2(dx, dz) * 180 / Math.PI), rx(-Math.atan2(dy, flat) * 180 / Math.PI), sc(thick, thick, len / 16));
  };
  const legHalf = (y) => 48 + (22 - 48) * Math.min(1, Math.max(0, y / 576));
  for (let y = 0; y < 592; y += 16) piece('stack', [0, y, 0]);
  piece('tip', [0, 592, 0]);
  const corners = [[1, 1], [-1, 1], [-1, -1], [1, -1]];
  for (let k = 0; k * 96 < 576; k++) {
    const y0 = k * 96, y1 = y0 + 96, w0 = legHalf(y0), w1 = legHalf(y1);
    for (let c = 0; c < 4; c++) {
      const a = corners[c], b = corners[(c + 1) % 4];
      rod(k % 2 ? 'leg_red' : 'leg', a[0] * w0, y0, a[1] * w0, a[0] * w1, y1, a[1] * w1);
      rod('rod', a[0] * w0, y0, a[1] * w0, b[0] * w1, y1, b[1] * w1);
      rod('rod', b[0] * w0, y0, b[1] * w0, a[0] * w1, y1, a[1] * w1);
      rod('rod', a[0] * w1, y1, a[1] * w1, b[0] * w1, y1, b[1] * w1, 1.5);
    }
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) rod('rod', dx * 5, y1, dz * 5, dx * w1, y1, dz * w1);
  }
  [[192, 48], [384, 40], [576, 40]].forEach(([y, half], d) => {
    for (let x = -half; x < half; x += 8) for (let z = -half; z < half; z += 8) {
      const cx = x + 4, cz = z + 4;
      if ((Math.abs(cx) < 8 && Math.abs(cz) < 8) || (cx > 8 && cx < 24 && Math.abs(cz) < 8)) continue;
      piece('deck', [cx, y, cz]);
    }
    const h = half - 0.5;
    for (let c = 0; c < 4; c++) {
      const a = corners[c], b = corners[(c + 1) % 4];
      for (let k = 0; k < 6; k++) { const t = k / 6, x = h * (a[0] + (b[0] - a[0]) * t), z = h * (a[1] + (b[1] - a[1]) * t); rod('rail', x, y, z, x, y + 12, z); }
      for (const r of [6, 12]) rod('rail', a[0] * h, y + r, a[1] * h, b[0] * h, y + r, b[1] * h);
      if (d > 0) piece('lamp', [a[0] * h, y + 12, a[1] * h]);
    }
  });
  for (let c = 0; c * 32 <= 576; c++) {
    piece('ladder', [0, c * 32, 0], mv(-62, 0, 0), ry(90));
    if (c > 0 && ![192, 384, 576].includes(c * 32)) piece('cage', [0, c * 32, 0], mv(-62, 0, 0), ry(90));
  }
  piece('drum', [0, 0, 80]); piece('head', [0, 0, 104]); piece('head', [0, 0, 0], mv(0, 0, 56), ry(180));
  piece('pipe', [0, 0, 0], mv(0, 28, 64), rx(-90));
  for (let n = 0; n < 4; n++) piece('pipe', [0, 0, n * 16], mv(0, 40, 64), ry(180));
  piece('station', [-8, 0, 104]);
  if (load > 0) {
    const h = Math.min(16, 1.5 + 2.2 * Math.sqrt(load / 20)), w = 0.4 + h * 0.3;
    const flame = [mv(0, 640, 0), ry(-45), rx(14), ry(20), sc(w, h, w)];
    piece('flame', [0, 0, 0], ...flame);
    piece('flame_core', [0, 0, 0], ...flame, sc(0.55, 0.6, 0.55), ry(30));
  }
  return parts;
};
render('flare_stack', flareStack(150), 1);
// the pumpjack, put together like PumpjackRenderer does (PUMPJACK_SHIFT in gen-assets.mjs), side on, mid-nod
const PUMPJACK_SHIFT = { frame: [0, 0, 32], post: [0, -12, 32], beam_front: [0, 0, -12], beam_back: [0, 0, 12], crank: [0, 0, 0],
  gearbox: [0, 0, 44], wellhead: [0, 0, 0], rod: [0, 8, 0] };
const pumpjack = (crankDeg) => {
  const parts = [];
  const ry = (deg) => { const a = deg * Math.PI / 180; return ([x, y, z]) => [x * Math.cos(a) + z * Math.sin(a), y, -x * Math.sin(a) + z * Math.cos(a)]; };
  const rx = (deg) => { const a = deg * Math.PI / 180; return ([x, y, z]) => [x, y * Math.cos(a) - z * Math.sin(a), y * Math.sin(a) + z * Math.cos(a)]; };
  const mv = (dx, dy, dz) => ([x, y, z]) => [x + dx, y + dy, z + dz];
  const sc = (kx, ky, kz) => ([x, y, z]) => [x * kx, y * ky, z * kz];
  const piece = (name, frame) => {
    const sh = PUMPJACK_SHIFT[name], steps = [ry(-90), ...frame, mv(-8 - sh[0], -sh[1], -8 - sh[2])];
    parts.push({ model: 'pumpjack_' + name, xf: (p) => steps.reduceRight((q, step) => step(q), p) });
  };
  const phi = crankDeg * Math.PI / 180, tilt = -12 * Math.sin(phi), t = tilt * Math.PI / 180;
  for (const name of ['frame', 'post', 'gearbox', 'wellhead']) piece(name, []);
  for (const name of ['beam_front', 'beam_back']) piece(name, [mv(0, 40, -32), rx(-tilt)]);
  const rod = (x1, y1, z1, x2, y2, z2) => {
    const dx = x2 - x1, dy = y2 - y1, dz = z2 - z1, flat = Math.hypot(dx, dz), len = Math.hypot(flat, dy);
    piece('rod', [mv(x1, y1, z1), ry(Math.atan2(dx, dz) * 180 / Math.PI), rx(-Math.atan2(dy, flat) * 180 / Math.PI), sc(1, 1, len / 16)]);
  };
  const pinZ = -48 + 8 * Math.cos(phi), pinY = 20 + 8 * Math.sin(phi), backZ = -32 - 20 * Math.cos(t), backY = 40 - 20 * Math.sin(t);
  for (const x of [-7, 7]) { piece('crank', [mv(x, 20, -48), rx(90 - crankDeg)]); rod(x, pinY, pinZ, x, backY, backZ); }
  rod(0, 12, 0, 0, 40 + 33 * Math.sin(t) - 10, 0);
  return parts;
};
render('pumpjack', pumpjack(60), 3);
// the wind turbine, put together like WindTurbineRenderer does: the tower narrowing to the top, the rotor toward the
// camera and turned a little, its blades stretched from one short model
const windTurbine = (rotorDeg) => {
  const parts = [];
  const ry = (deg) => { const a = deg * Math.PI / 180; return ([x, y, z]) => [x * Math.cos(a) + z * Math.sin(a), y, -x * Math.sin(a) + z * Math.cos(a)]; };
  const rz = (deg) => { const a = deg * Math.PI / 180; return ([x, y, z]) => [x * Math.cos(a) - y * Math.sin(a), x * Math.sin(a) + y * Math.cos(a), z]; };
  const mv = (dx, dy, dz) => ([x, y, z]) => [x + dx, y + dy, z + dz];
  const sc = (kx, ky, kz) => ([x, y, z]) => [x * kx, y * ky, z * kz];
  const piece = (name, frame) => {
    const steps = [ry(25), ...frame, mv(-8, 0, -8)];
    parts.push({ model: 'wind_turbine_' + name, xf: (p) => steps.reduceRight((q, step) => step(q), p) });
  };
  const TOWER = 30, BLADE = 11;
  piece('foot', []);
  for (let up = 1; up < TOWER; up++) { const w = 1 - 0.4 * up / TOWER; piece('tower', [mv(0, up * 16, 0), sc(w, 1, w)]); }
  piece('nacelle', [mv(0, TOWER * 16, 0)]);
  const hub = [mv(0, TOWER * 16 + 5, 15), rz(rotorDeg)];
  piece('hub', hub);
  for (let b = 0; b < 3; b++) piece('blade', [...hub, rz(b * 120), mv(0, 3, 1.5), ry(12), sc(1, BLADE - 3 / 16, 1)]);
  return parts;
};
render('wind_turbine', windTurbine(20), 1);
// the distillation column, put together like DistillationColumnRenderer does (COLUMN_SHIFT in gen-assets.mjs)
const COLUMN_SHIFT = { shell: [0, 0, 0], head: [0, 0, 0], ring: [0, 0, -8], reboiler: [0, 0, 0], condenser: [0, 0, 0], nozzle: [0, 8, 0] };
const distillationColumn = () => {
  const parts = [];
  const ry = (deg) => { const a = deg * Math.PI / 180; return ([x, y, z]) => [x * Math.cos(a) + z * Math.sin(a), y, -x * Math.sin(a) + z * Math.cos(a)]; };
  const mv = (dx, dy, dz) => ([x, y, z]) => [x + dx, y + dy, z + dz];
  const piece = (model, shift, [x, y, z], frame = []) => {
    const steps = [...frame, mv(x - 8 - shift[0], y - shift[1], z - 8 - shift[2])];
    parts.push({ model: 'distillation_column_' + model, xf: (p) => steps.reduceRight((q, step) => step(q), p) });
  };
  for (let k = 0; k < 24; k++) {
    const frame = [mv(0, 0, -32), ry(k * 15)];
    for (let c = 0; c < 5; c++) piece('shell', COLUMN_SHIFT.shell, [0, c * 32, 0], frame);
    for (const y of [64, 128]) piece('ring', COLUMN_SHIFT.ring, [0, y, 0], frame);
  }
  piece('head', COLUMN_SHIFT.head, [0, 160, -32]);
  piece('condenser', COLUMN_SHIFT.condenser, [0, 160, -32]);
  piece('reboiler', COLUMN_SHIFT.reboiler, [0, 0, 0]);
  for (const [n, x, y, z] of [['feed', 0, 8, -4], ['steam', -16, 8, -4], ['residue', 16, 8, -4], ['diesel', 0, 56, -20], ['kerosene', 0, 88, -20],
    ['naphtha', 0, 120, -20], ['water', -16, 152, -20], ['lpg', 16, 152, -20]]) piece('nozzle_' + n, COLUMN_SHIFT.nozzle, [x, y, z]);
  return parts;
};
render('distillation_column', distillationColumn(), 2);
render('diesel_generator', [{ model: 'diesel_generator_on', y: S }]);
// 0.2: gas valves, the burner, the console desk
render('gas_valve', [{ model: 'gas_valve_open', y: 90 }], 6);
render('gas_control_valve', [{ model: 'gas_control_valve_3', y: 90 }]);
render('burner', [{ model: 'burner_on', y: S }]);
render('console_desk', [{ model: 'console_desk', y: S }], 6);


// factory blocks, one by one, and put together as a small platform: grating in the top of the blocks on I-beams,
// handrails round its edges, stairs up to it, checker plate and hazard stripes on the ground, a lamp on a column
// pallet racking: two bays of two levels, a loaded pallet on the upper beams and one on the ground
const DRUMS4 = [[0xb0, 0x2e, 0x26], [0x3b, 0x79, 0xde], [0xfe, 0xd8, 0x3d], [0x5e, 0x7c, 0x16]];
render('pallet_rack', [
  ...[0, 3, 6].flatMap((x) => [0, 1].flatMap((z) => [0, 1, 2, 3, 4, 5].map((y) => ({ model: 'rack_upright', at: [x, y, z] })))),
  ...[1, 2, 4, 5].flatMap((x) => [0, 1].map((z) => ({ model: 'rack_beam', y: 90, at: [x, 2, z] }))),
  { model: 'pallet_4', y: 90, at: [2, 3, 0], tints: DRUMS4 }, // a turned pallet model reaches a block back in x
  { model: 'pallet_4', y: 90, at: [5, 0, 0], tints: DRUMS4.slice().reverse() },
], 3);
render('rack_beam', [{ model: 'rack_beam' }], 6);
render('rack_upright', [{ model: 'rack_upright' }], 6);
render('grating', [{ model: 'grating_top' }], 6);
render('grating_stairs', [{ model: 'grating_stairs', y: 180 }], 6);
render('handrail', [{ model: 'handrail_edge', y: 180 }], 6);
render('caged_ladder', [{ model: 'caged_ladder', y: 180 }], 6);
render('steel_beam', [{ model: 'steel_beam' }], 6);
for (const name of ['checker_plate', 'hazard_stripes', 'corrugated_steel']) render(name, [{ model: name }], 6);
render('industrial_lamp', [{ model: 'industrial_lamp' }], 8);
render('factory_platform', [ // beams, then grating and handrails sharing the blocks above them, stairs up from the ground
  ...[0, 1, 2, 3].flatMap((x) => [0, 1].map((z) => ({ model: x < 3 ? 'checker_plate' : 'hazard_stripes', at: [x, -1, z] }))),
  ...[0, 2].flatMap((x) => [0, 1].map((z) => ({ model: 'steel_beam', at: [x, 0, z] }))),
  ...[0, 1, 2].flatMap((x) => [0, 1].map((z) => ({ model: 'grating', at: [x, 1, z] }))),
  ...[0, 1, 2].map((x) => ({ model: 'handrail_edge', at: [x, 1, 0] })), ...[0, 1].map((x) => ({ model: 'handrail_edge', y: 180, at: [x, 1, 1] })),
  { model: 'handrail_edge', y: 270, at: [0, 1, 0] }, { model: 'handrail_edge', y: 270, at: [0, 1, 1] },
  { model: 'grating_stairs', y: 180, at: [3, 0, 1] }, { model: 'grating_stairs', y: 180, at: [4, -1, 1] },
  { model: 'industrial_lamp', at: [2, 1, 0] },
], 3);

// control room: the pieces one by one, and a small control room put together
render('overview_screen', [{ model: 'overview_screen', y: S }], 6);
render('alarm_beacon', [{ model: 'alarm_beacon_on' }], 8);
render('esd_button', [{ model: 'esd_button' }], 8);
render('radio_charger', [{ model: 'radio_charger', y: S }], 8);
render('control_desk', [{ model: 'control_desk_item', y: S }], 6);
render('desk_corner', [{ model: 'desk_corner', y: S }], 6);
render('desk_screens', [{ model: 'desk_screens', y: S }], 6);
render('office_chair', [{ model: 'office_chair' }], 6); // the cushion towards the camera
for (const name of ['raised_floor', 'raised_floor_vent', 'ceiling_panel', 'ceiling_light', 'wired_glass']) render(name, [{ model: name }], 6);
render('filing_cabinet', [{ model: 'filing_cabinet', y: S }], 6);
render('printer', [{ model: 'printer', y: S }], 8);
render('coffee_machine', [{ model: 'coffee_machine', y: S }], 8);
render('exit_sign', [{ model: 'exit_sign_left', y: S }], 8);
render('blast_door', [{ model: 'blast_door_bottom_left' }, { model: 'blast_door_top_left', at: [0, 1, 0] }], 6);
render('control_room', [ // a raised floor, a back wall with the door, a window and the video wall, a row of desks with their screens
  ...[0, 1, 2, 3, 4, 5].flatMap((x) => [0, 1, 2, 3].map((z) => ({ model: x === 4 && z === 3 ? 'raised_floor_vent' : 'raised_floor', at: [x, -1, z] }))),
  ...[0, 1, 2, 3, 4, 5].flatMap((x) => [0, 1, 2].filter((y) => !(x === 0 && y < 2) && !(x >= 4 && y === 1)).map((y) => ({ model: 'ceiling_panel', at: [x, y, -1] }))),
  { model: 'blast_door_bottom_left', y: 270, at: [0, 0, -1] }, { model: 'blast_door_top_left', y: 270, at: [0, 1, -1] }, // facing north: it hangs in the room side of the wall
  { model: 'wired_glass', at: [4, 1, -1] }, { model: 'wired_glass', at: [5, 1, -1] },
  ...[1, 2, 3].flatMap((x) => [1, 2].map((y) => ({ model: 'overview_screen', y: S, at: [x, y, 0] }))),
  { model: 'exit_sign_left', y: S, at: [0, 2, 0] }, { model: 'wall_clock', y: S, at: [4, 2, 0] },
  { model: 'control_desk', y: S, at: [1, 0, 1] }, { model: 'control_desk_side_left', y: S, at: [1, 0, 1] },
  { model: 'control_desk', y: S, at: [2, 0, 1] },
  { model: 'control_desk', y: S, at: [3, 0, 1] }, { model: 'control_desk_side_right', y: S, at: [3, 0, 1] },
  { model: 'desk_screens', y: S, at: [1, 1, 1] }, { model: 'desk_screens', y: S, at: [2, 1, 1] }, { model: 'esd_button', at: [3, 1, 1] },
  { model: 'office_chair', y: S, at: [1, 0, 2] }, { model: 'office_chair', y: S, at: [2, 0, 2] },
  { model: 'filing_cabinet', y: S, at: [5, 0, 0] }, { model: 'alarm_beacon_on', at: [5, 1, 0] },
  { model: 'filing_cabinet', y: S, at: [5, 0, 1] }, { model: 'coffee_machine', y: S, at: [5, 1, 1] },
  { model: 'radio_charger', y: W_, at: [5, 0, 3] },
], 3);

render('floating_roof_tank', floatingRoofTank(52), 2);
render('floating_roof_tank_full', floatingRoofTank(88), 2);
render('floating_roof_tank_low', floatingRoofTank(14), 2);
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
  // a plain model, or the first one inside a select or range (the clock, the compass): the picture you mostly see
  const first = (m) => (m.type === 'minecraft:model' ? m.model : first(m.cases?.[0]?.model ?? m.entries?.[0]?.model ?? m.fallback ?? m.on_false ?? m.models?.[0]));
  const modelId = first(definition.model);
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
