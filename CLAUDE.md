# Setpoint (Minecraft mod)

Process engineering mod for Minecraft by Hook Development. Showcased on https://hookdev.io/projects/minecraft-mods/ (site repo: `../hook-development`, see its CLAUDE.md).

## Working with the owner
- Talk to the owner in **Dutch**. All mod text (item names, tooltips, README, changelogs) is **English**.
- The owner knows from practice how real pumps, valves, controllers and DCS screens behave: use them as the realism check. They do not write Java; do the technical work, explain in plain language, one clear step at a time.
- **Privacy:** this repository is public. Never add the owner's name or personal details anywhere (code, docs, metadata, Modrinth/CurseForge pages, commit messages). The author is "Hook Development".
- Ideas go in the backlog below instead of being built all at once.

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
- [x] Power 2 · Steam (built 21 Oct 2026, playtest pending): coal-fired boiler (water drum 8 B, steam 4 B, heats to 100 °C in 10 s, 20 mB/t steam) and steam turbine (place against a boiler, spins up to 3000 rpm, 60 FE/t: three times the FE per coal of a Stirling engine). The drum is a Vessel, so pumps feed it and a controller against it holds the drum level by itself. Low-low level trip on by default (config `boiler.lowLevelTrip`); with it off a dry drum overheats and bursts, and water on a red-hot drum flashes.
- [ ] Power 3 · Diesel generator, running on diesel from the M5 distillation column (late game).
- [ ] M3 · Control room: DCS screen with overview, trends, alarms
- [ ] M4 · Pressure: closed vessels, relief valve, overpressure consequences (configurable)
- [ ] M5 · Distillation: multiblock column, reboiler, condenser

## Code notes
- `hydraulics/Vessel` (tank, boiler drum) + `VesselBlock` marker: pumps, pipe runs, controllers and transmitters work with any vessel.
- Physics in plain classes with JUnit tests: `PumpModel`, `BoilerModel`, `TurbineModel`; control in `PidController`.
- Config: `SetpointConfig` (ModConfig.Type.SYNCED; NeoForge 26.3 has no SERVER type).

## Power progression (decided 20 Oct 2026)
Early Stirling engine / biogas, mid steam, late diesel from your own distillation. Gradual but no grind: every tier is built from normal vanilla materials, the next tier is better but never mandatory, the challenge is designing the plant, not gathering.

## Backlog
- GitHub repo https://github.com/ParadiseArchive/setpoint (public) is live; Actions builds and runs all tests on every push.
- Modrinth organisation + CurseForge account named "Hook Development": the owner creates them before the first release.
- Jade/WTHIT tooltips (flow, pressure), JEI/EMI recipes, spark profiling for big pipe networks.

## Assets
- `node tools/gen-assets.mjs` regenerates textures, models, blockstates, item models, recipes and the game test structure. Use `--keep-textures` once textures are drawn by hand.
- `node tools/render-showcase.mjs <folder>` renders isometric block pictures for the website straight from the models and textures.
- `sounds.json` reuses vanilla sound files for now (cavitation = campfire crackle at low pitch + bubbles). Custom .ogg files can replace them later.
