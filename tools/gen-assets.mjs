// Generates Setpoint's client assets (textures, models, blockstates, item models), recipes and the
// empty game test structure. Run from the project root:  node tools/gen-assets.mjs
// Pass --keep-textures to leave existing PNGs alone (for when textures are drawn by hand).
import { mkdirSync, writeFileSync, existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { deflateSync, gzipSync } from 'node:zlib';
import { HANDBOOK } from './handbook-content.mjs';

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
function png(pixels) { // pixels: 16 rows of 16 '#rrggbb' (or '#rrggbbaa', see-through) or null (transparent)
  const raw = Buffer.alloc(16 * (1 + 16 * 4));
  pixels.forEach((row, y) => row.forEach((hex, x) => {
    const o = y * (1 + 64) + 1 + x * 4;
    if (!hex) return;
    raw[o] = parseInt(hex.slice(1, 3), 16); raw[o + 1] = parseInt(hex.slice(3, 5), 16);
    raw[o + 2] = parseInt(hex.slice(5, 7), 16); raw[o + 3] = hex.length > 7 ? parseInt(hex.slice(7, 9), 16) : 255;
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
    p.set(6, 3, '#2a3140');                        // glint (the liquid is its own layer, coloured per fluid)
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
const MODELS = {};
const M = (name, value) => { MODELS[name] = value; return json(join(ASSETS, 'models/block', name + '.json'), value); };
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
// the liquid in the sight glass: light grey, tinted with the colour of the fluid in the tank (tintindex 0)
for (let level = 1; level <= 8; level++) {
  texture('tank_liquid_' + level, (p) => {
    const rows = Math.round(level / 8 * 12);
    p.rect(6, 14 - rows, 4, rows, '#c8c8c8');
    p.rect(6, 14 - rows, 4, 1, '#ffffff');       // meniscus
    p.rect(9, 15 - rows, 1, rows - 1, '#b0b0b0'); // shade along the frame
  });
}
for (let level = 0; level <= 8; level++) {
  const sides = (t, extra = {}) => Object.fromEntries(['north', 'south', 'east', 'west'].map((f) => [f, { uv: [0, 0, 16, 16], texture: t, cullface: f, ...extra }]));
  M('tank_' + level, {
    parent: 'minecraft:block/block',
    textures: { side: tex('tank_side_' + level), top: tex('tank_top'), bottom: tex('tank_bottom'), particle: tex('tank_side_' + level),
      ...(level > 0 ? { liquid: tex('tank_liquid_' + level) } : {}) },
    elements: [
      box([0, 0, 0], [16, 16, 16], { ...sides('#side'), up: { uv: [0, 0, 16, 16], texture: '#top', cullface: 'up' }, down: { uv: [0, 0, 16, 16], texture: '#bottom', cullface: 'down' } }),
      ...(level > 0 ? [box([-0.01, 0, -0.01], [16.01, 16, 16.01], sides('#liquid', { tintindex: 0 }))] : []),
    ],
  });
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
json(join(ASSETS, 'items', 'tank.json'), { model: { type: 'minecraft:model', model: 'setpoint:block/tank_5',
  tints: [{ type: 'minecraft:constant', value: -12618012 }] } }); // water blue in the inventory
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

// =====================================================================================
// Power: Stirling engine (early) and biogas generator
// =====================================================================================

texture('stirling_base', (p) => { plate(p, C.steel1); rivets(p, [[1, 1], [13, 1], [1, 13], [13, 13]]); });
texture('stirling_cylinder', (p) => {
  const shade = [C.cu1, C.cu2, C.cu3, C.cu4, C.cu3, C.cu2, C.cu2, C.cu1, C.cu1, C.cu1, C.cu0, C.cu0, C.cu1, C.cu2, C.cu1, C.cu0];
  for (let y = 0; y < 16; y++) p.rect(0, y, 16, 1, shade[y]);
  for (let x = 1; x < 16; x += 3) p.rect(x, 0, 1, 16, C.cu0); // cooling fins
});
texture('stirling_hot_off', (p) => { p.rect(0, 0, 16, 16, C.steel0); p.rect(2, 2, 12, 12, '#2a2e36'); p.rect(4, 4, 8, 8, C.steel1); });
texture('stirling_hot_on', (p) => {
  p.rect(0, 0, 16, 16, '#7a2a10'); p.rect(2, 2, 12, 12, '#c2451a'); p.rect(4, 4, 8, 8, '#f07a2a'); p.rect(6, 6, 4, 4, '#ffc06a');
});
texture('stirling_flywheel', (p) => {
  p.rect(0, 0, 16, 16, null);
  p.rect(2, 0, 12, 16, C.steel0); p.rect(0, 2, 16, 12, C.steel0);        // rim
  p.rect(3, 2, 10, 12, '#1d2027'); p.rect(2, 3, 12, 10, '#1d2027');      // inside
  p.rect(7, 2, 2, 12, C.steel2); p.rect(2, 7, 12, 2, C.steel2);          // spokes
  p.rect(6, 6, 4, 4, C.steel3); p.rect(7, 7, 2, 2, C.amber);              // hub
});

texture('biogas_side', (p) => {
  p.rect(0, 0, 16, 16, '#3c5a3a');
  p.rect(0, 0, 16, 1, '#58805a'); p.rect(0, 15, 16, 1, '#26381f');
  for (let y = 4; y <= 12; y += 4) p.rect(0, y, 16, 1, '#2f4a2d');   // plate seams
  rivets(p, [[2, 2], [12, 2], [2, 12], [12, 12]]);
});
texture('biogas_top', (p) => { p.rect(0, 0, 16, 16, '#4a6e47'); p.rect(3, 3, 10, 10, '#3c5a3a'); p.rect(6, 6, 4, 4, '#2f4a2d'); });
function biogasFront(led, ledDark, flame) {
  return (p) => {
    p.rect(0, 0, 16, 16, '#3c5a3a');
    p.rect(0, 0, 16, 1, '#58805a'); p.rect(0, 15, 16, 1, '#26381f');
    p.rect(3, 3, 10, 7, C.outline); p.rect(4, 4, 8, 5, '#151923');          // engine window
    if (flame) { p.rect(6, 6, 4, 2, '#3a7bff'); p.rect(7, 5, 2, 1, '#9ad0ff'); } // gas flame
    p.rect(3, 12, 2, 2, ledDark); p.set(3, 12, led);                         // status light
    p.rect(7, 12, 6, 2, C.amber); p.rect(8, 13, 4, 1, '#c98a2c');            // nameplate
  };
}
texture('biogas_front_off', biogasFront(C.ledOff, C.outline, false));
texture('biogas_front_on', biogasFront(C.cyan, C.cyanDark, true));

for (const lit of [false, true]) {
  M('stirling_engine' + (lit ? '_on' : ''), {
    parent: 'minecraft:block/block',
    textures: { base: tex('stirling_base'), cylinder: tex('stirling_cylinder'), hot: tex(lit ? 'stirling_hot_on' : 'stirling_hot_off'),
      flywheel: tex('stirling_flywheel'), particle: tex('stirling_base') },
    elements: [
      box([0, 0, 0], [16, 3, 16], allFaces([0, 0, 16, 16], '#base')),
      box([4, 3, 5], [11, 10, 11], allFaces([0, 3, 16, 10], '#cylinder')),
      box([1, 3, 4], [4, 11, 12], allFaces([2, 2, 14, 14], '#hot')),
      box([11, 7, 7], [12, 9, 9], allFaces([7, 7, 9, 9], '#base')),
      box([12, 1, 1], [14, 15, 15], {
        east: { uv: [0, 0, 16, 16], texture: '#flywheel' }, west: { uv: [0, 0, 16, 16], texture: '#flywheel' },
        north: { uv: [0, 0, 2, 14], texture: '#base' }, south: { uv: [0, 0, 2, 14], texture: '#base' },
        up: { uv: [0, 0, 2, 14], texture: '#base' }, down: { uv: [0, 0, 2, 14], texture: '#base' },
      }),
    ],
  });
  M('biogas_generator' + (lit ? '_on' : ''), {
    parent: 'minecraft:block/block',
    textures: { side: tex('biogas_side'), top: tex('biogas_top'), front: tex(lit ? 'biogas_front_on' : 'biogas_front_off'), pipe: tex('pipe'), particle: tex('biogas_side') },
    elements: [
      box([1, 0, 1], [15, 11, 15], {
        north: { uv: [1, 5, 15, 16], texture: '#front' }, south: { uv: [1, 5, 15, 16], texture: '#side' },
        east: { uv: [1, 5, 15, 16], texture: '#side' }, west: { uv: [1, 5, 15, 16], texture: '#side' },
        up: { uv: [1, 1, 15, 15], texture: '#top' }, down: { uv: [1, 1, 15, 15], texture: '#side', cullface: 'down' },
      }),
      box([3, 11, 3], [13, 14, 13], allFaces([3, 3, 13, 13], '#top', ['down'])),
      box([7, 14, 7], [9, 16, 9], allFaces([5, 5, 7, 7], '#pipe', ['down'])),
    ],
  });
}
for (const name of ['stirling_engine', 'biogas_generator']) {
  BS(name, {
    variants: Object.fromEntries(Object.entries(yaw).flatMap(([f, y]) => [false, true].map((lit) =>
      [`facing=${f},lit=${lit}`, { model: 'setpoint:block/' + name + (lit ? '_on' : ''), ...(y ? { y } : {}) }]))),
  });
  item(name, 'setpoint:block/' + name + '_on');
}
recipe('stirling_engine', [' C ', 'IPI', 'III'], { C: 'minecraft:copper_ingot', I: 'minecraft:iron_ingot', P: 'minecraft:piston' }, 'setpoint:stirling_engine');
recipe('biogas_generator', ['IGI', 'CXC', 'III'], { I: 'minecraft:iron_ingot', G: 'minecraft:glass', C: 'minecraft:copper_ingot', X: 'minecraft:composter' }, 'setpoint:biogas_generator');

// =====================================================================================
// Power 2: coal-fired boiler and steam turbine
// =====================================================================================

// firebrick lower half, steel water drum upper half
function brick(p, y0, y1) {
  const B = { mortar: '#3a2a24', b0: '#6b3324', b1: '#8a4330', b2: '#a5543b' };
  p.rect(0, y0, 16, y1 - y0, B.mortar);
  for (let y = y0, row = 0; y < y1; y += 3, row++) {
    for (let x = row % 2 ? -4 : 0; x < 16; x += 8) {
      p.rect(Math.max(0, x), y, Math.min(7, x + 7) - Math.max(0, x), 2, row % 3 === 0 ? B.b2 : B.b1);
      if (x + 1 >= 0) p.set(Math.max(0, x + 1), y, '#b86a4e');
    }
  }
}
function drum(p) {
  p.rect(0, 0, 16, 8, C.steel2);
  p.rect(0, 0, 16, 1, C.steel3); p.rect(0, 7, 16, 1, C.steel0);
  rivets(p, [[1, 2], [13, 2]]);
}
texture('boiler_side', (p) => {
  drum(p); brick(p, 8, 16);
  p.rect(6, 1, 4, 6, C.steel1); p.rect(7, 2, 2, 4, C.glass); p.rect(7, 4, 2, 2, C.water); // gauge glass
});
function boilerFront(lit) {
  return (p) => {
    drum(p); brick(p, 8, 16);
    p.rect(2, 1, 5, 5, C.outline); p.rect(3, 2, 3, 3, '#e8e8e8'); p.set(4, 3, C.magenta); p.set(5, 2, C.outline); // pressure gauge
    p.rect(10, 2, 4, 3, C.amber); p.rect(11, 3, 2, 1, '#c98a2c');      // nameplate
    p.rect(4, 9, 8, 6, C.outline);                                        // firebox door
    p.rect(5, 10, 6, 4, lit ? '#c2451a' : '#22252c');
    if (lit) { p.rect(6, 11, 4, 3, '#f07a2a'); p.rect(7, 12, 2, 2, '#ffc06a'); }
    for (const x of [6, 8, 10]) p.rect(x, 10, 1, 4, lit ? '#7a2a10' : C.steel0); // grate bars
  };
}
texture('boiler_front', boilerFront(false));
texture('boiler_front_on', boilerFront(true));
texture('boiler_top', (p) => {
  plate(p);
  p.rect(5, 5, 6, 6, C.steel0); p.rect(6, 6, 4, 4, C.steel3); p.rect(7, 7, 2, 2, C.steel1); // steam outlet
  p.rect(2, 2, 2, 2, C.amber);                                                          // safety valve
  rivets(p, [[12, 2], [2, 12], [12, 12]]);
});
texture('boiler_bottom', (p) => brick(p, 0, 16));

// steam turbine: casing with ribs, a generator end with a status light
texture('turbine_side', (p) => {
  plate(p, C.steel2);
  p.rect(1, 4, 14, 8, C.steel1);                                 // rotor casing
  for (let x = 2; x < 15; x += 3) p.rect(x, 4, 1, 8, C.steel3);   // ribs
  p.rect(1, 7, 14, 2, C.steel0);                                  // split line
  rivets(p, [[1, 1], [13, 1], [1, 13], [13, 13]]);
});
function turbineFront(led, ledDark) {
  return (p) => {
    plate(p, C.steel1);
    p.rect(3, 3, 10, 10, C.steel0); p.rect(4, 4, 8, 8, C.steel2);  // bearing housing
    p.rect(6, 6, 4, 4, C.steel3); p.rect(7, 7, 2, 2, C.outline);    // shaft end
    p.rect(2, 13, 2, 2, ledDark); p.set(2, 13, led);                // status light
    p.rect(6, 13, 7, 2, C.amber); p.rect(7, 14, 5, 1, '#c98a2c');   // nameplate
  };
}
texture('turbine_front', turbineFront(C.ledOff, C.outline));
texture('turbine_front_on', turbineFront(C.cyan, C.cyanDark));
texture('turbine_top', (p) => {
  plate(p, C.steel2);
  p.rect(4, 4, 8, 8, C.steel0); p.rect(5, 5, 6, 6, C.cu2); p.rect(6, 6, 4, 4, C.cu1); // steam inlet flange
  rivets(p, [[1, 1], [13, 1], [1, 13], [13, 13]]);
});

for (const on of [false, true]) {
  M('boiler' + (on ? '_on' : ''), { parent: 'minecraft:block/orientable_with_bottom',
    textures: { front: tex(on ? 'boiler_front_on' : 'boiler_front'), side: tex('boiler_side'), top: tex('boiler_top'), bottom: tex('boiler_bottom') } });
  M('steam_turbine' + (on ? '_on' : ''), { parent: 'minecraft:block/orientable_with_bottom',
    textures: { front: tex(on ? 'turbine_front_on' : 'turbine_front'), side: tex('turbine_side'), top: tex('turbine_top'), bottom: tex('turbine_top') } });
}
BS('boiler', { variants: Object.fromEntries(Object.entries(yaw).flatMap(([f, y]) => [false, true].map((lit) =>
  [`facing=${f},lit=${lit}`, { model: 'setpoint:block/boiler' + (lit ? '_on' : ''), ...(y ? { y } : {}) }]))) });
BS('steam_turbine', { variants: Object.fromEntries(Object.entries(yaw).flatMap(([f, y]) => [false, true].map((run) =>
  [`facing=${f},running=${run}`, { model: 'setpoint:block/steam_turbine' + (run ? '_on' : ''), ...(y ? { y } : {}) }]))) });
item('boiler', 'setpoint:block/boiler_on');
item('steam_turbine', 'setpoint:block/steam_turbine_on');
recipe('boiler', ['CCC', 'I I', 'IFI'], { C: 'minecraft:copper_ingot', I: 'minecraft:iron_ingot', F: 'minecraft:furnace' }, 'setpoint:boiler');
recipe('steam_turbine', ['III', 'CPC', 'IRI'], { I: 'minecraft:iron_ingot', C: 'minecraft:copper_ingot', P: 'minecraft:piston', R: 'minecraft:redstone' }, 'setpoint:steam_turbine');

// =====================================================================================
// Ores. Hookonium: endgame ore, as rare as diamond, needs a diamond pickaxe
// =====================================================================================

const HK = { o: '#1e0b33', k: '#3d1663', m: '#6a2bb0', l: '#9b54ec', h: '#c895ff', w: '#f1e2ff' };
/** Draw a pixel map: one string per row, each character a palette key ('.' = leave as is). */
function pixels(p, rows, palette) {
  rows.forEach((row, y) => [...row].forEach((c, x) => { if (palette[c]) p.set(x, y, palette[c]); }));
}

// Hand-drawn pixel art in the style of vanilla ores: dark outlines, three shades and a highlight.
const HP = { o: '#160826', d: '#3f1470', m: '#6f28c2', l: '#9d51f0', h: '#cf9dff', w: '#f7ecff' };
// ore: crystal clusters on a transparent overlay; the model lays it over vanilla stone or deepslate
texture('hookonium_ore_overlay', (p) => pixels(p, [
  '................',
  '...oo...........',
  '..olho....oo....',
  '..odlmo..ohlo...',
  '...odmo..odmlo..',
  '....oo....odo...',
  '...........o....',
  '.oo.............',
  'ohlo......oo....',
  'odmlo....olho...',
  '.odmo...olwmlo..',
  '..oo....odmmdo..',
  '.........oddo...',
  '...oo.....oo....',
  '..ohlo..........',
  '..odmo..........',
], HP));
// raw hookonium: a lumpy clump, like raw iron
texture('raw_hookonium', (p) => pixels(p, [
  '................',
  '................',
  '......ooo.......',
  '.....ohllo......',
  '....ohlmmmo.oo..',
  '...oolmmmmoolho.',
  '..ohllmmmdolmmdo',
  '..olmmmmddolmmdo',
  '.ohlmmmdddoodddo',
  '.olmmmddooohlmo.',
  '.odmmddo.ohlmmdo',
  '..oddoo..olmmddo',
  '...oo.....oddoo.',
  '...........oo...',
  '................',
  '................',
], HP), 'item');
// ingot: the vanilla ingot shape, with a shiny top
texture('hookonium_ingot', (p) => pixels(p, [
  '................',
  '................',
  '................',
  '................',
  '........oooo....',
  '......oowhhlo...',
  '....oohhhllllo..',
  '..oohhlllllmmo..',
  '.ohlllllmmmmdo..',
  '.olllmmmmmddo...',
  '.ommmmmmddoo....',
  '.odmmmddoo......',
  '..oddoo.........',
  '...oo...........',
  '................',
  '................',
], HP), 'item');
// block of hookonium: a polished, faceted block with the hook engraved in it
texture('hookonium_block', (p) => {
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const s = x + y;
    p.set(x, y, s < 11 ? HP.l : s > 19 ? HP.d : HP.m);
  }
  p.rect(0, 0, 16, 1, HP.h); p.rect(0, 0, 1, 16, HP.h);
  p.rect(0, 15, 16, 1, HP.o); p.rect(15, 0, 1, 16, HP.o);
  p.rect(1, 14, 14, 1, HP.d); p.rect(14, 1, 1, 14, HP.d);
  p.set(2, 2, HP.w); p.set(3, 2, HP.h); p.set(2, 3, HP.h);
  const hook = [[8, 3], [9, 3], [7, 4], [10, 4], [8, 5], [9, 5], [9, 6], [9, 7], [9, 8], [9, 9], [9, 10], [8, 11], [7, 11], [6, 11], [5, 10], [5, 9], [6, 9]];
  for (const [x, y] of hook) p.set(x + 1, y + 1, HP.h);
  for (const [x, y] of hook) p.set(x, y, HP.o);
});
// block of raw hookonium: clumps packed together
texture('raw_hookonium_block', (p) => {
  p.rect(0, 0, 16, 16, HP.d);
  for (const [cx, cy] of [[2, 2], [7, 1], [12, 3], [4, 7], [9, 6], [14, 8], [2, 12], [7, 11], [12, 13], [0, 7], [15, 14]]) {
    for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) {
      if (dx * dx + dy * dy > 5) continue;
      const x = (cx + dx + 16) % 16, y = (cy + dy + 16) % 16; // wraps, so the block tiles
      const c = dx + dy <= -3 ? HP.h : dx + dy <= -1 ? HP.l : dx + dy >= 3 ? HP.o : dx + dy >= 2 ? HP.d : HP.m;
      p.set(x, y, c);
    }
  }
});

const oreModel = (name, side, top) => M(name, {
  parent: 'minecraft:block/block',
  textures: { base: side, base_top: top, overlay: tex('hookonium_ore_overlay'), particle: side },
  elements: [
    box([0, 0, 0], [16, 16, 16], Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) =>
      [f, { texture: f === 'up' || f === 'down' ? '#base_top' : '#base', cullface: f }]))),
    box([0, 0, 0], [16, 16, 16], Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) =>
      [f, { texture: '#overlay', cullface: f }]))),
  ],
});
oreModel('hookonium_ore', 'minecraft:block/stone', 'minecraft:block/stone');
oreModel('deepslate_hookonium_ore', 'minecraft:block/deepslate', 'minecraft:block/deepslate_top');
M('hookonium_block', { parent: 'minecraft:block/cube_all', textures: { all: tex('hookonium_block') } });
M('raw_hookonium_block', { parent: 'minecraft:block/cube_all', textures: { all: tex('raw_hookonium_block') } });
for (const name of ['hookonium_ore', 'deepslate_hookonium_ore', 'hookonium_block', 'raw_hookonium_block']) {
  BS(name, { variants: { '': { model: 'setpoint:block/' + name } } });
  item(name, 'setpoint:block/' + name);
}
for (const name of ['raw_hookonium', 'hookonium_ingot']) {
  json(join(ASSETS, 'models/item', name + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'setpoint:item/' + name } });
  item(name, 'setpoint:item/' + name);
}

// recipes: smelt or blast raw hookonium (or the ore itself) into an ingot; compact into blocks and back
const cook = (name, type, ingredient, result, experience, time) => json(join(DATA, 'recipe', name + '.json'),
  { type: 'minecraft:' + type, cookingtime: time, experience, group: 'hookonium_ingot', ingredient, result: { id: result } });
for (const [from, what] of [['raw_hookonium', 'setpoint:raw_hookonium'], ['hookonium_ore', 'setpoint:hookonium_ore'], ['deepslate_hookonium_ore', 'setpoint:deepslate_hookonium_ore']]) {
  cook('hookonium_ingot_from_smelting_' + from, 'smelting', what, 'setpoint:hookonium_ingot', 1.0, 200);
  cook('hookonium_ingot_from_blasting_' + from, 'blasting', what, 'setpoint:hookonium_ingot', 1.0, 100);
}
recipe('hookonium_block', ['###', '###', '###'], { '#': 'setpoint:hookonium_ingot' }, 'setpoint:hookonium_block');
recipe('raw_hookonium_block', ['###', '###', '###'], { '#': 'setpoint:raw_hookonium' }, 'setpoint:raw_hookonium_block');
const shapeless = (name, ingredients, id, count) => json(join(DATA, 'recipe', name + '.json'),
  { type: 'minecraft:crafting_shapeless', category: 'misc', ingredients, result: { count, id } });
shapeless('hookonium_ingot_from_hookonium_block', ['setpoint:hookonium_block'], 'setpoint:hookonium_ingot', 9);
shapeless('raw_hookonium_from_raw_hookonium_block', ['setpoint:raw_hookonium_block'], 'setpoint:raw_hookonium', 9);

// worldgen: the same four vein types as diamond (small, medium, large, buried), with hookonium in place of diamond
const oreTargets = [
  { state: 'setpoint:hookonium_ore', target: { predicate_type: 'minecraft:any_of', rules: [
    { predicate_type: 'minecraft:all_of', rules: [{ predicate_type: 'minecraft:tag_match', tag: 'minecraft:height_specific_ore_replaceables' },
      { predicate_type: 'minecraft:height_match', min_inclusive: 0, max_inclusive: 2031 }] },
    { predicate_type: 'minecraft:all_of', rules: [{ predicate_type: 'minecraft:not', rule: { predicate_type: 'minecraft:tag_match', tag: 'minecraft:height_specific_ore_replaceables' } },
      { predicate_type: 'minecraft:tag_match', tag: 'minecraft:stone_ore_replaceables' }] }] } },
  { state: 'setpoint:deepslate_hookonium_ore', target: { predicate_type: 'minecraft:any_of', rules: [
    { predicate_type: 'minecraft:all_of', rules: [{ predicate_type: 'minecraft:tag_match', tag: 'minecraft:height_specific_ore_replaceables' },
      { predicate_type: 'minecraft:height_match', min_inclusive: -2032, max_inclusive: 8 }] },
    { predicate_type: 'minecraft:all_of', rules: [{ predicate_type: 'minecraft:not', rule: { predicate_type: 'minecraft:tag_match', tag: 'minecraft:height_specific_ore_replaceables' } },
      { predicate_type: 'minecraft:tag_match', tag: 'minecraft:deepslate_ore_replaceables' }] }] } },
];
const deep = { type: 'minecraft:trapezoid', min_inclusive: { above_bottom: -80 }, max_inclusive: { above_bottom: 80 } };
const VEINS = {
  small: { size: 4, discard: 0.5, frequency: { type: 'minecraft:count', count: 7 }, height: deep },
  medium: { size: 8, discard: 0.5, frequency: { type: 'minecraft:count', count: 2 },
    height: { type: 'minecraft:uniform', min_inclusive: { absolute: -64 }, max_inclusive: { absolute: -4 } } },
  large: { size: 12, discard: 0.7, frequency: { type: 'minecraft:rarity_filter', chance: 9 }, height: deep },
  buried: { size: 8, discard: 1.0, frequency: { type: 'minecraft:count', count: 4 }, height: deep },
};
for (const [vein, v] of Object.entries(VEINS)) {
  json(join(DATA, 'worldgen/feature', 'ore_hookonium_' + vein + '.json'),
    { type: 'minecraft:ore', discard_chance_on_air_exposure: v.discard, size: v.size, targets: oreTargets });
  json(join(DATA, 'worldgen/placed_feature', 'ore_hookonium_' + vein + '.json'), {
    feature: 'setpoint:ore_hookonium_' + vein,
    placement: [v.frequency, { type: 'minecraft:in_square' }, { type: 'minecraft:height_range', height: v.height }, { type: 'minecraft:biome' }],
  });
}
json(join(DATA, 'neoforge/biome_modifier', 'hookonium_ore.json'), {
  type: 'neoforge:add_features', biomes: '#minecraft:is_overworld',
  features: Object.keys(VEINS).map((vein) => 'setpoint:ore_hookonium_' + vein), step: 'underground_ores',
});

// =====================================================================================
// M3: DCS console (the control room)
// =====================================================================================

/** An animated texture: frames stacked vertically, 16x16 each, with a .mcmeta next to it. */
function animated(name, frametime, frames) {
  const path = join(ASSETS, 'textures/block', name + '.png');
  if (KEEP_TEXTURES && existsSync(path)) return;
  const px = [];
  for (const draw of frames) {
    const f = Array.from({ length: 16 }, () => Array(16).fill(null));
    const set = (x, y, c) => { if (x >= 0 && x < 16 && y >= 0 && y < 16) f[y][x] = c; };
    const rect = (x, y, w, h, c) => { for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) set(i, j, c); };
    draw({ set, rect });
    px.push(...f);
  }
  const raw = Buffer.alloc(px.length * (1 + 16 * 4));
  px.forEach((row, y) => row.forEach((hex, x) => {
    if (!hex) return;
    const o = y * (1 + 64) + 1 + x * 4;
    raw[o] = parseInt(hex.slice(1, 3), 16); raw[o + 1] = parseInt(hex.slice(3, 5), 16); raw[o + 2] = parseInt(hex.slice(5, 7), 16); raw[o + 3] = hex.length > 7 ? parseInt(hex.slice(7, 9), 16) : 255;
  }));
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(16, 0); ihdr.writeUInt32BE(px.length, 4); ihdr[8] = 8; ihdr[9] = 6;
  write(path, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]));
  json(path + '.mcmeta', { animation: { frametime } });
}

// the screen: an overview with rows of tags, values and a little trend
function consoleScreen(p, banner) {
  p.rect(0, 0, 16, 16, C.steel1);
  p.rect(0, 0, 16, 1, C.steel3); p.rect(0, 15, 16, 1, C.steel0);
  p.rect(1, 1, 14, 10, C.outline); p.rect(2, 2, 12, 8, '#12151b');                // bezel and screen
  for (const [y, w, c] of [[3, 6, '#7c869d'], [5, 9, '#d9deea'], [7, 7, '#d9deea']]) p.rect(3, y, w, 1, c);
  p.set(12, 3, C.cyan); p.set(12, 5, C.cyan); p.set(12, 7, C.cyan);                // status dots
  if (banner) { p.rect(2, 8, 12, 2, C.magenta); p.rect(3, 8, 3, 1, '#ffd0e0'); }    // alarm banner
  else p.rect(3, 9, 9, 1, '#2b2f38');
  p.rect(2, 12, 12, 2, C.steel0);                                                   // keyboard ledge
  for (let x = 3; x < 13; x += 2) p.set(x, 12, C.steel3);
  p.set(14, 13, C.amber);                                                           // power light
}
texture('console_front', (p) => consoleScreen(p, false));
animated('console_front_alarm', 8, [(p) => consoleScreen(p, true), (p) => consoleScreen(p, false)]);
texture('console_side', (p) => {
  plate(p, C.steel1);
  for (let y = 3; y < 13; y += 2) p.rect(3, y, 10, 1, C.steel0);                    // vents
  rivets(p, [[1, 1], [13, 1], [1, 13], [13, 13]]);
});
texture('console_top', (p) => {
  plate(p, C.steel2);
  p.rect(2, 3, 12, 6, C.steel0);                                                    // keyboard
  for (let y = 4; y < 8; y += 2) for (let x = 3; x < 13; x += 2) p.set(x, y, C.steel3);
  p.rect(5, 10, 6, 3, C.steel0); p.rect(6, 11, 4, 1, C.steel3);                     // trackball
});
for (const alarm of [false, true]) {
  M('dcs_console' + (alarm ? '_alarm' : ''), { parent: 'minecraft:block/orientable_with_bottom',
    textures: { front: tex(alarm ? 'console_front_alarm' : 'console_front'), side: tex('console_side'), top: tex('console_top'), bottom: tex('console_side') } });
}
BS('dcs_console', { variants: Object.fromEntries(Object.entries(yaw).flatMap(([f, y]) => [false, true].map((alarm) =>
  [`alarm=${alarm},facing=${f}`, { model: 'setpoint:block/dcs_console' + (alarm ? '_alarm' : ''), ...(y ? { y } : {}) }]))) });
item('dcs_console', 'setpoint:block/dcs_console');
recipe('dcs_console', ['GGG', 'CRC', 'III'], { G: 'minecraft:glass_pane', C: 'minecraft:comparator', R: 'minecraft:redstone_block', I: 'minecraft:iron_ingot' }, 'setpoint:dcs_console');
json(join(ASSETS, 'sounds.json'), {
  'block.centrifugal_pump.cavitate': {
    subtitle: 'subtitles.setpoint.block.centrifugal_pump.cavitate',
    sounds: [1, 2, 3, 4, 5, 6].map((n, i) => ({ name: 'minecraft:block/campfire/crackle' + n, pitch: [0.7, 0.75, 0.7, 0.8, 0.75, 0.7][i] }))
      .concat([1, 2].map((n) => ({ name: 'minecraft:block/bubble_column/bubble' + n, pitch: 0.6, volume: 0.6 }))),
  },
  'block.centrifugal_pump.run': { subtitle: 'subtitles.setpoint.block.centrifugal_pump.run', sounds: [{ name: 'minecraft:block/beacon/ambient', pitch: 1.5, volume: 0.6 }] },
  'block.steam_turbine.run': { subtitle: 'subtitles.setpoint.block.steam_turbine.run', sounds: [{ name: 'minecraft:block/beacon/ambient', volume: 0.7 }] },
  'block.firebox.crackle': { subtitle: 'subtitles.setpoint.block.firebox.crackle', sounds: [1, 2, 3, 4, 5].map((n) => ({ name: 'minecraft:block/furnace/fire_crackle' + n, volume: 0.8 })) },
  'block.dcs_console.alarm': {
    subtitle: 'subtitles.setpoint.block.dcs_console.alarm',
    sounds: [{ name: 'minecraft:note/bell', pitch: 1.0, volume: 0.5 }],
  },
  'entity.pallet_truck.horn': {
    subtitle: 'subtitles.setpoint.entity.pallet_truck.horn',
    sounds: [{ name: 'setpoint:vehicle/horn', volume: 1.0 }], // a one-second note that loops while H is held (tools/make-horn.mjs)
  },
  'entity.forklift.horn': {
    subtitle: 'subtitles.setpoint.entity.forklift.horn',
    sounds: [{ name: 'setpoint:vehicle/horn_low', volume: 1.0 }], // the same, lower (tools/make-horn.mjs low)
  },
  'block.alarm_beacon.horn': { subtitle: 'subtitles.setpoint.block.alarm_beacon.horn', sounds: [{ name: 'setpoint:block/alarm_horn', volume: 1.0 }] }, // tools/make-alarm.mjs
  'block.flare_stack.roar': { subtitle: 'subtitles.setpoint.block.flare_stack.roar', sounds: [{ name: 'setpoint:block/flare_roar', volume: 1.0, attenuation_distance: 48 }] }, // tools/make-flare.mjs
  'block.safety_valve.lift': {
    subtitle: 'subtitles.setpoint.block.safety_valve.lift',
    sounds: [{ name: 'minecraft:random/fizz', pitch: 0.6, volume: 0.6 }, { name: 'minecraft:random/fizz', pitch: 0.5, volume: 0.6 }],
  },
});

