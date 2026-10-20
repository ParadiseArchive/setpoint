// Generates Setpoint's client assets (textures, models, blockstates, item models), recipes and the
// empty game test structure. Run from the project root:  node tools/gen-assets.mjs
// Pass --keep-textures to leave existing PNGs alone (for when textures are drawn by hand).
import { mkdirSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { deflateSync, gzipSync } from 'node:zlib';

const ROOT = 'src/main/resources';
const ASSETS = join(ROOT, 'assets/setpoint');
const DATA = join(ROOT, 'data/setpoint');
const KEEP_TEXTURES = process.argv.includes('--keep-textures');

function write(path, content) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, content);
}
const json = (path, value) => write(path, JSON.stringify(value, null, 2) + '\n');

// ---------- PNG writer (RGBA, no dependencies) ----------
const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function png(pixels) { // pixels: 16 rows of 16 '#rrggbb' or null (transparent)
  const raw = Buffer.alloc(16 * (1 + 16 * 4));
  pixels.forEach((row, y) => row.forEach((hex, x) => {
    const o = y * (1 + 64) + 1 + x * 4;
    if (!hex) return;
    raw[o] = parseInt(hex.slice(1, 3), 16); raw[o + 1] = parseInt(hex.slice(3, 5), 16);
    raw[o + 2] = parseInt(hex.slice(5, 7), 16); raw[o + 3] = 255;
  }));
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(16, 0); ihdr.writeUInt32BE(16, 4); ihdr[8] = 8; ihdr[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
function texture(name, draw, folder = 'block') {
  const path = join(ASSETS, 'textures', folder, name + '.png');
  if (KEEP_TEXTURES && existsSync(path)) return;
  const px = Array.from({ length: 16 }, () => Array(16).fill(null));
  const set = (x, y, c) => { if (x >= 0 && x < 16 && y >= 0 && y < 16) px[y][x] = c; };
  const rect = (x, y, w, h, c) => { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) set(i, j, c); };
  draw({ set, rect });
  write(path, png(px));
}

// ---------- palette: industrial greys with the Hook signature colours ----------
const C = {
  outline: '#23262e', steel0: '#3d424d', steel1: '#4f5562', steel2: '#636a79', steel3: '#7b8394', steel4: '#a3abbb',
  glass: '#161a21', water: '#3a6fd8', waterTop: '#6a9af0',
  cu0: '#6b3a25', cu1: '#94553a', cu2: '#b8714d', cu3: '#d99368', cu4: '#f0b48a',
  cyan: '#2de2e6', cyanDark: '#178a8d', magenta: '#ff4f8b', magentaDark: '#a3305a', amber: '#ffb547', ledOff: '#30343d',
};

// steel plate with a dark rim and a light top-left edge
function plate(p, base = C.steel2) {
  p.rect(0, 0, 16, 16, base);
  p.rect(0, 0, 16, 1, C.steel3); p.rect(0, 0, 1, 16, C.steel3);
  p.rect(0, 15, 16, 1, C.steel0); p.rect(15, 0, 1, 16, C.steel0);
}
function rivets(p, spots) { for (const [x, y] of spots) { p.set(x, y, C.steel4); p.set(x + 1, y + 1, C.steel0); } }

// pipe: copper, shaded like a cylinder, with a darker joint line at the ends
texture('pipe', (p) => {
  const shade = [C.cu1, C.cu2, C.cu3, C.cu4, C.cu3, C.cu3, C.cu2, C.cu2, C.cu2, C.cu2, C.cu1, C.cu1, C.cu1, C.cu0, C.cu0, C.cu0];
  for (let y = 0; y < 16; y++) p.rect(0, y, 16, 1, shade[y]);
  p.rect(0, 0, 1, 16, C.cu0); p.rect(15, 0, 1, 16, C.cu0);
  p.set(4, 3, C.cu4); p.set(10, 4, C.cu4);
});

// tank: steel plates with a vertical sight glass; the level shows in eighths
for (let level = 0; level <= 8; level++) {
  texture('tank_side_' + level, (p) => {
    plate(p);
    rivets(p, [[2, 2], [12, 2], [2, 12], [12, 12]]);
    p.rect(5, 1, 6, 14, C.steel1);                 // glass frame
    p.rect(6, 2, 4, 12, C.glass);                  // glass
    const rows = Math.round(level / 8 * 12);
    if (rows > 0) {
      p.rect(6, 14 - rows, 4, rows, C.water);
      p.rect(6, 14 - rows, 4, 1, C.waterTop);      // meniscus
    }
    p.set(6, 3, '#2a3140');                        // glint
    for (const y of [2, 5, 8, 11]) p.set(11, y, C.steel4); // scale marks
    p.set(4, 2, C.amber);                          // high level mark
  });
}
texture('tank_top', (p) => {
  plate(p);
  p.rect(5, 4, 6, 8, C.steel1); p.rect(4, 5, 8, 6, C.steel1); // manhole
  p.rect(6, 5, 4, 6, C.steel3); p.rect(5, 6, 6, 4, C.steel3);
  p.rect(7, 7, 2, 2, C.steel1);
  rivets(p, [[2, 2], [12, 2], [2, 12], [12, 12]]);
});
texture('tank_bottom', (p) => { plate(p, C.steel1); rivets(p, [[2, 2], [12, 2], [2, 12], [12, 12]]); });

// pump: motor sides with cooling fins and a status light, flanges front and back
function motorSide(led, ledDark) {
  return (p) => {
    plate(p, C.steel1);
    for (let y = 4; y <= 13; y += 2) p.rect(2, y, 12, 1, C.steel3); // fins
    for (let y = 5; y <= 13; y += 2) p.rect(2, y, 12, 1, C.steel0);
    p.rect(2, 1, 3, 2, ledDark); p.set(2, 1, led); p.set(3, 1, led); p.set(2, 2, led); // status light
    p.rect(9, 1, 5, 2, C.amber); p.rect(10, 2, 3, 1, '#c98a2c');                         // nameplate
  };
}
texture('pump_side_off', motorSide(C.ledOff, C.outline));
texture('pump_side_running', motorSide(C.cyan, C.cyanDark));
texture('pump_side_alarm', motorSide(C.magenta, C.magentaDark));
function flange(size) {
  return (p) => {
    plate(p);
    const o = (16 - size) / 2;
    p.rect(o, o, size, size, C.cu1);
    p.rect(o + 1, o + 1, size - 2, size - 2, C.cu2);
    p.rect(o + 2, o + 2, size - 4, size - 4, C.outline); // bore
    for (const [x, y] of [[o, o], [o + size - 1, o], [o, o + size - 1], [o + size - 1, o + size - 1]]) p.set(x, y, C.steel4); // bolts
  };
}
texture('pump_discharge', flange(8));
texture('pump_suction', flange(10)); // suction nozzle is bigger than discharge, like the real thing
texture('pump_top', (p) => {
  plate(p, C.steel1);
  p.rect(3, 3, 10, 10, C.steel0);
  for (let i = 3; i <= 12; i += 2) { p.rect(i, 3, 1, 10, C.steel2); }
  p.rect(7, 7, 2, 2, C.steel4);
});
texture('pump_bottom', (p) => { plate(p, C.steel0); rivets(p, [[1, 1], [13, 1], [1, 13], [13, 13]]); });

// creative generator: dark panel with a cyan bolt
texture('creative_generator', (p) => {
  p.rect(0, 0, 16, 16, '#151923');
  p.rect(0, 0, 16, 1, C.magenta); p.rect(0, 15, 16, 1, C.magenta); p.rect(0, 0, 1, 16, C.magenta); p.rect(15, 0, 1, 16, C.magenta);
  const bolt = [[9, 2], [8, 3], [8, 4], [7, 5], [7, 6], [6, 7], [7, 7], [8, 7], [9, 7], [9, 8], [8, 9], [8, 10], [7, 11], [7, 12], [6, 13]];
  for (const [x, y] of bolt) { p.set(x, y, C.cyan); p.set(x + 1, y, C.cyanDark); }
});

// ---------- models ----------
const M = (name, value) => json(join(ASSETS, 'models/block', name + '.json'), value);
const tex = (n) => 'setpoint:block/' + n;
const box = (from, to, faces) => ({ from, to, faces });
const face = (uv) => ({ uv, texture: '#pipe' });

M('pipe_core', {
  parent: 'minecraft:block/block', textures: { pipe: tex('pipe'), particle: tex('pipe') },
  elements: [box([5, 5, 5], [11, 11, 11], Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, face([5, 5, 11, 11])])))],
});
M('pipe_arm', { // pointing north; the blockstate rotates it
  textures: { pipe: tex('pipe'), particle: tex('pipe') },
  elements: [box([5, 5, 0], [11, 11, 5], {
    north: { uv: [5, 5, 11, 11], texture: '#pipe', cullface: 'north' },
    east: face([0, 5, 5, 11]), west: face([11, 5, 16, 11]), up: face([5, 0, 11, 5]), down: face([5, 11, 11, 16]),
  })],
});
M('pipe_item', {
  parent: 'minecraft:block/block', textures: { pipe: tex('pipe'), particle: tex('pipe') },
  elements: [box([5, 5, 0], [11, 11, 16], {
    north: face([5, 5, 11, 11]), south: face([5, 5, 11, 11]),
    east: face([0, 5, 16, 11]), west: face([0, 5, 16, 11]), up: face([5, 0, 11, 16]), down: face([5, 0, 11, 16]),
  })],
});
for (let level = 0; level <= 8; level++) {
  M('tank_' + level, { parent: 'minecraft:block/cube_bottom_top', textures: { side: tex('tank_side_' + level), top: tex('tank_top'), bottom: tex('tank_bottom') } });
}
for (const status of ['off', 'running', 'alarm']) {
  M('pump_' + status, {
    parent: 'minecraft:block/cube',
    textures: {
      north: tex('pump_discharge'), south: tex('pump_suction'), east: tex('pump_side_' + status), west: tex('pump_side_' + status),
      up: tex('pump_top'), down: tex('pump_bottom'), particle: tex('pump_side_off'),
    },
  });
}
M('creative_generator', { parent: 'minecraft:block/cube_all', textures: { all: tex('creative_generator') } });

