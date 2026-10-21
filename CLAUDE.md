# Setpoint (Minecraft mod)

Process engineering mod for Minecraft by Hook Development. Showcased on https://hookdev.io/projects/minecraft-mods/ (site repo: `../hook-development`, see its CLAUDE.md).

## Working with the owner
- Talk to the owner in **Dutch**. All mod text (item names, tooltips, README, changelogs) is **English**.
- The owner knows from practice how real pumps, valves, controllers and DCS screens behave: use them as the realism check. They do not write Java; do the technical work, explain in plain language, one clear step at a time.
- **Privacy:** this repository is public. Never add the owner's name or personal details anywhere (code, docs, metadata, Modrinth/CurseForge pages, commit messages). The author is "Hook Development".
- Ideas go in the backlog below instead of being built all at once.

## Core concept (owner, 21 Oct 2026): P&IDs
Building a plant and drawing it as a P&ID is the heart of Setpoint. A blank P&ID picks two corners (the box shows in the world with dust particles); pid/PidCapture turns the box into a plan view (one symbol per column, priority: process equipment > controllers/power/console > valves > instruments > pipe; ties: the top block), turned a quarter if deeper than wide (max 32 x 24; always 10 blocks up from the lowest corner, owner's choice 21 Oct 2026; one blank sheet used up per drawing in survival), plus dashed signal links from controllers to their valve and measurement. The drawing lives on the P&ID item as the data component setpoint:pid_drawing; PidScreen draws it on blueprint paper (a cell per block, the symbol inside with padding so pipe runs show between symbols) with the 8x8 masks in client/PidSymbols (the site's docs use the same masks). Renaming goes to the server with the PidRename payload. Next (owner's plan): give P&IDs a job in the game; ideas still open.

## Design principle (owner feedback after the M2 playtest)
Easy by default, depth for those who want it. A normal Minecraft player must be able to use every machine without knowing process engineering: things link and configure themselves, sensible defaults, plain-language messages. The realistic layer (tuning, MAN/CAS, fail actions, NPSH numbers, field communicator) stays, but is optional.
- Pumps run by themselves; a redstone signal stops them.
- A controller placed against a tank measures it, finds the control valve in that tank's pipe runs, picks reverse/direct action and goes to AUTO at 50 %. Linking with the field communicator turns self-linking off.

## Decisions (20 Oct 2026)
- Name **Setpoint**, mod id `setpoint` (permanent after first release), package `io.hookdev.setpoint`.
- Minecraft **26.3**, **NeoForge** (ModDevGradle), **Java 25** (Temurin 25 installed locally).
- License **MIT** (open source).
- Textures: Claude makes first versions (16×16 pixel art), the owner may redraw later.
- Shipping: Modrinth + CurseForge under the name "Hook Development", automated upload from GitHub Actions; links on the site.

## Style in game
- 16×16 textures that sit well next to vanilla. Industrial greys with the Hook signature: cyan status lights, magenta/amber for alarms.
- GUIs follow high-performance HMI ideas: calm grey, colour only for abnormal states. Equipment gets tags (P-101, LIC-101).
- Sounds: cavitation rattle, relief valve hiss, quiet alarm tone.

## Testing
- Physics and control maths in plain classes with JUnit tests (no Minecraft needed).
- NeoForge GameTests for in-world behaviour (`./gradlew runGameTestServer`).
- The owner plays a test world per milestone with a checklist; multiplayer check with `runServer`.
- GitHub Actions builds and tests every push.

## Milestones
- [x] M1 · First machine: pipes, tank, centrifugal pump, cavitation when starved (playtested 20 Oct 2026)
- [x] M2 · Control: control valve (FC/FO, trip), level transmitter, DCS controller with faceplate (PV/SP/OP, MAN/AUTO/CAS, HI/LO, tuning presets), field communicator. Playtested 20 Oct 2026; then made self-linking
- [x] Power 1 · Stirling engine (any furnace fuel, coal is the sensible one, 20 FE/t) and biogas generator (anything compostable + rotten flesh, 30 FE/t). Both have a hopper-fed fuel slot, a 20k FE buffer, push FE to neighbours and only burn when the buffer has room.
- [x] Power 2 · Steam (built and playtested 21 Oct 2026): coal-fired boiler (water drum 8 B, steam 4 B, heats to 100 °C in 10 s, 20 mB/t steam) and steam turbine (place against a boiler, spins up to 3000 rpm, 60 FE/t: three times the FE per coal of a Stirling engine). The drum is a Vessel, so pumps feed it and a controller against it holds the drum level by itself. Low-low level trip on by default (config `boiler.lowLevelTrip`); with it off a dry drum overheats and bursts, and water on a red-hot drum flashes.
- [x] Ore · Hookonium (built and playtested 21 Oct 2026): endgame ore, purple. Stone and deepslate variants, needs a diamond pickaxe, drops Raw Hookonium (Fortune works, Silk Touch gives the ore, 3 to 7 XP), smelts or blasts into a Hookonium Ingot (1 XP). Storage blocks for ingots and raw. Worldgen copies diamond's four vein types exactly (small/medium/large/buried, y 16 to bedrock) through a NeoForge biome modifier on #minecraft:is_overworld. The ore model lays a transparent overlay over vanilla stone/deepslate textures (no Mojang textures copied into the mod).
- [x] Foundations 1 · Help (built 21 Oct 2026): Setpoint Handbook item (own screen, content in tools/handbook-content.mjs, recipes filled in by gen-assets into assets/setpoint/handbook/en_us.json; given on first join via a player attachment, config handbook.giveOnFirstJoin; craft: book + copper ingot), item tooltips (lang tooltip.setpoint.<id> and .more for Shift), advancement tab (data/setpoint/advancement, custom trigger setpoint:plant_event: cavitation, loop_holding, turbine_rated, alarm_acknowledged), comparator output on tanks and boilers (Vessel.comparatorSignal), Jade plugin (compat/jade, compileOnly + localRuntime from Modrinth maven; WTHIT has no 26.3 build yet).
- [x] Foundations 2 · Piping (built 21 Oct 2026): water intake (Vessel that refills every tick with 2+ adjacent water sources), hand valve (gate valve, OPEN + POWERED, rising stem model), pipe wrench (onItemUseFirst: cut/join a pipe arm via FluidPipeBlock CUT properties, turn FACING/AXIS, sneak to pick up; setpoint:wrenchable tag), running sounds (pump hum, turbine whine pitched by speed, firebox crackle; staggered by position), advancements bottomless / lockout / righty_tighty. Pipe runs now follow pipe arms (hydraulics/Piping), and pipes work out their connections in onPlace.
- [x] Foundations 3 · Tanks and flow (built 21 Oct 2026): tall tanks (stacked TankBlockEntity form a column: settle down 2000 mB/t, column handler fills bottom-up and drains top-down, Vessel methods report the column; own() is one block), flow transmitter (inline FT, pumps report flow to meters on their pipe runs; a controller against an FT holds flow, finds the valve with LoopFinder.valveInLine, REVERSE action), other mods' tanks (hydraulics/ForeignVessel over the fluid capability; pumps carry flow until a whole bucket fits for bucket-only storages), new hand-drawn Hookonium art, advancements skyscraper / steady_flow / good_neighbours.
- [ ] Foundations · more: the owner wants the base complete before big new things. Ask what is next.
- [ ] Power 3 · Diesel generator, running on diesel from the M5 distillation column (late game).
- [x] M3 · Control room (built and playtested 21 Oct 2026): DCS console block. Scans all equipment within 32 blocks every second (console/PlantScanner reads each block entity into a Reading), keeps 5 min trends per tag (TrendBuffer), runs an ISA-style alarm list (AlarmList: NEW / ACK / RTN). New alarms chime (note bell, quiet) and the console front flashes (animated texture) until ACK. Screen tabs OVERVIEW / TRENDS / ALARMS; a selected controller opens its faceplate from the console. Live data goes to the open screen as a ConsoleSnapshot payload twice a second.
- [ ] M4 · Pressure: closed vessels, relief valve, overpressure consequences (configurable)
- [ ] M5 · Distillation: multiblock column, reboiler, condenser

## Code notes
- `hydraulics/Vessel` (tank, boiler drum) + `VesselBlock` marker: pumps, pipe runs, controllers and transmitters work with any vessel.
- Physics in plain classes with JUnit tests: `PumpModel`, `BoilerModel`, `TurbineModel`; control in `PidController`.
- Config: `SetpointConfig` (ModConfig.Type.SYNCED; NeoForge 26.3 has no SERVER type).

## Power progression (decided 20 Oct 2026)
Early Stirling engine / biogas, mid steam, late diesel from your own distillation. Gradual but no grind: every tier is built from normal vanilla materials, the next tier is better but never mandatory, the challenge is designing the plant, not gathering.

## Content notes
- Every block has a loot table (gen-assets writes them) and is in `mineable/pickaxe`; a game test checks that every block drops itself.
- Ores are listed from least to most rare on the docs page; give each new ore a rank there.
- Handbook: when a block, item or recipe changes, update tools/handbook-content.mjs too (recipes fill in by themselves), then run gen-assets.

## Backlog
- P&IDs in the game (owner's plan, not designed yet): for example the console showing a P&ID with live values, building or checking a plant from a P&ID, or handing out P&IDs as quests.
- Endgame · Hookonium machines: parked by the owner on 21 Oct 2026 until the foundations are done. Ideas so far: a stronger pump, a wireless transmitter or controller, a reactor with its own control loop, a multiblock mega tank.
- WTHIT support once WTHIT has a build for 26.3.
- GitHub repo https://github.com/ParadiseArchive/setpoint (public) is live; Actions builds and runs all tests on every push.
- Modrinth organisation + CurseForge account named "Hook Development": the owner creates them before the first release.
- Jade/WTHIT tooltips (flow, pressure), JEI/EMI recipes, spark profiling for big pipe networks.

## Assets
- `node tools/gen-assets.mjs` regenerates textures, models, blockstates, item models, recipes and the game test structure. Use `--keep-textures` once textures are drawn by hand.
- `node tools/render-showcase.mjs ../hook-development/projects/minecraft-mods/img` renders the website pictures straight from the models and textures: block renders, real vanilla item icons (read from the Minecraft client jar in the Gradle cache) for every recipe ingredient, and recipes.js for the docs' crafting grids.
- `node tools/draw-logo.mjs <extra paths>` draws the logo (yellow SETPOINT in front of a pixel factory, 256x144) into src/main/resources/logo.png (mod list logo, at 2x) and any extra path (the website).
- **Docs rule (owner, 21 Oct 2026):** every player-visible change (new block or item, recipe, screen, number, behaviour, milestone status) is written into the docs page on the site (`../hook-development/projects/minecraft-mods/setpoint/`) in the same session, the renderer is re-run, and both repos are pushed. Vanilla items always appear with their real textures, never placeholders.
- `sounds.json` reuses vanilla sound files for now (cavitation = campfire crackle at low pitch + bubbles). Custom .ogg files can replace them later.