// =====================================================================================
// Foundations 2: hand valve, water intake, pipe wrench
// =====================================================================================

// hand valve: a gate valve body on a pipe, a red handwheel, and a rising stem that shows it is open
texture('hand_valve_body', (p) => {
  plate(p, C.steel1);
  p.rect(3, 3, 10, 10, C.steel0); p.rect(4, 4, 8, 8, C.steel2);
  rivets(p, [[1, 1], [13, 1], [1, 13], [13, 13]]);
});
texture('hand_valve_wheel', (p) => {
  const rim = '#b8312f', dark = '#7a1f1d', hub = C.steel3;
  for (let i = 1; i < 15; i++) { p.set(i, 1, rim); p.set(i, 14, dark); p.set(1, i, rim); p.set(14, i, dark); }
  p.rect(2, 2, 12, 1, '#d24a45');
  for (let i = 2; i < 14; i++) { p.set(i, i, rim); p.set(15 - i, i, rim); } // spokes
  p.rect(6, 6, 4, 4, hub); p.rect(7, 7, 2, 2, C.steel0);
});
texture('hand_valve_stem', (p) => { p.rect(0, 0, 16, 16, C.steel3); for (let y = 0; y < 16; y += 2) p.rect(0, y, 16, 1, C.steel4); });

for (const open of [false, true]) {
  M('hand_valve' + (open ? '_open' : '_shut'), { // flow along Z, wheel on top
    parent: 'minecraft:block/block',
    textures: { pipe: tex('pipe'), body: tex('hand_valve_body'), wheel: tex('hand_valve_wheel'), stem: tex('hand_valve_stem'), particle: tex('hand_valve_body') },
    elements: [
      box([5, 5, 0], [11, 11, 16], {
        north: { uv: [5, 5, 11, 11], texture: '#pipe', cullface: 'north' }, south: { uv: [5, 5, 11, 11], texture: '#pipe', cullface: 'south' },
        east: { uv: [0, 5, 16, 11], texture: '#pipe' }, west: { uv: [0, 5, 16, 11], texture: '#pipe' }, down: { uv: [5, 0, 11, 16], texture: '#pipe' },
      }),
      box([4, 4, 5], [12, 11, 11], allFaces([4, 4, 12, 11], '#body')),
      box([6, 11, 6], [10, 13, 10], allFaces([6, 6, 10, 10], '#body', ['down'])),
      box([7, 13, 7], [9, open ? 18 : 14, 9], allFaces([7, 0, 9, 5], '#stem', ['down'])),
      box([3, 14, 3], [13, 15, 13], {
        up: { uv: [0, 0, 16, 16], texture: '#wheel' }, down: { uv: [0, 0, 16, 16], texture: '#wheel' },
        north: { uv: [3, 0, 13, 1], texture: '#wheel' }, south: { uv: [3, 0, 13, 1], texture: '#wheel' },
        east: { uv: [3, 0, 13, 1], texture: '#wheel' }, west: { uv: [3, 0, 13, 1], texture: '#wheel' },
      }),
    ],
  });
}
{
  const axisRot = { z: {}, x: { y: 90 }, y: { x: 90 } };
  const variants = {};
  for (const [axis, rot] of Object.entries(axisRot)) for (const open of [false, true]) for (const powered of [false, true]) {
    variants[`axis=${axis},open=${open},powered=${powered}`] = { model: 'setpoint:block/hand_valve' + (open && !powered ? '_open' : '_shut'), ...rot };
  }
  BS('hand_valve', { variants });
  item('hand_valve', 'setpoint:block/hand_valve_open');
}
recipe('hand_valve', [' L ', 'CIC'], { L: 'minecraft:lever', C: 'minecraft:copper_ingot', I: 'minecraft:iron_ingot' }, 'setpoint:hand_valve');

// gas valves (0.2): the hand valve and the control valve on a gas pipe, the hand valve with a yellow wheel
texture('gas_valve_wheel', (p) => {
  const rim = '#e0b021', dark = '#9c7a12', hub = C.steel3;
  for (let i = 1; i < 15; i++) { p.set(i, 1, rim); p.set(i, 14, dark); p.set(1, i, rim); p.set(14, i, dark); }
  p.rect(2, 2, 12, 1, '#f2cc4a');
  for (let i = 2; i < 14; i++) { p.set(i, i, rim); p.set(15 - i, i, rim); }
  p.rect(6, 6, 4, 4, hub); p.rect(7, 7, 2, 2, C.steel0);
});
for (const open of [false, true]) {
  const base = JSON.parse(JSON.stringify(MODELS['hand_valve' + (open ? '_open' : '_shut')]));
  base.textures = { ...base.textures, pipe: tex('gas_pipe'), wheel: tex('gas_valve_wheel') };
  M('gas_valve' + (open ? '_open' : '_shut'), base);
}
{
  const axisRot = { z: {}, x: { y: 90 }, y: { x: 90 } };
  const variants = {};
  for (const [axis, rot] of Object.entries(axisRot)) for (const open of [false, true]) for (const powered of [false, true]) {
    variants[`axis=${axis},open=${open},powered=${powered}`] = { model: 'setpoint:block/gas_valve' + (open && !powered ? '_open' : '_shut'), ...rot };
  }
  BS('gas_valve', { variants });
  item('gas_valve', 'setpoint:block/gas_valve_open');
}
for (let open = 0; open <= 4; open++) {
  const base = JSON.parse(JSON.stringify(MODELS['control_valve_' + open]));
  base.textures = { ...base.textures, pipe: tex('gas_pipe') };
  M('gas_control_valve_' + open, base);
}
BS('gas_control_valve', {
  variants: Object.fromEntries(Object.entries(axisRotation).flatMap(([axis, rot]) =>
    [0, 1, 2, 3, 4].map((open) => [`axis=${axis},open=${open}`, { model: 'setpoint:block/gas_control_valve_' + open, ...rot }]))),
});
item('gas_control_valve', 'setpoint:block/gas_control_valve_2');
shapeless('gas_valve', ['setpoint:hand_valve', 'minecraft:yellow_dye'], 'setpoint:gas_valve', 1);
shapeless('gas_control_valve', ['setpoint:control_valve', 'minecraft:yellow_dye'], 'setpoint:gas_control_valve', 1);

// water intake: a steel strainer box, bars on every side
texture('water_intake_side', (p) => {
  plate(p, C.steel1);
  p.rect(2, 2, 12, 12, '#1c3f6e');
  for (let x = 3; x < 14; x += 3) p.rect(x, 2, 1, 12, C.steel3);
  p.rect(2, 7, 12, 1, C.steel2);
  p.set(4, 4, '#3a6fd8'); p.set(10, 10, '#3a6fd8');
});
texture('water_intake_top', (p) => {
  plate(p, C.steel2);
  p.rect(4, 4, 8, 8, C.steel0); p.rect(5, 5, 6, 6, C.cu2); p.rect(6, 6, 4, 4, C.cu1); // pipe flange
  rivets(p, [[1, 1], [13, 1], [1, 13], [13, 13]]);
});
M('water_intake', { parent: 'minecraft:block/cube_bottom_top', textures: { side: tex('water_intake_side'), top: tex('water_intake_top'), bottom: tex('water_intake_side') } });
BS('water_intake', { variants: { '': { model: 'setpoint:block/water_intake' } } });
item('water_intake', 'setpoint:block/water_intake');
recipe('water_intake', ['IBI', 'BPB', 'IBI'], { I: 'minecraft:iron_ingot', B: 'minecraft:iron_bars', P: 'setpoint:pipe' }, 'setpoint:water_intake');

// pipe wrench
texture('pipe_wrench', (p) => pixels(p, [
  '................',
  '..........kkk...',
  '.........k...k..',
  '.........k.......',
  '.........k...k..',
  '..........kkk...',
  '.........kk.....',
  '........kk......',
  '.......kk.......',
  '......kk........',
  '.....rr.........',
  '....rrr.........',
  '...rrr..........',
  '..rrr...........',
  '..rr............',
  '................',
], { k: C.steel4, r: '#b8312f' }), 'item');
json(join(ASSETS, 'models/item/pipe_wrench.json'), { parent: 'minecraft:item/handheld', textures: { layer0: 'setpoint:item/pipe_wrench' } });
item('pipe_wrench', 'setpoint:item/pipe_wrench');
recipe('pipe_wrench', ['I I', ' I ', ' C '], { I: 'minecraft:iron_ingot', C: 'minecraft:copper_ingot' }, 'setpoint:pipe_wrench');

// flow transmitter: an inline meter body with a flow arrow, and a little display on top
texture('flow_meter_body', (p) => {
  plate(p, C.steel1);
  p.rect(3, 6, 8, 4, C.cyanDark); p.rect(3, 7, 9, 2, C.cyan);           // flow arrow
  p.set(11, 6, C.cyan); p.set(11, 9, C.cyan); p.set(12, 7, C.cyan); p.set(12, 8, C.cyan); p.set(13, 7, C.cyanDark); p.set(13, 8, C.cyanDark);
  p.rect(2, 12, 6, 2, C.amber); p.rect(3, 13, 4, 1, '#c98a2c');          // tag plate
  rivets(p, [[1, 1], [13, 1], [1, 13], [13, 13]]);
});
texture('flow_meter_display', (p) => {
  p.rect(0, 0, 16, 16, C.outline); p.rect(2, 2, 12, 12, '#0d1a1c');
  for (const x of [3, 6, 9]) { p.rect(x, 5, 2, 1, C.cyan); p.rect(x, 9, 2, 1, C.cyan); p.rect(x, 6, 1, 3, C.cyanDark); }
  p.rect(12, 9, 1, 1, C.cyan);
});
M('flow_transmitter', {
  parent: 'minecraft:block/block',
  textures: { pipe: tex('pipe'), body: tex('flow_meter_body'), display: tex('flow_meter_display'), particle: tex('flow_meter_body') },
  elements: [
    box([5, 5, 0], [11, 11, 16], {
      north: { uv: [5, 5, 11, 11], texture: '#pipe', cullface: 'north' }, south: { uv: [5, 5, 11, 11], texture: '#pipe', cullface: 'south' },
      east: { uv: [0, 5, 16, 11], texture: '#pipe' }, west: { uv: [0, 5, 16, 11], texture: '#pipe' }, down: { uv: [5, 0, 11, 16], texture: '#pipe' },
    }),
    box([4, 4, 3], [12, 12, 13], {
      east: { uv: [0, 2, 16, 14], texture: '#body' }, west: { uv: [0, 2, 16, 14], texture: '#body' },
      north: { uv: [4, 4, 12, 12], texture: '#body' }, south: { uv: [4, 4, 12, 12], texture: '#body' },
      up: { uv: [4, 3, 12, 13], texture: '#body' }, down: { uv: [4, 3, 12, 13], texture: '#body' },
    }),
    box([5, 12, 5], [11, 15, 11], {
      up: { uv: [2, 2, 14, 14], texture: '#display' },
      north: { uv: [0, 0, 16, 3], texture: '#body' }, south: { uv: [0, 0, 16, 3], texture: '#body' },
      east: { uv: [0, 0, 16, 3], texture: '#body' }, west: { uv: [0, 0, 16, 3], texture: '#body' },
    }),
  ],
});
BS('flow_transmitter', { variants: { 'axis=z': { model: 'setpoint:block/flow_transmitter' }, 'axis=x': { model: 'setpoint:block/flow_transmitter', y: 90 },
  'axis=y': { model: 'setpoint:block/flow_transmitter', x: 90 } } });
item('flow_transmitter', 'setpoint:block/flow_transmitter');
recipe('flow_transmitter', [' G ', 'PCP'], { G: 'minecraft:glass_pane', P: 'setpoint:pipe', C: 'minecraft:comparator' }, 'setpoint:flow_transmitter');

// =====================================================================================
// Drum and IBC: containers you can carry, with their liquid inside
// =====================================================================================

// Models bigger than a block are cut at every block line, so each piece maps its texture 1 to 1:
// the pixels stay the size of every other Minecraft pixel. texFor(face, piece) gives a face its texture (or none).
function tiled(from, to, texFor, extra = () => ({})) {
  const cuts = (a, b) => { const pts = [a]; for (let k = Math.floor(a / 16) * 16 + 16; k < b; k += 16) pts.push(k); pts.push(b); return pts; };
  const xs = cuts(from[0], to[0]), ys = cuts(from[1], to[1]), zs = cuts(from[2], to[2]), out = [];
  for (let a = 0; a < xs.length - 1; a++) for (let b = 0; b < ys.length - 1; b++) for (let c = 0; c < zs.length - 1; c++) {
    const f = [xs[a], ys[b], zs[c]], t = [xs[a + 1], ys[b + 1], zs[c + 1]];
    const cell = f.map((v) => Math.floor(v / 16 + 1e-9) * 16);
    const [x1, y1, z1] = f.map((v, k) => v - cell[k]), [x2, y2, z2] = t.map((v, k) => v - cell[k]);
    const uv = { north: [16 - x2, 16 - y2, 16 - x1, 16 - y1], south: [x1, 16 - y2, x2, 16 - y1], east: [16 - z2, 16 - y2, 16 - z1, 16 - y1],
      west: [z1, 16 - y2, z2, 16 - y1], up: [x1, z1, x2, z2], down: [x1, 16 - z2, x2, 16 - z1] };
    const inside = { west: a > 0, east: a < xs.length - 2, down: b > 0, up: b < ys.length - 2, north: c > 0, south: c < zs.length - 2 };
    const faces = {};
    for (const face of Object.keys(uv)) {
      if (inside[face]) continue;
      const texture = texFor(face, { from: f, to: t });
      if (texture) faces[face] = { uv: uv[face], texture, ...extra(face) };
    }
    if (Object.keys(faces).length) out.push(box(f, t, faces));
  }
  return out;
}
// The inventory shows a big model shrunk to one block, around its middle.
function shrunk(elements, centre, factor) {
  const map = (p) => p.map((v, k) => +(8 + (v - centre[k]) * factor).toFixed(4));
  return elements.map((e) => ({ ...e, from: map(e.from), to: map(e.to), ...(e.rotation ? { rotation: { ...e.rotation, origin: map(e.rotation.origin) } } : {}) }));
}

// drum: a blue steel barrel, one and a half blocks high. Its round side is a pixel circle: slices that step in towards
// the front and back, so from every side it looks round. Two hoops are pressed into the steel; the label sits on the front.
// The steel is grey: the tint gives it its colour, the drum's own blue or the colour of a dye.
texture('drum_side', (p) => { // the lower block: bottom chime, a hoop, room for the label
  p.rect(0, 0, 16, 16, '#c8c8c8');
  p.rect(0, 15, 16, 1, '#8a8a8a');                                         // bottom chime
  p.rect(0, 7, 16, 1, '#e0e0e0'); p.rect(0, 8, 16, 1, '#8a8a8a');         // pressed hoop
  p.rect(1, 0, 1, 15, '#e0e0e0');                                          // highlight
});
texture('drum_side_top', (p) => { // the upper half block (rows 8 to 15 are used): top chime and the second hoop
  p.rect(0, 0, 16, 16, '#c8c8c8');
  p.rect(0, 8, 16, 1, '#f8f8f8'); p.rect(0, 9, 16, 1, '#9c9c9c');         // top chime
  p.rect(0, 12, 16, 1, '#e0e0e0'); p.rect(0, 13, 16, 1, '#8a8a8a');       // pressed hoop
  p.rect(1, 10, 1, 6, '#e0e0e0');
});
texture('drum_top', (p) => {
  p.rect(0, 0, 16, 16, '#bababa');
  p.rect(2, 2, 12, 12, '#d2d2d2');                                         // the lid inside its rim
  p.rect(5, 4, 2, 2, C.steel3); p.rect(10, 10, 2, 2, C.steel3);            // bungs
  p.set(5, 4, C.steel4); p.set(10, 10, C.steel4);
});
texture('drum_label', (p) => {
  p.rect(0, 0, 16, 16, '#e8ecf2');
  p.rect(2, 3, 9, 2, '#9aa3b2'); p.rect(2, 7, 6, 2, '#9aa3b2'); p.rect(2, 11, 8, 2, '#9aa3b2');
  p.rect(12, 9, 3, 5, '#ffb547');                                          // a hazard diamond, in pixels
});
texture('drum_bung', (p) => { p.rect(0, 0, 16, 16, C.steel3); p.rect(0, 0, 16, 1, C.steel4); });
{
  const H = 24;
  // the circle, radius 7: for each band of z, how far it reaches in x
  const bands = [[1, 2, 5, 11], [2, 3, 3, 13], [3, 5, 2, 14], [5, 11, 1, 15], [11, 13, 2, 14], [13, 14, 3, 13], [14, 15, 5, 11]];
  const elements = bands.flatMap(([z1, z2, x1, x2]) => tiled([x1, 0, z1], [x2, H, z2],
    (face, piece) => face === 'up' || face === 'down' ? '#top' : piece.from[1] >= 16 ? '#side_top' : '#side',
    (face) => (face === 'down' ? { cullface: 'down', tintindex: 0 } : { tintindex: 0 })));
  elements.push(box([5.5, 9, 0.9], [10.5, 13, 1], { north: { uv: [0, 0, 16, 16], texture: '#label' } }));        // the label on the front
  elements.push(box([5, H, 4], [7, H + 0.5, 6], allFaces([0, 0, 2, 2], '#bung', ['down'])), box([10, H, 10], [12, H + 0.5, 12], allFaces([0, 0, 2, 2], '#bung', ['down'])));
  const textures = { side: tex('drum_side'), side_top: tex('drum_side_top'), top: tex('drum_top'), label: tex('drum_label'), bung: tex('drum_bung'), particle: tex('drum_side') };
  M('drum', { parent: 'minecraft:block/block', textures, elements });
  M('drum_item', { parent: 'minecraft:block/block', textures, elements: shrunk(elements, [8, (H + 0.5) / 2, 8], 16 / (H + 0.5)) });
  BS('drum', { variants: Object.fromEntries(Object.entries(yaw).map(([f, y]) => [`facing=${f}`, { model: 'setpoint:block/drum', ...(y ? { y } : {}) }])) });
  json(join(ASSETS, 'items', 'drum.json'), { model: { type: 'minecraft:model', model: 'setpoint:block/drum_item',
    tints: [{ type: 'minecraft:dye', default: 0xFF3B79DE | 0 }] } }); // its own blue, or the dye it was painted with
}
recipe('drum', ['I I', 'IDI', 'III'], { I: 'minecraft:iron_ingot', D: 'minecraft:blue_dye' }, 'setpoint:drum');

// IBC: two by two by two blocks. A white tote in a steel cage on a pallet, an outlet valve at the front. The liquid shows
// through the tote: a dotted layer just outside it, tinted with the colour of the liquid (every other pixel, so the
// white shines through). The model belongs to the front corner block and reaches over the other seven.
texture('ibc_tote', (p) => {
  p.rect(0, 0, 16, 16, '#e6e8e3');
  for (let x = 0; x < 16; x += 4) p.rect(x, 0, 1, 16, '#d9dcd6');
  for (const y of [3, 7, 11]) p.rect(12, y, 3, 1, '#9aa09a');               // graduation marks
});
texture('ibc_cage', (p) => { p.rect(0, 0, 16, 16, '#8d939c'); p.rect(0, 0, 16, 1, '#b5bac2'); p.rect(0, 0, 1, 16, '#b5bac2'); p.rect(15, 0, 1, 16, '#5b6068'); });
texture('ibc_pallet', (p) => {
  p.rect(0, 0, 16, 16, '#3a3d42');
  p.rect(3, 4, 10, 8, '#1e2024');                                            // fork slot
  p.rect(0, 0, 16, 1, '#4d5157');
});
texture('ibc_cap', (p) => { p.rect(0, 0, 16, 16, '#f2f2ee'); p.rect(0, 0, 16, 1, '#ffffff'); p.rect(0, 15, 16, 1, '#c9ccc6'); });
texture('ibc_valve', (p) => { p.rect(0, 0, 16, 16, C.steel2); p.rect(0, 0, 16, 1, C.steel4); });
texture('ibc_liquid', (p) => {
  for (let y = 1; y < 16; y++) for (let x = 0; x < 16; x++) if ((x + y) % 2 === 0) p.set(x, y, '#d0d0d0');
  p.rect(0, 0, 16, 1, '#ffffff');                                            // the surface
});
let IBC_ON_PALLET; // an IBC's elements above its own base, for one standing on a wooden pallet
{
  const all = (t) => () => t;
  const ibc = (level) => {
    const elements = [
      ...tiled([0, 0, 0], [32, 3, 32], all('#pallet'), (face) => (face === 'down' ? { cullface: 'down' } : {})),
      ...tiled([2, 3, 2], [30, 30, 30], (face) => (face === 'down' ? null : '#tote'), () => ({ tintindex: 1 })), // white, or black when dyed
    ];
    if (level > 0) { // the liquid behind the tote: its surface row only at the top
      const top = 3 + Math.round(27 * level / 8);
      const pieces = top <= 16 ? [[3, top, 0]] : [[3, 16, 1], [16, top, 0]];
      for (const [y1, y2, skip] of pieces) for (const [a, b] of [[1.98, 16], [16, 30.02]]) {
        const h = y2 - y1, u = b - a;
        const face = { uv: [0, skip, Math.min(16, u), skip + h], texture: '#liquid', tintindex: 0 };
        elements.push(box([a, y1, 1.98], [b, y2, 1.99], { north: face }), box([a, y1, 30.01], [b, y2, 30.02], { south: face }),
          box([1.98, y1, a], [1.99, y2, b], { west: face }), box([30.01, y1, a], [30.02, y2, b], { east: face }));
      }
    }
    const cage = all('#cage');
    for (const [x, z] of [[0, 0], [30, 0], [0, 30], [30, 30]]) elements.push(...tiled([x, 3, z], [x + 2, 32, z + 2], cage));      // corner posts
    for (const x of [10, 21]) elements.push(...tiled([x, 3, 0.8], [x + 1, 30, 1.6], cage), ...tiled([x, 3, 30.4], [x + 1, 30, 31.2], cage));
    for (const z of [10, 21]) elements.push(...tiled([0.8, 3, z], [1.6, 30, z + 1], cage), ...tiled([30.4, 3, z], [31.2, 30, z + 1], cage));
    for (const y of [11, 20]) { // two rings around the tote
      elements.push(...tiled([2, y, 0.8], [30, y + 1, 1.6], cage), ...tiled([2, y, 30.4], [30, y + 1, 31.2], cage),
        ...tiled([0.8, y, 2], [1.6, y + 1, 30], cage), ...tiled([30.4, y, 2], [31.2, y + 1, 30], cage));
    }
    elements.push(...tiled([2, 30, 0], [30, 32, 2], cage), ...tiled([2, 30, 30], [30, 32, 32], cage),                         // top frame
      ...tiled([0, 30, 2], [2, 32, 30], cage), ...tiled([30, 30, 2], [32, 32, 30], cage));
    elements.push(...tiled([13, 30, 13], [19, 32, 19], (face) => (face === 'down' ? null : '#cap')));                           // filling cap
    elements.push(box([14, 3, -1], [18, 7, 2], allFaces([0, 0, 4, 4], '#valve')), box([15, 7, -1], [17, 9, 0], allFaces([0, 0, 2, 2], '#pallet'))); // outlet valve
    return elements;
  };
  const textures = { pallet: tex('ibc_pallet'), tote: tex('ibc_tote'), cage: tex('ibc_cage'), cap: tex('ibc_cap'), valve: tex('ibc_valve'),
    liquid: tex('ibc_liquid'), particle: tex('ibc_tote') };
  IBC_ON_PALLET = { textures, elements: (level) => ibc(level).filter((e) => !(e.from[1] === 0 && e.to[1] === 3)) }; // without its own base
  for (let level = 0; level <= 8; level++) M('ibc_' + level, { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: ibc(level) });
  M('ibc_item', { parent: 'minecraft:block/block', textures, elements: shrunk(ibc(5), [16, 16, 16], 0.5) });
  BS('ibc', { variants: Object.fromEntries(Object.entries(yaw).flatMap(([f, y]) => Array.from({ length: 9 }, (_, l) =>
    [`facing=${f},level=${l}`, { model: 'setpoint:block/ibc_' + l, ...(y ? { y } : {}) }]))) });
  json(join(ASSETS, 'items', 'ibc.json'), { model: { type: 'minecraft:model', model: 'setpoint:block/ibc_item',
    tints: [{ type: 'minecraft:constant', value: -12618012 }, { type: 'minecraft:dye', default: -1 }] } }); // water blue; a white or black tote
}
// =====================================================================================
// Pallet: two by two blocks of wood. Up to four drums on it, each in its own colour, held by a green strap
// =====================================================================================
texture('pallet_board', (p) => {
  p.rect(0, 0, 16, 16, '#b8915a');
  for (const y of [3, 9, 13]) p.rect(0, y, 16, 1, '#a37d49');               // grain
  p.set(4, 6, '#7a5a30'); p.set(12, 11, '#7a5a30');                          // nails
});
texture('pallet_block', (p) => {
  p.rect(0, 0, 16, 16, '#9c7642');
  p.rect(0, 0, 16, 1, '#b8915a');
  for (const x of [2, 7, 12]) p.rect(x, 4, 3, 12, '#3b2c18');               // the fork openings between the blocks
});
texture('pallet_strap', (p) => { p.rect(0, 0, 16, 16, '#2f9e44'); p.rect(0, 0, 16, 1, '#5ac26a'); p.rect(0, 15, 16, 1, '#1f6e2e'); });
// A drum standing on the pallet: the drum's own shape, moved to (cx, cz), its steel tinted with tint index tint.
function drumOnPallet(cx, cz, y0, tint) {
  const bands = [[1, 2, 5, 11], [2, 3, 3, 13], [3, 5, 2, 14], [5, 11, 1, 15], [11, 13, 2, 14], [13, 14, 3, 13], [14, 15, 5, 11]];
  const dx = cx - 8, dz = cz - 8, out = [];
  for (const [z1, z2, x1, x2] of bands) {
    for (const [ya, yb, side] of [[0, 16, '#drum_side'], [16, 24, '#drum_side_top']]) {
      const vTop = side === '#drum_side' ? 0 : 8;
      out.push(box([x1 + dx, y0 + ya, z1 + dz], [x2 + dx, y0 + yb, z2 + dz], {
        north: { uv: [x1, vTop, x2, vTop + (yb - ya)], texture: side, tintindex: tint }, south: { uv: [x1, vTop, x2, vTop + (yb - ya)], texture: side, tintindex: tint },
        east: { uv: [z1, vTop, z2, vTop + (yb - ya)], texture: side, tintindex: tint }, west: { uv: [z1, vTop, z2, vTop + (yb - ya)], texture: side, tintindex: tint },
        ...(yb === 24 ? { up: { uv: [x1, z1, x2, z2], texture: '#drum_top', tintindex: tint } } : {}),
      }));
    }
  }
  out.push(box([5.5 + dx, y0 + 9, 0.9 + dz], [10.5 + dx, y0 + 13, 1 + dz], { north: { uv: [0, 0, 16, 16], texture: '#drum_label' } }));
  out.push(box([5 + dx, y0 + 24, 4 + dz], [7 + dx, y0 + 24.5, 6 + dz], allFaces([0, 0, 2, 2], '#drum_bung', ['down'])));
  return out;
}
// The strap: a band hugging the drums, straight between them and round where it bends around one.
function strapAround(centres, y, h = 1.5, r = 7.35, t = 0.5) {
  if (centres.length < 2) return [];
  const mx = centres.reduce((a, c) => a + c[0], 0) / centres.length, mz = centres.reduce((a, c) => a + c[1], 0) / centres.length;
  const pts = [...centres].sort((a, b) => Math.atan2(a[1] - mz, a[0] - mx) - Math.atan2(b[1] - mz, b[0] - mx));
  const out = [];
  // each piece is laid along the axis it nearly follows and turned at most 45°, so it stays inside the model's bounds
  const piece = (px, pz, tx, tz, len) => {
    const alongX = Math.abs(tx) >= Math.abs(tz);
    const angle = +(alongX ? Math.atan2(-tz, tx) : Math.atan2(tx, tz)) * 180 / Math.PI;
    const turn = ((angle + 90) % 180 + 180) % 180 - 90; // a band piece looks the same turned half round
    const from = alongX ? [px - len / 2, y, pz - t / 2] : [px - t / 2, y, pz - len / 2], to = alongX ? [px + len / 2, y + h, pz + t / 2] : [px + t / 2, y + h, pz + len / 2];
    out.push({ from, to, rotation: { origin: [px, y, pz], x: 0, y: +turn.toFixed(3), z: 0 }, faces: allFaces([0, 0, Math.min(16, len), h], '#pallet_strap') });
  };
  const n = pts.length, normals = [];
  for (let i = 0; i < n; i++) { // the straight runs, each on the outside of a line from drum to drum
    const a = pts[i], b = pts[(i + 1) % n], len = Math.hypot(b[0] - a[0], b[1] - a[1]), tx = (b[0] - a[0]) / len, tz = (b[1] - a[1]) / len;
    const mxe = (a[0] + b[0]) / 2, mze = (a[1] + b[1]) / 2;
    let nx = tz, nz = -tx;
    const awayX = n === 2 ? (i === 0 ? 1 : -1) * nx : mxe - mx, awayZ = n === 2 ? (i === 0 ? 1 : -1) * nz : mze - mz;
    if (nx * awayX + nz * awayZ < 0) { nx = -nx; nz = -nz; }
    normals.push([nx, nz]);
    piece(mxe + nx * r, mze + nz * r, tx, tz, len);
  }
  for (let i = 0; i < n; i++) { // the bends, around each drum from one straight run to the next
    const c = pts[i], nin = normals[(i - 1 + n) % n], nout = normals[i];
    const a1 = Math.atan2(nin[1], nin[0]);
    let delta = Math.atan2(nout[1], nout[0]) - a1;
    while (delta <= -Math.PI) delta += 2 * Math.PI;
    while (delta > Math.PI) delta -= 2 * Math.PI;
    const mid = a1 + delta / 2, ox = c[0] - mx, oz = c[1] - mz;
    if (Math.cos(mid) * ox + Math.sin(mid) * oz < 0) delta = delta > 0 ? delta - 2 * Math.PI : delta + 2 * Math.PI; // bend the outside way
    const steps = Math.max(1, Math.ceil(Math.abs(delta) / (Math.PI / 12)));
    for (let k = 0; k < steps; k++) {
      const phi = a1 + delta * (k + 0.5) / steps;
      piece(c[0] + r * Math.cos(phi), c[1] + r * Math.sin(phi), -Math.sin(phi), Math.cos(phi), r * Math.abs(delta) / steps + 0.35);
    }
  }
  return out;
}
{
  const layouts = { 0: [], 1: [[16, 16]], 2: [[9, 16], [23, 16]], 3: [[16, 9.94], [9, 22.06], [23, 22.06]], 4: [[9, 9], [23, 9], [9, 23], [23, 23]] };
  const deck = () => {
    const els = [...tiled([0, 0, 0], [32, 2, 32], () => '#pallet_block', (face) => (face === 'down' ? { cullface: 'down' } : {}))];
    for (const [z1, z2] of [[0, 5], [6.75, 11.75], [13.5, 18.5], [20.25, 25.25], [27, 32]]) {
      els.push(...tiled([0, 2, z1], [32, 3, z2], (face) => (face === 'down' ? null : '#pallet_board')));
    }
    return els;
  };
  const textures = { pallet_board: tex('pallet_board'), pallet_block: tex('pallet_block'), pallet_strap: tex('pallet_strap'), drum_side: tex('drum_side'),
    drum_side_top: tex('drum_side_top'), drum_top: tex('drum_top'), drum_label: tex('drum_label'), drum_bung: tex('drum_bung'), particle: tex('pallet_board') };
  for (const [count, centres] of Object.entries(layouts)) {
    const elements = deck();
    centres.forEach(([cx, cz], i) => elements.push(...drumOnPallet(cx, cz, 3, i)));
    elements.push(...strapAround(centres, 3 + 5), ...strapAround(centres, 3 + 18));
    M('pallet_' + count, { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements });
  }
  // an IBC on the pallet: it stands on the wood instead of its own base (tint 0 its liquid, tint 1 its tote)
  for (let level = 0; level <= 8; level++) {
    M('pallet_ibc_' + level, { parent: 'minecraft:block/block', ambientocclusion: false, textures: { ...IBC_ON_PALLET.textures, ...textures },
      elements: [...deck(), ...IBC_ON_PALLET.elements(level)] });
  }
  M('pallet_item', { parent: 'minecraft:block/block', textures, elements: shrunk(deck(), [16, 8, 16], 0.5) });
  BS('pallet', { variants: Object.fromEntries(Object.entries(yaw).flatMap(([f, y]) => [0, 1, 2, 3, 4].flatMap((n) => Array.from({ length: 10 }, (_, k) =>
    [`drums=${n},facing=${f},ibc=${k}`, { model: 'setpoint:block/' + (k ? 'pallet_ibc_' + (k - 1) : 'pallet_' + n), ...(y ? { y } : {}) }])))) });
  item('pallet', 'setpoint:block/pallet_item');
  M('pallet_part', { textures: { particle: tex('pallet_board') } });
  BS('pallet_part', { variants: Object.fromEntries([0, 1, 2].map((k) => [`kind=${k}`, { model: 'setpoint:block/pallet_part' }])) });
}
recipe('pallet', ['SSS', 'P P'], { S: 'minecraft:oak_slab', P: 'minecraft:oak_planks' }, 'setpoint:pallet');