// ---------- blockstates ----------
const BS = (name, value) => json(join(ASSETS, 'blockstates', name + '.json'), value);
const armRotation = { north: {}, east: { y: 90 }, south: { y: 180 }, west: { y: 270 }, up: { x: 270 }, down: { x: 90 } };
BS('pipe', {
  multipart: [
    { apply: { model: 'setpoint:block/pipe_core' } },
    ...Object.entries(armRotation).map(([dir, rot]) => ({ when: { [dir]: 'true' }, apply: { model: 'setpoint:block/pipe_arm', ...rot } })),
  ],
});
BS('tank', { variants: Object.fromEntries(Array.from({ length: 9 }, (_, l) => [`level=${l}`, { model: 'setpoint:block/tank_' + l }])) });
const yaw = { north: 0, east: 90, south: 180, west: 270 };
BS('centrifugal_pump', {
  variants: Object.fromEntries(Object.entries(yaw).flatMap(([facing, y]) => ['off', 'running', 'alarm'].map((status) =>
    [`facing=${facing},status=${status}`, { model: 'setpoint:block/pump_' + status, ...(y ? { y } : {}) }]))),
});
BS('creative_generator', { variants: { '': { model: 'setpoint:block/creative_generator' } } });

// ---------- item models ----------
const item = (name, model) => json(join(ASSETS, 'items', name + '.json'), { model: { type: 'minecraft:model', model } });
item('pipe', 'setpoint:block/pipe_item');
item('tank', 'setpoint:block/tank_5');
item('centrifugal_pump', 'setpoint:block/pump_running');
item('creative_generator', 'setpoint:block/creative_generator');

