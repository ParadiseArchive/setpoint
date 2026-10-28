// Makes the pictures for the GitHub README (.github/readme/*.png) in the style of hookdev.io: a banner, shelves of
// machine renders per theme, and the P&ID, board, console and faceplate redrawn on the docs page. Everything comes from
// the site repo next to this one (its fonts, its renders from render-showcase.mjs and its docs page), shot with
// headless Chrome. Re-run it after the renders change:
//   node tools/readme-art.mjs ../hook-development
import { spawn } from 'node:child_process';
import { createReadStream, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, resolve } from 'node:path';
import { tmpdir } from 'node:os';

const SITE = resolve(process.argv[2] || '../hook-development');
const OUT = '.github/readme';
const CHROME = process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const PORT = 8131, DEBUG = 9341;
// the version on the banner comes from the build, so it can never fall behind a release
const VERSION = readFileSync('gradle.properties', 'utf8').match(/^mod_version=(.+)$/m)[1].trim();
mkdirSync(OUT, { recursive: true });

// ---------- the pictures ----------
const IMG = '/projects/minecraft-mods/img/';
const HOOK = '<svg width="28" height="28" viewBox="0 0 16 16" shape-rendering="crispEdges"><g fill="#2de2e6"><rect x="7" y="0" width="4" height="1"/><rect x="6" y="1" width="1" height="2"/><rect x="11" y="1" width="1" height="2"/><rect x="7" y="3" width="4" height="1"/><rect x="9" y="4" width="2" height="10"/><rect x="8" y="14" width="2" height="1"/><rect x="2" y="15" width="7" height="1"/><rect x="1" y="11" width="2" height="4"/><rect x="3" y="12" width="1" height="1"/></g></svg>';
const BASE = `<link rel="stylesheet" href="/assets/fonts.css"><style>
*{box-sizing:border-box}html,body{margin:0;background:transparent}
body{font-family:"Chakra Petch",sans-serif;color:#d9deea}
.art{background:#0a0c13;background-image:repeating-linear-gradient(0deg,rgba(255,255,255,.018) 0 1px,transparent 1px 3px);position:relative}
img{image-rendering:pixelated}
.bar{display:flex;align-items:center;gap:8px;padding:10px 14px;border-bottom:2px solid #232a3d;background:#0f121c;font:20px "VT323",monospace;color:#7c869d}
.bar i{width:12px;height:12px;display:block}
.chip{font:12px "Silkscreen",monospace;padding:6px 10px;border:2px solid;letter-spacing:.04em}
</style>`;

const banner = `${BASE}<style>
.art{width:1280px;height:470px;overflow:hidden;border:2px solid #232a3d}
.grid{position:absolute;inset:0;background-image:linear-gradient(#2de2e60d 1px,transparent 1px),linear-gradient(90deg,#2de2e60d 1px,transparent 1px);background-size:32px 32px}
.left{position:absolute;left:56px;top:30px;width:640px;display:flex;flex-direction:column;gap:16px}
.logo{width:352px;height:198px;margin:0 0 4px -10px}
h1{margin:0;font:700 26px/1.25 "Silkscreen",monospace;color:#f2f4fa;text-shadow:3px 3px 0 #ff4f8b66}
p{margin:0;font-size:18px;color:#aeb6c9;max-width:440px}
.chips{display:flex;gap:10px}
.sky{position:absolute;right:40px;bottom:0;display:flex;align-items:flex-end;gap:22px}
.ground{position:absolute;left:0;right:0;bottom:0;height:22px;background:#0f121c;border-top:2px solid #232a3d}
.by{position:absolute;right:28px;top:22px;display:flex;align-items:center;gap:10px;font:14px "Silkscreen",monospace;color:#f2f4fa}
</style>
<div class="art" id="art"><div class="grid"></div><div class="ground"></div>
  <div class="left">
    <img class="logo" src="${IMG}setpoint-logo.png">
    <h1>Process engineering<br>you can play</h1>
    <p>Build a plant that behaves like the real thing, then draw it as a live P&amp;ID.</p>
    <div class="chips"><span class="chip" style="color:#2de2e6">Minecraft 26.3</span><span class="chip" style="color:#2de2e6">NeoForge</span><span class="chip" style="color:#ffb547">Version ${VERSION}</span><span class="chip" style="color:#ff4f8b">Open source</span></div>
  </div>
  <div class="sky">
    <img src="${IMG}gas_sphere.png" style="height:212px;margin-bottom:12px">
    <img src="${IMG}distillation_column.png" style="height:300px;margin-bottom:12px">
    <img src="${IMG}flare_stack.png" style="height:372px;margin-bottom:12px">
    <img src="${IMG}floating_roof_tank_full.png" style="height:176px;margin-bottom:12px">
  </div>
  <div class="by">${HOOK}HOOK DEV</div>
</div>`;