// =====================================================================================
// Pallet truck: an electric walkie pallet truck. Silver battery box, an orange rounded nose, a black top, a grey
// tiller leaning forward with black handles and a red belly button, and two long forks with load rollers.
// The model's tiller points north; the entity stands at (8, 0, 8) in it.
// =====================================================================================
texture('truck_body', (p) => {
  p.rect(0, 0, 16, 16, '#b6babf');
  p.rect(0, 0, 16, 1, '#cfd2d6'); p.rect(0, 15, 16, 1, '#8e9297');
  p.rect(3, 4, 10, 6, '#aeb2b7'); p.rect(3, 4, 10, 1, '#9a9ea4'); p.rect(3, 9, 10, 1, '#c7cace'); // a pressed panel
  p.set(14, 13, '#6f7378');
});
texture('truck_body_dark', (p) => { p.rect(0, 0, 16, 16, '#7d8187'); p.rect(0, 0, 16, 1, '#8e9297'); });
texture('truck_orange', (p) => { p.rect(0, 0, 16, 16, '#f08a1c'); p.rect(0, 0, 16, 1, '#f6a647'); p.rect(0, 15, 16, 1, '#c86e12'); });
texture('truck_black', (p) => { p.rect(0, 0, 16, 16, '#2a2c30'); for (const [x, y] of [[3, 4], [11, 9], [6, 13]]) p.set(x, y, '#3a3d42'); });
texture('truck_red', (p) => { p.rect(0, 0, 16, 16, '#c62828'); p.rect(0, 0, 16, 1, '#e04848'); });
texture('truck_tiller', (p) => { p.rect(0, 0, 16, 16, '#8f949a'); p.rect(0, 0, 1, 16, '#a9adb2'); p.rect(15, 0, 1, 16, '#6f7378'); });
texture('truck_fork', (p) => { p.rect(0, 0, 16, 16, '#c4c7cb'); p.rect(0, 0, 16, 1, '#dadde0'); p.rect(0, 15, 16, 1, '#a3a7ac'); });
texture('truck_wheel', (p) => { p.rect(0, 0, 16, 16, '#b0623a'); p.rect(6, 6, 4, 4, '#7a3f22'); });
{
  const all = (t) => () => t;
  const tilt = { origin: [8, 15, -13], x: -20, y: 0, z: 0 }; // the tiller leans forward (its origin is already two pixels back)
  const elements = [
    ...tiled([-1, 1, -14], [17, 14, -3], (f) => (f === 'up' ? null : '#body')),                       // battery box
    ...tiled([-1, 0, -17], [17, 1, -3], all('#body_dark')),                                             // chassis
    ...tiled([-1, 1, -16], [17, 12, -14], (f) => (f === 'south' ? null : '#orange')),                  // the nose
    ...tiled([0, 1.5, -17], [16, 11, -16], (f) => (f === 'south' ? null : '#orange')),                 // rounded off
    ...tiled([-1, 12, -16], [17, 14, -14], all('#orange')),                                             // the band over the top
    ...tiled([-1, 14, -14], [17, 15, -3], all('#black')),                                               // black lid
    box([6, 15, -12], [10, 15.6, -10], allFaces([0, 0, 4, 2], '#red', ['down'])),                       // the red grip
    box([6, 0, -12], [10, 1, -6], allFaces([0, 0, 4, 6], '#black')),                                    // drive wheel
  ];
  for (const x of [-1, 12]) { // the forks, raised a little at the tips over their load rollers
    elements.push(...tiled([x, 1, -3], [x + 5, 3, 28], all('#fork')), ...tiled([x, 0, -3], [x + 5, 1, 2], all('#fork')),
      box([x + 1.5, 0, 22], [x + 3.5, 1.2, 26], allFaces([0, 0, 2, 4], '#wheel')));
  }
  const tiller = [
    box([7, 15, -16], [9, 27, -14], allFaces([0, 0, 2, 12], '#tiller')),
    box([3.5, 27, -16.5], [12.5, 28, -13.5], allFaces([0, 0, 9, 1], '#black')),
    box([3, 23, -16], [4, 28, -14], allFaces([0, 0, 1, 5], '#black')), box([12, 23, -16], [13, 28, -14], allFaces([0, 0, 1, 5], '#black')),
    box([7, 28, -16], [9, 29.5, -14], allFaces([0, 0, 2, 2], '#red')),
  ].map((e) => ({ ...e, rotation: tilt }));
  elements.push(...tiller);
  // two pixels back, so the nose and the tiller stay inside a model's bounds (the entity stands at (8, 0, 8))
  for (const e of elements) { e.from = [e.from[0], e.from[1], e.from[2] + 2]; e.to = [e.to[0], e.to[1], e.to[2] + 2]; }
  const textures = { body: tex('truck_body'), body_dark: tex('truck_body_dark'), orange: tex('truck_orange'), black: tex('truck_black'), red: tex('truck_red'),
    tiller: tex('truck_tiller'), fork: tex('truck_fork'), wheel: tex('truck_wheel'), particle: tex('truck_body') };
  M('pallet_truck', { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements });
  M('pallet_truck_item', { parent: 'minecraft:block/block', textures, elements: shrunk(elements, [8, 14.75, 7.5], 16 / 46) });
  BS('pallet_truck', { variants: { '': { model: 'setpoint:block/pallet_truck' } } });
  item('pallet_truck', 'setpoint:block/pallet_truck_item');
}
recipe('pallet_truck', [' L ', 'IRI', 'I I'], { L: 'minecraft:lever', R: 'minecraft:redstone_block', I: 'minecraft:iron_ingot' }, 'setpoint:pallet_truck');

// =====================================================================================
// Forklift: an electric counterbalance forklift (modelled on a 3-tonne one, no brand name). Silver body, an orange
// rounded counterweight at the back, a black triplex mast, black forks on a carriage, a silver overhead guard with a
// slatted roof, big black drive wheels at the front and small steering wheels at the back.
// Drawn in "forklift pixels": the entity stands at (0, 0, 0) and the forks point north (-z). Each moving part is its
// own model (the block setpoint:forklift, one per part=), moved into a model's bounds by FORKLIFT_SHIFT;
// ForkliftRenderer moves each part back (keep its SHIFT table in step with this one).
// =====================================================================================
texture('forklift_silver', (p) => {
  p.rect(0, 0, 16, 16, '#b9bdc2');
  p.rect(0, 0, 16, 1, '#d3d6da'); p.rect(0, 15, 16, 1, '#90949a');
  p.set(4, 6, '#c6c9cd'); p.set(11, 10, '#a9adb2');
});
texture('forklift_panel', (p) => { // the hood side: silver with the black inlay
  p.rect(0, 0, 16, 16, '#b9bdc2'); p.rect(0, 0, 16, 1, '#d3d6da'); p.rect(0, 15, 16, 1, '#90949a');
  p.rect(2, 3, 12, 9, '#26282c'); p.rect(2, 3, 12, 1, '#3a3d42');
  p.rect(3, 5, 5, 1, '#e8e8e8'); p.rect(3, 7, 7, 1, '#f08a1c'); // a white and an orange stripe, no name
});
texture('forklift_black', (p) => { p.rect(0, 0, 16, 16, '#26282c'); for (const [x, y] of [[3, 4], [11, 9], [6, 13]]) p.set(x, y, '#34373c'); });
texture('forklift_mast', (p) => { // a steel channel: dark, with a lighter rolled edge
  p.rect(0, 0, 16, 16, '#2d3035'); p.rect(0, 0, 1, 16, '#454950'); p.rect(15, 0, 1, 16, '#1d1f23');
  for (let y = 2; y < 16; y += 5) p.set(8, y, '#383b41');
});
texture('forklift_fork', (p) => { p.rect(0, 0, 16, 16, '#35383d'); p.rect(0, 0, 16, 1, '#4a4e55'); p.rect(0, 15, 16, 1, '#24262a'); });
texture('forklift_tyre', (p) => { p.rect(0, 0, 16, 16, '#1c1d20'); for (let x = 0; x < 16; x += 3) p.rect(x, 0, 1, 16, '#2a2c30'); });
texture('forklift_wheel', (p) => { // the side of a wheel: tyre, black rim, a hub with its nuts
  p.rect(0, 0, 16, 16, '#1c1d20');
  p.rect(3, 3, 10, 10, '#2c2e33'); p.rect(5, 5, 6, 6, '#3d4046'); p.rect(7, 7, 2, 2, '#62666d');
  for (const [x, y] of [[6, 6], [9, 6], [6, 9], [9, 9]]) p.set(x, y, '#8a8e95');
});
texture('forklift_roof', (p) => { // slats in a silver frame
  p.rect(0, 0, 16, 16, '#b9bdc2');
  for (let x = 1; x < 15; x += 3) p.rect(x, 1, 2, 14, '#2b2d31');
});
texture('forklift_seat', (p) => { p.rect(0, 0, 16, 16, '#232427'); p.rect(0, 7, 16, 1, '#36383c'); p.rect(7, 0, 1, 16, '#36383c'); });
texture('forklift_light', (p) => { p.rect(0, 0, 16, 16, '#3a3d42'); p.rect(2, 2, 12, 12, '#fff3c4'); p.rect(4, 4, 8, 8, '#ffffff'); });
texture('forklift_orange', (p) => { p.rect(0, 0, 16, 16, '#f08a1c'); p.rect(0, 0, 16, 1, '#f6a647'); p.rect(0, 15, 16, 1, '#c86e12'); p.rect(3, 6, 3, 2, '#d23a1c'); p.rect(10, 6, 3, 2, '#d23a1c'); });
const FORKLIFT_SHIFT = { body: [0, -2, 0], outer: [0, -14, 0], middle: [0, -14, 0], inner: [0, -14, 0], carriage: [0, 0, 26] };
{
  const P = { body: [], outer: [], middle: [], inner: [], carriage: [] };
  // a box in forklift pixels; t is a texture name, or { face: name, all: name }; skip lists faces left out
  const add = (part, from, to, t, extra = {}) => P[part].push({ from, to, t, ...extra });
  const mirror = (part, [x1, y1, z1], [x2, y2, z2], t, extra) => { add(part, [x1, y1, z1], [x2, y2, z2], t, extra); add(part, [-x2, y1, z1], [-x1, y2, z2], t, extra); };

  // ---- body: hull, counterweight, hood with the seat, the dash, steering wheel, overhead guard, front wheels
  add('body', [-9, 2, -18], [9, 10, 14], { all: 'silver', down: 'black' });                  // the hull
  add('body', [-8, 10, -16], [8, 10.5, -4], 'black');                                         // the floor plate where your feet go
  add('body', [-9, 10, -18], [9, 17, -15], { all: 'silver', up: 'black' });                  // the dash
  add('body', [-9, 10, -4], [9, 15, 14], { all: 'silver', east: 'panel', west: 'panel', up: 'black' }); // the hood over the battery
  add('body', [-4, 15, -1], [4, 17, 7], 'seat');                                              // the seat
  add('body', [-4, 17, 6], [4, 26, 8], 'seat', { rot: { origin: [0, 17, 7], x: 10 } });      // its back, leaning back a little
  add('body', [-9, 2, 14], [9, 17, 20], 'orange');                                            // the counterweight
  add('body', [-8, 3, 20], [8, 16, 22], 'orange');                                            // rounded off at the back
  add('body', [-8.5, 17, 14.5], [8.5, 18, 19.5], 'orange');                                   // and on top
  add('body', [-1, 13, -13], [1, 23, -11], 'black', { rot: { origin: [0, 13, -12], x: 25 } }); // the steering column, leaning back to you
  add('body', [-3.5, 22, -16], [3.5, 23, -10], 'black', { rot: { origin: [0, 13, -12], x: 25 } }); // the steering wheel
  add('body', [1, 21, -14], [2, 25, -13], 'black', { rot: { origin: [0, 13, -12], x: 25 } });  // its knob
  mirror('body', [7.5, 14, -17], [9, 34, -15.5], 'silver', { rot: { origin: [8, 14, -16], x: 17 } }); // front posts, leaning back
  mirror('body', [7.5, 15, 12.5], [9, 34, 14], 'silver');                                     // rear posts
  add('body', [-9, 32.5, -11], [9, 34, 14], { all: 'silver', up: 'roof', down: 'roof' });    // the roof
  mirror('body', [6, 30.5, -11], [8.5, 32.5, -9], { all: 'silver', north: 'light' });        // work lights
  mirror('body', [6, 0, -19.5], [11, 11, -8.5], { all: 'tyre', east: 'wheel', west: 'wheel' }, { whole: true }); // drive wheels
  mirror('body', [6.5, 1, -20.5], [10.5, 10, -7.5], 'tyre');                                  // round them off a little
  mirror('body', [6.5, -1, -18.5], [10.5, 0, -9.5], 'tyre');                                  // (and down to the floor)

  // ---- the mast: three stages of steel channel, each one inside the last; the carriage runs up the inner one
  mirror('outer', [6, 0, -22], [9, 44, -19], 'mast');
  add('outer', [-9, 41, -22], [9, 44, -20], 'mast');                                          // top cross bar
  add('outer', [-9, 1, -21], [9, 4, -19], 'mast');                                            // bottom cross bar
  add('outer', [-1, 2, -20], [1, 36, -18.5], 'silver');                                       // the lift cylinder
  mirror('middle', [4, 0, -22.5], [6, 44, -20], 'mast');
  add('middle', [-6, 41, -22.5], [6, 43, -21], 'mast');
  mirror('inner', [2, 0, -23], [4, 44, -21], 'mast');
  add('inner', [-4, 40, -23], [4, 43.5, -21.5], 'mast');

  // ---- the carriage with its load backrest and two forks
  add('carriage', [-10, 1, -24.5], [10, 9, -22.5], 'mast');                                   // the carriage plates
  mirror('carriage', [9, 9, -24], [10, 22, -23], 'mast');                                     // the backrest frame
  add('carriage', [-10, 21, -24], [10, 22, -23], 'mast');
  for (const x of [-6, -2, 2, 6]) add('carriage', [x - 0.5, 9, -23.8], [x + 0.5, 21, -23.2], 'mast');
  mirror('carriage', [4, 0, -25.5], [7, 11, -24], 'fork');                                     // the fork shanks
  mirror('carriage', [4, 0, -47], [7, 1.5, -25.5], 'fork');                                    // the tines
  mirror('carriage', [4.5, 0, -50], [6.5, 1, -47], 'fork');                                    // their thin tips

  const BUILD = (list, shift) => list.flatMap(({ from, to, t, rot, skip = [], whole }) => {
    const m = (p) => p.map((v, k) => +(v + 8 * (k !== 1) + shift[k]).toFixed(4));
    const pick = (f) => (skip.includes(f) ? null : '#' + (typeof t === 'string' ? t : (t[f] ?? t.all)));
    // whole: one element, each face showing its whole texture (a wheel's side with the hub in the middle)
    const els = whole ? [box(m(from), m(to), Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, { uv: [0, 0, 16, 16], texture: pick(f) }])))]
      : tiled(m(from), m(to), pick);
    return rot ? els.map((e) => ({ ...e, rotation: { origin: m(rot.origin), x: rot.x ?? 0, y: rot.y ?? 0, z: rot.z ?? 0 } })) : els;
  });
  const textures = { silver: tex('forklift_silver'), panel: tex('forklift_panel'), black: tex('forklift_black'), mast: tex('forklift_mast'),
    fork: tex('forklift_fork'), tyre: tex('forklift_tyre'), wheel: tex('forklift_wheel'), roof: tex('forklift_roof'), seat: tex('forklift_seat'),
    light: tex('forklift_light'), orange: tex('forklift_orange'), particle: tex('forklift_silver') };
  const names = { body: 'forklift_body', outer: 'forklift_mast_outer', middle: 'forklift_mast_middle', inner: 'forklift_mast_inner', carriage: 'forklift_carriage' };
  for (const [part, name] of Object.entries(names)) {
    M(name, { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: BUILD(P[part], FORKLIFT_SHIFT[part]) });
  }
  // a rear steering wheel, around the model's middle so the renderer can turn it
  const rearWheel = [
    box([6, 0, 4.5], [10, 8, 11.5], { ...allFaces([0, 0, 7, 8], '#tyre'), east: { uv: [0, 0, 16, 16], texture: '#wheel' }, west: { uv: [0, 0, 16, 16], texture: '#wheel' } }),
    box([6.5, 1, 3.5], [9.5, 7, 12.5], allFaces([0, 0, 3, 6], '#tyre')),
  ];
  M('forklift_rear_wheel', { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: rearWheel });
  const partModels = ['forklift_body', 'forklift_mast_outer', 'forklift_mast_middle', 'forklift_mast_inner', 'forklift_carriage', 'forklift_rear_wheel'];
  while (partModels.length < 16) partModels.push('forklift_body'); // the unused part= states
  BS('forklift', { variants: Object.fromEntries(partModels.map((m, i) => [`part=${i}`, { model: 'setpoint:block/' + m }])) });
  // the item: the whole forklift, forks down, shrunk to one block (the wheels at the back where they belong)
  const whole = Object.values(P).flat();
  const rear = [-6, 6].map((x) => ({ from: [x - 2, 0, 8.5], to: [x + 2, 8, 15.5], t: { all: 'tyre', east: 'wheel', west: 'wheel' }, whole: true }));
  M('forklift_item', { parent: 'minecraft:block/block', textures, elements: shrunk(BUILD([...whole, ...rear], [-8, 0, -8]), [0, 22, -14], 16 / 74) });
  item('forklift', 'setpoint:block/forklift_item');
}
recipe('forklift', ['P I', 'PRB', 'I I'], { P: 'minecraft:piston', I: 'minecraft:iron_ingot', R: 'minecraft:redstone_block', B: 'minecraft:iron_block' }, 'setpoint:forklift');

// the rest of a drum or IBC: nothing to draw (the base block's model reaches over it), only break particles
M('tank_part', { textures: { particle: tex('ibc_tote') } });
BS('tank_part', { variants: { 'kind=0': { model: 'setpoint:block/tank_part' }, 'kind=1': { model: 'setpoint:block/tank_part' } } });

// =====================================================================================
// Floating roof tank: the big storage tank, eight blocks across and six high, in Hookonium steel
// =====================================================================================
// Its renderer puts it together from part models (the never-placed block setpoint:floating_roof_tank_model, one per
// part=). Each is built in "tank pixels": from the middle of the tank, up from its floor, looking out along +z. Round
// pieces are one 15° segment that the renderer turns all the way round. TANK_SHIFT moves each into a model's bounds;
// FloatingRoofTankRenderer.SHIFT must match, and so must the sizes in FloatingRoofTankBlock.
const HKS = { shell: '#d9dce4', shellHi: '#e8eaf0', shellLo: '#c3c7d1', seam: '#a9aeb9' };
texture('frt_shell', (p) => { // the lower half of a two-block plate: the weld seam to the plate below, and the seam to the next one
  p.rect(0, 0, 16, 16, HKS.shell);
  for (const [x, y] of [[4, 3], [11, 6], [7, 11], [13, 13], [2, 9]]) p.set(x, y, HKS.shellLo);
  for (const [x, y] of [[9, 2], [5, 7], [12, 10]]) p.set(x, y, HKS.shellHi);
  p.rect(0, 0, 1, 16, HKS.seam); p.rect(1, 0, 1, 16, HKS.shellHi);
  p.rect(0, 15, 16, 1, HKS.seam); p.rect(0, 14, 16, 1, HKS.shellHi);
});
texture('frt_shell_upper', (p) => { // the upper half: only the seam to the next plate
  p.rect(0, 0, 16, 16, HKS.shell);
  for (const [x, y] of [[6, 4], [12, 8], [3, 12], [9, 14]]) p.set(x, y, HKS.shellLo);
  for (const [x, y] of [[10, 3], [4, 9]]) p.set(x, y, HKS.shellHi);
  p.rect(0, 0, 1, 16, HKS.seam); p.rect(1, 0, 1, 16, HKS.shellHi);
});
texture('frt_shell_in', (p) => { p.rect(0, 0, 16, 16, '#8b909b'); p.rect(0, 0, 1, 16, '#767b86'); p.rect(0, 15, 16, 1, '#767b86'); });
texture('frt_trim', (p) => { p.rect(0, 0, 16, 16, HK.m); p.rect(0, 0, 16, 1, HK.l); p.rect(0, 15, 16, 1, HK.k); });
texture('frt_trim_top', (p) => p.rect(0, 0, 16, 16, HK.m)); // flat: the segments overlap a little, so their tops must look alike
texture('frt_rail', (p) => p.rect(0, 0, 16, 16, HK.l));
texture('frt_pontoon', (p) => p.rect(0, 0, 16, 16, '#8f96a3'));
texture('frt_pontoon_side', (p) => { p.rect(0, 0, 16, 16, '#6f7683'); p.rect(0, 0, 16, 1, '#a3abbb'); });
texture('frt_seal', (p) => p.rect(0, 0, 16, 16, '#1d1e22'));
texture('frt_deck', (p) => { // the roof deck: plates welded in an 8-pixel grid (each 8 x 8 quarter is the same)
  p.rect(0, 0, 16, 16, '#9aa1ad');
  for (const o of [0, 8]) { p.rect(o + 7, 0, 1, 16, '#848b97'); p.rect(0, o + 7, 16, 1, '#848b97'); }
  for (const [x, y] of [[2, 3], [5, 1], [3, 5]]) for (const o of [[0, 0], [8, 0], [0, 8], [8, 8]]) p.set(x + o[0], y + o[1], '#a6adb9');
});
texture('frt_manhole', (p) => { // a bolted cover in the middle 8 x 8
  p.rect(0, 0, 16, 16, '#5d6370');
  p.rect(5, 4, 6, 8, '#737a87'); p.rect(4, 5, 8, 6, '#737a87');
  for (const [x, y] of [[5, 4], [10, 4], [4, 7], [11, 8], [5, 11], [10, 11]]) p.set(x, y, '#b9bfca');
  p.rect(7, 7, 2, 2, '#3f444e');
});
texture('frt_steel', (p) => { p.rect(0, 0, 16, 16, '#b6bbc4'); p.rect(0, 15, 16, 1, '#8e939c'); p.rect(0, 0, 16, 1, '#cdd1d8'); });
texture('frt_grating', (p) => { // open floor grating
  p.rect(0, 0, 16, 16, '#3a3e46');
  for (let i = 0; i < 16; i += 2) { p.rect(i, 0, 1, 16, '#8e939c'); p.rect(0, i, 16, 1, '#6f747d'); }
});
texture('frt_flange', (p) => { // the inlet nozzle: a steel flange with its bolts round the bore
  p.rect(0, 0, 16, 16, '#9aa0ab');
  p.rect(5, 5, 6, 6, '#3f444e'); p.rect(6, 6, 4, 4, C.outline);
  for (const [x, y] of [[7, 1], [13, 7], [8, 14], [1, 8], [3, 3], [12, 3], [3, 12], [12, 12]]) p.set(x, y, '#d4d8de');
});
const TANK_SHIFT = { shell: [0, 0, -60], top: [0, -76, -56], rail: [0, -96, -56], pontoon: [0, 2, -40], deck: [0, 0, 0], manhole: [0, 0, 0],
  rolling_ladder: [0, 0, 16], platform: [0, -80, -60], ladder: [0, 0, -66], cage: [0, 0, -66], nozzle: [0, 0, -52] };
// half the width of a 15° segment at this radius
const segment = (r) => +(r * Math.tan(Math.PI / 24)).toFixed(4);
{
  const P = Object.fromEntries(Object.keys(TANK_SHIFT).map((k) => [k, []]));
  // a box in tank pixels; faces: { face: texture } cut 1:1 at block lines, or with stretch, each face its whole uv
  const add = (part, from, to, faces, stretch = false) => P[part].push({ from, to, faces, stretch });
  const sides = (t, skip = []) => Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].filter((f) => !skip.includes(f)).map((f) => [f, t]));
  const mirror = (part, [x1, y1, z1], [x2, y2, z2], faces) => { add(part, [x1, y1, z1], [x2, y2, z2], faces); add(part, [-x2, y1, z1], [-x1, y2, z2], faces); };

  // ---- the shell: one plate two blocks high; three courses all round
  for (const y of [0, 16]) add('shell', [-segment(64), y, 63], [segment(64), y + 16, 64],
    { south: { t: y ? 'shell_upper' : 'shell', uv: [0, 0, 16, 16] }, north: { t: 'shell_in', uv: [0, 0, 16, 16] } }, true);
  // ---- the top: the curb angle on the rim, the wind girder below it; the handrail on the rim
  add('top', [-segment(66), 94, 62], [segment(66), 97, 66], { ...sides('trim', ['up', 'east', 'west']), up: 'trim_top' });
  add('top', [-segment(68), 76, 64], [segment(68), 78, 68], { south: 'trim', down: 'trim', up: 'trim_top' });
  add('rail', [-0.5, 97, 64.5], [0.5, 108, 65.5], sides('rail', ['up', 'down']));
  for (const y of [102, 107]) add('rail', [-segment(65.5), y, 64.5], [segment(65.5), y + 1, 65.5], sides('rail', ['east', 'west']));
  // ---- the roof (y from the top of its deck): a ring of pontoons with the rim seal against the shell
  add('pontoon', [-segment(60), -2, 44], [segment(60), 4, 60], { up: 'pontoon', north: 'pontoon_side', south: 'pontoon_side' });
  add('pontoon', [-segment(63), 0, 60], [segment(63), 5, 63], { up: 'seal', north: 'seal' });
  add('deck', [-4, -1, -4], [4, 0, 4], { up: { t: 'deck', uv: [0, 0, 8, 8] } }, true);
  add('manhole', [-4, 0, -4], [4, 1.5, 4], { up: { t: 'manhole', uv: [4, 4, 12, 12] }, ...Object.fromEntries(['north', 'south', 'east', 'west'].map((f) => [f, { t: 'steel', uv: [0, 0, 8, 1.5] }])) }, true);
  // ---- the rolling ladder: one length of it, hanging from its hinge at (0, 0, 0) towards the middle (-z)
  mirror('rolling_ladder', [5, -1, -28], [6, 1, 0], sides('steel'));
  for (let z = -2; z > -28; z -= 4) add('rolling_ladder', [-5, -0.5, z - 0.5], [5, 0.5, z + 0.5], sides('steel', ['east', 'west']));
  // ---- the gauger's platform over the rim, where the caged ladder comes up and the rolling ladder hangs
  add('platform', [-7, 95, 56], [7, 96, 72], { up: 'grating', down: 'grating', north: 'steel', south: 'steel', east: 'steel', west: 'steel' });
  for (const z of [56.5, 64, 71.5]) mirror('platform', [6.5, 96, z - 0.5], [7.5, 108, z + 0.5], sides('rail', ['up', 'down']));
  for (const y of [102, 107]) mirror('platform', [6.5, y, 56], [7.5, y + 1, 72], sides('rail'));
  // ---- the caged ladder in the corner, two blocks at a time; the cage from two blocks up. Its rungs stand where the
  // ladder blocks' collision is (three pixels into the corner block), so you climb it by walking into it.
  mirror('ladder', [4, 0, 71], [5, 32, 73], sides('steel', ['up', 'down']));
  for (let y = 2; y < 32; y += 4) add('ladder', [-4, y - 0.5, 71.5], [4, y + 0.5, 72.5], sides('steel', ['east', 'west']));
  for (const y of [4, 14, 24]) {
    add('cage', [-7, y, 80], [7, y + 1, 81], sides('steel'));
    mirror('cage', [6, y, 73], [7, y + 1, 80], sides('steel'));
  }
  for (const x of [-4, 0, 4]) add('cage', [x - 0.5, 0, 80], [x + 0.5, 32, 81], sides('steel', ['up', 'down']));
  mirror('cage', [6.5, 0, 76], [7.5, 32, 77], sides('steel', ['up', 'down']));
  // ---- the inlet nozzle at the front, where you placed the tank
  add('nozzle', [-3, 5, 60], [3, 11, 63], sides('steel', ['north', 'south']));
  add('nozzle', [-5, 3, 63], [5, 13, 64], { south: { t: 'flange', uv: [3, 3, 13, 13] }, north: { t: 'steel', uv: [0, 0, 10, 10] },
    east: { t: 'steel', uv: [0, 0, 1, 10] }, west: { t: 'steel', uv: [0, 0, 1, 10] }, up: { t: 'steel', uv: [0, 0, 10, 1] }, down: { t: 'steel', uv: [0, 0, 10, 1] } }, true);

  const BUILD = (list, shift) => list.flatMap(({ from, to, faces, stretch }) => {
    const m = (p) => p.map((v, k) => +(v + 8 * (k !== 1) + shift[k]).toFixed(4));
    if (stretch) return [box(m(from), m(to), Object.fromEntries(Object.entries(faces).map(([f, { t, uv }]) => [f, { uv, texture: '#' + t }])))];
    return tiled(m(from), m(to), (f) => (faces[f] ? '#' + faces[f] : null));
  });
  const names = ['shell', 'shell_upper', 'shell_in', 'trim', 'trim_top', 'rail', 'pontoon', 'pontoon_side', 'seal', 'deck', 'manhole', 'steel', 'grating', 'flange'];
  const textures = { ...Object.fromEntries(names.map((n) => [n, tex('frt_' + n)])), particle: tex('frt_shell') };
  const parts = Object.keys(TANK_SHIFT);
  for (const part of parts) M('floating_roof_tank_' + part, { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: BUILD(P[part], TANK_SHIFT[part]) });
  BS('floating_roof_tank_model', { variants: Object.fromEntries(Array.from({ length: 16 }, (_, i) => [`part=${i}`, { model: 'setpoint:block/floating_roof_tank_' + (parts[i] ?? 'shell') }])) });
  // the placed blocks draw nothing themselves; only their breaking crumbs need a texture
  M('floating_roof_tank', { textures: { particle: tex('frt_shell') } });
  BS('floating_roof_tank', { variants: Object.fromEntries(['north', 'east', 'south', 'west'].map((f) => [`facing=${f}`, { model: 'setpoint:block/floating_roof_tank' }])) });
  BS('roof_tank_part', { variants: Object.fromEntries([0, 1].flatMap((k) => ['false', 'true'].map((l) => [`kind=${k},ladder=${l}`, { model: 'setpoint:block/floating_roof_tank' }]))) });

  // the item: the whole tank shrunk to one block. Round pieces are turned at most 45° and laid along the nearest axis.
  const turned = (theta, [x1, y1, z1], [x2, y2, z2], faces) => {
    const k = ((Math.round(theta / 90) % 4) + 4) % 4, phi = theta - 90 * Math.round(theta / 90);
    const quarter = [(x, z) => [x, z], (x, z) => [z, -x], (x, z) => [-x, -z], (x, z) => [-z, x]][k];
    const a = quarter(x1, z1), b = quarter(x2, z2);
    const rename = [{}, { south: 'east', east: 'north', north: 'west', west: 'south' }, { south: 'north', north: 'south', east: 'west', west: 'east' },
      { south: 'west', west: 'north', north: 'east', east: 'south' }][k];
    return { from: [Math.min(a[0], b[0]), y1, Math.min(a[1], b[1])], to: [Math.max(a[0], b[0]), y2, Math.max(a[1], b[1])],
      faces: Object.fromEntries(Object.entries(faces).map(([f, v]) => [rename[f] ?? f, v])),
      ...(Math.abs(phi) > 1e-6 ? { rotation: { origin: [0, y1, 0], x: 0, y: +phi.toFixed(3), z: 0 } } : {}) };
  };
  const whole = [];
  for (let s = 0; s < 24; s++) {
    const theta = s * 15;
    whole.push(turned(theta, [-segment(64), 0, 63], [segment(64), 96, 64], { south: { uv: [0, 0, 16, 16], texture: '#shell' } }));
    whole.push(turned(theta, [-segment(66), 94, 62], [segment(66), 97, 66], { up: { uv: [0, 0, 16, 4], texture: '#trim_top' }, south: { uv: [0, 0, 16, 3], texture: '#trim' } }));
    whole.push(turned(theta, [-segment(60), 66, 44], [segment(60), 72, 60], { up: { uv: [0, 0, 16, 16], texture: '#pontoon' } }));
  }
  for (let z = -48; z < 48; z += 8) { // the deck as strips across, each as wide as the circle there
    const w = Math.sqrt(48 * 48 - (z + 4) ** 2) + 2;
    whole.push({ from: [-w, 67, z], to: [w, 68, z + 8], faces: { up: { uv: [0, 0, 16, 8], texture: '#deck' } } });
  }
  M('floating_roof_tank_item', { parent: 'minecraft:block/block', textures,
    elements: shrunk(whole, [0, 54, 0], 16 / 136) });
  item('floating_roof_tank', 'setpoint:block/floating_roof_tank_item');
}