// ---------- recipes ----------
const recipe = (name, pattern, key, id, count = 1) => json(join(DATA, 'recipe', name + '.json'), {
  type: 'minecraft:crafting_shaped', category: 'redstone', key, pattern, result: count > 1 ? { count, id } : { id },
});
recipe('pipe', ['CCC'], { C: 'minecraft:copper_ingot' }, 'setpoint:pipe', 6);
recipe('tank', ['IGI', 'G G', 'IGI'], { I: 'minecraft:iron_ingot', G: 'minecraft:glass' }, 'setpoint:tank');
recipe('centrifugal_pump', [' C ', 'IRI', 'IPI'], { C: 'minecraft:copper_ingot', I: 'minecraft:iron_ingot', R: 'minecraft:redstone', P: 'minecraft:piston' }, 'setpoint:centrifugal_pump');

// =====================================================================================
// M2: level transmitter, control valve, controller, field communicator
// =====================================================================================

// level transmitter: housing with a little display and a tag plate
texture('transmitter_front', (p) => {
  p.rect(0, 0, 16, 16, C.steel1);
  p.rect(2, 2, 12, 7, C.outline); p.rect(3, 3, 10, 5, '#0d1a1c');           // display
  p.rect(4, 5, 2, 1, C.cyan); p.rect(7, 5, 2, 1, C.cyan); p.rect(10, 5, 2, 1, C.cyan); // digits
  p.rect(4, 4, 1, 1, C.cyanDark); p.rect(7, 4, 1, 1, C.cyanDark);
  p.rect(4, 11, 8, 2, C.amber); p.rect(5, 12, 6, 1, '#c98a2c');              // tag plate
  p.set(2, 14, C.steel4); p.set(13, 14, C.steel4);
});
texture('transmitter_side', (p) => { p.rect(0, 0, 16, 16, C.steel1); p.rect(0, 0, 16, 1, C.steel3); p.rect(0, 15, 16, 1, C.steel0); p.rect(7, 2, 2, 12, C.steel0); });
texture('transmitter_top', (p) => { p.rect(0, 0, 16, 16, C.steel2); p.rect(5, 5, 6, 6, C.steel0); p.rect(6, 6, 4, 4, C.steel3); });

