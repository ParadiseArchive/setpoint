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

// ore: purple crystals on a transparent overlay; the model lays it over vanilla stone or deepslate
texture('hookonium_ore_overlay', (p) => {
  const big = ['.hl', 'lwm', 'mmk', '.o.'], small = ['hl', 'mk'];
  for (const [x, y] of [[2, 2], [10, 4], [4, 10], [11, 11]]) big.forEach((r, j) => [...r].forEach((c, i) => HK[c] && p.set(x + i, y + j, HK[c])));
  for (const [x, y] of [[7, 1], [1, 7], [13, 1], [7, 7], [14, 8]]) small.forEach((r, j) => [...r].forEach((c, i) => HK[c] && p.set(x + i, y + j, HK[c])));
});
texture('raw_hookonium', (p) => pixels(p, [
  '................',
  '................',
  '................',
  '.......ooo......',
  '.....oohlloo....',
  '....ohllmmmlo...',
  '...ohlmmmkmmo...',
  '..ohlmmkmmmmko..',
  '..olmmmmmmlmko..',
  '..ommkmmmmmmko..',
  '...ommmmmkmko...',
  '...okmmmmmkko...',
  '....okkmmkko....',
  '.....oookoo.....',
  '................',
  '................',
], HK), 'item');
texture('hookonium_ingot', (p) => pixels(p, [
  '................',
  '................',
  '................',
  '................',
  '......oooooo....',
  '....oowhhhhlo...',
  '...ohwhllllllo..',
  '..ohllllllllmko.',
  '..olmmmmmmmmmko.',
  '..okmmmmmmmmkko.',
  '...okkkkkkkkko..',
  '....ooooooooo...',
  '................',
  '................',
  '................',
  '................',
], HK), 'item');
// block of hookonium: a polished plate with the hook engraved in it
texture('hookonium_block', (p) => {
  p.rect(0, 0, 16, 16, HK.m);
  p.rect(0, 0, 16, 1, HK.h); p.rect(0, 0, 1, 16, HK.h);
  p.rect(1, 1, 14, 1, HK.l); p.rect(1, 1, 1, 14, HK.l);
  p.rect(0, 15, 16, 1, HK.o); p.rect(15, 0, 1, 16, HK.o);
  p.rect(1, 14, 14, 1, HK.k); p.rect(14, 1, 1, 14, HK.k);
  const hook = [[7, 3], [8, 3], [9, 3], [6, 4], [10, 4], [7, 5], [8, 5], [9, 5], [9, 6], [9, 7], [9, 8], [9, 9], [9, 10], [9, 11],
    [8, 12], [7, 12], [6, 12], [5, 12], [4, 11], [4, 10], [5, 10]];
  for (const [x, y] of hook) p.set(x + 1, y + 1, HK.l);
  for (const [x, y] of hook) p.set(x, y, HK.k);
  p.set(3, 3, HK.w);
});
texture('raw_hookonium_block', (p) => {
  p.rect(0, 0, 16, 16, HK.m);
  const spots = { k: [[2, 3], [9, 1], [13, 6], [5, 9], [11, 12], [1, 13], [7, 14]], l: [[4, 1], [12, 2], [1, 6], [8, 6], [14, 10], [3, 11], [10, 9]],
    h: [[5, 1], [12, 3], [8, 7], [3, 12]], o: [[3, 4], [14, 7], [6, 10], [12, 13]] };
  for (const [c, list] of Object.entries(spots)) for (const [x, y] of list) { p.set(x, y, HK[c]); if (c !== 'h') p.set(x + 1, y, HK[c]); }
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
    raw[o] = parseInt(hex.slice(1, 3), 16); raw[o + 1] = parseInt(hex.slice(3, 5), 16); raw[o + 2] = parseInt(hex.slice(5, 7), 16); raw[o + 3] = 255;
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
  'stirling_engine', 'biogas_generator', 'boiler', 'steam_turbine', 'dcs_console', 'hand_valve', 'water_intake'];
for (const name of [...MACHINES, 'hookonium_block', 'raw_hookonium_block']) lootSelf(name);
lootOre('hookonium_ore', 'setpoint:raw_hookonium');
lootOre('deepslate_hookonium_ore', 'setpoint:raw_hookonium');
const HOOKONIUM_BLOCKS = ['hookonium_ore', 'deepslate_hookonium_ore', 'hookonium_block', 'raw_hookonium_block'].map((n) => 'setpoint:' + n);
const tag = (path, values) => json(join(ROOT, 'data', path + '.json'), { values });
tag('minecraft/tags/block/mineable/pickaxe', [...MACHINES.map((n) => 'setpoint:' + n), ...HOOKONIUM_BLOCKS]);
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