// =====================================================================================
// Gases: hydrogen, oxygen, nitrogen and steam, in gas pipes and cylinders
// =====================================================================================
// Gas colours are the shoulder colours of real gas cylinders (EN 1089-3): hydrogen red, oxygen white, nitrogen black.
const GAS = { hydrogen: '#c7322f', oxygen: '#f2f4f7', nitrogen: '#2c2e33', yellow: '#e2b13c', yellowDark: '#a87f22' };
texture('liquid', (p) => { // a process liquid as recipe viewers show it: a rippled surface, tinted with its colour
  p.rect(0, 0, 16, 16, '#cfcfcf');
  for (let y = 1; y < 16; y += 4) for (let x = 0; x < 16; x += 6) p.rect((x + y) % 16, y, 3, 1, '#ededed');
});
texture('gas', (p) => { // a gas, as recipe viewers show it: a soft light cloud, tinted with the gas colour
  p.rect(0, 0, 16, 16, '#d8d8d8');
  for (const [x, y, w] of [[2, 3, 5], [9, 2, 4], [4, 8, 6], [11, 9, 3], [1, 12, 4], [8, 13, 5]]) { p.rect(x, y, w, 2, '#f4f4f4'); p.rect(x + 1, y + 2, w - 2, 1, '#e6e6e6'); }
});
texture('gas_pipe', (p) => { // galvanised steel with a yellow band near each end: the colour code of a gas line
  const shade = ['#6f7682', '#8b929e', '#a7aeb9', '#c3c9d2', '#a7aeb9', '#a7aeb9', '#8b929e', '#8b929e', '#8b929e', '#8b929e', '#6f7682', '#6f7682', '#6f7682', '#575d68', '#575d68', '#575d68'];
  for (let y = 0; y < 16; y++) p.rect(0, y, 16, 1, shade[y]);
  p.rect(0, 0, 1, 16, '#4a4f58'); p.rect(15, 0, 1, 16, '#4a4f58');
  p.rect(2, 0, 2, 16, GAS.yellow); p.rect(2, 10, 2, 6, GAS.yellowDark);
  p.rect(12, 0, 2, 16, GAS.yellow); p.rect(12, 10, 2, 6, GAS.yellowDark);
});
// a gas connection on a machine's side: a flange with a band in the colour of its gas
const gasPort = (band) => (p) => {
  plate(p, C.steel2);
  p.rect(3, 3, 10, 10, band);
  p.rect(4, 4, 8, 8, C.steel3); p.rect(5, 5, 6, 6, C.steel1); p.rect(6, 6, 4, 4, C.outline); // flange and bore
  for (const [x, y] of [[4, 4], [11, 4], [4, 11], [11, 11]]) p.set(x, y, C.steel4);
  rivets(p, [[1, 1], [13, 1], [1, 13], [13, 13]]);
};
texture('electrolyser_hydrogen', gasPort(GAS.hydrogen));
texture('electrolyser_oxygen', gasPort(GAS.oxygen));
texture('nitrogen_generator_nitrogen', gasPort(GAS.nitrogen));
texture('electrolyser_water', (p) => { // the back: a copper water inlet with a green band
  plate(p, C.steel2);
  p.rect(3, 3, 10, 10, '#3f8f4f');
  p.rect(4, 4, 8, 8, C.cu2); p.rect(5, 5, 6, 6, C.cu1); p.rect(6, 6, 4, 4, C.outline);
  rivets(p, [[1, 1], [13, 1], [1, 13], [13, 13]]);
});
const electrolyserFront = (led, ledDark) => (p) => { // a window on the cell stack: plates pressed between end plates
  plate(p, C.steel2);
  p.rect(2, 2, 12, 9, C.steel0);
  for (let x = 3; x < 13; x += 2) { p.rect(x, 3, 1, 7, '#d3d9e2'); p.rect(x + 1, 3, 1, 7, '#6b7280'); }
  p.rect(2, 2, 12, 1, C.steel4); p.rect(2, 10, 12, 1, C.steel1);                 // tie rods
  p.rect(3, 12, 2, 2, led); p.set(4, 13, ledDark);                                // status light
  p.rect(7, 12, 3, 2, GAS.hydrogen); p.rect(11, 12, 2, 2, GAS.oxygen);           // H2 / O2 nameplates
};
texture('electrolyser_front', electrolyserFront(C.ledOff, C.outline));
texture('electrolyser_front_on', electrolyserFront(C.cyan, C.cyanDark));
texture('electrolyser_top', (p) => {
  plate(p, C.steel2);
  for (let y = 3; y < 13; y += 2) p.rect(3, y, 10, 1, C.steel0);                  // vent slots
  rivets(p, [[1, 1], [13, 1], [1, 13], [13, 13]]);
});
texture('electrolyser_bottom', (p) => { plate(p, C.steel1); p.rect(6, 6, 4, 4, C.cu2); p.rect(7, 7, 2, 2, C.outline); });
const nitrogenFront = (led, ledDark) => (p) => { // two adsorber columns side by side, a gauge between them
  plate(p, C.steel1);
  for (const x of [2, 9]) {
    p.rect(x, 1, 5, 14, '#b9c4d3'); p.rect(x + 1, 1, 1, 14, '#e3e9f1'); p.rect(x + 4, 1, 1, 14, '#7d8796');
    p.rect(x, 1, 5, 1, '#f1f4f8'); p.rect(x, 14, 5, 1, '#5d6673');
  }
  p.rect(7, 4, 2, 2, '#e8ecf2'); p.set(7, 4, C.outline);                          // pressure gauge
  p.rect(7, 11, 2, 2, led); p.set(8, 12, ledDark);                                // status light
};
texture('nitrogen_generator_front', nitrogenFront(C.ledOff, C.outline));
texture('nitrogen_generator_front_on', nitrogenFront(C.cyan, C.cyanDark));
texture('nitrogen_generator_vent', (p) => { // louvres where the oxygen-rich air is blown off
  plate(p, C.steel2);
  for (let y = 3; y < 14; y += 3) { p.rect(3, y, 10, 1, C.steel0); p.rect(3, y + 1, 10, 1, C.steel4); }
});
texture('nitrogen_generator_side', (p) => { plate(p, C.steel2); rivets(p, [[2, 2], [12, 2], [2, 12], [12, 12]]); });
texture('nitrogen_generator_top', (p) => { plate(p, C.steel2); p.rect(3, 3, 4, 4, C.steel0); p.rect(9, 3, 4, 4, C.steel0); rivets(p, [[2, 12], [12, 12]]); });
for (const [name, left, right, back, bottom] of [
  ['electrolyser', 'electrolyser_hydrogen', 'electrolyser_oxygen', 'electrolyser_water', 'electrolyser_bottom'],
  ['nitrogen_generator', 'nitrogen_generator_nitrogen', 'nitrogen_generator_vent', 'nitrogen_generator_side', 'nitrogen_generator_side'],
]) {
  for (const on of [false, true]) {
    // front north; your left as you face it is east, your right west
    M(name + (on ? '_on' : ''), { parent: 'minecraft:block/cube', textures: {
      north: tex(name + '_front' + (on ? '_on' : '')), east: tex(left), west: tex(right), south: tex(back),
      up: tex(name === 'electrolyser' ? 'electrolyser_top' : 'nitrogen_generator_top'), down: tex(bottom), particle: tex(left) } });
  }
  BS(name, { variants: Object.fromEntries(Object.entries(yaw).flatMap(([f, y]) => [false, true].map((run) =>
    [`facing=${f},running=${run}`, { model: 'setpoint:block/' + name + (run ? '_on' : ''), ...(y ? { y } : {}) }]))) });
  item(name, 'setpoint:block/' + name + '_on');
}
// the gas pipe: the liquid pipe's shapes in steel
M('gas_pipe_core', { parent: 'minecraft:block/block', textures: { pipe: tex('gas_pipe'), particle: tex('gas_pipe') },
  elements: [box([5, 5, 5], [11, 11, 11], Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, face([5, 5, 11, 11])])))] });
M('gas_pipe_arm', { textures: { pipe: tex('gas_pipe'), particle: tex('gas_pipe') },
  elements: [box([5, 5, 0], [11, 11, 5], { north: { uv: [5, 5, 11, 11], texture: '#pipe', cullface: 'north' },
    east: face([0, 5, 5, 11]), west: face([11, 5, 16, 11]), up: face([5, 0, 11, 5]), down: face([5, 11, 11, 16]) })] });
M('gas_pipe_item', { parent: 'minecraft:block/block', textures: { pipe: tex('gas_pipe'), particle: tex('gas_pipe') },
  elements: [box([5, 5, 0], [11, 11, 16], { north: face([5, 5, 11, 11]), south: face([5, 5, 11, 11]),
    east: face([0, 5, 16, 11]), west: face([0, 5, 16, 11]), up: face([5, 0, 11, 16]), down: face([5, 0, 11, 16]) })] });
BS('gas_pipe', { multipart: [{ apply: { model: 'setpoint:block/gas_pipe_core' } },
  ...Object.entries(armRotation).map(([dir, rot]) => ({ when: { [dir]: 'true' }, apply: { model: 'setpoint:block/gas_pipe_arm', ...rot } }))] });
item('gas_pipe', 'setpoint:block/gas_pipe_item');

// the gas cylinder: a round steel bottle, its shoulder in the colour of its gas (tintindex 0), a valve on top
texture('gas_cylinder_body', (p) => {
  p.rect(0, 0, 16, 16, '#7d8593');
  p.rect(1, 0, 1, 16, '#a1a9b6'); p.rect(13, 0, 2, 16, '#606774');           // light from the left
  p.rect(0, 5, 16, 4, '#e8ecf2'); p.rect(0, 5, 16, 1, '#ffffff');            // the label band
  p.rect(3, 6, 6, 1, '#9aa3b2'); p.rect(3, 8, 4, 1, '#9aa3b2');
});
texture('gas_cylinder_shoulder', (p) => { p.rect(0, 0, 16, 16, '#d6d6d6'); p.rect(1, 0, 1, 16, '#ffffff'); p.rect(13, 0, 2, 16, '#a8a8a8'); });
texture('gas_cylinder_valve', (p) => { p.rect(0, 0, 16, 16, '#b08d3c'); p.rect(0, 0, 16, 1, '#d8b763'); }); // brass
texture('gas_cylinder_bottom', (p) => { p.rect(0, 0, 16, 16, '#5d6470'); });
{
  // round as a pixel circle: bands of z, each as wide in x as the circle there
  const r5 = [[4, 5, 6, 10], [5, 6, 5, 11], [6, 10, 4, 12], [10, 11, 5, 11], [11, 12, 6, 10]]; // the body, eight pixels across
  const r4 = [[5, 6, 7, 9], [6, 7, 6, 10], [7, 9, 5, 11], [9, 10, 6, 10], [10, 11, 7, 9]];      // the shoulder, six
  const els = [];
  for (const [z1, z2, x1, x2] of r5) els.push(box([x1, 0, z1], [x2, 12, z2], { ...Object.fromEntries(['north', 'south', 'east', 'west'].map((f) => [f, { uv: [x1, 4, x2, 16], texture: '#body' }])),
    down: { uv: [x1, z1, x2, z2], texture: '#bottom', cullface: 'down' } }));
  for (const [z1, z2, x1, x2] of r4) els.push(box([x1, 12, z1], [x2, 14, z2], { ...Object.fromEntries(['north', 'south', 'east', 'west'].map((f) => [f, { uv: [x1, 0, x2, 2], texture: '#shoulder', tintindex: 0 }])),
    up: { uv: [x1, z1, x2, z2], texture: '#shoulder', tintindex: 0 } }));
  for (const [z1, z2, x1, x2] of r5) els.push(box([x1, 11.99, z1], [x2, 12, z2], { up: { uv: [x1, z1, x2, z2], texture: '#shoulder', tintindex: 0 } })); // the shoulder's foot
  els.push(box([7, 14, 7], [9, 15.5, 9], allFaces([0, 0, 2, 1.5], '#valve', ['down'])));         // the valve
  els.push(box([6, 15.5, 7.5], [10, 16, 8.5], allFaces([0, 0, 4, 0.5], '#valve')));               // its handwheel
  els.push(box([9, 14.2, 7.5], [10.5, 15, 8.5], allFaces([0, 0, 1.5, 1], '#valve', ['west'])));   // and its outlet
  const textures = { body: tex('gas_cylinder_body'), shoulder: tex('gas_cylinder_shoulder'), valve: tex('gas_cylinder_valve'), bottom: tex('gas_cylinder_bottom'), particle: tex('gas_cylinder_body') };
  M('gas_cylinder', { parent: 'minecraft:block/block', textures, elements: els });
  BS('gas_cylinder', { variants: { '': { model: 'setpoint:block/gas_cylinder' } } });
  json(join(ASSETS, 'items', 'gas_cylinder.json'), { model: { type: 'minecraft:model', model: 'setpoint:block/gas_cylinder',
    tints: [{ type: 'minecraft:constant', value: 0xFF8D95A5 | 0 }] } });
}
// compressor: a two-cylinder reciprocating compressor on its motor. Like the pump: discharge north (away from you as
// you place it), suction south; cylinder heads on top with their cooling fins
texture('compressor_side', (p) => { // the motor and the crankcase, the flywheel guard, a status light
  plate(p, '#4f6b8a');
  p.rect(1, 9, 14, 6, '#3d5470'); for (let x = 2; x < 15; x += 2) p.rect(x, 10, 1, 4, '#5f7d9e');  // motor fins
  p.rect(3, 2, 6, 6, C.steel1); p.rect(4, 3, 4, 4, C.steel0); p.set(5, 4, C.steel4);                 // the flywheel guard
});
const compressorFront = (led, ledDark) => (p) => {
  plate(p, '#4f6b8a');
  p.rect(3, 3, 10, 10, GAS.yellow); p.rect(4, 4, 8, 8, C.steel3); p.rect(5, 5, 6, 6, C.steel1); p.rect(6, 6, 4, 4, C.outline);
  for (const [x, y] of [[4, 4], [11, 4], [4, 11], [11, 11]]) p.set(x, y, C.steel4);
  p.rect(13, 1, 2, 2, led); p.set(14, 2, ledDark);
};
texture('compressor_discharge', compressorFront(C.ledOff, C.outline));
texture('compressor_discharge_on', compressorFront(C.cyan, C.cyanDark));
texture('compressor_suction', (p) => { // the bigger suction flange
  plate(p, '#4f6b8a');
  p.rect(2, 2, 12, 12, GAS.yellow); p.rect(3, 3, 10, 10, C.steel3); p.rect(4, 4, 8, 8, C.steel1); p.rect(5, 5, 6, 6, C.outline);
});
texture('compressor_top', (p) => { // two cylinder heads with fins
  plate(p, '#3d5470');
  for (const x of [1, 9]) { p.rect(x, 2, 6, 12, C.steel2); for (let y = 3; y < 14; y += 2) p.rect(x, y, 6, 1, C.steel0); p.rect(x + 2, 7, 2, 2, C.steel4); }
});
texture('compressor_bottom', (p) => { plate(p, C.steel0); });
for (const on of [false, true]) {
  M('compressor' + (on ? '_on' : ''), { parent: 'minecraft:block/cube', textures: {
    north: tex('compressor_discharge' + (on ? '_on' : '')), south: tex('compressor_suction'), east: tex('compressor_side'), west: tex('compressor_side'),
    up: tex('compressor_top'), down: tex('compressor_bottom'), particle: tex('compressor_side') } });
}
BS('compressor', { variants: Object.fromEntries(Object.entries(yaw).flatMap(([f, y]) => [false, true].map((run) =>
  [`facing=${f},running=${run}`, { model: 'setpoint:block/compressor' + (run ? '_on' : ''), ...(y ? { y } : {}) }]))) });
item('compressor', 'setpoint:block/compressor_on');
recipe('compressor', ['IGI', 'PRP', 'III'], { I: 'minecraft:iron_ingot', G: 'setpoint:gas_pipe', P: 'minecraft:piston', R: 'minecraft:redstone_block' }, 'setpoint:compressor');
// fuel cell, ammonia reactor and fertilizer mixer: gas machines you face, with their gas lines left and right
GAS.ammonia = '#e8c547';
texture('fuel_cell_hydrogen', gasPort(GAS.hydrogen));
texture('fuel_cell_oxygen', gasPort(GAS.oxygen));
const fuelCellFront = (led, ledDark) => (p) => { // the cell stack behind a window, a bolt for the power it makes
  plate(p, '#3e6b5a');
  p.rect(2, 2, 12, 7, C.steel0);
  for (let x = 3; x < 13; x += 2) { p.rect(x, 3, 1, 5, '#cfe3da'); p.rect(x + 1, 3, 1, 5, '#55796b'); }
  const bolt = [[8, 10], [7, 11], [7, 12], [8, 12], [9, 12], [8, 13], [7, 14]];
  for (const [x, y] of bolt) p.set(x, y, C.amber);
  p.rect(12, 12, 2, 2, led); p.set(13, 13, ledDark);
};
texture('fuel_cell_front', fuelCellFront(C.ledOff, C.outline));
texture('fuel_cell_front_on', fuelCellFront(C.cyan, C.cyanDark));
texture('fuel_cell_side', (p) => { plate(p, '#3e6b5a'); for (let y = 3; y < 14; y += 3) p.rect(3, y, 10, 1, '#2c4c40'); });
texture('fuel_cell_back', (p) => { // the water outlet
  plate(p, '#3e6b5a'); p.rect(4, 4, 8, 8, C.cu2); p.rect(5, 5, 6, 6, C.cu1); p.rect(6, 6, 4, 4, C.outline);
});
texture('reactor_nitrogen', gasPort(GAS.nitrogen));
texture('reactor_hydrogen', gasPort(GAS.hydrogen));
const reactorFront = (led, ledDark) => (p) => { // an insulated pressure vessel with its bolted head and a temperature gauge
  plate(p, '#8c4a32');
  p.rect(3, 1, 10, 14, '#c9cdd4'); p.rect(3, 1, 10, 1, '#e3e6ea'); p.rect(12, 1, 1, 14, '#9ea3ab');
  for (let y = 3; y < 15; y += 3) p.rect(3, y, 10, 1, '#b2b7bf');                                   // cladding bands
  p.rect(6, 6, 4, 4, '#f2f2f2'); p.set(7, 7, C.outline); p.set(8, 8, C.magenta);                    // the temperature gauge
  p.rect(13, 13, 2, 2, led); p.set(14, 14, ledDark);
};
texture('ammonia_reactor_front', reactorFront(C.ledOff, C.outline));
texture('ammonia_reactor_front_on', reactorFront(C.amber, '#c98a2c'));
texture('ammonia_reactor_side', (p) => { plate(p, '#8c4a32'); p.rect(3, 1, 10, 14, '#c9cdd4'); for (let y = 3; y < 15; y += 3) p.rect(3, y, 10, 1, '#b2b7bf'); });
texture('ammonia_reactor_top', (p) => { // the ammonia outlet, yellow band
  plate(p, '#8c4a32'); p.rect(3, 3, 10, 10, GAS.ammonia); p.rect(4, 4, 8, 8, C.steel3); p.rect(5, 5, 6, 6, C.steel1); p.rect(6, 6, 4, 4, C.outline);
});
texture('mixer_ammonia', gasPort(GAS.ammonia));
const mixerFront = (led, ledDark) => (p) => { // a hopper for bone meal on top of a mixing drum, the bagging spout below
  plate(p, '#5a6b3a');
  p.rect(3, 2, 10, 7, '#e8e4d2'); p.rect(3, 2, 10, 1, '#f6f3e6'); p.rect(4, 4, 8, 3, '#d2cdb4');      // the drum
  p.rect(6, 10, 4, 4, C.steel1); p.rect(7, 14, 2, 1, C.steel0);                                     // the spout
  p.rect(12, 12, 2, 2, led); p.set(13, 13, ledDark);
};
texture('fertilizer_mixer_front', mixerFront(C.ledOff, C.outline));
texture('fertilizer_mixer_front_on', mixerFront(C.cyan, C.cyanDark));
texture('fertilizer_mixer_side', (p) => { plate(p, '#5a6b3a'); rivets(p, [[2, 2], [12, 2], [2, 12], [12, 12]]); });
texture('fertilizer_mixer_top', (p) => { plate(p, '#5a6b3a'); p.rect(3, 3, 10, 10, C.steel0); p.rect(4, 4, 8, 8, '#2a2c30'); }); // the hopper mouth
texture('fertilizer', (p) => pixels(p, [ // a bag of NPK fertilizer
  '................',
  '.....######.....',
  '....#aaaaaa#....',
  '...#aaaaaaaa#...',
  '...#abbbbbba#...',
  '..#abbccccbba#..',
  '..#abcbcbcbba#..',
  '..#abbccccbba#..',
  '..#abbbbbbbba#..',
  '..#aaaaaaaaaa#..',
  '..#adddddddda#..',
  '..#aaaaaaaaaa#..',
  '..#aaaaaaaaaa#..',
  '...##########...',
  '................',
  '................',
], { '#': '#3a3f2a', a: '#e8e4d2', b: '#7aa63c', c: '#f2f2ee', d: '#c9c4ad' }), 'item');
for (const [name, left, right, back, top, side] of [
  ['fuel_cell', 'fuel_cell_hydrogen', 'fuel_cell_oxygen', 'fuel_cell_back', 'fuel_cell_side', 'fuel_cell_side'],
  ['ammonia_reactor', 'reactor_nitrogen', 'reactor_hydrogen', 'ammonia_reactor_side', 'ammonia_reactor_top', 'ammonia_reactor_side'],
  ['fertilizer_mixer', 'mixer_ammonia', 'fertilizer_mixer_side', 'fertilizer_mixer_side', 'fertilizer_mixer_top', 'fertilizer_mixer_side'],
]) {
  for (const on of [false, true]) {
    M(name + (on ? '_on' : ''), { parent: 'minecraft:block/cube', textures: {
      north: tex(name + '_front' + (on ? '_on' : '')), east: tex(left), west: tex(right), south: tex(back), up: tex(top), down: tex(side), particle: tex(side) } });
  }
  BS(name, { variants: Object.fromEntries(Object.entries(yaw).flatMap(([f, y]) => [false, true].map((run) =>
    [`facing=${f},running=${run}`, { model: 'setpoint:block/' + name + (run ? '_on' : ''), ...(y ? { y } : {}) }]))) });
  item(name, 'setpoint:block/' + name + '_on');
}
item('fertilizer', 'setpoint:item/fertilizer');
json(join(ASSETS, 'models/item', 'fertilizer.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'setpoint:item/fertilizer' } });
recipe('fuel_cell', ['IGI', 'PCP', 'IRI'], { I: 'minecraft:iron_ingot', G: 'minecraft:gold_ingot', P: 'setpoint:gas_pipe', C: 'minecraft:copper_block', R: 'minecraft:redstone' }, 'setpoint:fuel_cell');
recipe('ammonia_reactor', ['IPI', 'PTP', 'IFI'], { I: 'minecraft:iron_block', P: 'setpoint:gas_pipe', T: 'setpoint:tank', F: 'minecraft:blast_furnace' }, 'setpoint:ammonia_reactor');
recipe('fertilizer_mixer', ['IPI', 'IHI', 'III'], { I: 'minecraft:iron_ingot', P: 'setpoint:gas_pipe', H: 'minecraft:hopper' }, 'setpoint:fertilizer_mixer');
recipe('gas_pipe', [' Y ', 'III'], { Y: 'minecraft:yellow_dye', I: 'minecraft:iron_ingot' }, 'setpoint:gas_pipe', 6);
recipe('electrolyser', ['IGI', 'CTC', 'IRI'], { I: 'minecraft:iron_ingot', G: 'minecraft:glass', C: 'minecraft:copper_block', T: 'setpoint:tank', R: 'minecraft:redstone' }, 'setpoint:electrolyser');
recipe('nitrogen_generator', ['ITI', 'TRT', 'IPI'], { I: 'minecraft:iron_ingot', T: 'setpoint:tank', R: 'minecraft:redstone', P: 'minecraft:piston' }, 'setpoint:nitrogen_generator');
recipe('gas_cylinder', [' V ', 'I I', 'III'], { V: 'setpoint:hand_valve', I: 'minecraft:iron_ingot' }, 'setpoint:gas_cylinder');

// =====================================================================================
// Gas sphere: a Horton sphere on eight columns, eight by eight blocks and ten high
// =====================================================================================
// Like the floating roof tank, its renderer puts it together from part models (the never-placed block
// setpoint:gas_sphere_model, one per part=), built in pixels from the middle of its footprint and moved into a model's
// bounds by SPHERE_SHIFT; GasSphereRenderer.SHIFT must match, and the sizes GasSphereBlock. The shell is one plate,
// stretched and turned into 12 bands of 24. The corner ladder and its cage are the floating roof tank's own models.
texture('sphere_plate', (p) => { // white steel, the weld seams to the next plates on its right and bottom edges
  p.rect(0, 0, 16, 16, '#eceef1');
  for (const [x, y] of [[4, 3], [11, 6], [7, 11], [2, 9], [13, 13]]) p.set(x, y, '#e1e4e9');
  p.rect(15, 0, 1, 16, '#c4c9d1'); p.rect(0, 15, 16, 1, '#c4c9d1');
});
texture('sphere_leg', (p) => { p.rect(0, 0, 16, 16, '#d9dce2'); p.rect(0, 0, 1, 16, '#eef0f3'); p.rect(15, 0, 1, 16, '#a9aeb7'); });
texture('sphere_rod', (p) => p.rect(0, 0, 16, 16, '#4f555f'));
texture('sphere_concrete', (p) => {
  p.rect(0, 0, 16, 16, '#9c9b96');
  for (const [x, y] of [[2, 3], [9, 1], [12, 7], [5, 10], [14, 13], [7, 14], [1, 12]]) p.set(x, y, '#86857f');
  for (const [x, y] of [[4, 6], [11, 11], [13, 3]]) p.set(x, y, '#b2b1ab');
});
texture('sphere_rail', (p) => { p.rect(0, 0, 16, 16, '#e2b13c'); p.rect(0, 15, 16, 1, '#a87f22'); });
texture('sphere_valve', (p) => { p.rect(0, 0, 16, 16, '#c0392b'); p.rect(0, 0, 16, 1, '#e0574a'); p.rect(0, 15, 16, 1, '#8e2a20'); });
texture('sphere_gauge', (p) => { p.rect(0, 0, 16, 16, '#f2f2f2'); p.rect(0, 0, 16, 1, '#2a2c30'); p.rect(0, 15, 16, 1, '#2a2c30'); p.rect(7, 4, 2, 6, '#2a2c30'); });
texture('sphere_station', (p) => { plate(p, '#6a707b'); });
const SPHERE_SHIFT = { plate: [0, 8, 0], leg: [0, 0, 0], foot: [0, 0, 0], rod: [0, 8, 0], bridge: [0, 1, 16], platform: [0, 1, 0],
  pipe_up: [0, 0, 0], pipe_along: [0, 0, 0], station: [-8, 0, -8] };
{
  const P = Object.fromEntries(Object.keys(SPHERE_SHIFT).map((k) => [k, []]));
  const add = (part, from, to, faces, stretch = false) => P[part].push({ from, to, faces, stretch });
  const sides = (t, skip = []) => Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].filter((f) => !skip.includes(f)).map((f) => [f, t]));
  const mirror = (part, [x1, y1, z1], [x2, y2, z2], faces) => { add(part, [x1, y1, z1], [x2, y2, z2], faces); add(part, [-x2, y1, z1], [-x1, y2, z2], faces); };

  add('plate', [-8, -8, -1], [8, 8, 0], { south: { t: 'plate', uv: [0, 0, 16, 16] } }, true);
  add('leg', [-2.5, 0, -2.5], [2.5, 32, 2.5], sides('leg', ['up', 'down']));
  add('foot', [-5, 0, -5], [5, 3, 5], sides('concrete', ['down']));
  add('foot', [-3.5, 3, -3.5], [3.5, 3.5, 3.5], sides('steel', ['down']));
  add('rod', [-0.5, -0.5, 0], [0.5, 0.5, 16], Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, { t: 'rod', uv: [0, 0, 1, 16] }])), true);
  // the bridge, one length of it, from its outer end (0, 0, 0) towards the middle (-z)
  add('bridge', [-6, -1, -24], [6, 0, 0], { up: 'grating', down: 'grating', east: 'steel', west: 'steel' });
  for (const z of [-0.5, -12, -23.5]) mirror('bridge', [5.5, 0, z - 0.5], [6.5, 12, z + 0.5], sides('rail', ['up', 'down']));
  for (const y of [6, 11]) mirror('bridge', [5.5, y, -24], [6.5, y + 1, 0], sides('rail'));
  // the platform on top, the bridge arriving in the middle of its +z side; two safety valves and a gauge
  add('platform', [-18, -1, -18], [18, 0, 18], { up: 'grating', down: 'grating', north: 'steel', south: 'steel', east: 'steel', west: 'steel' });
  for (const [x, z] of [[-17.5, -17.5], [17.5, -17.5], [-17.5, 17.5], [17.5, 17.5], [0, -17.5], [-17.5, 0], [17.5, 0], [-7.5, 17.5], [7.5, 17.5]]) {
    add('platform', [x - 0.5, 0, z - 0.5], [x + 0.5, 12, z + 0.5], sides('rail', ['up', 'down']));
  }
  for (const y of [6, 11]) {
    add('platform', [-18, y, -18], [18, y + 1, -17], sides('rail'));
    mirror('platform', [17, y, -18], [18, y + 1, 18], sides('rail'));
    mirror('platform', [7, y, 17], [18, y + 1, 18], sides('rail'));
  }
  for (const x of [-8, 8]) {
    add('platform', [x - 1.5, 0, -9.5], [x + 1.5, 5, -6.5], sides('valve', ['down']));       // a safety valve
    add('platform', [x - 1, 3, -12], [x + 1, 4.5, -9.5], sides('valve', ['down']));          // its outlet
  }
  add('platform', [-0.5, 0, -14.5], [0.5, 8, -13.5], sides('steel', ['down']));              // the gauge on its post
  add('platform', [-2, 8, -14.5], [2, 12, -13.5], { south: { t: 'gauge', uv: [0, 0, 16, 16] }, north: { t: 'steel', uv: [0, 0, 4, 4] },
    east: { t: 'steel', uv: [0, 0, 1, 4] }, west: { t: 'steel', uv: [0, 0, 1, 4] }, up: { t: 'steel', uv: [0, 0, 4, 1] } }, true);
  // the pipe from the bottom of the sphere down to the ground, and one length of it along the ground
  add('pipe_up', [-3, 4, -3], [3, 26, 3], sides('pipe', ['up', 'down']));
  add('pipe_up', [-4.5, 26, -4.5], [4.5, 28, 4.5], sides('steel'));
  add('pipe_up', [-3, 4, -3], [3, 11, 3], { down: 'pipe' });
  add('pipe_along', [-3, 5, 0], [3, 11, 24], sides('pipe', ['north', 'south']));
  // the valve station at the front, in its own block's pixels: a plinth, a valve with its wheel, pipe stubs to every side
  add('station', [1, 0, 1], [15, 2, 15], sides('concrete', ['down']));
  add('station', [5, 2, 5], [11, 10, 11], sides('station', ['down']));
  add('station', [7.5, 10, 7.5], [8.5, 13, 8.5], sides('steel', ['down']));
  add('station', [4, 13, 4], [12, 14, 12], sides('valve'));
  add('station', [0, 4, 5], [5, 10, 11], sides('pipe', ['east']));
  add('station', [11, 4, 5], [16, 10, 11], sides('pipe', ['west']));
  add('station', [5, 4, 11], [11, 10, 16], sides('pipe', ['north']));
  add('station', [5, 4, 0], [11, 10, 5], sides('pipe', ['south']));

  const BUILD = (list, shift) => list.flatMap(({ from, to, faces, stretch }) => {
    const m = (p) => p.map((v, k) => +(v + 8 * (k !== 1) + shift[k]).toFixed(4));
    if (stretch) return [box(m(from), m(to), Object.fromEntries(Object.entries(faces).map(([f, { t, uv }]) => [f, { uv, texture: '#' + t }])))];
    return tiled(m(from), m(to), (f) => (faces[f] ? '#' + faces[f] : null));
  });
  const textures = { plate: tex('sphere_plate'), leg: tex('sphere_leg'), rod: tex('sphere_rod'), concrete: tex('sphere_concrete'), rail: tex('sphere_rail'),
    valve: tex('sphere_valve'), gauge: tex('sphere_gauge'), station: tex('sphere_station'), steel: tex('frt_steel'), grating: tex('frt_grating'),
    pipe: tex('gas_pipe'), particle: tex('sphere_plate') };
  const parts = Object.keys(SPHERE_SHIFT);
  for (const part of parts) M('gas_sphere_' + part, { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: BUILD(P[part], SPHERE_SHIFT[part]) });
  const models = [...parts.map((p) => 'gas_sphere_' + p), 'floating_roof_tank_ladder', 'floating_roof_tank_cage'];
  BS('gas_sphere_model', { variants: Object.fromEntries(Array.from({ length: 16 }, (_, i) => [`part=${i}`, { model: 'setpoint:block/' + (models[i] ?? 'gas_sphere_plate') }])) });
  M('gas_sphere', { textures: { particle: tex('sphere_plate') } });
  BS('gas_sphere', { variants: Object.fromEntries(['north', 'east', 'south', 'west'].map((f) => [`facing=${f}`, { model: 'setpoint:block/gas_sphere' }])) });
  BS('gas_sphere_part', { variants: { 'ladder=false': { model: 'setpoint:block/gas_sphere' }, 'ladder=true': { model: 'setpoint:block/gas_sphere' } } });

  // the item: the sphere as slices, on its eight columns, shrunk to one block
  const whole = [];
  for (let y = 28; y < 148; y += 8) {
    const mid = y + 4 - 88, r = Math.sqrt(Math.max(0, 60 * 60 - mid * mid));
    for (let z = -Math.floor(r / 8) * 8 - 8; z < r; z += 8) {
      const zc = z + 4; if (Math.abs(zc) >= r) continue;
      const w = Math.sqrt(r * r - zc * zc);
      whole.push({ from: [-w, y, z], to: [w, y + 8, z + 8], faces: Object.fromEntries(['north', 'south', 'east', 'west', 'up'].map((f) => [f, { uv: [0, 0, 16, 16], texture: '#plate' }])) });
    }
  }
  for (let k = 0; k < 8; k++) {
    const a = (22.5 + 45 * k) * Math.PI / 180, x = 62 * Math.sin(a), z = 62 * Math.cos(a);
    whole.push({ from: [x - 3, 0, z - 3], to: [x + 3, 88, z + 3], faces: Object.fromEntries(['north', 'south', 'east', 'west'].map((f) => [f, { uv: [0, 0, 16, 16], texture: '#leg' }])) });
  }
  M('gas_sphere_item', { parent: 'minecraft:block/block', textures, elements: shrunk(whole, [0, 76, 0], 16 / 152) });
  item('gas_sphere', 'setpoint:block/gas_sphere_item');
}
recipe('gas_sphere', ['IGI', 'GPG', 'IGI'], { I: 'minecraft:iron_block', G: 'setpoint:gas_cylinder', P: 'setpoint:gas_pipe' }, 'setpoint:gas_sphere');