// control valve: body in steel, actuator with a position bar (0..4 quarters lit)
texture('valve_body', (p) => {
  plate(p, C.steel2);
  p.rect(3, 3, 10, 10, C.steel1); p.rect(5, 5, 6, 6, C.steel3);
  rivets(p, [[1, 1], [13, 1], [1, 13], [13, 13]]);
});
for (let open = 0; open <= 4; open++) {
  texture('valve_actuator_' + open, (p) => {
    p.rect(0, 0, 16, 16, C.steel1);
    p.rect(0, 0, 16, 1, C.steel3); p.rect(0, 15, 16, 1, C.steel0);
    p.rect(3, 1, 10, 2, C.outline);                         // position indicator window (top rows show on the actuator side)
    for (let q = 0; q < open; q++) p.rect(3 + q * 2 + (q > 1 ? 2 : 0), 1, 2, 2, C.cyan);
    p.rect(2, 5, 12, 1, C.steel0); p.rect(2, 9, 12, 1, C.steel0); // diaphragm housing seams
  });
}
texture('valve_actuator_top', (p) => { p.rect(0, 0, 16, 16, C.steel2); p.rect(4, 4, 8, 8, C.steel1); p.rect(7, 7, 2, 2, C.amber); });

// controller cabinet: front with a small faceplate and status light
function cabinetFront(led, ledDark) {
  return (p) => {
    plate(p, C.steel1);
    p.rect(2, 2, 12, 8, C.outline); p.rect(3, 3, 10, 6, '#151923');            // screen
    p.rect(4, 4, 2, 4, '#5d6472'); p.rect(4, 6, 2, 2, C.steel4);               // PV bar
    p.rect(8, 4, 4, 1, C.steel3); p.rect(8, 6, 3, 1, C.steel3);                // numbers
    p.rect(2, 12, 2, 2, ledDark); p.set(2, 12, led); p.set(3, 12, led);        // status light
    p.rect(6, 12, 8, 2, C.steel0); p.rect(7, 12, 2, 1, C.steel3); p.rect(10, 12, 2, 1, C.steel3); // keys
  };
}
texture('controller_front', cabinetFront(C.cyan, C.cyanDark));
texture('controller_front_alarm', cabinetFront(C.magenta, C.magentaDark));
texture('controller_side', (p) => { plate(p, C.steel1); for (let y = 3; y <= 12; y += 3) p.rect(4, y, 8, 1, C.steel0); });
texture('controller_top', (p) => { plate(p, C.steel2); rivets(p, [[2, 2], [12, 2], [2, 12], [12, 12]]); });

