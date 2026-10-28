// Writes the data files that Minecraft 26.1.2 reads differently from 26.3, from the 26.3 ones in src/main/resources,
// into versions/26.1/resources. Never edit those by hand: change the 26.3 file (or gen-assets.mjs) and run this
// (gen-assets.mjs runs it at the end). versions/26.1/gradle.properties leaves the 26.3 originals out of the 26.1 jar.
//   node tools/port-26.1.mjs
import { mkdirSync, writeFileSync, readdirSync, readFileSync, rmSync, existsSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';

const FROM = 'src/main/resources/data/setpoint';
const TO = 'versions/26.1/resources/data/setpoint';

function files(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) => e.isDirectory() ? files(join(dir, e.name)) : e.name.endsWith('.json') ? [join(dir, e.name)] : []);
}
function port(folder, to, change) {
  rmSync(join(TO, to), { recursive: true, force: true });
  for (const file of files(join(FROM, folder))) {
    const out = join(TO, to, relative(join(FROM, folder), file));
    mkdirSync(dirname(out), { recursive: true });
    writeFileSync(out, JSON.stringify(change(JSON.parse(readFileSync(file, 'utf8'))), null, 2) + '\n');
  }
}
const list = (v) => v === undefined ? [] : Array.isArray(v) ? v : [v];

// ---- loot tables: 26.3 writes "condition" and "modifier" (with "type"), 26.1 "conditions" and "functions" (with "function")
const SILK_TOUCH = { condition: 'minecraft:match_tool', predicate: { predicates: { 'minecraft:enchantments': [{ enchantments: 'minecraft:silk_touch', levels: { min: 1 } }] } } };
function condition(c) {
  if (c === 'minecraft:tool/can_silk_touch') return SILK_TOUCH;
  if (typeof c === 'string') return { condition: c };
  if (c.condition) return c; // already the 26.1 way
  const { type, ...rest } = c;
  if (type === 'minecraft:match_block') return { condition: 'minecraft:block_state_property', block: rest.blocks, ...(rest.state ? { properties: rest.state } : {}) };
  return { condition: type, ...rest };
}
function lootFunction(f) {
  if (f.function) return f;
  const { type, ...rest } = f;
  return { function: type, ...rest };
}
function lootPart(v) {
  if (Array.isArray(v)) return v.map(lootPart);
  if (!v || typeof v !== 'object') return v;
  const out = {};
  for (const [k, x] of Object.entries(v)) {
    if (k === 'condition' || k === 'conditions') out.conditions = [...(out.conditions || []), ...list(x).map(condition)];
    else if (k === 'modifier' || k === 'functions') out.functions = list(x).map(lootFunction);
    else out[k] = lootPart(x);
  }
  return out;
}
port('loot_table', 'loot_table', lootPart);

// ---- ore veins: 26.1 keeps them in worldgen/configured_feature, the settings under "config", block states as {Name}
function oreTarget(t) {
  // 26.3 splits each target by height; 26.1 has no height-specific ore replaceables, so the plain stone tag is enough
  const tags = JSON.stringify(t).match(/minecraft:(stone|deepslate)_ore_replaceables/);
  return { predicate_type: 'minecraft:tag_match', tag: tags ? tags[0] : 'minecraft:stone_ore_replaceables' };
}
port('worldgen/feature', 'worldgen/configured_feature', ({ type, ...config }) => ({
  type,
  config: { ...config, targets: (config.targets || []).map((t) => ({ state: typeof t.state === 'string' ? { Name: t.state } : t.state, target: oreTarget(t.target) })) },
}));

console.log('26.1 data written to ' + TO);