// =====================================================================================
// Flare stack: a riser forty blocks high in a derrick, the knock-out drum at its foot, the flame on top
// =====================================================================================
// Like the gas sphere, its renderer puts it together from part models (the never-placed block setpoint:flare_stack_model,
// one per part=). Each is built in its own small frame (a leg or rod lies along z, one block long, and is stretched to
// length; a deck tile is 8 x 8) and moved into a model's bounds by FLARE_SHIFT; FlareRenderer.SHIFT must match, and the
// sizes FlareBlock. The ladder and its cage are the floating roof tank's own models.
texture('flare_stack', (p) => { // heat-resistant dark steel, lit from the left
  p.rect(0, 0, 16, 16, '#5a5f68'); p.rect(0, 0, 3, 16, '#6c727c'); p.rect(3, 0, 2, 16, '#7a808a'); p.rect(11, 0, 5, 16, '#4b4f57');
  p.rect(0, 15, 16, 1, '#41454c');
  for (const [x, y] of [[6, 4], [9, 11], [2, 9], [13, 2]]) p.set(x, y, '#535860');
});
texture('flare_tip', (p) => { // stainless steel, sooted at the top where the flame licks it
  p.rect(0, 0, 16, 16, '#a7adb5'); p.rect(0, 0, 3, 16, '#bcc2ca'); p.rect(11, 0, 5, 16, '#8f959e');
  const soot = ['#24252a', '#2e3035', '#3b3d43', '#4c4f56', '#646870', '#80858d'];
  soot.forEach((c, y) => p.rect(0, y, 16, 1, c));
  for (const [x, y] of [[3, 6], [9, 7], [13, 6], [6, 8]]) p.set(x, y, '#6f747c');
});
texture('flare_tip_top', (p) => { p.rect(0, 0, 16, 16, '#1d1e22'); p.rect(0, 0, 16, 1, '#3b3d43'); p.rect(0, 0, 1, 16, '#3b3d43'); });
texture('flare_leg', (p) => { p.rect(0, 0, 16, 16, '#e8eaed'); p.rect(0, 0, 1, 16, '#ffffff'); p.rect(5, 0, 1, 16, '#c3c7cd'); });
texture('flare_leg_red', (p) => { p.rect(0, 0, 16, 16, '#c8372d'); p.rect(0, 0, 1, 16, '#e0574a'); p.rect(5, 0, 1, 16, '#9e2a22'); });
texture('flare_rod', (p) => { p.rect(0, 0, 16, 16, '#6b717b'); p.rect(0, 0, 1, 16, '#858b95'); });
texture('flare_drum', (p) => { // the knock-out drum: light grey plates, a weld seam round it
  p.rect(0, 0, 16, 16, '#c3c7cd');
  for (const [x, y] of [[4, 3], [11, 6], [7, 11], [2, 9], [13, 13]]) p.set(x, y, '#b5b9c0');
  p.rect(15, 0, 1, 16, '#a2a7af');
});
texture('flare_panel', (p) => { // the ignition panel: a grey cabinet, a red IGNITE button, an amber pilot lamp
  plate(p, '#8a9099');
  p.rect(3, 4, 4, 4, '#2a2c30'); p.rect(4, 5, 2, 2, '#e0392b'); p.set(4, 5, '#ff7a6e');
  p.rect(10, 4, 3, 3, '#2a2c30'); p.set(11, 5, C.amber);
  p.rect(3, 10, 10, 2, '#5d636c');
});
texture('flare_lamp', (p) => { p.rect(0, 0, 16, 16, '#ff3b2f'); p.rect(0, 0, 16, 2, '#ff8a80'); });
// the flame: eight frames of fire, narrow where it leaves the tip, widest a third of the way up, licking off in
// tongues at the top; see-through at its edges. The core is the same, smaller, nearly white.
for (const core of [false, true]) {
  animated(core ? 'flare_flame_core' : 'flare_flame', 2, Array.from({ length: 8 }, (_, f) => (p) => {
    const rnd = (a, b) => { const s = Math.sin(a * 12.9898 + b * 78.233 + f * 37.719) * 43758.5453; return s - Math.floor(s); };
    for (let y = 0; y < 16; y++) {
      const t = (15 - y) / 15;
      const sway = Math.sin((f / 8) * Math.PI * 2 + t * 4) * 1.3 * t;
      const body = Math.max(0, Math.sin(Math.PI * Math.min(1, 0.14 + t * 0.9))) ** 0.8 * (1 - t * 0.45);
      const half = (core ? 4.4 : 7.8) * body * (0.9 + 0.2 * rnd(y, 1)) + 0.4;
      for (let x = 0; x < 16; x++) {
        const d = Math.abs(x + 0.5 - 8 - sway) / half;
        if (d > 1 || (t > 0.5 && rnd(x, y) < (t - 0.5) * 1.5)) continue;
        const heat = (1 - d) * (1 - t * 0.55);
        if (core) p.set(x, y, heat > 0.45 ? '#fffbe6f0' : '#ffe9a0e0');
        else p.set(x, y, heat > 0.6 ? '#ffe27ae8' : heat > 0.35 ? '#ffb43cd8' : heat > 0.15 ? '#ff7a22c0' : '#e8461e90');
      }
    }
  }));
}
const FLARE_SHIFT = { stack: [0, 0, 0], tip: [0, -16, 0], leg: [0, 8, 0], leg_red: [0, 8, 0], rod: [0, 8, 0], rail: [0, 8, 0], deck: [0, 2, 0],
  drum: [0, 0, 0], head: [0, 0, 0], pipe: [0, 8, 0], station: [-8, 0, -8], flame: [0, 0, 0], flame_core: [0, 0, 0], lamp: [0, 0, 0] };
{
  const P = Object.fromEntries(Object.keys(FLARE_SHIFT).map((k) => [k, []]));
  const add = (part, from, to, faces, stretch = false, extra = {}) => P[part].push({ from, to, faces, stretch, extra });
  const sides = (t, skip = []) => Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].filter((f) => !skip.includes(f)).map((f) => [f, t]));
  // a rod along z, a block long, stretched by the renderer: every face shows its whole texture
  const rod = (part, r, t) => add(part, [-r, -r, 0], [r, r, 16], Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down']
    .map((f) => [f, { t, uv: f === 'north' || f === 'south' ? [0, 0, 2 * r, 2 * r] : [0, 0, 2 * r, 16] }])), true);

  // ---- the stack, a block of it, and the tip: a reducer up to the wider tip, its steam ring, three pilots beside it
  add('stack', [-5, 0, -5], [5, 16, 5], sides('stack', ['up', 'down']));
  add('tip', [-6, 0, -6], [6, 4, 6], sides('tip', ['down']));
  add('tip', [-7, 4, -7], [7, 44, 7], { ...sides('tip', ['up', 'down']), up: 'tip_top' });
  add('tip', [-7.5, 43, -7.5], [7.5, 44, 7.5], sides('rod', ['up']));                        // the rim
  for (const [x1, z1, x2, z2] of [[-9, -9, 9, -8], [-9, 8, 9, 9], [-9, -8, -8, 8], [8, -8, 9, 8]]) add('tip', [x1, 8, z1], [x2, 10, z2], sides('rod')); // the steam ring
  for (const [x, z] of [[0, -8.5], [0, 8.5], [-8.5, 0], [8.5, 0]]) add('tip', [x - 0.5, 10, z - 0.5], [x + 0.5, 42, z + 0.5], sides('rod', ['up', 'down'])); // its risers
  for (const a of [30, 150, 270]) { // the pilots, with their burner heads just under the rim
    const x = 9.5 * Math.sin(a * Math.PI / 180), z = 9.5 * Math.cos(a * Math.PI / 180);
    add('tip', [x - 1, 4, z - 1], [x + 1, 44, z + 1], sides('stack', ['down']));
    add('tip', [x - 1.5, 44, z - 1.5], [x + 1.5, 47, z + 1.5], sides('tip'));
  }
  // ---- the derrick's members and the platform pieces
  rod('leg', 3, 'leg'); rod('leg_red', 3, 'leg_red'); rod('rod', 1, 'rod'); rod('rail', 0.5, 'rail');
  add('deck', [-4, -2, -4], [4, 0, 4], { up: { t: 'grating', uv: [0, 0, 8, 8] }, down: { t: 'grating', uv: [0, 0, 8, 8] } }, true);
  add('lamp', [-0.5, -2, -0.5], [0.5, 0, 0.5], sides('rod', ['up']));
  add('lamp', [-1.5, 0, -1.5], [1.5, 2.5, 1.5], sides('lamp'), false, { light_emission: 15 });
  // ---- the knock-out drum, from its middle: the shell as slices of a circle along z, two saddles, the outlet on top
  for (let y = 6; y < 30; y += 2) {
    const h = Math.sqrt(144 - (y + 1 - 18) ** 2);
    add('drum', [-h, y, -24], [h, y + 2, 24], { east: 'drum', west: 'drum', north: 'drum', south: 'drum', ...(y + 1 > 18 ? { up: 'drum' } : { down: 'drum' }) });
  }
  for (const z of [-14, 14]) {
    add('drum', [-10, 0, z - 3], [10, 8, z + 3], sides('concrete', ['down']));
    add('drum', [-9, 8, z - 2], [9, 11, z + 2], sides('steel', ['down']));
  }
  add('drum', [-4.5, 29, -20.5], [4.5, 31, -11.5], sides('steel'));                           // the outlet flange
  add('drum', [-2, 13, 22], [2, 23, 23], { ...sides('rod', ['up', 'down']) });                 // a level gauge on the front end
  add('drum', [11.5, 12, -6], [13, 24, 6], sides('steel', ['west']));                           // the manway on its side
  // ---- a dished head, from the end of the shell (z = 0) outwards
  add('head', [-11, 8, 0], [11, 28, 2], sides('drum', ['north']));
  add('head', [-8, 10, 2], [8, 26, 4], sides('drum', ['north']));
  add('head', [-4, 14, 4], [4, 22, 5], sides('drum', ['north']));
  // ---- a length of the pipe from the drum to the stack
  add('pipe', [-4, -4, 0], [4, 4, 16], sides('pipe', ['north', 'south']));
  // ---- the ignition panel, in its own block's pixels: a plinth, the cabinet, a stub with a band for each gas line,
  // the IGNITE box on top and the pipe up and back into the drum
  add('station', [1, 0, 1], [15, 2, 15], sides('concrete', ['down']));
  add('station', [4, 2, 4], [12, 12, 12], { ...sides('station', ['down', 'south']), south: 'panel' });
  add('station', [5, 5, 12], [11, 11, 16], sides('pipe', ['south', 'north']));                // the flare header, at the front
  add('station', [0, 5, 5], [4, 11, 11], sides('pipe', ['east', 'west']));                     // pilot gas, on the left
  add('station', [1.5, 4.5, 4.5], [2.5, 11.5, 11.5], sides('band_lpg'));
  add('station', [12, 5, 5], [16, 11, 11], sides('pipe', ['east', 'west']));                   // steam, on the right
  add('station', [13.5, 4.5, 4.5], [14.5, 11.5, 11.5], sides('band_steam'));
  add('station', [6, 12, 4], [10, 22, 8], sides('pipe', ['down']));                            // up to the drum
  add('station', [6, 14, 0], [10, 22, 4], sides('pipe', ['north']));
  add('station', [3, 12, 9], [13, 16, 13], { ...sides('station', ['down', 'south']), south: { t: 'panel', uv: [0, 2, 16, 8] } });
  // ---- the flame: three crossed sheets, and a core like it; they glow by themselves
  for (const part of ['flame', 'flame_core']) {
    for (const angle of [0, 60, -60]) {
      add(part, [-8, 0, 0], [8, 16, 0], { north: { t: part, uv: [0, 0, 16, 16] }, south: { t: part, uv: [0, 0, 16, 16] } }, true,
        { light_emission: 15, shade: false, ...(angle ? { rotation: { origin: [8, 0, 8], x: 0, y: angle, z: 0 } } : {}) });
    }
  }

  const BUILD = (list, shift) => list.flatMap(({ from, to, faces, stretch, extra }) => {
    const m = (p) => p.map((v, k) => +(v + 8 * (k !== 1) + shift[k]).toFixed(4));
    if (stretch) return [{ ...box(m(from), m(to), Object.fromEntries(Object.entries(faces).map(([f, { t, uv }]) => [f, { uv, texture: '#' + t }]))), ...extra }];
    return tiled(m(from), m(to), (f) => {
      const t = faces[f];
      return t ? '#' + (typeof t === 'string' ? t : t.t) : null;
    }).map((e) => ({ ...e, ...extra }));
  });
  const textures = { stack: tex('flare_stack'), tip: tex('flare_tip'), tip_top: tex('flare_tip_top'), leg: tex('flare_leg'), leg_red: tex('flare_leg_red'),
    rod: tex('flare_rod'), rail: tex('sphere_rail'), grating: tex('frt_grating'), steel: tex('frt_steel'), concrete: tex('sphere_concrete'),
    drum: tex('flare_drum'), pipe: tex('gas_pipe'), station: tex('sphere_station'), panel: tex('flare_panel'), lamp: tex('flare_lamp'),
    band_lpg: tex('band_lpg'), band_steam: tex('band_steam'), flame: { force_translucent: true, sprite: tex('flare_flame') },
    flame_core: { force_translucent: true, sprite: tex('flare_flame_core') }, particle: tex('flare_leg_red') };
  const parts = Object.keys(FLARE_SHIFT);
  for (const part of parts) M('flare_stack_' + part, { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: BUILD(P[part], FLARE_SHIFT[part]) });
  const models = [...parts.map((p) => 'flare_stack_' + p), 'floating_roof_tank_ladder', 'floating_roof_tank_cage'];
  BS('flare_stack_model', { variants: Object.fromEntries(Array.from({ length: 16 }, (_, i) => [`part=${i}`, { model: 'setpoint:block/' + models[i] }])) });
  M('flare_stack', { textures: { particle: tex('flare_leg_red') } });
  BS('flare_stack', { variants: Object.fromEntries(['north', 'east', 'south', 'west'].map((f) => [`facing=${f}`, { model: 'setpoint:block/flare_stack' }])) });
  BS('flare_stack_part', { multipart: [{ apply: { model: 'setpoint:block/flare_stack' } }] });
}
// the item: a flat picture, a model this tall and thin would be a line in the inventory
texture('flare_stack', (p) => pixels(p, [
  '.......a........',
  '......aba.......',
  '......bcb.......',
  '.......s........',
  '......wsw.......',
  '......rsr.......',
  '.....w.s.w......',
  '.....rgsgr......',
  '....w.gsg.w.....',
  '....r.gsg.r.....',
  '...wggggggw.....',
  '...r.g.s.g.r....',
  '..w.g..s..g.w...',
  '..r.g..s..g.r...',
  '..wddddsddddw...',
  '.ffffffffffff...',
], { a: '#ffb43c', b: '#ff7a22', c: '#ffe27a', s: '#5a5f68', w: '#e8eaed', r: '#c8372d', g: '#6b717b', d: '#c3c7cd', f: '#3d424d' }), 'item');
json(join(ASSETS, 'models/item', 'flare_stack.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'setpoint:item/flare_stack' } });
item('flare_stack', 'setpoint:item/flare_stack');
recipe('flare_stack', ['IFI', 'BPB', 'IDI'], { I: 'minecraft:iron_block', F: 'minecraft:flint_and_steel', B: 'minecraft:iron_bars', P: 'setpoint:gas_pipe', D: 'setpoint:drum' }, 'setpoint:flare_stack');

// =====================================================================================
// Factory blocks: grating, stairs, handrails, ladders, beams and cladding to build round the plant
// =====================================================================================
texture('grating', (p) => { // see-through floor grating: bearing bars one way, cross bars the other, holes between
  for (let x = 0; x < 16; x += 3) p.rect(x, 0, 1, 16, '#8e949e');
  for (let y = 1; y < 16; y += 4) p.rect(0, y, 16, 1, '#b6bcc5');
  p.rect(0, 0, 16, 1, '#6f747d'); p.rect(0, 15, 16, 1, '#6f747d'); p.rect(0, 0, 1, 16, '#6f747d'); p.rect(15, 0, 1, 16, '#6f747d');
});
texture('grating_edge', (p) => { p.rect(0, 0, 16, 16, '#7d838d'); p.rect(0, 0, 16, 1, '#a3a9b2'); p.rect(0, 15, 16, 1, '#5d626b'); });
texture('stair_stringer', (p) => { // the steel side of a stair, with the bolts of the treads
  p.rect(0, 0, 16, 16, '#7d838d'); p.rect(0, 0, 16, 1, '#a3a9b2'); p.rect(0, 15, 16, 1, '#5d626b');
  for (const [x, y] of [[3, 3], [11, 3], [3, 11], [11, 11]]) p.set(x, y, '#c7ccd3');
});
texture('checker_plate', (p) => { // chequer plate: raised bars in a herringbone of pairs
  p.rect(0, 0, 16, 16, '#8a909a');
  for (let y = 0; y < 16; y += 4) for (let x = 0; x < 16; x += 4) {
    const flip = ((x + y) / 4) % 2 === 0;
    if (flip) { p.set(x + 1, y + 1, '#c3c9d1'); p.set(x + 2, y + 2, '#c3c9d1'); p.set(x + 2, y + 1, '#6c727c'); }
    else { p.set(x + 2, y + 1, '#c3c9d1'); p.set(x + 1, y + 2, '#c3c9d1'); p.set(x + 1, y + 1, '#6c727c'); }
  }
});
texture('hazard_stripes', (p) => { // yellow and black at 45°, like the edge of a loading dock
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) p.set(x, y, Math.floor((x + y) / 4) % 2 === 0 ? '#e2b13c' : '#1f2125');
});
texture('corrugated_steel', (p) => { // cladding: vertical ribs, light on one side, shadow on the other
  const ribs = ['#a9b0bb', '#c4cad3', '#b6bdc7', '#959ca7'];
  for (let x = 0; x < 16; x++) p.rect(x, 0, 1, 16, ribs[x % 4]);
  p.rect(0, 15, 16, 1, '#80868f');
});
texture('steel_beam', (p) => { p.rect(0, 0, 16, 16, '#5c6b7d'); p.rect(0, 0, 16, 1, '#7a8a9d'); p.rect(0, 15, 16, 1, '#465363'); });
texture('steel_beam_end', (p) => { p.rect(0, 0, 16, 16, '#4d5a6a'); for (const [x, y] of [[3, 3], [12, 12]]) p.set(x, y, '#6f7f92'); });
texture('lamp_glass', (p) => { p.rect(0, 0, 16, 16, '#fff6d6'); p.rect(2, 2, 12, 12, '#fffbea'); p.rect(5, 5, 6, 6, '#ffffff'); });
texture('lamp_body', (p) => { p.rect(0, 0, 16, 16, '#3c4048'); p.rect(0, 0, 16, 1, '#5a5f69'); });
{
  // grating: a 2-pixel floor in the bottom or the top of the block
  const gratingFaces = (y1, y2) => ({
    up: { uv: [0, 0, 16, 16], texture: '#grating' }, down: { uv: [0, 0, 16, 16], texture: '#grating' },
    ...Object.fromEntries(['north', 'south', 'east', 'west'].map((f) => [f, { uv: [0, 16 - y2, 16, 16 - y1], texture: '#edge', cullface: f }])),
  });
  const gt = { grating: tex('grating'), edge: tex('grating_edge'), particle: tex('grating_edge') };
  M('grating', { parent: 'minecraft:block/block', textures: gt, elements: [box([0, 0, 0], [16, 2, 16], gratingFaces(0, 2))] });
  M('grating_top', { parent: 'minecraft:block/block', textures: gt, elements: [box([0, 14, 0], [16, 16, 16], gratingFaces(14, 16))] });
  BS('grating', { variants: { 'half=bottom': { model: 'setpoint:block/grating' }, 'half=top': { model: 'setpoint:block/grating_top' } } });
  item('grating', 'setpoint:block/grating');

  // grating stairs: open steel stairs, two thin grating treads between steel stringers, in vanilla's stair shapes (a
  // straight flight, an inner and an outer corner; climbing towards east). They walk like any stairs.
  const st = { grating: tex('grating'), steel: tex('stair_stringer'), particle: tex('stair_stringer') };
  const tread = (x1, z1, x2, z2, y) => box([x1, y - 2, z1], [x2, y, z2], { up: { uv: [x1, z1, x2, z2], texture: '#grating' }, down: { uv: [x1, z1, x2, z2], texture: '#grating' },
    north: { uv: [x1, 0, x2, 2], texture: '#steel' }, south: { uv: [x1, 0, x2, 2], texture: '#steel' }, east: { uv: [z1, 0, z2, 2], texture: '#steel' }, west: { uv: [z1, 0, z2, 2], texture: '#steel' } });
  const plateBox = (from, to) => box(from, to, Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, { uv: [0, 0, 16, 16], texture: '#steel' }])));
  const stringer = (z) => [plateBox([0, 0, z], [8, 8, z + 1]), plateBox([8, 0, z], [16, 16, z + 1])];
  M('grating_stairs', { parent: 'minecraft:block/block', textures: st, elements: [tread(0, 0, 8, 16, 8), tread(8, 0, 16, 16, 16), ...stringer(0), ...stringer(15)] });
  M('grating_stairs_inner', { parent: 'minecraft:block/block', textures: st, elements: [tread(0, 0, 8, 8, 8), tread(8, 0, 16, 16, 16), tread(0, 8, 8, 16, 16),
    plateBox([0, 0, 0], [8, 8, 1]), plateBox([8, 0, 0], [16, 16, 1]), plateBox([0, 0, 0], [1, 8, 8]), plateBox([0, 0, 8], [1, 16, 16])] });
  M('grating_stairs_outer', { parent: 'minecraft:block/block', textures: st, elements: [tread(0, 0, 16, 8, 8), tread(0, 8, 8, 16, 8), tread(8, 8, 16, 16, 16),
    plateBox([8, 0, 15], [16, 16, 16]), plateBox([15, 0, 8], [16, 16, 15]), plateBox([0, 0, 15], [8, 8, 16]), plateBox([15, 0, 0], [16, 8, 8])] });
  const stairs = {};
  for (const [f, y0] of Object.entries({ east: 0, south: 90, west: 180, north: 270 })) for (const half of ['bottom', 'top']) {
    for (const shape of ['straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right']) {
      let y = y0, x = 0; // the same turns vanilla gives its stairs
      if (half === 'bottom') { if (shape.endsWith('left')) y += 270; } else { x = 180; if (shape.endsWith('right')) y += 90; }
      y %= 360;
      const model = 'setpoint:block/grating_stairs' + (shape === 'straight' ? '' : shape.startsWith('inner') ? '_inner' : '_outer');
      stairs[`facing=${f},half=${half},shape=${shape}`] = { model, ...(x ? { x } : {}), ...(y ? { y } : {}), ...(x || y ? { uvlock: true } : {}) };
    }
  }
  BS('grating_stairs', { variants: stairs });
  item('grating_stairs', 'setpoint:block/grating_stairs');

  // handrail: one model per side (on the north edge, turned for the others): two posts, a knee rail, a top rail, a toe board
  const ht = { rail: tex('sphere_rail'), particle: tex('sphere_rail') };
  const railBox = (from, to) => box(from, to, Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, { uv: [0, 0, 16, 16], texture: '#rail' }])));
  M('handrail_edge', { textures: ht, elements: [
    railBox([1, 0, 0.25], [2.5, 16, 1.75]), railBox([13.5, 0, 0.25], [15, 16, 1.75]),
    railBox([0, 14.5, 0], [16, 16, 2]), railBox([0, 8, 0.5], [16, 9, 1.5]), railBox([0, 0, 0.5], [16, 2.5, 1.25]),
  ] });
  BS('handrail', { multipart: [...Object.entries({ north: 0, east: 90, south: 180, west: 270 }).map(([edge, y]) =>
    ({ when: { [edge]: 'true' }, apply: { model: 'setpoint:block/handrail_edge', ...(y ? { y } : {}) } })),
    { when: { floor: 'true' }, apply: { model: 'setpoint:block/grating' } }] }); // a grating floor in the same block
  M('handrail_item', { parent: 'minecraft:block/block', textures: ht, elements: [
    railBox([1, 0, 7.25], [2.5, 16, 8.75]), railBox([13.5, 0, 7.25], [15, 16, 8.75]),
    railBox([0, 14.5, 7], [16, 16, 9]), railBox([0, 8, 7.5], [16, 9, 8.5]), railBox([0, 0, 7.5], [16, 2.5, 8.25]),
  ] });
  item('handrail', 'setpoint:block/handrail_item');

  // caged ladder: rungs against the wall (south, for facing north), hoops round the climber
  const lt = { steel: tex('frt_steel'), particle: tex('frt_steel') };
  const steelBox = (from, to) => box(from, to, Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, { uv: [0, 0, 16, 16], texture: '#steel' }])));
  const ladder = [steelBox([2, 0, 14], [3, 16, 16]), steelBox([13, 0, 14], [14, 16, 16])];
  for (let y = 2; y < 16; y += 4) ladder.push(steelBox([3, y - 0.5, 14.5], [13, y + 0.5, 15.5]));
  for (const y of [3, 11]) ladder.push(steelBox([1, y, 2], [15, y + 1, 3]), steelBox([1, y, 3], [2, y + 1, 14]), steelBox([14, y, 3], [15, y + 1, 14]));
  for (const x of [4.5, 7.5, 10.5]) ladder.push(steelBox([x, 0, 2], [x + 1, 16, 3]));
  ladder.push(steelBox([1, 0, 8], [2, 16, 9]), steelBox([14, 0, 8], [15, 16, 9]));
  M('caged_ladder', { parent: 'minecraft:block/block', ambientocclusion: false, textures: lt, elements: ladder });
  BS('caged_ladder', { variants: Object.fromEntries(Object.entries({ north: 0, east: 90, south: 180, west: 270 }).map(([f, y]) => [`facing=${f}`, { model: 'setpoint:block/caged_ladder', ...(y ? { y } : {}) }])) });
  item('caged_ladder', 'setpoint:block/caged_ladder');

  // steel I-beam: two flanges and a web, standing up; laid along x or z it turns like a log
  const bt = { side: tex('steel_beam'), end: tex('steel_beam_end'), particle: tex('steel_beam') };
  const beamPart = (from, to) => box(from, to, {
    north: { uv: [from[0], 0, to[0], 16], texture: '#side' }, south: { uv: [from[0], 0, to[0], 16], texture: '#side' },
    east: { uv: [from[2], 0, to[2], 16], texture: '#side' }, west: { uv: [from[2], 0, to[2], 16], texture: '#side' },
    up: { uv: [from[0], from[2], to[0], to[2]], texture: '#end', cullface: 'up' }, down: { uv: [from[0], from[2], to[0], to[2]], texture: '#end', cullface: 'down' } });
  M('steel_beam', { parent: 'minecraft:block/block', textures: bt, elements: [beamPart([0, 0, 0], [16, 16, 2]), beamPart([0, 0, 14], [16, 16, 16]), beamPart([7, 0, 2], [9, 16, 14])] });
  BS('steel_beam', { variants: { 'axis=y': { model: 'setpoint:block/steel_beam' }, 'axis=z': { model: 'setpoint:block/steel_beam', x: 90 },
    'axis=x': { model: 'setpoint:block/steel_beam', x: 90, y: 90 } } });
  item('steel_beam', 'setpoint:block/steel_beam');

  for (const name of ['checker_plate', 'hazard_stripes', 'corrugated_steel']) {
    M(name, { parent: 'minecraft:block/cube_all', textures: { all: tex(name) } });
    BS(name, { variants: { '': { model: 'setpoint:block/' + name } } });
    item(name, 'setpoint:block/' + name);
  }

  // industrial lamp: a bulkhead light with a wire guard, pointing up; the blockstate turns it like an end rod
  const lp = { body: tex('lamp_body'), glass: tex('lamp_glass'), guard: tex('frt_steel'), particle: tex('lamp_body') };
  const lampBox = (from, to, t) => box(from, to, Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, { uv: [0, 0, 16, 16], texture: '#' + t }])));
  M('industrial_lamp', { parent: 'minecraft:block/block', ambientocclusion: false, textures: lp, elements: [
    lampBox([4, 0, 4], [12, 1.5, 12], 'body'), lampBox([5, 1.5, 5], [11, 5, 11], 'glass'),
    lampBox([4.5, 1.5, 7.5], [11.5, 6, 8.5], 'guard'), lampBox([7.5, 1.5, 4.5], [8.5, 6, 11.5], 'guard'),
  ] });
  BS('industrial_lamp', { variants: { 'facing=up': { model: 'setpoint:block/industrial_lamp' }, 'facing=down': { model: 'setpoint:block/industrial_lamp', x: 180 },
    'facing=north': { model: 'setpoint:block/industrial_lamp', x: 90 }, 'facing=south': { model: 'setpoint:block/industrial_lamp', x: 90, y: 180 },
    'facing=east': { model: 'setpoint:block/industrial_lamp', x: 90, y: 90 }, 'facing=west': { model: 'setpoint:block/industrial_lamp', x: 90, y: 270 } } });
  item('industrial_lamp', 'setpoint:block/industrial_lamp');
}
// pallet racking: blue uprights with holes, orange beams with a wire deck in the top of the block
texture('rack_upright', (p) => {
  p.rect(0, 0, 16, 16, '#2f5fa8'); p.rect(0, 0, 1, 16, '#4b7cc6'); p.rect(15, 0, 1, 16, '#21467e');
  for (let y = 1; y < 16; y += 3) { p.rect(5, y, 2, 1, '#14284a'); p.rect(9, y, 2, 1, '#14284a'); }  // the holes for the beam hooks
});
texture('rack_upright_end', (p) => { p.rect(0, 0, 16, 16, '#21467e'); p.rect(5, 5, 6, 6, '#2f5fa8'); });
texture('rack_beam', (p) => { p.rect(0, 0, 16, 16, '#e46a1c'); p.rect(0, 0, 16, 1, '#f59149'); p.rect(0, 15, 16, 1, '#a94a10'); });
texture('rack_deck', (p) => { // wire mesh: see-through
  for (let x = 0; x < 16; x += 4) p.rect(x, 0, 1, 16, '#9aa0ab');
  for (let y = 1; y < 16; y += 4) p.rect(0, y, 16, 1, '#b6bcc5');
});
{
  const all = (t, uv = [0, 0, 16, 16]) => Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, { uv, texture: t }]));
  M('rack_upright', { parent: 'minecraft:block/block', textures: { side: tex('rack_upright'), end: tex('rack_upright_end'), particle: tex('rack_upright') },
    elements: [box([5, 0, 5], [11, 16, 11], { ...Object.fromEntries(['north', 'south', 'east', 'west'].map((f) => [f, { uv: [5, 0, 11, 16], texture: '#side' }])),
      up: { uv: [5, 5, 11, 11], texture: '#end', cullface: 'up' }, down: { uv: [5, 5, 11, 11], texture: '#end', cullface: 'down' } })] });
  BS('rack_upright', { variants: { '': { model: 'setpoint:block/rack_upright' } } });
  item('rack_upright', 'setpoint:block/rack_upright');
  M('rack_beam', { parent: 'minecraft:block/block', textures: { beam: tex('rack_beam'), deck: tex('rack_deck'), particle: tex('rack_beam') }, elements: [
    box([0, 12, 0], [16, 16, 2], all('#beam')), box([0, 12, 14], [16, 16, 16], all('#beam')),
    box([0, 14.5, 2], [16, 15.5, 14], { up: { uv: [0, 2, 16, 14], texture: '#deck' }, down: { uv: [0, 2, 16, 14], texture: '#deck' } }),
  ] });
  BS('rack_beam', { variants: { 'axis=x': { model: 'setpoint:block/rack_beam' }, 'axis=z': { model: 'setpoint:block/rack_beam', y: 90 } } });
  item('rack_beam', 'setpoint:block/rack_beam');
}
recipe('rack_upright', ['I', 'D', 'I'], { I: 'minecraft:iron_ingot', D: 'minecraft:blue_dye' }, 'setpoint:rack_upright', 4);
recipe('rack_beam', ['DDD', 'III'], { I: 'minecraft:iron_ingot', D: 'minecraft:orange_dye' }, 'setpoint:rack_beam', 4);
recipe('grating', ['BBB'], { B: 'minecraft:iron_bars' }, 'setpoint:grating', 6);
recipe('grating_stairs', ['B  ', 'BB ', 'BBB'], { B: 'minecraft:iron_bars' }, 'setpoint:grating_stairs', 4);
recipe('handrail', ['NNN', 'NYN'], { N: 'minecraft:iron_nugget', Y: 'minecraft:yellow_dye' }, 'setpoint:handrail', 6);
recipe('caged_ladder', ['NLN', 'NLN', 'NLN'], { N: 'minecraft:iron_nugget', L: 'minecraft:ladder' }, 'setpoint:caged_ladder', 3);
recipe('steel_beam', ['III', ' I ', 'III'], { I: 'minecraft:iron_ingot' }, 'setpoint:steel_beam', 6);
recipe('checker_plate', ['I I', ' I ', 'I I'], { I: 'minecraft:iron_ingot' }, 'setpoint:checker_plate', 8);
recipe('hazard_stripes', ['CYC', 'CKC', 'CYC'], { C: 'setpoint:checker_plate', Y: 'minecraft:yellow_dye', K: 'minecraft:black_dye' }, 'setpoint:hazard_stripes', 6);
recipe('corrugated_steel', ['I I', 'I I', 'I I'], { I: 'minecraft:iron_ingot' }, 'setpoint:corrugated_steel', 12);
recipe('industrial_lamp', [' I ', 'NGN'], { I: 'minecraft:iron_ingot', N: 'minecraft:iron_nugget', G: 'minecraft:glowstone' }, 'setpoint:industrial_lamp', 2);

