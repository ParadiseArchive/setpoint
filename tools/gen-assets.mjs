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
  'entity.pallet_truck.horn': {
    subtitle: 'subtitles.setpoint.entity.pallet_truck.horn',
    sounds: [{ name: 'setpoint:vehicle/horn', volume: 1.0 }], // a one-second note that loops while H is held (tools/make-horn.mjs)
  },
  'entity.forklift.horn': {
    subtitle: 'subtitles.setpoint.entity.forklift.horn',
    sounds: [{ name: 'setpoint:vehicle/horn_low', volume: 1.0 }], // the same, lower (tools/make-horn.mjs low)
  },
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
recipe('grating', ['BBB'], { B: 'minecraft:iron_bars' }, 'setpoint:grating', 6);
recipe('grating_stairs', ['B  ', 'BB ', 'BBB'], { B: 'minecraft:iron_bars' }, 'setpoint:grating_stairs', 4);
recipe('handrail', ['NNN', 'NYN'], { N: 'minecraft:iron_nugget', Y: 'minecraft:yellow_dye' }, 'setpoint:handrail', 6);
recipe('caged_ladder', ['NLN', 'NLN', 'NLN'], { N: 'minecraft:iron_nugget', L: 'minecraft:ladder' }, 'setpoint:caged_ladder', 3);
recipe('steel_beam', ['III', ' I ', 'III'], { I: 'minecraft:iron_ingot' }, 'setpoint:steel_beam', 6);
recipe('checker_plate', ['I I', ' I ', 'I I'], { I: 'minecraft:iron_ingot' }, 'setpoint:checker_plate', 8);
recipe('hazard_stripes', ['CYC', 'CKC', 'CYC'], { C: 'setpoint:checker_plate', Y: 'minecraft:yellow_dye', K: 'minecraft:black_dye' }, 'setpoint:hazard_stripes', 6);
recipe('corrugated_steel', ['I I', 'I I', 'I I'], { I: 'minecraft:iron_ingot' }, 'setpoint:corrugated_steel', 12);
recipe('industrial_lamp', [' I ', 'NGN'], { I: 'minecraft:iron_ingot', N: 'minecraft:iron_nugget', G: 'minecraft:glowstone' }, 'setpoint:industrial_lamp', 2);
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
  'pressure_transmitter', 'safety_valve', 'drain_valve', 'check_valve', 'pid_board', 'pallet', 'gas_pipe', 'electrolyser', 'nitrogen_generator', 'compressor'];
for (const name of [...MACHINES, 'hookonium_block', 'raw_hookonium_block']) lootSelf(name);
// factory blocks: each drops itself; a handrail drops one for every side it rails
const FACTORY = ['grating', 'grating_stairs', 'caged_ladder', 'steel_beam', 'checker_plate', 'hazard_stripes', 'corrugated_steel', 'industrial_lamp'];
for (const name of FACTORY) lootSelf(name);
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
tag('minecraft/tags/block/mineable/pickaxe', [...MACHINES.map((n) => 'setpoint:' + n), 'setpoint:drum', 'setpoint:ibc', 'setpoint:floating_roof_tank', 'setpoint:roof_tank_part', 'setpoint:gas_cylinder', 'setpoint:gas_sphere', 'setpoint:gas_sphere_part', ...FACTORY.map((n) => 'setpoint:' + n), 'setpoint:handrail', ...HOOKONIUM_BLOCKS]);
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
// the test benches: empty rooms, the normal one and a big one for the floating roof tank
for (const [name, W, H, D] of [['bench', 7, 11, 3], ['big_bench', 10, 12, 10]]) {
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
