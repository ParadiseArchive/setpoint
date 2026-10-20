# Setpoint

Process engineering for Minecraft, by [Hook Development](https://hookdev.io).

Pumps that cavitate when you starve them, tanks that overpressure when you ignore them,
control valves and PID loops you have to tune, and a control room to run it all.

**Status:** early development, nothing playable yet.

## Roadmap
| Milestone | What |
|---|---|
| M1 · First machine | Pipes, a tank and a centrifugal pump. Starve the pump and it cavitates. |
| M2 · Control | Control valve and PID controller with a faceplate. Your first level loop. |
| M3 · Control room | DCS screen block with overview, trends and alarms. |
| M4 · Pressure | Closed vessels, pressure and relief valves. Overpressure has consequences. |
| M5 · Distillation | A multiblock distillation column with reboiler and condenser. |

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