// =====================================================================================
// Pumpjack: a nodding oil pump on a well, one wide, four long and three high
// =====================================================================================
// Built in pixels from the bottom middle of the wellhead (z towards the front, the pumpjack behind it); the walking
// beam pieces from its pivot on the samson post, the crank from its axle. PUMPJACK_SHIFT moves each into a model's
// bounds; PumpjackRenderer.SHIFT must match. The renderer makes it nod.
texture('pumpjack_frame', (p) => { p.rect(0, 0, 16, 16, '#5a5f69'); p.rect(0, 0, 16, 1, '#7a808b'); p.rect(0, 15, 16, 1, '#42464e'); });
texture('pumpjack_beam', (p) => { p.rect(0, 0, 16, 16, '#3b4048'); p.rect(0, 0, 16, 1, '#555b65'); p.rect(0, 15, 16, 1, '#2a2e34'); });
texture('pumpjack_yellow', (p) => { p.rect(0, 0, 16, 16, '#e2b13c'); p.rect(0, 0, 16, 1, '#f2c862'); p.rect(0, 15, 16, 1, '#a87f22');
  for (let i = 0; i < 16; i += 8) p.rect(i, 6, 4, 4, '#1f2125'); });
texture('pumpjack_blue', (p) => { p.rect(0, 0, 16, 16, '#2f5fa8'); p.rect(0, 0, 16, 1, '#4b7cc6'); p.rect(0, 15, 16, 1, '#21467e'); });
texture('pumpjack_valve', (p) => { p.rect(0, 0, 16, 16, '#c0392b'); p.rect(0, 0, 16, 1, '#e0574a'); });
texture('pumpjack_rod', (p) => { p.rect(0, 0, 16, 16, '#c8ccd3'); p.rect(0, 0, 1, 16, '#eceef1'); });
const PUMPJACK_SHIFT = { frame: [0, 0, 32], post: [0, -12, 32], beam_front: [0, 0, -12], beam_back: [0, 0, 12], crank: [0, 0, 0],
  gearbox: [0, 0, 44], wellhead: [0, 0, 0], rod: [0, 8, 0] };
{
  const P = Object.fromEntries(Object.keys(PUMPJACK_SHIFT).map((k) => [k, []]));
  const add = (part, from, to, t, stretch = false) => P[part].push({ from, to, t, stretch });
  const mirror = (part, [x1, y1, z1], [x2, y2, z2], t) => { add(part, [x1, y1, z1], [x2, y2, z2], t); add(part, [-x2, y1, z1], [-x1, y2, z2], t); };
  // the frame on the ground: two rails and three cross members
  mirror('frame', [3, 0, -54], [6, 3, -8], 'frame');
  for (const z of [-13, -30, -46]) add('frame', [-3, 0, z - 1], [3, 2, z + 1], 'frame');
  // the samson post: an A-frame of four legs, the bearing block on top
  for (const z of [-40, -26]) mirror('post', [4, 2, z], [6, 32, z + 2], 'frame');
  mirror('post', [4, 30, -34], [6, 38, -30], 'frame');
  add('post', [-6, 38, -34], [6, 42, -30], 'beam');
  // the walking beam, from its pivot: forward to the horsehead, back to the equalizer
  add('beam_front', [-1.5, -2, 0], [1.5, 2, 32], 'beam');
  add('beam_front', [-2, -10, 30], [2, 5, 33], 'yellow');
  add('beam_front', [-2, -9, 33], [2, 3, 35], 'yellow');
  add('beam_front', [-2, -7, 35], [2, 1, 36], 'yellow');
  add('beam_back', [-1.5, -2, -22], [1.5, 2, 0], 'beam');
  add('beam_back', [-8, -1.5, -21], [8, 1.5, -19], 'beam');
  // one crank, from its axle (the renderer draws two): the arm, its counterweight, the hub
  add('crank', [-1, -3, -2], [1, 10, 2], 'beam');
  add('crank', [-1.5, 6, -6], [1.5, 14, 6], 'yellow');
  add('crank', [-1.5, -2, -2], [1.5, 2, 2], 'frame');
  // the gearbox with the crank shaft through it, the motor in front of it
  add('gearbox', [-4, 2, -54], [4, 22, -42], 'blue');
  add('gearbox', [-8, 19, -49], [8, 21, -47], 'frame');
  add('gearbox', [-3, 2, -42], [3, 9, -36], 'blue');
  // the wellhead: casing, a christmas tree with two wing valves and their wheels, the stuffing box
  add('wellhead', [-3, 0, -3], [3, 3, 3], 'frame');
  add('wellhead', [-1.5, 3, -1.5], [1.5, 11, 1.5], 'frame');
  add('wellhead', [-6, 6, -1], [6, 8, 1], 'frame');
  for (const x of [-6.5, 6.5]) add('wellhead', [x - 0.5, 4, -2], [x + 0.5, 10, 2], 'valve');
  add('wellhead', [-2, 11, -2], [2, 12, 2], 'beam');
  add('rod', [-0.5, -0.5, 0], [0.5, 0.5, 16], 'rod', true);

  const BUILD = (list, shift) => list.flatMap(({ from, to, t, stretch }) => {
    const m = (p) => p.map((v, k) => +(v + 8 * (k !== 1) + shift[k]).toFixed(4));
    if (stretch) return [box(m(from), m(to), Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, { uv: [0, 0, 1, 16], texture: '#' + t }])))];
    return tiled(m(from), m(to), () => '#' + t);
  });
  const textures = { frame: tex('pumpjack_frame'), beam: tex('pumpjack_beam'), yellow: tex('pumpjack_yellow'), blue: tex('pumpjack_blue'),
    valve: tex('pumpjack_valve'), rod: tex('pumpjack_rod'), particle: tex('pumpjack_frame') };
  const parts = Object.keys(PUMPJACK_SHIFT);
  for (const part of parts) M('pumpjack_' + part, { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: BUILD(P[part], PUMPJACK_SHIFT[part]) });
  BS('pumpjack_model', { variants: Object.fromEntries(Array.from({ length: 16 }, (_, i) => [`part=${i}`, { model: 'setpoint:block/pumpjack_' + (parts[i] ?? 'frame') }])) });
  M('pumpjack', { textures: { particle: tex('pumpjack_frame') } });
  BS('pumpjack', { variants: Object.fromEntries(['north', 'east', 'south', 'west'].flatMap((f) => ['false', 'true'].map((r) => [`facing=${f},running=${r}`, { model: 'setpoint:block/pumpjack' }]))) });
  BS('pumpjack_part', { variants: Object.fromEntries([0, 1, 2].map((k) => [`kind=${k}`, { model: 'setpoint:block/pumpjack' }])) });
  // the item: the whole pumpjack at rest, shrunk to one block (in pumpjack pixels; the beam put back on its pivot)
  const at = (list, dy, dz) => list.map((e) => ({ ...e, from: [e.from[0], e.from[1] + dy, e.from[2] + dz], to: [e.to[0], e.to[1] + dy, e.to[2] + dz] }));
  const raw = (list) => list.flatMap(({ from, to, t }) => [box(from, to, Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, { uv: [0, 0, 16, 16], texture: '#' + t }])))]);
  const whole = [...raw(P.frame), ...raw(P.post), ...at(raw(P.beam_front), 40, -32), ...at(raw(P.beam_back), 40, -32), ...raw(P.gearbox), ...raw(P.wellhead),
    ...at(raw(P.crank), 20, -48).map((e) => ({ ...e, from: [e.from[0] + 7, e.from[1], e.from[2]], to: [e.to[0] + 7, e.to[1], e.to[2]] }))];
  M('pumpjack_item', { parent: 'minecraft:block/block', textures, elements: shrunk(whole, [0, 24, -24], 16 / 64) });
  item('pumpjack', 'setpoint:block/pumpjack_item');
}
recipe('pumpjack', ['IBI', ' P ', 'ITI'], { I: 'minecraft:iron_ingot', B: 'minecraft:iron_block', P: 'minecraft:piston', T: 'setpoint:tank' }, 'setpoint:pumpjack');

// =====================================================================================
// Distillation column, its reboiler and condenser; diesel generator
// =====================================================================================
// The column is drawn by its renderer from part models built in pixels from the bottom middle of the feed block (z to
// the front; the column's middle 32 behind): a 15° plate of the round shell, the domed head, a platform ring segment,
// the reboiler, the condenser, and a nozzle per stream with its colour band. COLUMN_SHIFT = DistillationColumnRenderer.SHIFT.
texture('column_shell', (p) => { // insulation cladding: aluminium sheets with their overlaps
  p.rect(0, 0, 16, 16, '#c7ccd3'); p.rect(0, 0, 16, 1, '#e4e7eb'); p.rect(0, 7, 16, 1, '#a9aeb6'); p.rect(0, 8, 16, 1, '#dde0e5');
  p.rect(0, 15, 16, 1, '#a9aeb6');
});
texture('column_inner', (p) => p.rect(0, 0, 16, 16, '#6d727a'));
texture('reboiler', (p) => { p.rect(0, 0, 16, 16, '#b9bec6'); for (let x = 0; x < 16; x += 4) p.rect(x, 0, 1, 16, '#9ea3ab'); p.rect(0, 0, 16, 1, '#dde0e5'); });
texture('condenser', (p) => { p.rect(0, 0, 16, 16, '#7d8794'); for (let y = 2; y < 16; y += 4) p.rect(0, y, 16, 1, '#646d79'); p.rect(0, 0, 16, 1, '#a3abb6'); });
const BANDS = { feed: '#2b241e', steam: '#d8dde3', residue: '#141212', diesel: '#c9962c', kerosene: '#c9ddea', naphtha: '#e6d9a6', water: '#3f8f4f', lpg: '#e07b22' };
for (const [name, colour] of Object.entries(BANDS)) texture('band_' + name, (p) => { p.rect(0, 0, 16, 16, colour); p.rect(0, 0, 16, 1, '#ffffff33'.slice(0, 7)); });
texture('diesel_generator_front', (p) => { // the radiator grille
  plate(p, '#c9962c'); p.rect(2, 2, 12, 10, '#2a2c30'); for (let y = 3; y < 12; y += 2) p.rect(2, y, 12, 1, '#4a4e55');
  p.rect(3, 13, 3, 2, C.ledOff); p.rect(10, 13, 3, 2, '#2a2c30');
});
texture('diesel_generator_front_on', (p) => {
  plate(p, '#c9962c'); p.rect(2, 2, 12, 10, '#2a2c30'); for (let y = 3; y < 12; y += 2) p.rect(2, y, 12, 1, '#4a4e55');
  p.rect(3, 13, 3, 2, C.cyan); p.rect(10, 13, 3, 2, '#2a2c30');
});
texture('diesel_generator_side', (p) => { // the engine block with its cylinders
  plate(p, '#c9962c'); p.rect(2, 4, 12, 8, '#3b4048'); for (let x = 3; x < 14; x += 3) p.rect(x, 5, 2, 6, '#5a5f69');
});
texture('diesel_generator_top', (p) => { plate(p, '#c9962c'); p.rect(10, 2, 4, 4, '#2a2c30'); p.rect(11, 3, 2, 2, '#141414'); }); // the exhaust stack
texture('diesel_generator_back', (p) => { plate(p, '#c9962c'); p.rect(4, 4, 8, 8, BANDS.diesel); p.rect(5, 5, 6, 6, C.steel1); p.rect(6, 6, 4, 4, C.outline); });
const COLUMN_SHIFT = { shell: [0, 0, 0], head: [0, 0, 0], ring: [0, 0, -8], reboiler: [0, 0, 0], condenser: [0, 0, 0], nozzle: [0, 8, 0] };
{
  const all = (t, uv = [0, 0, 16, 16]) => Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, { uv, texture: '#' + t }]));
  const sh = (part, list) => list.map((e) => ({ ...e, from: e.from.map((v, k) => +(v + 8 * (k !== 1) + COLUMN_SHIFT[part][k]).toFixed(4)),
    to: e.to.map((v, k) => +(v + 8 * (k !== 1) + COLUMN_SHIFT[part][k]).toFixed(4)) }));
  const w = segment(20), wr = segment(28), wl = segment(27.5);
  const shell = [0, 16].map((y) => box([-w, y, 19], [w, y + 16, 20], { south: { uv: [0, 0, 5, 16], texture: '#shell' }, north: { uv: [0, 0, 5, 16], texture: '#inner' } }));
  const disc = (r, y1, y2, t) => [box([-r, y1, -r * 0.42], [r, y2, r * 0.42], all(t)), box([-r * 0.42, y1, -r], [r * 0.42, y2, r], all(t))];
  const head = [...disc(20, 0, 4, 'shell'), ...disc(16, 4, 7, 'shell'), ...disc(11, 7, 9, 'shell'), ...disc(5, 9, 10, 'shell'), box([-3, 10, -3], [3, 14, 3], all('steel'))];
  const ring = [
    box([-wr, -1, 20], [wr, 0, 28], { up: { uv: [0, 0, 16, 8], texture: '#grating' }, down: { uv: [0, 0, 16, 8], texture: '#grating' } }),
    box([-0.5, 0, 27], [0.5, 12, 28], all('rail')),
    box([-wl, 6, 27], [wl, 7, 28], all('rail')), box([-wl, 11, 27], [wl, 12, 28], all('rail')),
  ];
  const reboiler = [box([-22, 2, -7], [22, 12, 7], all('reboiler')), box([-22, 0.5, -5], [22, 13.5, 5], all('reboiler')),
    box([-23, 3, -4], [-22, 11, 4], all('steel')), box([22, 3, -4], [23, 11, 4], all('steel')),
    box([-17, 0, -6], [-14, 3, 6], all('steel')), box([14, 0, -6], [17, 3, 6], all('steel'))];
  const condenser = [box([-19, 0, -1], [-17, 8, 1], all('steel')), box([17, 0, -1], [19, 8, 1], all('steel')),
    box([-22, 9, -6], [22, 17, 6], all('condenser')), box([-22, 8, -4], [22, 18, 4], all('condenser')),
    box([-24, 9, -5], [-22, 17, 5], all('steel')), box([22, 9, -5], [24, 17, 5], all('steel')),
    box([-1.5, -6, -1.5], [1.5, 9, 1.5], all('steel'))]; // the vapour line down into the head
  const textures = { shell: tex('column_shell'), inner: tex('column_inner'), steel: tex('frt_steel'), grating: tex('frt_grating'), rail: tex('sphere_rail'),
    reboiler: tex('reboiler'), condenser: tex('condenser'), particle: tex('column_shell'),
    ...Object.fromEntries(Object.keys(BANDS).map((n) => ['band_' + n, tex('band_' + n)])) };
  M('distillation_column_shell', { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: sh('shell', shell) });
  M('distillation_column_head', { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: sh('head', head) });
  M('distillation_column_ring', { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: sh('ring', ring) });
  M('distillation_column_reboiler', { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: sh('reboiler', reboiler) });
  M('distillation_column_condenser', { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: sh('condenser', condenser) });
  const nozzles = Object.keys(BANDS);
  for (const n of nozzles) {
    M('distillation_column_nozzle_' + n, { parent: 'minecraft:block/block', ambientocclusion: false, textures, elements: sh('nozzle', [
      box([-2.5, -2.5, 0], [2.5, 2.5, 11], all('steel')), box([-3, -3, 5], [3, 3, 7], all('band_' + n)), box([-4, -4, 11], [4, 4, 12], all('steel')),
    ]) });
  }
  const models = ['shell', 'head', 'ring', 'reboiler', 'condenser', ...nozzles.map((n) => 'nozzle_' + n)];
  BS('distillation_column_model', { variants: Object.fromEntries(Array.from({ length: 16 }, (_, i) => [`part=${i}`, { model: 'setpoint:block/distillation_column_' + (models[i] ?? 'shell') }])) });
  M('distillation_column', { textures: { particle: tex('column_shell') } });
  // one model for every state (a multipart with no condition)
  BS('distillation_column', { multipart: [{ apply: { model: 'setpoint:block/distillation_column' } }] });
  BS('column_part', { multipart: [{ apply: { model: 'setpoint:block/distillation_column' } }] });
  // the item: the column as a stack of discs on its front row, with the condenser on top, shrunk to one block
  const whole = [];
  for (let y = 0; y < 160; y += 16) whole.push(...disc(20, y, y + 16, 'shell').map((e) => ({ ...e, from: [e.from[0], e.from[1], e.from[2] - 32], to: [e.to[0], e.to[1], e.to[2] - 32] })));
  whole.push(...head.map((e) => ({ ...e, from: [e.from[0], e.from[1] + 160, e.from[2] - 32], to: [e.to[0], e.to[1] + 160, e.to[2] - 32] })));
  whole.push(...condenser.map((e) => ({ ...e, from: [e.from[0], e.from[1] + 160, e.from[2] - 32], to: [e.to[0], e.to[1] + 160, e.to[2] - 32] })));
  whole.push(...reboiler);
  M('distillation_column_item', { parent: 'minecraft:block/block', textures, elements: shrunk(whole, [0, 88, -24], 16 / 184) });
  item('distillation_column', 'setpoint:block/distillation_column_item');

  for (const on of [false, true]) {
    M('diesel_generator' + (on ? '_on' : ''), { parent: 'minecraft:block/cube', textures: {
      north: tex('diesel_generator_front' + (on ? '_on' : '')), east: tex('diesel_generator_side'), west: tex('diesel_generator_side'),
      south: tex('diesel_generator_back'), up: tex('diesel_generator_top'), down: tex('diesel_generator_side'), particle: tex('diesel_generator_side') } });
  }
  BS('diesel_generator', { variants: Object.fromEntries(Object.entries(yaw).flatMap(([f, y]) => [false, true].map((run) =>
    [`facing=${f},running=${run}`, { model: 'setpoint:block/diesel_generator' + (run ? '_on' : ''), ...(y ? { y } : {}) }]))) });
  item('diesel_generator', 'setpoint:block/diesel_generator_on');
}
recipe('distillation_column', ['IPI', 'TTT', 'IFI'], { I: 'minecraft:iron_block', P: 'setpoint:gas_pipe', T: 'setpoint:tank', F: 'minecraft:blast_furnace' }, 'setpoint:distillation_column');
recipe('diesel_generator', ['III', 'PFP', 'IRI'], { I: 'minecraft:iron_ingot', P: 'minecraft:piston', F: 'minecraft:furnace', R: 'minecraft:redstone' }, 'setpoint:diesel_generator');
recipe('floating_roof_tank', ['IBI', 'BTB', 'IBI'], { I: 'setpoint:hookonium_ingot', B: 'setpoint:hookonium_block', T: 'setpoint:tank' }, 'setpoint:floating_roof_tank');
recipe('ibc', ['BBB', 'BCB', 'SSS'], { B: 'minecraft:iron_bars', C: 'minecraft:cauldron', S: 'minecraft:oak_slab' }, 'setpoint:ibc');

// P&ID board: a thin dark panel in a steel frame; the drawing itself is painted on it by its renderer
texture('pid_board_front', (p) => {
  p.rect(0, 0, 16, 16, '#0c1e3a');
  for (let i = 3; i < 16; i += 4) { p.rect(i, 0, 1, 16, '#10294d'); p.rect(0, i, 16, 1, '#10294d'); } // blueprint grid
  p.rect(0, 0, 16, 1, C.steel3); p.rect(0, 15, 16, 1, C.steel0); p.rect(0, 0, 1, 16, C.steel3); p.rect(15, 0, 1, 16, C.steel0);
});
texture('pid_board_back', (p) => { plate(p, C.steel1); rivets(p, [[2, 2], [12, 2], [2, 12], [12, 12]]); });
M('pid_board', { // the drawing faces north: the board hangs on a wall to the south
  parent: 'minecraft:block/block',
  textures: { front: tex('pid_board_front'), back: tex('pid_board_back'), particle: tex('pid_board_back') },
  elements: [box([0, 0, 15], [16, 16, 16], {
    north: { uv: [0, 0, 16, 16], texture: '#front' }, south: { uv: [0, 0, 16, 16], texture: '#back', cullface: 'south' },
    east: { uv: [0, 0, 1, 16], texture: '#back' }, west: { uv: [15, 0, 16, 16], texture: '#back' },
    up: { uv: [0, 15, 16, 16], texture: '#back' }, down: { uv: [0, 0, 16, 1], texture: '#back' },
  })],
});
BS('pid_board', { variants: Object.fromEntries(Object.entries(yaw).flatMap(([f, y]) => [false, true].map((lit) =>
  [`facing=${f},lit=${lit}`, { model: 'setpoint:block/pid_board', ...(y ? { y } : {}) }]))) });
item('pid_board', 'setpoint:block/pid_board');
recipe('pid_board', ['III', 'IPI'], { I: 'minecraft:iron_ingot', P: 'minecraft:painting' }, 'setpoint:pid_board');

// check valve: a swing-check body on the pipe with an arrow cast on top, pointing the way the liquid may go
texture('check_valve_body', (p) => {
  plate(p, C.steel2);
  p.rect(3, 7, 7, 2, C.cyan);                                   // arrow shaft
  p.rect(10, 5, 1, 6, C.cyan); p.rect(11, 6, 1, 4, C.cyan); p.rect(12, 7, 1, 2, C.cyan); // arrow head
  p.rect(3, 7, 1, 2, C.cyanDark);
  rivets(p, [[1, 1], [13, 1], [1, 13], [13, 13]]);
});
M('check_valve', { // arrow points north (flow along -Z)
  parent: 'minecraft:block/block',
  textures: { pipe: tex('pipe'), body: tex('check_valve_body'), particle: tex('check_valve_body') },
  elements: [
    box([5, 5, 0], [11, 11, 16], {
      north: { uv: [5, 5, 11, 11], texture: '#pipe', cullface: 'north' }, south: { uv: [5, 5, 11, 11], texture: '#pipe', cullface: 'south' },
      east: face([0, 5, 16, 11]), west: face([0, 5, 16, 11]), down: face([5, 0, 11, 16]),
    }),
    box([4, 4, 4], [12, 12, 12], {
      up: { uv: [2, 2, 14, 14], texture: '#body', rotation: 270 }, down: { uv: [2, 2, 14, 14], texture: '#body', rotation: 270 },
      east: { uv: [2, 4, 14, 12], texture: '#body' }, west: { uv: [2, 4, 14, 12], texture: '#body' },
      north: { uv: [4, 4, 12, 12], texture: '#pipe' }, south: { uv: [4, 4, 12, 12], texture: '#pipe' },
    }),
  ],
});
{
  const turn = { north: {}, east: { y: 90 }, south: { y: 180 }, west: { y: 270 }, up: { x: 270 }, down: { x: 90 } };
  BS('check_valve', { variants: Object.fromEntries(Object.entries(turn).map(([f, rot]) => [`facing=${f}`, { model: 'setpoint:block/check_valve', ...rot }])) });
  item('check_valve', 'setpoint:block/check_valve');
}
recipe('check_valve', [' H ', 'CIC'], { H: 'minecraft:iron_trapdoor', C: 'minecraft:copper_ingot', I: 'minecraft:iron_ingot' }, 'setpoint:check_valve');

// drain valve: a small hand valve with an open spout, on the side of a tank; the wheel turns up when it is open
for (const open of [false, true]) {
  M('drain_valve' + (open ? '_open' : ''), { // outlet points north: the tank is to the south
    parent: 'minecraft:block/block',
    textures: { pipe: tex('pipe'), body: tex('hand_valve_body'), wheel: tex('hand_valve_wheel'), stem: tex('hand_valve_stem'), particle: tex('hand_valve_body') },
    elements: [
      box([5, 5, 10], [11, 11, 16], allFaces([5, 5, 11, 11], '#pipe')),
      box([4, 4, 5], [12, 11, 10], allFaces([4, 4, 12, 11], '#body')),
      box([6, 5, 1], [10, 9, 5], allFaces([6, 5, 10, 9], '#pipe')),
      box([7, 3, 1], [9, 5, 3], allFaces([7, 3, 9, 5], '#pipe')),
      box([7, 11, 7], [9, open ? 14 : 12, 9], allFaces([7, 0, 9, 3], '#stem', ['down'])),
      box([4, open ? 14 : 12, 4], [12, open ? 15 : 13, 12], {
        up: { uv: [0, 0, 16, 16], texture: '#wheel' }, down: { uv: [0, 0, 16, 16], texture: '#wheel' },
        north: { uv: [4, 0, 12, 1], texture: '#wheel' }, south: { uv: [4, 0, 12, 1], texture: '#wheel' },
        east: { uv: [4, 0, 12, 1], texture: '#wheel' }, west: { uv: [4, 0, 12, 1], texture: '#wheel' },
      }),
    ],
  });
}
{
  const turn = { north: {}, east: { y: 90 }, south: { y: 180 }, west: { y: 270 }, up: { x: 270 }, down: { x: 90 } };
  const variants = {};
  for (const [facing, rot] of Object.entries(turn)) for (const open of [false, true]) for (const powered of [false, true]) {
    variants[`facing=${facing},open=${open},powered=${powered}`] = { model: 'setpoint:block/drain_valve' + (open || powered ? '_open' : ''), ...rot };
  }
  BS('drain_valve', { variants });
  item('drain_valve', 'setpoint:block/drain_valve');
}
shapeless('drain_valve', ['setpoint:hand_valve', 'minecraft:bucket'], 'setpoint:drain_valve', 1);

// =====================================================================================
// M4 · Pressure: pressure transmitter and safety valve
// =====================================================================================

// pressure transmitter: the transmitter housing with a round gauge on its face
texture('pressure_transmitter_front', (p) => {
  p.rect(0, 0, 16, 16, C.steel1);
  p.rect(4, 1, 8, 1, C.outline); p.rect(3, 2, 10, 1, C.outline); p.rect(2, 3, 12, 6, C.outline); p.rect(3, 9, 10, 1, C.outline); p.rect(4, 10, 8, 1, C.outline);
  p.rect(5, 2, 6, 1, '#e8edf5'); p.rect(4, 3, 8, 1, '#e8edf5'); p.rect(3, 4, 10, 4, '#e8edf5'); p.rect(4, 8, 8, 1, '#e8edf5'); p.rect(5, 9, 6, 1, '#e8edf5'); // dial
  p.set(4, 6, C.steel0); p.set(8, 3, C.steel0); p.set(11, 6, C.steel0);   // scale marks
  p.rect(10, 4, 2, 1, C.magenta);                                          // red zone
  p.set(8, 6, C.outline); p.set(7, 6, C.outline); p.set(6, 5, C.magenta); p.set(5, 4, C.magenta); // needle
  p.rect(4, 12, 8, 2, C.amber); p.rect(5, 13, 6, 1, '#c98a2c');            // tag plate
});
M('pressure_transmitter', {
  parent: 'minecraft:block/block',
  textures: { front: tex('pressure_transmitter_front'), side: tex('transmitter_side'), top: tex('transmitter_top'), particle: tex('transmitter_side') },
  elements: [box([4, 1, 4], [12, 13, 12], {
    north: { uv: [4, 2, 12, 14], texture: '#front' }, south: { uv: [4, 2, 12, 14], texture: '#side' },
    east: { uv: [4, 2, 12, 14], texture: '#side' }, west: { uv: [4, 2, 12, 14], texture: '#side' },
    up: { uv: [4, 4, 12, 12], texture: '#top' }, down: { uv: [4, 4, 12, 12], texture: '#top', cullface: 'down' },
  })],
});
BS('pressure_transmitter', { variants: Object.fromEntries(Object.entries(yaw).map(([f, y]) => [`facing=${f}`, { model: 'setpoint:block/pressure_transmitter', ...(y ? { y } : {}) }])) });
item('pressure_transmitter', 'setpoint:block/pressure_transmitter');
recipe('pressure_transmitter', [' G ', 'ICI', ' I '], { G: 'minecraft:glass_pane', I: 'minecraft:iron_ingot', C: 'minecraft:comparator' }, 'setpoint:pressure_transmitter');