// one shelf: [render, label, height in px]
function shelf(file, items) {
  return `${BASE}<style>
.art{width:1200px;border:2px solid #232a3d}
.items{display:flex;flex-wrap:wrap;align-items:flex-end;justify-content:center;gap:26px 30px;padding:30px 26px 24px}
figure{margin:0;display:flex;flex-direction:column;align-items:center;gap:10px}
figcaption{font:20px "VT323",monospace;color:#aeb6c9;text-align:center;max-width:160px;line-height:1}
</style>
<div class="art" id="art"><div class="bar"><i style="background:#ff4f8b"></i><i style="background:#ffb547"></i><i style="background:#2de2e6"></i><span>&nbsp;${file}</span></div>
<div class="items">${items.map(([img, label, h]) => `<figure><img src="${IMG}${img}.png" style="height:${h}px"><figcaption>${label}</figcaption></figure>`).join('')}</div></div>`;
}
const S = 110, M = 160, L = 230;
const SHELVES = {
  liquids: shelf('liquids.render', [['water_intake', 'Water intake', S], ['pump', 'Centrifugal pump', S], ['hand_valve', 'Hand valve', S], ['control_valve', 'Control valve', S], ['check_valve', 'Check valve', S], ['tank', 'Tank', S], ['tall_tank', 'Tall tank', M], ['drum', 'Drum', M], ['ibc', 'IBC', M], ['floating_roof_tank_full', 'Floating roof tank', L]]),
  control: shelf('control.render', [['level_transmitter', 'Level transmitter', S], ['flow_transmitter', 'Flow transmitter', S], ['pressure_transmitter', 'Pressure transmitter', S], ['controller', 'DCS controller', S], ['field_communicator', 'Field communicator', S], ['dcs_console', 'DCS console', S], ['console_desk', 'Console desk', S], ['pid_board', 'P&amp;ID board', S], ['alarm_beacon', 'Alarm beacon', S], ['esd_button', 'Emergency stop', S], ['control_room', 'Control room', L + 20]]),
  power: shelf('power.render', [['stirling_engine', 'Stirling engine', S], ['biogas_generator', 'Biogas generator', S], ['boiler', 'Boiler', S], ['burner', 'Oil and gas burner', S], ['safety_valve', 'Safety valve', S], ['steam_turbine', 'Steam turbine', S], ['diesel_generator', 'Diesel generator', S], ['fuel_cell', 'Fuel cell', S], ['power_cable', 'Power cable', 70], ['wind_turbine', 'Wind turbine', L], ['steam_plant', 'A steam plant', M]]),
  gases: shelf('gases-and-oil.render', [['electrolyser', 'Electrolyser', S], ['nitrogen_generator', 'Nitrogen generator', S], ['compressor', 'Compressor', S], ['gas_control_valve', 'Gas control valve', S], ['gas_cylinders', 'Gas cylinders', 70], ['ammonia_reactor', 'Ammonia reactor', S], ['fertilizer_mixer', 'Fertilizer mixer', S], ['gas_sphere', 'Gas sphere', L], ['pumpjack', 'Pumpjack', M], ['distillation_column', 'Distillation column', L + 40], ['flare_stack', 'Flare stack', L + 110]]),
  logistics: shelf('logistics.render', [['pallet_4', 'Pallet', M - 30], ['pallet_truck_loaded', 'Pallet truck', M - 20], ['forklift_lifted', 'Forklift', M], ['pallet_rack', 'Pallet racking', L]]),
};

