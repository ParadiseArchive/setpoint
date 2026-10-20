# Setpoint (Minecraft mod)

Process engineering mod for Minecraft by Hook Development. Showcased on https://hookdev.io/projects/minecraft-mods/ (site repo: `../hook-development`, see its CLAUDE.md).

## Working with the owner
- Talk to the owner in **Dutch**. All mod text (item names, tooltips, README, changelogs) is **English**.
- The owner knows from practice how real pumps, valves, controllers and DCS screens behave: use them as the realism check. They do not write Java; do the technical work, explain in plain language, one clear step at a time.
- **Privacy:** this repository is public. Never add the owner's name or personal details anywhere (code, docs, metadata, Modrinth/CurseForge pages, commit messages). The author is "Hook Development".
- Ideas go in the backlog below instead of being built all at once.

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
- [ ] M2 · Control: control valve, PID controller with faceplate (SP/PV/OP, auto/manual)
- [ ] M3 · Control room: DCS screen with overview, trends, alarms
- [ ] M4 · Pressure: closed vessels, relief valve, overpressure consequences (configurable)
- [ ] M5 · Distillation: multiblock column, reboiler, condenser

## Backlog
- GitHub repo https://github.com/ParadiseArchive/setpoint (public) is live; Actions builds and runs all tests on every push.
- Modrinth organisation + CurseForge account named "Hook Development": the owner creates them before the first release.
- Jade/WTHIT tooltips (flow, pressure), JEI/EMI recipes, spark profiling for big pipe networks.

## Assets
- `node tools/gen-assets.mjs` regenerates textures, models, blockstates, item models, recipes and the game test structure. Use `--keep-textures` once textures are drawn by hand.
- `sounds.json` reuses vanilla sound files for now (cavitation = campfire crackle at low pitch + bubbles). Custom .ogg files can replace them later.