// field communicator (item sprite, transparent background)
texture('field_communicator', (p) => {
  p.rect(4, 1, 8, 14, C.outline);
  p.rect(5, 2, 6, 12, '#2a2f3a');
  p.rect(6, 3, 4, 4, '#0d1a1c'); p.rect(6, 4, 3, 1, C.cyan); p.rect(6, 6, 2, 1, C.cyanDark); // screen
  for (const [x, y] of [[6, 8], [8, 8], [6, 10], [8, 10], [6, 12], [8, 12]]) p.set(x, y, C.steel4); // keys
  p.set(9, 12, C.magenta);
  p.rect(5, 0, 2, 1, C.amber); p.rect(9, 0, 2, 1, C.amber); // test leads
  p.rect(5, 1, 1, 1, '#c98a2c'); p.rect(10, 1, 1, 1, '#c98a2c');
}, 'item');

M('level_transmitter', {
  parent: 'minecraft:block/block',
  textures: { front: tex('transmitter_front'), side: tex('transmitter_side'), top: tex('transmitter_top'), particle: tex('transmitter_side') },
  elements: [box([4, 1, 4], [12, 13, 12], {
    north: { uv: [4, 2, 12, 14], texture: '#front' }, south: { uv: [4, 2, 12, 14], texture: '#side' },
    east: { uv: [4, 2, 12, 14], texture: '#side' }, west: { uv: [4, 2, 12, 14], texture: '#side' },
    up: { uv: [4, 4, 12, 12], texture: '#top' }, down: { uv: [4, 4, 12, 12], texture: '#top', cullface: 'down' },
  })],
});
const allFaces = (uv, texture, skip = []) => Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down']
  .filter((f) => !skip.includes(f)).map((f) => [f, { uv, texture }]));
for (let open = 0; open <= 4; open++) {
  M('control_valve_' + open, { // flow along Z, actuator on top
    parent: 'minecraft:block/block',
    textures: { pipe: tex('pipe'), body: tex('valve_body'), actuator: tex('valve_actuator_' + open), cap: tex('valve_actuator_top'), particle: tex('valve_body') },
    elements: [
      box([5, 5, 0], [11, 11, 16], {
        north: { uv: [5, 5, 11, 11], texture: '#pipe', cullface: 'north' }, south: { uv: [5, 5, 11, 11], texture: '#pipe', cullface: 'south' },
        east: face([0, 5, 16, 11]), west: face([0, 5, 16, 11]), down: face([5, 0, 11, 16]),
      }),
      box([4, 4, 4], [12, 12, 12], allFaces([4, 4, 12, 12], '#body')),
      box([7, 12, 7], [9, 13, 9], allFaces([7, 7, 9, 9], '#cap', ['down'])),
      box([3, 13, 3], [13, 16, 13], {
        north: { uv: [3, 0, 13, 3], texture: '#actuator' }, south: { uv: [3, 0, 13, 3], texture: '#actuator' },
        east: { uv: [3, 0, 13, 3], texture: '#actuator' }, west: { uv: [3, 0, 13, 3], texture: '#actuator' },
        up: { uv: [3, 3, 13, 13], texture: '#cap' }, down: { uv: [3, 3, 13, 13], texture: '#cap' },
      }),
    ],
  });
}
for (const alarm of [false, true]) {
  M('controller' + (alarm ? '_alarm' : ''), {
    parent: 'minecraft:block/orientable_with_bottom',
    textures: { front: tex(alarm ? 'controller_front_alarm' : 'controller_front'), side: tex('controller_side'), top: tex('controller_top'), bottom: tex('controller_top') },
  });
}