// ---------- a little server for the site, plus the pictures above ----------
const TYPES = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
const PAGES = { banner, ...SHELVES };
const server = createServer((req, res) => {
  let p = decodeURIComponent(req.url.split('?')[0]);
  const page = p.match(/^\/__readme\/(\w+)$/);
  if (page) { res.writeHead(200, { 'content-type': 'text/html' }); res.end(`<!doctype html><meta charset="utf-8">${PAGES[page[1]]}`); return; }
  if (p.endsWith('/')) p += 'index.html';
  const f = join(SITE, p);
  if (!existsSync(f)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': TYPES[extname(f)] || 'application/octet-stream' }); createReadStream(f).pipe(res);
}).listen(PORT);

const profile = join(tmpdir(), 'setpoint-readme-chrome');
rmSync(profile, { recursive: true, force: true });
const chrome = spawn(CHROME, ['--headless=new', '--disable-gpu', `--remote-debugging-port=${DEBUG}`, `--user-data-dir=${profile}`, '--window-size=1400,1000', 'about:blank']);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let tabs;
for (let i = 0; i < 120 && !tabs?.length; i++) { try { tabs = await (await fetch(`http://127.0.0.1:${DEBUG}/json`)).json(); } catch {} await sleep(250); }
const ws = new WebSocket(tabs.find((t) => t.type === 'page').webSocketDebuggerUrl);
await new Promise((r) => (ws.onopen = r));
let id = 0; const pend = {};
ws.onmessage = (e) => { const m = JSON.parse(e.data); if (m.id && pend[m.id]) { pend[m.id](m); delete pend[m.id]; } };
const send = (method, params = {}) => new Promise((r) => { const i = ++id; pend[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
const ev = async (x) => (await send('Runtime.evaluate', { expression: x, returnByValue: true, awaitPromise: true })).result.result.value;
await send('Page.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 1400, height: 1000, deviceScaleFactor: 2, mobile: false });

async function shoot(url, selector, name, prep = '') {
  await send('Page.navigate', { url: `http://127.0.0.1:${PORT}${url}` });
  await sleep(1500);
  await ev('document.fonts.ready.then(()=>true)');
  // no boot screen, no pixel transition, no footer toasts in the shots
  await ev(`document.querySelectorAll('.ws-boot,.ws-toast,.px-overlay').forEach(e=>e.remove());${prep}`);
  await sleep(400);
  const box = await ev(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();return {x:r.x+scrollX,y:r.y+scrollY,width:r.width,height:r.height}})()`);
  await sleep(300);
  const shot = await send('Page.captureScreenshot', { format: 'png', clip: { ...box, scale: 1 }, captureBeyondViewport: true });
  writeFileSync(join(OUT, name + '.png'), Buffer.from(shot.result.data, 'base64'));
  console.log('wrote', join(OUT, name + '.png'), Math.round(box.width), 'x', Math.round(box.height));
}

for (const name of Object.keys(PAGES)) await shoot(`/__readme/${name}`, '#art', name === 'banner' ? 'banner' : 'shelf-' + name);
const DOCS = '/projects/minecraft-mods/setpoint/';
await shoot(DOCS, '.pid-frame', 'pid');
await shoot(DOCS, '.board-wall', 'board');
await shoot(DOCS, '#gui-dcs', 'console');
await shoot(DOCS, '#fp', 'faceplate');

chrome.kill(); server.close(); process.exit(0);
