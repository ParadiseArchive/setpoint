# Setpoint

Process engineering for Minecraft, by [Hook Development](https://hookdev.io).

Build a working process plant of pumps, tanks, valves and controllers, run it from a DCS console, and draw it as
a live P&ID. Easy by default, real physics underneath.

**Status:** alpha (0.1.0-alpha). Things still change between versions: back up your world before updating.
Full documentation: https://hookdev.io/projects/minecraft-mods/setpoint/

## What is in it
- **P&IDs:** draw your plant as a P&ID, show it live in the DCS console or on a wall of P&ID boards.
- **Liquids:** pipes, tanks, centrifugal pumps that cavitate, hand, check, drain and control valves, gravity flow,
  drums, IBCs and a floating roof tank.
- **Control:** level, flow and pressure transmitters, self-linking DCS controllers with faceplates, a DCS console
  with trends and alarms.
- **Power and pressure:** Stirling engine, biogas generator, a coal-fired boiler with real steam pressure, a safety
  valve and a steam turbine.
- **Gases:** hydrogen, oxygen, nitrogen and steam with real pressure, gas pipes, an electrolyser, a nitrogen
  generator and gas cylinders.
- **Logistics:** pallets, an electric pallet truck and a forklift.
- **Hookonium:** a purple endgame ore.

## How it is made
Designed by Hook Development; the code and the first textures are written with Claude, an AI assistant.

## Platform
Minecraft 26.3 · NeoForge · Java 25

## Building
```
./gradlew build               # jar in build/libs
./gradlew runClient           # start a development client
./gradlew runGameTestServer   # run the automated in-game tests
```

## License
MIT, see [LICENSE](LICENSE). Built on the NeoForge MDK (see TEMPLATE_LICENSE.txt).
Minecraft names used in code come from Mojang's official mappings, see https://github.com/NeoForged/NeoForm/blob/main/Mojang.md.