BS('level_transmitter', { variants: Object.fromEntries(Object.entries(yaw).map(([f, y]) => [`facing=${f}`, { model: 'setpoint:block/level_transmitter', ...(y ? { y } : {}) }])) });
const axisRotation = { x: { y: 90 }, y: { x: 90 }, z: {} };
BS('control_valve', {
  variants: Object.fromEntries(Object.entries(axisRotation).flatMap(([axis, rot]) =>
    [0, 1, 2, 3, 4].map((open) => [`axis=${axis},open=${open}`, { model: 'setpoint:block/control_valve_' + open, ...rot }]))),
});
BS('controller', {
  variants: Object.fromEntries(Object.entries(yaw).flatMap(([f, y]) => [false, true].map((alarm) =>
    [`alarm=${alarm},facing=${f}`, { model: 'setpoint:block/controller' + (alarm ? '_alarm' : ''), ...(y ? { y } : {}) }]))),
});

item('level_transmitter', 'setpoint:block/level_transmitter');
item('control_valve', 'setpoint:block/control_valve_2');
item('controller', 'setpoint:block/controller');
json(join(ASSETS, 'models/item/field_communicator.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'setpoint:item/field_communicator' } });
item('field_communicator', 'setpoint:item/field_communicator');

recipe('level_transmitter', ['IGI', 'ICI'], { I: 'minecraft:iron_ingot', G: 'minecraft:glass_pane', C: 'minecraft:comparator' }, 'setpoint:level_transmitter');
recipe('control_valve', [' P ', 'CIC'], { P: 'minecraft:piston', C: 'minecraft:copper_ingot', I: 'minecraft:iron_ingot' }, 'setpoint:control_valve');
recipe('controller', ['IGI', 'RCR', 'III'], { I: 'minecraft:iron_ingot', G: 'minecraft:glass_pane', R: 'minecraft:redstone', C: 'minecraft:comparator' }, 'setpoint:controller');
recipe('field_communicator', [' G ', 'IRI', 'ICI'], { G: 'minecraft:glass_pane', I: 'minecraft:iron_ingot', R: 'minecraft:redstone', C: 'minecraft:copper_ingot' }, 'setpoint:field_communicator');

// ---------- empty game test structure (NBT) ----------
function nbt() {
  const parts = [];
  const u8 = (v) => parts.push(Buffer.from([v]));
  const i32 = (v) => { const b = Buffer.alloc(4); b.writeInt32BE(v); parts.push(b); };
  const str = (s) => { const b = Buffer.from(s, 'utf8'); const l = Buffer.alloc(2); l.writeUInt16BE(b.length); parts.push(l, b); };
  return {
    parts, u8, i32, str,
    named(type, name) { u8(type); str(name); },
    end() { u8(0); },
  };
}
const n = nbt();
const [W, H, D] = [7, 11, 3];
n.named(10, '');                                       // root compound
n.named(3, 'DataVersion'); n.i32(5023);                // Minecraft 26.3
n.named(9, 'size'); n.u8(3); n.i32(3); n.i32(W); n.i32(H); n.i32(D);
n.named(9, 'palette'); n.u8(10); n.i32(1); n.named(8, 'Name'); n.str('minecraft:air'); n.end();
n.named(9, 'blocks'); n.u8(10); n.i32(W * H * D);
for (let y = 0; y < H; y++) for (let z = 0; z < D; z++) for (let x = 0; x < W; x++) {
  n.named(9, 'pos'); n.u8(3); n.i32(3); n.i32(x); n.i32(y); n.i32(z);
  n.named(3, 'state'); n.i32(0);
  n.end();
}
n.named(9, 'entities'); n.u8(0); n.i32(0);
n.end();
write(join(DATA, 'structure/bench.nbt'), gzipSync(Buffer.concat(n.parts)));

console.log('assets generated' + (KEEP_TEXTURES ? ' (kept existing textures)' : ''));