// safety valve: a brass body on a flange, a spring bonnet with a red cap, and a side outlet; the cap pops up when it lifts
texture('safety_valve_body', (p) => {
  p.rect(0, 0, 16, 16, '#b08a3e'); p.rect(0, 0, 16, 1, '#d6b062'); p.rect(0, 15, 16, 1, '#7d5f26');
  p.rect(2, 3, 12, 1, '#7d5f26'); p.rect(2, 12, 12, 1, '#d6b062');
  p.set(2, 2, C.steel4); p.set(13, 2, C.steel4); p.set(2, 13, C.steel4); p.set(13, 13, C.steel4);
});
texture('safety_valve_spring', (p) => {
  p.rect(0, 0, 16, 16, C.steel1);
  for (let y = 0; y < 16; y += 3) { p.rect(0, y, 16, 1, C.steel3); p.rect(0, y + 1, 16, 1, C.steel4); }
});
texture('safety_valve_cap', (p) => {
  p.rect(0, 0, 16, 16, '#b8312f'); p.rect(0, 0, 16, 1, '#d24a45'); p.rect(0, 15, 16, 1, '#7a1f1d');
  p.rect(6, 6, 4, 4, '#7a1f1d'); p.rect(7, 7, 2, 2, C.steel3);
});
for (const lifting of [false, true]) {
  const up = lifting ? 2 : 0;
  M('safety_valve' + (lifting ? '_lifting' : ''), { // points up: the vessel is below
    parent: 'minecraft:block/block',
    textures: { body: tex('safety_valve_body'), spring: tex('safety_valve_spring'), cap: tex('safety_valve_cap'), flange: tex('valve_body'), particle: tex('safety_valve_body') },
    elements: [
      box([3, 0, 3], [13, 2, 13], allFaces([3, 3, 13, 13], '#flange')),
      box([5, 2, 5], [11, 8, 11], allFaces([5, 2, 11, 8], '#body')),
      box([11, 3, 6], [15, 7, 10], allFaces([0, 4, 4, 8], '#body')),
      box([6, 8, 6], [10, 13, 10], allFaces([0, 0, 4, 5], '#spring', ['down'])),
      box([7.5, 13, 7.5], [8.5, 13 + up + 0.01, 8.5], allFaces([0, 0, 1, 2], '#spring', ['down'])),
      box([5, 13 + up, 5], [11, 15 + up, 11], allFaces([5, 5, 11, 11], '#cap')),
    ],
  });
}
{
  const turn = { up: {}, down: { x: 180 }, north: { x: 90 }, south: { x: 90, y: 180 }, east: { x: 90, y: 90 }, west: { x: 90, y: 270 } };
  const variants = {};
  for (const [facing, rot] of Object.entries(turn)) for (const lifting of [false, true]) {
    variants[`facing=${facing},lifting=${lifting}`] = { model: 'setpoint:block/safety_valve' + (lifting ? '_lifting' : ''), ...rot };
  }
  BS('safety_valve', { variants });
  item('safety_valve', 'setpoint:block/safety_valve');
}
recipe('safety_valve', [' H ', 'CIC'], { H: 'minecraft:tripwire_hook', C: 'minecraft:copper_ingot', I: 'minecraft:iron_ingot' }, 'setpoint:safety_valve');

// =====================================================================================
// P&IDs: the blank sheet and the drawn sheet
// =====================================================================================
const BP = { e: '#0b1f3d', f: '#1d4c8f', g: '#28599f', w: '#e8f1ff', c: '#2de2e6', k: '#173a6e' };
function blueprintSheet(p) {
  p.rect(2, 1, 12, 14, BP.e);
  p.rect(3, 2, 10, 12, BP.f);
  for (let x = 5; x < 13; x += 3) p.rect(x, 2, 1, 12, BP.g);
  for (let y = 4; y < 14; y += 3) p.rect(3, y, 10, 1, BP.g);
  p.rect(11, 1, 3, 3, null); p.set(11, 1, BP.e); p.rect(12, 2, 2, 2, null); // folded corner
  p.rect(11, 2, 1, 2, BP.k); p.rect(12, 3, 1, 1, BP.k); p.set(13, 4, BP.e); p.set(12, 4, BP.e); p.set(11, 4, BP.e);
}
texture('blank_pid', blueprintSheet, 'item');
texture('pid', (p) => {
  blueprintSheet(p);
  // a tank, a pipe, a pump and a valve, drawn in white
  p.rect(4, 4, 3, 5, BP.w); p.rect(5, 6, 1, 2, BP.c);                  // tank with liquid
  p.rect(7, 8, 3, 1, BP.w);                                            // pipe
  p.rect(10, 7, 2, 3, BP.w); p.set(10, 7, BP.f);                       // pump
  p.rect(9, 11, 3, 1, BP.w); p.set(10, 10, BP.w);                      // valve stem and body
  p.set(8, 12, BP.c); p.set(6, 12, BP.c);                              // dashed signal line
}, 'item');
for (const name of ['blank_pid', 'pid']) {
  json(join(ASSETS, 'models/item', name + '.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'setpoint:item/' + name } });
  item(name, 'setpoint:item/' + name);
}
shapeless('blank_pid', ['minecraft:paper', 'minecraft:blue_dye'], 'setpoint:blank_pid', 1);

// =====================================================================================
// Control room: overview screens, alarm beacon, ESD button, radios, desks, screens, chair, floor, ceiling, clock,
// filing cabinet, printer, coffee, exit sign, blast door and wired glass
// =====================================================================================
const CR = { bezel: '#2a2e36', screen: '#0b0e14', bar: '#1e222a', back: '#24272d', laminate: '#c9cdd3', laminateHi: '#dadde2', laminateLo: '#b3b8bf',
  edge: '#3e434b', panel: '#8b919a', panelLo: '#737981', key: '#1e2024', keyHi: '#4a4e56', amber: '#ffb547', amberLo: '#d98a1e', amberDull: '#a8742a',
  yellow: '#f2c12e', yellowLo: '#b8901c', red: '#d6262b', redHi: '#ff6b6e', redLo: '#8e1418', green: '#11a04a', greenLo: '#0b6b32', white: '#f4f6f8' };
const sprite = (p, rows, ox, oy, colour) => rows.forEach((row, y) => [...row].forEach((ch, x) => { if (ch === '#') p.set(ox + x, oy + y, colour); }));
const mirror = (rows) => rows.map((r) => [...r].reverse().join(''));
const crAll = (t, uv = [0, 0, 16, 16], extra = {}) => Object.fromEntries(['north', 'south', 'east', 'west', 'up', 'down'].map((f) => [f, { uv, texture: t, ...extra }]));

// ---------- overview screen: a dark panel; the renderer paints the live page over its front ----------
texture('overview_screen_front', (p) => {
  p.rect(0, 0, 16, 16, CR.bezel); p.rect(1, 1, 14, 14, CR.screen); p.rect(1, 1, 14, 2, CR.bar); p.rect(2, 1, 5, 1, '#7c869d');
  for (const [x, y, c] of [[2, 4, C.cyan], [6, 4, C.cyan], [10, 4, C.amber], [2, 8, C.cyan], [6, 8, C.magenta], [10, 8, C.cyan], [2, 12, C.cyan], [6, 12, C.cyan]]) {
    p.rect(x, y, 4, 3, '#161b24'); p.rect(x, y, 1, 3, c); p.rect(x + 1, y + 1, 2, 1, '#d9deea');
  }
});
texture('screen_back', (p) => { p.rect(0, 0, 16, 16, CR.back); p.rect(0, 0, 16, 1, '#33373f'); for (let y = 4; y < 13; y += 2) p.rect(4, y, 8, 1, '#1b1e23'); });
M('overview_screen', { parent: 'minecraft:block/block', textures: { front: tex('overview_screen_front'), back: tex('screen_back'), particle: tex('screen_back') }, elements: [
  box([0, 0, 14], [16, 16, 16], { north: { uv: [0, 0, 16, 16], texture: '#front' }, south: { uv: [0, 0, 16, 16], texture: '#back', cullface: 'south' },
    east: { uv: [14, 0, 16, 16], texture: '#back' }, west: { uv: [0, 0, 2, 16], texture: '#back' }, up: { uv: [0, 14, 16, 16], texture: '#back' }, down: { uv: [0, 14, 16, 16], texture: '#back' } }),
] });
BS('overview_screen', { variants: Object.fromEntries(Object.entries(yaw).map(([f, y]) => [`facing=${f}`, { model: 'setpoint:block/overview_screen', ...(y ? { y } : {}) }])) });
item('overview_screen', 'setpoint:block/overview_screen');

// ---------- alarm beacon: an amber dome on a dark base, pointing up; the blockstate turns it like the lamp ----------
texture('beacon_base', (p) => { p.rect(0, 0, 16, 16, '#2f333a'); p.rect(0, 0, 16, 1, '#454a53'); for (const [x, y] of [[2, 2], [13, 2], [2, 13], [13, 13]]) p.set(x, y, '#6d737d'); });
const dome = (p, band) => {
  p.rect(0, 0, 16, 16, band === null ? CR.amberDull : CR.amberLo);
  p.rect(2, 0, 12, 16, band === null ? '#b98236' : CR.amber);
  for (let y = 0; y < 16; y += 4) p.rect(0, y, 16, 1, band === null ? '#93652a' : '#e89a2a'); // the ribs of the lens
  if (band !== null) for (let k = 0; k < 4; k++) p.rect((band + k) % 16, 0, 1, 16, k === 1 || k === 2 ? '#fff4c2' : '#ffd27a'); // the mirror's beam sweeping round
  else p.rect(4, 2, 2, 5, '#d9a75e');
};
texture('beacon_dome', (p) => dome(p, null));
animated('beacon_dome_on', 1, Array.from({ length: 16 }, (_, f) => (p) => dome(p, f)));
for (const on of [false, true]) {
  const glow = on ? { light_emission: 15 } : {};
  M('alarm_beacon' + (on ? '_on' : ''), { parent: 'minecraft:block/block', ambientocclusion: false,
    textures: { base: tex('beacon_base'), dome: tex(on ? 'beacon_dome_on' : 'beacon_dome'), particle: tex('beacon_base') }, elements: [
      box([4, 0, 4], [12, 2, 12], crAll('#base')),
      { ...box([5, 2, 5], [11, 9, 11], crAll('#dome', [0, 0, 16, 16])), ...glow },
      { ...box([6, 9, 6], [10, 10, 10], crAll('#dome', [4, 4, 12, 12])), ...glow },
    ] });
}
const sixWay = { up: {}, down: { x: 180 }, north: { x: 90 }, south: { x: 90, y: 180 }, east: { x: 90, y: 90 }, west: { x: 90, y: 270 } };
BS('alarm_beacon', { variants: Object.fromEntries(Object.entries(sixWay).flatMap(([f, rot]) => ['false', 'true'].flatMap((alarm) => ['false', 'true'].map((silenced) =>
  [`alarm=${alarm},facing=${f},silenced=${silenced}`, { model: 'setpoint:block/alarm_beacon' + (alarm === 'true' ? '_on' : ''), ...rot }])))) });
item('alarm_beacon', 'setpoint:block/alarm_beacon');

// ---------- ESD button: a red mushroom in a yellow box, pointing up; pressed in, the mushroom sits low ----------
texture('esd_box', (p) => { p.rect(0, 0, 16, 16, CR.yellow); p.rect(0, 15, 16, 1, CR.yellowLo); p.rect(15, 0, 1, 16, CR.yellowLo); p.rect(0, 0, 16, 1, '#f8d769'); });
texture('esd_face', (p) => { // the top of the box: a black ring round the button, the words in two black bars
  p.rect(0, 0, 16, 16, CR.yellow);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) { const d = Math.hypot(x - 7.5, y - 7.5); if (d > 4.2 && d < 5.6) p.set(x, y, '#1f1f1f'); }
  p.rect(4, 1, 8, 1, '#1f1f1f'); p.rect(4, 14, 8, 1, '#1f1f1f');
});
texture('esd_red', (p) => { p.rect(0, 0, 16, 16, CR.red); p.rect(0, 12, 16, 4, CR.redLo); p.rect(3, 3, 4, 3, CR.redHi); });
for (const pressed of [false, true]) {
  const top = pressed ? 6.5 : 9;
  M('esd_button' + (pressed ? '_pressed' : ''), { parent: 'minecraft:block/block', textures: { box: tex('esd_box'), face: tex('esd_face'), red: tex('esd_red'), particle: tex('esd_box') }, elements: [
    box([3, 0, 3], [13, 5, 13], { ...crAll('#box', [3, 11, 13, 16]), up: { uv: [3, 3, 13, 13], texture: '#face' }, down: { uv: [3, 3, 13, 13], texture: '#box', cullface: 'down' } }),
    box([7, 5, 7], [9, top - 2, 9], crAll('#red', [6, 12, 8, 14])),
    box([5, top - 2, 5], [11, top, 11], crAll('#red')),
  ] });
}
BS('esd_button', { variants: Object.fromEntries(Object.entries(sixWay).flatMap(([f, rot]) => ['false', 'true'].map((pressed) =>
  [`facing=${f},pressed=${pressed}`, { model: 'setpoint:block/esd_button' + (pressed === 'true' ? '_pressed' : ''), ...rot }]))) });
item('esd_button', 'setpoint:block/esd_button');

// ---------- radio charger: three radios standing in a charging rack ----------
texture('radio_body', (p) => {
  p.rect(0, 0, 16, 16, '#1f2124'); p.rect(0, 0, 16, 3, '#e8731c'); p.rect(0, 3, 16, 1, '#a9500f');
  for (let y = 6; y < 12; y += 2) for (let x = 3; x < 13; x += 2) p.set(x, y, '#3a3d42'); // speaker grille
  p.rect(5, 13, 6, 2, '#5f9c6b'); // display
});
texture('radio_rack', (p) => { p.rect(0, 0, 16, 16, '#2b2e33'); p.rect(0, 0, 16, 1, '#40444b'); for (const x of [3, 7, 11]) { p.set(x, 8, '#38e36a'); p.set(x + 1, 8, '#38e36a'); } });
M('radio_charger', { parent: 'minecraft:block/block', textures: { rack: tex('radio_rack'), radio: tex('radio_body'), antenna: tex('beacon_base'), particle: tex('radio_rack') }, elements: [
  box([2, 0, 5], [14, 3, 11], { ...crAll('#rack', [2, 5, 14, 11]), north: { uv: [2, 5, 14, 11], texture: '#rack' }, down: { uv: [2, 5, 14, 11], texture: '#rack', cullface: 'down' } }),
  ...[3, 6.75, 10.5].flatMap((x) => [
    box([x, 3, 6.5], [x + 2.5, 9, 9], { north: { uv: [3, 0, 13, 16], texture: '#radio' }, south: { uv: [3, 0, 13, 16], texture: '#radio' }, east: { uv: [0, 0, 4, 16], texture: '#radio' },
      west: { uv: [0, 0, 4, 16], texture: '#radio' }, up: { uv: [0, 0, 4, 3], texture: '#radio' } }),
    box([x + 0.5, 9, 7.25], [x + 1.25, 12, 8], crAll('#antenna', [0, 0, 1, 3])),
  ]),
] });
BS('radio_charger', { variants: Object.fromEntries(Object.entries(yaw).map(([f, y]) => [`facing=${f}`, { model: 'setpoint:block/radio_charger', ...(y ? { y } : {}) }])) });
item('radio_charger', 'setpoint:block/radio_charger');

// ---------- desks: a laminate top, a panel at the back, keyboard and mouse; side panels where no desk joins ----------
texture('desk_top', (p) => {
  p.rect(0, 0, 16, 16, CR.laminate);
  for (const [x, y] of [[3, 2], [11, 5], [6, 9], [13, 12], [2, 13], [8, 14]]) p.set(x, y, CR.laminateHi);
  for (const [x, y] of [[9, 3], [4, 6], [12, 10], [7, 12]]) p.set(x, y, CR.laminateLo);
});
texture('desk_edge', (p) => { p.rect(0, 0, 16, 16, CR.edge); p.rect(0, 0, 16, 1, '#5a5f68'); });
texture('desk_panel', (p) => { p.rect(0, 0, 16, 16, CR.panel); p.rect(0, 0, 16, 1, '#a4aab2'); p.rect(0, 15, 16, 1, CR.panelLo); for (let x = 3; x < 13; x += 2) p.rect(x, 4, 1, 4, CR.panelLo); });
texture('keyboard', (p) => { p.rect(0, 0, 16, 16, CR.key); for (let y = 1; y < 16; y += 3) for (let x = 1; x < 16; x += 2) p.set(x, y, CR.keyHi); });
const deskTex = { top: tex('desk_top'), edge: tex('desk_edge'), panel: tex('desk_panel'), key: tex('keyboard'), particle: tex('desk_panel') };
const DESK_TOP = 16; // ControlDeskBlock.TOP: a full block, so what you put on it stands on it
const deskTop = box([0, DESK_TOP - 2, 0], [16, DESK_TOP, 16], { up: { uv: [0, 0, 16, 16], texture: '#top' }, down: { uv: [0, 0, 16, 16], texture: '#panel' },
  north: { uv: [0, 3, 16, 5], texture: '#edge' }, south: { uv: [0, 3, 16, 5], texture: '#edge' }, east: { uv: [0, 3, 16, 5], texture: '#edge' }, west: { uv: [0, 3, 16, 5], texture: '#edge' } });
const backPanel = box([0, 0, 14], [16, 14, 15.5], crAll('#panel', [0, 2, 16, 16]));
const keyboard = [box([3, 16, 2], [11, 16.5, 5], crAll('#key', [0, 0, 8, 3])), box([12, 16, 2.5], [13.5, 16.75, 4.5], crAll('#edge', [0, 0, 2, 2]))];
const sideLeft = box([14.5, 0, 0.5], [16, 14, 15.5], crAll('#panel', [0, 2, 15, 16]));   // the operator's left: east, for a desk facing north
const sideRight = box([0, 0, 0.5], [1.5, 14, 15.5], crAll('#panel', [0, 2, 15, 16]));
M('control_desk', { parent: 'minecraft:block/block', textures: deskTex, elements: [deskTop, backPanel, ...keyboard] });
M('control_desk_side_left', { textures: deskTex, elements: [sideLeft] });
M('control_desk_side_right', { textures: deskTex, elements: [sideRight] });
M('control_desk_item', { parent: 'minecraft:block/block', textures: deskTex, elements: [deskTop, backPanel, ...keyboard, sideLeft, sideRight] });
BS('control_desk', { multipart: Object.entries(yaw).flatMap(([f, y]) => [
  { when: { facing: f }, apply: { model: 'setpoint:block/control_desk', ...(y ? { y } : {}) } },
  { when: { facing: f, left: 'false' }, apply: { model: 'setpoint:block/control_desk_side_left', ...(y ? { y } : {}) } },
  { when: { facing: f, right: 'false' }, apply: { model: 'setpoint:block/control_desk_side_right', ...(y ? { y } : {}) } },
]) });
item('control_desk', 'setpoint:block/control_desk_item');
M('desk_corner', { parent: 'minecraft:block/block', textures: deskTex, elements: [deskTop, backPanel, box([0, 0, 0.5], [1.5, 14, 14], crAll('#panel', [0, 2, 14, 16]))] });
BS('desk_corner', { variants: Object.fromEntries(Object.entries(yaw).map(([f, y]) => [`facing=${f}`, { model: 'setpoint:block/desk_corner', ...(y ? { y } : {}) }])) });
item('desk_corner', 'setpoint:block/desk_corner');

// ---------- operator screens: three monitors on one stand, each with its own moving DCS picture ----------
const dcsScreen = (draw) => (p) => { p.rect(0, 0, 16, 16, '#10141c'); p.rect(1, 3, 14, 1, '#1e222a'); draw(p); };
animated('monitor_mimic', 10, Array.from({ length: 8 }, (_, f) => dcsScreen((p) => { // a tank filling and emptying, a pump, a valve
  const level = [3, 4, 5, 6, 6, 5, 4, 3][f];
  p.rect(2, 5, 4, 7, '#7c869d'); p.rect(3, 6, 2, 5, '#10141c'); p.rect(3, 11 - level + 1, 2, level - 1, C.cyan);
  p.rect(6, 10, 7, 1, C.cyan); p.rect(8, 9, 2, 3, C.cyan); p.set(8, 9, '#10141c'); p.rect(12, 6, 1, 5, C.cyan);
  p.set(11, 9, '#d9deea'); p.set(13, 9, '#d9deea'); p.set(12, 8, f % 4 < 2 ? C.amber : '#d9deea');
})));
animated('monitor_trend', 10, Array.from({ length: 8 }, (_, f) => dcsScreen((p) => { // PV wandering round SP, scrolling left
  for (let x = 1; x < 15; x += 3) for (let y = 5; y < 13; y += 3) p.set(x, y, '#1c2431');
  p.rect(1, 8, 14, 1, '#7a2b4b');
  for (let x = 1; x < 15; x++) p.set(x, Math.round(8 + 2.4 * Math.sin((x + f * 2) / 2.2)), C.cyan);
})));
animated('monitor_alarms', 10, Array.from({ length: 2 }, (_, f) => dcsScreen((p) => { // the alarm list, the newest one flashing
  p.rect(2, 5, 11, 1, f === 0 ? C.magenta : '#d9deea'); p.rect(2, 7, 9, 1, C.amber); p.rect(2, 9, 10, 1, '#d9deea'); p.rect(2, 11, 7, 1, '#7c869d');
})));
texture('monitor_back', (p) => { p.rect(0, 0, 16, 16, '#18191c'); p.rect(0, 0, 16, 1, '#2a2c30'); });
{
  const monitor = (x0, x1, screen, turn) => ({
    ...box([x0, 3, 9], [x1, 10, 9.75], { north: { uv: [1, 3, 15, 13], texture: '#' + screen }, south: { uv: [0, 0, 16, 16], texture: '#back' },
      east: { uv: [0, 0, 1, 7], texture: '#back' }, west: { uv: [0, 0, 1, 7], texture: '#back' }, up: { uv: [0, 0, 16, 1], texture: '#back' }, down: { uv: [0, 0, 16, 1], texture: '#back' } }),
    light_emission: 12,
    ...(turn ? { rotation: { origin: [turn < 0 ? x1 : x0, 3, 9.375], x: 0, y: turn, z: 0 } } : {}), // turned in towards the operator
  });
  M('desk_screens', { parent: 'minecraft:block/block', ambientocclusion: false,
    textures: { mimic: tex('monitor_mimic'), trend: tex('monitor_trend'), alarms: tex('monitor_alarms'), back: tex('monitor_back'), particle: tex('monitor_back') }, elements: [
      box([3, 0, 9], [13, 0.5, 12], crAll('#back')), box([7.5, 0.5, 10], [8.5, 3.5, 11], crAll('#back')), box([2, 3.5, 10], [14, 4.5, 10.75], crAll('#back')),
      monitor(5, 11, 'trend', 0), monitor(-0.5, 5, 'mimic', -22.5), monitor(11, 16.5, 'alarms', 22.5),
    ] });
}
BS('desk_screens', { variants: Object.fromEntries(Object.entries(yaw).map(([f, y]) => [`facing=${f}`, { model: 'setpoint:block/desk_screens', ...(y ? { y } : {}) }])) });
item('desk_screens', 'setpoint:block/desk_screens');

// ---------- 24-hour chair: five-star base, gas lift, cushion, armrests, a high back with a headrest (the back to the north) ----------
texture('chair_fabric', (p) => { for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) p.set(x, y, (x + y) % 2 === 0 ? '#1d1f24' : '#272a30'); p.rect(0, 0, 16, 1, '#33363d'); });
texture('chair_frame', (p) => { p.rect(0, 0, 16, 16, '#26282d'); p.rect(0, 0, 16, 1, '#3a3d44'); });
texture('chair_chrome', (p) => { p.rect(0, 0, 16, 16, '#9aa1ab'); p.rect(0, 0, 16, 2, '#c9ced5'); p.rect(0, 12, 16, 4, '#6f757e'); });
{
  const ct = { fabric: tex('chair_fabric'), frame: tex('chair_frame'), chrome: tex('chair_chrome'), particle: tex('chair_fabric') };
  const s = (from, to, t) => box(from, to, crAll('#' + t));
  M('office_chair', { parent: 'minecraft:block/block', textures: ct, elements: [
    s([1.5, 0.5, 7.25], [14.5, 1.5, 8.75], 'frame'), s([7.25, 0.5, 1.5], [8.75, 1.5, 14.5], 'frame'),               // the star
    ...[[1.5, 7.5], [13.5, 7.5], [7.5, 1.5], [7.5, 13.5]].map(([x, z]) => s([x, 0, z], [x + 1, 0.5, z + 1], 'chrome')),  // castors
    s([7.25, 1.5, 7.25], [8.75, 8.5, 8.75], 'chrome'),                                                                // gas lift
    s([3, 8.5, 3], [13, 11, 13], 'fabric'),                                                                          // cushion (OfficeChairBlock.SEAT_HEIGHT)
    s([2.5, 11, 6], [3.5, 13.5, 7], 'frame'), s([12.5, 11, 6], [13.5, 13.5, 7], 'frame'),                             // armrest posts
    s([2, 13.5, 5], [4, 14.5, 11], 'frame'), s([12, 13.5, 5], [14, 14.5, 11], 'frame'),                               // armrests
    s([7, 8.5, 2], [9, 12.5, 3.5], 'frame'),                                                                          // back spine
    s([3.5, 12, 1.5], [12.5, 24, 3.5], 'fabric'),                                                                    // high back
    s([5, 24.5, 1.75], [11, 27.5, 3.75], 'fabric'), s([7.5, 24, 2.25], [8.5, 24.5, 3.25], 'chrome'),                  // headrest
  ] });
}
BS('office_chair', { variants: Object.fromEntries(Object.entries(yaw).map(([fa, y]) => [`facing=${fa}`, { model: 'setpoint:block/office_chair', ...(y ? { y } : {}) }])) });
item('office_chair', 'setpoint:block/office_chair');

// ---------- raised floor, ceiling ----------
texture('raised_floor', (p) => {
  p.rect(0, 0, 16, 16, '#b4b9c0'); p.rect(0, 0, 16, 1, '#cfd3d8'); p.rect(0, 0, 1, 16, '#cfd3d8'); p.rect(0, 15, 16, 1, '#7d838c'); p.rect(15, 0, 1, 16, '#7d838c');
  for (const [x, y] of [[4, 3], [11, 6], [6, 10], [12, 12], [3, 13]]) p.set(x, y, '#a6abb3');
});
texture('raised_floor_vent', (p) => {
  p.rect(0, 0, 16, 16, '#b4b9c0'); p.rect(0, 0, 16, 1, '#cfd3d8'); p.rect(0, 0, 1, 16, '#cfd3d8'); p.rect(0, 15, 16, 1, '#7d838c'); p.rect(15, 0, 1, 16, '#7d838c');
  for (let y = 3; y < 13; y += 3) for (let x = 3; x < 13; x += 3) p.rect(x, y, 2, 2, '#3a3f46');
});
texture('raised_floor_side', (p) => { p.rect(0, 0, 16, 16, '#3b4048'); p.rect(0, 0, 16, 2, '#b4b9c0'); p.rect(0, 2, 16, 1, '#7d838c'); p.rect(7, 3, 2, 13, '#8a9099'); p.rect(5, 14, 6, 2, '#8a9099'); });
texture('ceiling_panel', (p) => {
  p.rect(0, 0, 16, 16, '#e9eaec'); p.rect(0, 15, 16, 1, '#b9bdc3'); p.rect(15, 0, 1, 16, '#b9bdc3');
  for (const [x, y] of [[2, 3], [7, 2], [12, 4], [4, 8], [10, 9], [13, 12], [3, 12], [8, 13], [6, 6]]) p.set(x, y, '#c8cbd0');
});
texture('ceiling_light', (p) => {
  p.rect(0, 0, 16, 16, '#d5d8dc'); p.rect(2, 2, 12, 12, '#fffef4');
  for (let k = 4; k < 14; k += 3) { p.rect(2, k, 12, 1, '#e6e3d3'); p.rect(k, 2, 1, 12, '#e6e3d3'); }
});
for (const name of ['raised_floor', 'raised_floor_vent']) {
  M(name, { parent: 'minecraft:block/cube_bottom_top', textures: { top: tex(name), bottom: tex('raised_floor_side'), side: tex('raised_floor_side') } });
  BS(name, { variants: { '': { model: 'setpoint:block/' + name } } });
  item(name, 'setpoint:block/' + name);
}
M('ceiling_panel', { parent: 'minecraft:block/cube_all', textures: { all: tex('ceiling_panel') } });
BS('ceiling_panel', { variants: { '': { model: 'setpoint:block/ceiling_panel' } } });
item('ceiling_panel', 'setpoint:block/ceiling_panel');
M('ceiling_light', { parent: 'minecraft:block/cube_bottom_top', textures: { top: tex('ceiling_panel'), bottom: tex('ceiling_light'), side: tex('ceiling_panel') } });
BS('ceiling_light', { variants: { '': { model: 'setpoint:block/ceiling_light' } } });
item('ceiling_light', 'setpoint:block/ceiling_light');

// ---------- wall clock: a round white face (its corners see-through); the renderer turns the hands ----------
texture('clock_face', (p) => {
  for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) {
    const d = Math.hypot(x - 7.5, y - 7.5);
    if (d < 5.2) p.set(x, y, '#f4f6f8'); else if (d < 6.3) p.set(x, y, '#23262e');
  }
  for (const [x, y] of [[7, 3], [8, 3], [12, 7], [12, 8], [7, 12], [8, 12], [3, 7], [3, 8]]) p.set(x, y, '#23262e'); // 12, 3, 6, 9
  for (const [x, y] of [[10, 4], [11, 5], [11, 10], [10, 11], [5, 11], [4, 10], [4, 5], [5, 4]]) p.set(x, y, '#8a9099');
});
texture('clock_rim', (p) => p.rect(0, 0, 16, 16, '#23262e'));
texture('clock_hand', (p) => p.rect(0, 0, 16, 16, '#141519'));
M('wall_clock', { parent: 'minecraft:block/block', textures: { face: tex('clock_face'), rim: tex('clock_rim'), particle: tex('clock_rim') }, elements: [
  box([2, 2, 15], [14, 14, 16], { north: { uv: [2, 2, 14, 14], texture: '#face' }, south: { uv: [2, 2, 14, 14], texture: '#rim' } }),
  box([3.5, 3.5, 15.5], [12.5, 12.5, 16], { east: { uv: [0, 0, 1, 9], texture: '#rim' }, west: { uv: [0, 0, 1, 9], texture: '#rim' }, up: { uv: [0, 0, 9, 1], texture: '#rim' }, down: { uv: [0, 0, 9, 1], texture: '#rim' } }),
] });
// the hands (parts of the control-room model block), drawn on a face looking south, pointing up from the middle
const handFaces = crAll('#hand', [0, 0, 1, 1]);
const hourHand = [box([7.5, 8, 0], [8.5, 11.5, 0.5], handFaces), box([7.25, 7.25, 0], [8.75, 8.75, 0.6], handFaces)];
const minuteHand = [box([7.6, 8, 0], [8.4, 13, 0.5], handFaces)];
M('control_room_hour_hand', { parent: 'minecraft:block/block', textures: { hand: tex('clock_hand'), particle: tex('clock_hand') }, elements: hourHand });
M('control_room_minute_hand', { parent: 'minecraft:block/block', textures: { hand: tex('clock_hand'), particle: tex('clock_hand') }, elements: minuteHand });
BS('control_room_model', { variants: Object.fromEntries(Array.from({ length: 16 }, (_, i) => [`part=${i}`, { model: 'setpoint:block/control_room_' + (i === 1 ? 'minute_hand' : 'hour_hand') }])) });
// in the inventory: a flat clock at ten past ten, as on every clock in a shop
texture('wall_clock', (p) => {
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    const d = Math.hypot(x - 7.5, y - 7.5);
    if (d < 6.4) p.set(x, y, '#f4f6f8'); else if (d < 7.6) p.set(x, y, '#23262e');
  }
  for (const [x, y] of [[7, 2], [8, 2], [13, 7], [13, 8], [7, 13], [8, 13], [2, 7], [2, 8]]) p.set(x, y, '#23262e');
  for (const [x, y] of [[7, 7], [6, 6], [5, 5], [8, 7], [9, 6], [10, 5], [11, 4]]) p.set(x, y, '#141519'); // the hands, on 10 and 2
  p.set(8, 8, '#c7322f');
}, 'item');
json(join(ASSETS, 'models/item', 'wall_clock.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'setpoint:item/wall_clock' } });
BS('wall_clock', { variants: Object.fromEntries(Object.entries(yaw).map(([f, y]) => [`facing=${f}`, { model: 'setpoint:block/wall_clock', ...(y ? { y } : {}) }])) });
item('wall_clock', 'setpoint:item/wall_clock');

// ---------- filing cabinet, printer, coffee machine ----------
texture('cabinet_front', (p) => {
  p.rect(0, 0, 16, 16, '#8e959f');
  for (let d = 0; d < 4; d++) {
    const y = d * 4; p.rect(0, y, 16, 1, '#6c727b'); p.rect(1, y + 1, 14, 1, '#a3aab3');
    p.rect(6, y + 2, 4, 1, '#d0d4da'); p.rect(2, y + 1, 3, 2, '#e9eaec'); p.set(2, y + 2, '#b9bdc3');
  }
});
texture('cabinet_side', (p) => { p.rect(0, 0, 16, 16, '#868d97'); p.rect(0, 0, 16, 1, '#9da4ad'); p.rect(15, 0, 1, 16, '#6c727b'); });
M('filing_cabinet', { parent: 'minecraft:block/orientable', textures: { front: tex('cabinet_front'), side: tex('cabinet_side'), top: tex('cabinet_side') } });
BS('filing_cabinet', { variants: Object.fromEntries(Object.entries(yaw).map(([f, y]) => [`facing=${f}`, { model: 'setpoint:block/filing_cabinet', ...(y ? { y } : {}) }])) });
item('filing_cabinet', 'setpoint:block/filing_cabinet');

texture('printer_body', (p) => { p.rect(0, 0, 16, 16, '#d9dce0'); p.rect(0, 0, 16, 1, '#eceef0'); p.rect(0, 15, 16, 1, '#a9adb3'); });
texture('printer_front', (p) => {
  p.rect(0, 0, 16, 16, '#d9dce0'); p.rect(0, 0, 16, 1, '#eceef0');
  p.rect(2, 5, 12, 2, '#2a2d33'); p.rect(3, 5, 10, 1, '#f4f6f8');           // the paper coming out
  p.rect(11, 9, 3, 2, '#2f6d4a'); p.set(12, 9, '#7ee0a3'); p.set(3, 10, C.cyan); p.set(5, 10, '#6f757e'); p.set(7, 10, '#6f757e');
});
texture('printer_top', (p) => { p.rect(0, 0, 16, 16, '#d9dce0'); p.rect(2, 3, 12, 6, '#f4f6f8'); p.rect(2, 3, 12, 1, '#c9ccd1'); p.rect(2, 11, 12, 2, '#2a2d33'); });
M('printer', { parent: 'minecraft:block/block', textures: { body: tex('printer_body'), front: tex('printer_front'), top: tex('printer_top'), particle: tex('printer_body') }, elements: [
  box([1, 0, 3], [15, 7, 13], { north: { uv: [1, 4, 15, 11], texture: '#front' }, south: { uv: [1, 9, 15, 16], texture: '#body' }, east: { uv: [3, 9, 13, 16], texture: '#body' },
    west: { uv: [3, 9, 13, 16], texture: '#body' }, up: { uv: [1, 1, 15, 15], texture: '#top' }, down: { uv: [1, 3, 15, 13], texture: '#body', cullface: 'down' } }),
  box([3, 7, 8], [13, 8, 12], crAll('#body', [3, 0, 13, 1])),                // the paper feed at the back
  box([3, 2, 1.5], [13, 2.5, 3], crAll('#top', [2, 3, 12, 5])),              // the output tray
] });
BS('printer', { variants: Object.fromEntries(Object.entries(yaw).map(([f, y]) => [`facing=${f}`, { model: 'setpoint:block/printer', ...(y ? { y } : {}) }])) });
item('printer', 'setpoint:block/printer');

texture('coffee_front', (p) => {
  p.rect(0, 0, 16, 16, '#1b1b1d'); p.rect(0, 0, 16, 1, '#2c2c30');
  p.rect(3, 2, 10, 3, '#0e2a33'); p.rect(4, 3, 5, 1, C.cyan);                 // the display
  p.rect(4, 6, 8, 7, '#2c2c30'); p.rect(7, 6, 2, 2, '#9aa1ab');               // the alcove and the spout
  p.rect(12, 3, 1, 1, '#38e36a');
});
texture('coffee_side', (p) => { p.rect(0, 0, 16, 16, '#1b1b1d'); p.rect(0, 0, 16, 1, '#2c2c30'); p.rect(0, 10, 16, 1, '#9aa1ab'); });
texture('coffee_top', (p) => { p.rect(0, 0, 16, 16, '#1b1b1d'); p.rect(4, 4, 6, 6, '#3a2a1e'); p.rect(5, 5, 4, 4, '#5a3f2a'); });
texture('coffee_cup', (p) => { p.rect(0, 0, 16, 16, '#f4f6f8'); p.rect(0, 0, 16, 4, '#4a2c17'); });
M('coffee_machine', { parent: 'minecraft:block/block', textures: { front: tex('coffee_front'), side: tex('coffee_side'), top: tex('coffee_top'), cup: tex('coffee_cup'), steel: tex('chair_chrome'), particle: tex('coffee_side') }, elements: [
  box([3, 0, 4], [13, 14, 14], { north: { uv: [3, 2, 13, 16], texture: '#front' }, south: { uv: [3, 2, 13, 16], texture: '#side' }, east: { uv: [2, 2, 12, 16], texture: '#side' },
    west: { uv: [2, 2, 12, 16], texture: '#side' }, up: { uv: [3, 3, 13, 13], texture: '#top' }, down: { uv: [3, 3, 13, 13], texture: '#side', cullface: 'down' } }),
  box([4.5, 0, 2], [11.5, 1, 4], crAll('#steel')),                            // the drip tray
  box([6.5, 1, 2.5], [9.5, 4, 4], { ...crAll('#cup', [0, 0, 3, 3]), up: { uv: [0, 0, 3, 2], texture: '#cup' } }), // a full cup
] });
BS('coffee_machine', { variants: Object.fromEntries(Object.entries(yaw).map(([f, y]) => [`facing=${f}`, { model: 'setpoint:block/coffee_machine', ...(y ? { y } : {}) }])) });
item('coffee_machine', 'setpoint:block/coffee_machine');

// ---------- emergency exit sign (ISO 7010 style): the running man, the door, an arrow ----------
const RUNNER = ['....#.', '..###.', '.#.#.#', '...##.', '..#..#', '.#....'];
const ARROW_LEFT = ['..#', '.##', '###', '.##', '..#'], ARROW_DOWN = ['..#..', '..#..', '#.#.#', '.###.', '..#..'];
const door = (p, x) => { p.rect(x, 5, 3, 6, CR.white); p.rect(x + 1, 6, 1, 4, CR.green); };
const SIGNS = {
  none: (p) => { sprite(p, RUNNER, 4, 5, CR.white); door(p, 11); },
  left: (p) => { sprite(p, ARROW_LEFT, 2, 6, CR.white); sprite(p, mirror(RUNNER), 5, 5, CR.white); door(p, 11); },
  right: (p) => { door(p, 2); sprite(p, RUNNER, 5, 5, CR.white); sprite(p, mirror(ARROW_LEFT), 11, 6, CR.white); },
  down: (p) => { sprite(p, ARROW_DOWN, 1, 6, CR.white); sprite(p, RUNNER, 6, 5, CR.white); door(p, 12); },
};
for (const [arrow, draw] of Object.entries(SIGNS)) {
  texture('exit_sign_' + arrow, (p) => { p.rect(0, 0, 16, 16, '#d5d8dc'); p.rect(1, 4, 14, 8, CR.green); p.rect(1, 4, 14, 1, CR.greenLo); p.rect(1, 11, 14, 1, CR.greenLo); draw(p); });
  M('exit_sign_' + arrow, { parent: 'minecraft:block/block', textures: { sign: tex('exit_sign_' + arrow), frame: tex('ceiling_panel'), particle: tex('exit_sign_' + arrow) }, elements: [
    { ...box([1, 4, 14], [15, 12, 16], { north: { uv: [1, 4, 15, 12], texture: '#sign' }, south: { uv: [1, 4, 15, 12], texture: '#frame' }, east: { uv: [0, 0, 2, 8], texture: '#frame' },
      west: { uv: [0, 0, 2, 8], texture: '#frame' }, up: { uv: [0, 0, 14, 2], texture: '#frame' }, down: { uv: [0, 0, 14, 2], texture: '#frame' } }), light_emission: 15 },
  ] });
}
BS('exit_sign', { variants: Object.fromEntries(Object.entries(yaw).flatMap(([f, y]) => Object.keys(SIGNS).map((arrow) =>
  [`arrow=${arrow},facing=${f}`, { model: 'setpoint:block/exit_sign_' + arrow, ...(y ? { y } : {}) }]))) });
item('exit_sign', 'setpoint:block/exit_sign_left');

// ---------- blast door: heavy steel, a small wired window, a warning plate; the vanilla door models ----------
const blastPlate = (p) => {
  p.rect(0, 0, 16, 16, '#6b7480'); p.rect(0, 0, 2, 16, '#4b525c'); p.rect(14, 0, 2, 16, '#4b525c');
  for (const x of [1, 14]) for (let y = 2; y < 16; y += 4) p.set(x, y, '#9aa2ad');
};
texture('blast_door_top', (p) => {
  blastPlate(p); p.rect(0, 0, 16, 2, '#4b525c');
  p.rect(5, 4, 6, 5, '#23303d'); p.rect(5, 4, 6, 1, '#4b525c'); for (const x of [6, 8]) p.rect(x + 1, 5, 1, 4, '#5b6168'); p.rect(5, 6, 6, 1, '#5b6168'); p.set(6, 5, '#7fa3c2');
  p.rect(5, 11, 6, 2, CR.yellow); p.rect(6, 11, 4, 1, '#1f1f1f');            // KEEP CLOSED plate
});
texture('blast_door_bottom', (p) => {
  blastPlate(p); p.rect(0, 14, 16, 2, '#4b525c');
  p.rect(3, 1, 10, 1, '#4b525c'); p.rect(3, 2, 10, 1, '#c9ced5');            // the push bar
  for (let x = 2; x < 14; x++) p.set(x, 11 + ((x >> 1) % 2), (x >> 1) % 2 ? '#e2b13c' : '#1f2125'); // hazard band
  p.rect(2, 13, 12, 1, '#8a929c');
});
texture('blast_door', (p) => { // the item: the door as you see it from the front
  p.rect(4, 0, 8, 16, '#6b7480'); p.rect(4, 0, 1, 16, '#4b525c'); p.rect(11, 0, 1, 16, '#4b525c'); p.rect(4, 0, 8, 1, '#4b525c'); p.rect(4, 15, 8, 1, '#4b525c');
  p.rect(6, 2, 4, 3, '#23303d'); p.set(7, 3, '#7fa3c2'); p.rect(6, 6, 4, 1, CR.yellow); p.rect(5, 9, 6, 1, '#c9ced5'); p.rect(5, 13, 6, 1, '#e2b13c');
}, 'item');
for (const half of ['bottom', 'top']) for (const hinge of ['left', 'right']) for (const open of ['', '_open']) {
  M(`blast_door_${half}_${hinge}${open}`, { parent: `minecraft:block/door_${half}_${hinge}${open}`, textures: { bottom: tex('blast_door_bottom'), top: tex('blast_door_top') } });
}
{
  const doorState = {};
  const facings = { east: 0, south: 90, west: 180, north: 270 };
  for (const [f, y0] of Object.entries(facings)) for (const half of ['lower', 'upper']) for (const hinge of ['left', 'right']) for (const open of [false, true]) {
    const y = (y0 + (open ? (hinge === 'left' ? 90 : 270) : 0)) % 360; // the same turns vanilla gives the iron door
    doorState[`facing=${f},half=${half},hinge=${hinge},open=${open}`] = { model: `setpoint:block/blast_door_${half === 'lower' ? 'bottom' : 'top'}_${hinge}${open ? '_open' : ''}`, ...(y ? { y } : {}) };
  }
  BS('blast_door', { variants: doorState });
}
json(join(ASSETS, 'models/item', 'blast_door.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'setpoint:item/blast_door' } });
item('blast_door', 'setpoint:item/blast_door');

// ---------- wired glass: lightly tinted glass with a square steel mesh cast in; a pane like glass panes ----------
texture('wired_glass', (p) => {
  p.rect(0, 0, 16, 16, '#cfe3ec40');
  for (let k = 1; k < 16; k += 4) { p.rect(0, k, 16, 1, '#5b6168d0'); p.rect(k, 0, 1, 16, '#5b6168d0'); }
  for (const [x, y] of [[3, 6], [4, 5], [5, 4], [11, 12], [12, 11]]) p.set(x, y, '#ffffff70');
});
texture('wired_glass_pane_top', (p) => { p.rect(0, 0, 16, 16, '#9fb3bd90'); for (let k = 1; k < 16; k += 4) p.rect(k, 0, 1, 16, '#5b6168d0'); });
const glassSprite = (name) => ({ force_translucent: true, sprite: tex(name) });
M('wired_glass', { parent: 'minecraft:block/cube_all', textures: { all: glassSprite('wired_glass') } });
BS('wired_glass', { variants: { '': { model: 'setpoint:block/wired_glass' } } });
item('wired_glass', 'setpoint:block/wired_glass');
for (const part of ['post', 'side', 'side_alt', 'noside', 'noside_alt']) {
  M('wired_glass_pane_' + part, { parent: 'minecraft:block/template_glass_pane_' + part,
    textures: { pane: glassSprite('wired_glass'), ...(part.startsWith('noside') ? {} : { edge: glassSprite('wired_glass_pane_top') }) } });
}
BS('wired_glass_pane', { multipart: [
  { apply: { model: 'setpoint:block/wired_glass_pane_post' } },
  { when: { north: 'true' }, apply: { model: 'setpoint:block/wired_glass_pane_side' } },
  { when: { east: 'true' }, apply: { model: 'setpoint:block/wired_glass_pane_side', y: 90 } },
  { when: { south: 'true' }, apply: { model: 'setpoint:block/wired_glass_pane_side_alt' } },
  { when: { west: 'true' }, apply: { model: 'setpoint:block/wired_glass_pane_side_alt', y: 90 } },
  { when: { north: 'false' }, apply: { model: 'setpoint:block/wired_glass_pane_noside' } },
  { when: { east: 'false' }, apply: { model: 'setpoint:block/wired_glass_pane_noside_alt' } },
  { when: { south: 'false' }, apply: { model: 'setpoint:block/wired_glass_pane_noside_alt', y: 90 } },
  { when: { west: 'false' }, apply: { model: 'setpoint:block/wired_glass_pane_noside', y: 270 } },
] });
json(join(ASSETS, 'models/item', 'wired_glass_pane.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'setpoint:block/wired_glass' } });
item('wired_glass_pane', 'setpoint:item/wired_glass_pane');

recipe('overview_screen', ['PPP', 'RQR', 'III'], { P: 'minecraft:glass_pane', R: 'minecraft:redstone', Q: 'minecraft:quartz', I: 'minecraft:iron_ingot' }, 'setpoint:overview_screen', 3);
recipe('alarm_beacon', [' G ', 'GLG', 'INI'], { G: 'minecraft:orange_stained_glass', L: 'minecraft:redstone_lamp', I: 'minecraft:iron_ingot', N: 'minecraft:note_block' }, 'setpoint:alarm_beacon');
recipe('esd_button', ['RBR', 'YIY'], { R: 'minecraft:red_dye', B: 'minecraft:stone_button', Y: 'minecraft:yellow_dye', I: 'minecraft:iron_ingot' }, 'setpoint:esd_button');
recipe('radio_charger', ['L L', 'CRC', 'III'], { L: 'minecraft:lightning_rod', C: 'minecraft:copper_ingot', R: 'minecraft:redstone', I: 'minecraft:iron_ingot' }, 'setpoint:radio_charger');
recipe('control_desk', ['SSS', 'I I', 'I I'], { S: 'minecraft:smooth_stone_slab', I: 'minecraft:iron_ingot' }, 'setpoint:control_desk', 3);
recipe('desk_corner', ['SSS', 'SI ', 'SI '], { S: 'minecraft:smooth_stone_slab', I: 'minecraft:iron_ingot' }, 'setpoint:desk_corner');
recipe('desk_screens', ['PPP', 'RIR', ' I '], { P: 'minecraft:glass_pane', R: 'minecraft:redstone', I: 'minecraft:iron_ingot' }, 'setpoint:desk_screens');
recipe('office_chair', ['W  ', 'WWW', 'III'], { W: 'minecraft:black_wool', I: 'minecraft:iron_ingot' }, 'setpoint:office_chair');
recipe('raised_floor', ['CCC', 'I I'], { C: 'minecraft:light_gray_concrete', I: 'minecraft:iron_ingot' }, 'setpoint:raised_floor', 6);
shapeless('raised_floor_vent', ['setpoint:raised_floor', 'minecraft:iron_bars'], 'setpoint:raised_floor_vent', 1);
recipe('ceiling_panel', ['CCC', 'CCC'], { C: 'minecraft:calcite' }, 'setpoint:ceiling_panel', 6);
shapeless('ceiling_light', ['setpoint:ceiling_panel', 'minecraft:glowstone'], 'setpoint:ceiling_light', 1);
recipe('wall_clock', [' N ', 'NCN', ' N '], { N: 'minecraft:iron_nugget', C: 'minecraft:clock' }, 'setpoint:wall_clock');
recipe('filing_cabinet', ['III', 'IBI', 'III'], { I: 'minecraft:iron_ingot', B: 'minecraft:barrel' }, 'setpoint:filing_cabinet');
recipe('printer', ['IPI', 'IRI'], { I: 'minecraft:iron_ingot', P: 'minecraft:paper', R: 'minecraft:redstone' }, 'setpoint:printer');
recipe('coffee_machine', ['IBI', 'IFI'], { I: 'minecraft:iron_ingot', B: 'minecraft:cocoa_beans', F: 'minecraft:furnace' }, 'setpoint:coffee_machine');
recipe('exit_sign', ['GGG', 'GLG'], { G: 'minecraft:green_stained_glass_pane', L: 'minecraft:glowstone_dust' }, 'setpoint:exit_sign', 2);
recipe('blast_door', ['IW', 'IO', 'II'], { I: 'minecraft:iron_ingot', W: 'setpoint:wired_glass', O: 'minecraft:obsidian' }, 'setpoint:blast_door');
recipe('wired_glass', ['GGG', 'GBG', 'GGG'], { G: 'minecraft:glass', B: 'minecraft:iron_bars' }, 'setpoint:wired_glass', 8);
recipe('wired_glass_pane', ['GGG', 'GGG'], { G: 'setpoint:wired_glass' }, 'setpoint:wired_glass_pane', 16);

// =====================================================================================
// Loot tables and tool tags for every block
// =====================================================================================
const lootSelf = (name) => json(join(DATA, 'loot_table/blocks', name + '.json'), {
  type: 'minecraft:block', random_sequence: 'setpoint:blocks/' + name,
  pools: [{ rolls: 1, conditions: [{ condition: 'minecraft:survives_explosion' }], entries: [{ type: 'minecraft:item', name: 'setpoint:' + name }] }],
});
const lootOre = (name, drop) => json(join(DATA, 'loot_table/blocks', name + '.json'), {
  type: 'minecraft:block', random_sequence: 'setpoint:blocks/' + name,
  pools: [{ rolls: 1, entries: [{ type: 'minecraft:alternatives', children: [
    { type: 'minecraft:item', condition: 'minecraft:tool/can_silk_touch', name: 'setpoint:' + name },
    { type: 'minecraft:item', name: drop, modifier: [
      { type: 'minecraft:apply_bonus', enchantment: 'minecraft:fortune', formula: 'minecraft:ore_drops' }, { type: 'minecraft:explosion_decay' }] },
  ] }] }],
});
const MACHINES = ['pipe', 'tank', 'centrifugal_pump', 'creative_generator', 'level_transmitter', 'control_valve', 'controller',
  'stirling_engine', 'biogas_generator', 'boiler', 'steam_turbine', 'dcs_console', 'hand_valve', 'water_intake', 'flow_transmitter',
  'pressure_transmitter', 'safety_valve', 'drain_valve', 'check_valve', 'pid_board', 'pallet', 'gas_pipe', 'electrolyser', 'nitrogen_generator', 'compressor', 'fuel_cell', 'ammonia_reactor', 'fertilizer_mixer', 'pumpjack', 'distillation_column', 'diesel_generator', 'gas_valve', 'gas_control_valve'];
for (const name of [...MACHINES, 'hookonium_block', 'raw_hookonium_block']) lootSelf(name);
// factory blocks: each drops itself; a handrail drops one for every side it rails
const FACTORY = ['rack_upright', 'rack_beam', 'grating', 'grating_stairs', 'caged_ladder', 'steel_beam', 'checker_plate', 'hazard_stripes', 'corrugated_steel', 'industrial_lamp'];
for (const name of FACTORY) lootSelf(name);
lootSelf('flare_stack');
// control room: each drops itself (the filing cabinet also what it holds); the blast door only from its lower half
const CONTROL_ROOM = ['overview_screen', 'alarm_beacon', 'esd_button', 'radio_charger', 'control_desk', 'desk_corner', 'desk_screens', 'office_chair',
  'raised_floor', 'raised_floor_vent', 'ceiling_panel', 'ceiling_light', 'wall_clock', 'filing_cabinet', 'printer', 'coffee_machine', 'exit_sign', 'wired_glass', 'wired_glass_pane'];
for (const name of CONTROL_ROOM) lootSelf(name);
json(join(DATA, 'loot_table/blocks', 'blast_door.json'), { type: 'minecraft:block', random_sequence: 'setpoint:blocks/blast_door',
  pools: [{ rolls: 1, conditions: [{ condition: 'minecraft:survives_explosion' }], entries: [{ type: 'minecraft:item', name: 'setpoint:blast_door',
    condition: { type: 'minecraft:match_block', blocks: 'setpoint:blast_door', state: { half: 'lower' } } }] }] });
json(join(DATA, 'loot_table/blocks', 'handrail.json'), { type: 'minecraft:block', random_sequence: 'setpoint:blocks/handrail',
  pools: ['north', 'east', 'south', 'west'].map((edge) => ({ rolls: 1, entries: [{ type: 'minecraft:item', name: 'setpoint:handrail',
    condition: { type: 'minecraft:match_block', blocks: 'setpoint:handrail', state: { [edge]: 'true' } } }] })).concat([{ rolls: 1,
    entries: [{ type: 'minecraft:item', name: 'setpoint:grating', condition: { type: 'minecraft:match_block', blocks: 'setpoint:handrail', state: { floor: 'true' } } }] }]) });
for (const name of ['drum', 'ibc', 'floating_roof_tank', 'gas_cylinder', 'gas_sphere']) json(join(DATA, 'loot_table/blocks', name + '.json'), {
  type: 'minecraft:block', random_sequence: 'setpoint:blocks/' + name,
  pools: [{ rolls: 1, conditions: [{ condition: 'minecraft:survives_explosion' }], entries: [{ type: 'minecraft:item', name: 'setpoint:' + name,
    modifier: { type: 'minecraft:copy_components', source: 'block_entity', include: ['minecraft:custom_name', 'setpoint:fluid_contents', 'minecraft:dyed_color'] } }] }],
});
lootOre('hookonium_ore', 'setpoint:raw_hookonium');
lootOre('deepslate_hookonium_ore', 'setpoint:raw_hookonium');
const HOOKONIUM_BLOCKS = ['hookonium_ore', 'deepslate_hookonium_ore', 'hookonium_block', 'raw_hookonium_block'].map((n) => 'setpoint:' + n);
const tag = (path, values) => json(join(ROOT, 'data', path + '.json'), { values });
tag('minecraft/tags/block/mineable/pickaxe', [...MACHINES.map((n) => 'setpoint:' + n), 'setpoint:drum', 'setpoint:ibc', 'setpoint:floating_roof_tank', 'setpoint:roof_tank_part', 'setpoint:gas_cylinder', 'setpoint:gas_sphere', 'setpoint:gas_sphere_part', 'setpoint:pumpjack_part', 'setpoint:column_part', 'setpoint:flare_stack', 'setpoint:flare_stack_part', ...FACTORY.map((n) => 'setpoint:' + n), 'setpoint:handrail', ...CONTROL_ROOM.filter((n) => n !== 'office_chair').map((n) => 'setpoint:' + n), 'setpoint:blast_door', ...HOOKONIUM_BLOCKS]);
tag('minecraft/tags/block/doors', ['setpoint:blast_door']);
tag('minecraft/tags/item/doors', ['setpoint:blast_door']);
tag('minecraft/tags/block/climbable', ['setpoint:caged_ladder']);
tag('minecraft/tags/block/stairs', ['setpoint:grating_stairs']);
tag('minecraft/tags/item/stairs', ['setpoint:grating_stairs']);
tag('minecraft/tags/block/needs_diamond_tool', HOOKONIUM_BLOCKS);
// the pipe wrench turns, cuts and picks up these
tag('setpoint/tags/block/wrenchable', MACHINES.map((n) => 'setpoint:' + n));
// common tags, so other mods recognise the ore and its products
for (const kind of ['block', 'item']) {
  tag(`c/tags/${kind}/ores/hookonium`, ['setpoint:hookonium_ore', 'setpoint:deepslate_hookonium_ore']);
  tag(`c/tags/${kind}/ores`, ['#c:ores/hookonium']);
  tag(`c/tags/${kind}/ores_in_ground/stone`, ['setpoint:hookonium_ore']);
  tag(`c/tags/${kind}/ores_in_ground/deepslate`, ['setpoint:deepslate_hookonium_ore']);
  tag(`c/tags/${kind}/storage_blocks/hookonium`, ['setpoint:hookonium_block']);
  tag(`c/tags/${kind}/storage_blocks/raw_hookonium`, ['setpoint:raw_hookonium_block']);
  tag(`c/tags/${kind}/storage_blocks`, ['#c:storage_blocks/hookonium', '#c:storage_blocks/raw_hookonium']);
}
tag('c/tags/item/ingots/hookonium', ['setpoint:hookonium_ingot']);
tag('c/tags/item/ingots', ['#c:ingots/hookonium']);
tag('c/tags/item/raw_materials/hookonium', ['setpoint:raw_hookonium']);
tag('c/tags/item/raw_materials', ['#c:raw_materials/hookonium']);

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
// the test benches: empty rooms, the normal one, a big one for the floating roof tank and a tall one for the flare
for (const [name, W, H, D] of [['bench', 7, 11, 3], ['big_bench', 10, 12, 10], ['tall_bench', 9, 44, 13]]) {
const n = nbt();
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
write(join(DATA, 'structure', name + '.nbt'), gzipSync(Buffer.concat(n.parts)));
}

// =====================================================================================
// The Setpoint Handbook: item, recipe, and the book's content with every recipe filled in
// =====================================================================================
texture('handbook', (p) => {
  p.rect(3, 1, 10, 14, '#1e222a');                       // cover
  p.rect(3, 1, 2, 14, C.cyanDark); p.rect(3, 1, 1, 14, C.cyan); // spine
  p.rect(12, 2, 1, 12, '#e9e4d4');                       // page edges
  p.rect(6, 3, 5, 1, C.steel3);                          // title lines
  p.rect(6, 5, 4, 1, C.steel2);
  // a small yellow S on the cover
  for (const [x, y] of [[7, 8], [8, 8], [9, 8], [7, 9], [7, 10], [8, 10], [9, 10], [9, 11], [7, 12], [8, 12], [9, 12]]) p.set(x, y, '#ffd23f');
  p.rect(10, 0, 1, 3, C.magenta);                        // bookmark
}, 'item');
json(join(ASSETS, 'models/item/handbook.json'), { parent: 'minecraft:item/generated', textures: { layer0: 'setpoint:item/handbook' } });
item('handbook', 'setpoint:item/handbook');
shapeless('handbook', ['minecraft:book', 'minecraft:copper_ingot'], 'setpoint:handbook', 1);

{
  const recipes = {}, cooking = {};
  for (const file of readdirSync(join(DATA, 'recipe'))) {
    const r = JSON.parse(readFileSync(join(DATA, 'recipe', file), 'utf8'));
    if (r.type === 'minecraft:crafting_shaped') {
      const rows = r.pattern.length === 1 ? ['   ', r.pattern[0], '   '] : r.pattern;
      const grid = [];
      for (let y = 0; y < 3; y++) for (let x = 0; x < 3; x++) {
        const c = (rows[y] || '')[x];
        grid.push(c && c !== ' ' ? [].concat(r.key[c])[0] : '');
      }
      recipes[r.result.id] = { grid, count: r.result.count || 1 };
    } else if (r.type === 'minecraft:crafting_shapeless' && !recipes[r.result.id]) {
      const grid = Array(9).fill('');
      r.ingredients.forEach((ing, i) => { grid[i] = [].concat(ing)[0]; });
      recipes[r.result.id] = { grid, count: r.result.count || 1 };
    } else if (r.type === 'minecraft:smelting') {
      cooking[r.ingredient] = { result: r.result.id, seconds: r.cookingtime / 20 };
    }
  }
  const chapters = HANDBOOK.chapters.map((chapter) => ({
    id: chapter.id, title: chapter.title, icon: chapter.icon,
    content: chapter.content.map(([type, ...args]) => {
      if (type === 'craft') {
        const r = recipes[args[0]];
        if (!r) throw new Error('handbook: no crafting recipe for ' + args[0]);
        return { type, result: args[0], grid: r.grid, count: r.count };
      }
      if (type === 'cook') {
        const c = cooking[args[0]];
        if (!c) throw new Error('handbook: no smelting recipe for ' + args[0]);
        return { type, input: args[0], result: c.result, seconds: c.seconds };
      }
      if (type === 'items') return { type, items: args };
      return { type, text: args[0] };
    }),
  }));
  json(join(ASSETS, 'handbook/en_us.json'), { title: HANDBOOK.title, chapters });
}

// =====================================================================================
// Advancements: a Setpoint tab that walks a new player through the mod
// =====================================================================================
{
  const has = (...items) => Object.fromEntries(items.map((id) => [id.split(':')[1],
    { trigger: 'minecraft:inventory_changed', conditions: { items: [{ items: id }] } }]));
  const happened = (event) => ({ [event]: { trigger: 'setpoint:plant_event', conditions: { event } } });
  const ADV = [
    ['root', null, 'setpoint:handbook', 'task', has('setpoint:handbook', 'setpoint:pipe', 'setpoint:tank', 'setpoint:centrifugal_pump'),
      { background: 'minecraft:gui/advancements/backgrounds/stone', show_toast: false, announce_to_chat: false }],
    ['moving_water', 'root', 'setpoint:centrifugal_pump', 'task', has('setpoint:centrifugal_pump')],
    ['hear_that_crackle', 'moving_water', 'setpoint:centrifugal_pump', 'goal', happened('cavitation')],
    ['hands_off', 'moving_water', 'setpoint:controller', 'goal', happened('loop_holding')],
    ['power_to_the_plant', 'root', 'setpoint:stirling_engine', 'task', has('setpoint:stirling_engine', 'setpoint:biogas_generator')],
    ['full_steam', 'power_to_the_plant', 'setpoint:steam_turbine', 'goal', happened('turbine_rated')],
    ['mission_control', 'hands_off', 'setpoint:dcs_console', 'task', has('setpoint:dcs_console')],
    ['acknowledged', 'mission_control', 'setpoint:dcs_console', 'goal', happened('alarm_acknowledged')],
    ['deep_purple', 'root', 'setpoint:raw_hookonium', 'challenge', has('setpoint:raw_hookonium')],
    ['bottomless', 'moving_water', 'setpoint:water_intake', 'task', happened('intake_running')],
    ['lockout', 'moving_water', 'setpoint:hand_valve', 'task', happened('valve_shut')],
    ['righty_tighty', 'root', 'setpoint:pipe_wrench', 'task', has('setpoint:pipe_wrench')],
    ['draftsman', 'root', 'setpoint:pid', 'goal', happened('pid_drawn')],
    ['mimic_panel', 'draftsman', 'setpoint:dcs_console', 'goal', happened('pid_on_console')],
    ['blowing_off_steam', 'full_steam', 'setpoint:safety_valve', 'task', happened('psv_lifted')],
    ['under_pressure', 'full_steam', 'setpoint:pressure_transmitter', 'goal', happened('pressure_holding')],
    ['skyscraper', 'moving_water', 'setpoint:tank', 'goal', happened('tall_tank')],
    ['steady_flow', 'hands_off', 'setpoint:flow_transmitter', 'goal', happened('flow_holding')],
    ['good_neighbours', 'moving_water', 'setpoint:centrifugal_pump', 'task', happened('foreign_tank')],
    ['light_the_sky', 'blowing_off_steam', 'setpoint:flare_stack', 'goal', happened('flare_lit')],
    ['smokeless', 'light_the_sky', 'setpoint:flare_stack', 'challenge', happened('smokeless')],
  ];
  for (const [name, parent, icon, frame, criteria, extra = {}] of ADV) {
    json(join(DATA, 'advancement', name + '.json'), {
      ...(parent ? { parent: 'setpoint:' + parent } : {}),
      criteria,
      display: {
        icon: { id: icon }, frame,
        title: { translate: `advancements.setpoint.${name}.title` },
        description: { translate: `advancements.setpoint.${name}.description` },
        ...extra,
      },
      requirements: [Object.keys(criteria)], // any one of them
    });
  }
}

console.log('assets generated' + (KEEP_TEXTURES ? ' (kept existing textures)' : ''));
