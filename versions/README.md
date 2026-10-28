# One mod, several Minecraft versions

Setpoint is built for **Minecraft 26.3** and **26.1.2** (the version of All the Mods 11) from the same code.

```
./gradlew build                 # 26.3: build/libs/setpoint-<version>+mc26.3.jar, plays in run/
./gradlew build -Pmc=26.1       # 26.1.2: build-26.1/libs/setpoint-<version>+mc26.1.2.jar, plays in run-26.1/
./gradlew runClient -Pmc=26.1   # the same for runClient, runGameTestServer, ...
```

## What lives where

- `src/` is shared: every block, machine, screen, recipe and texture is written once, for both versions.
- `versions/<mc>/gradle.properties`: the Minecraft and NeoForge versions, Jade and JEI, the dev client mods.
- `versions/<mc>/java`: the few classes that differ. The shared code calls them, each version has its own copy:
  - `McVersion`: dropping an item, fuel and burn time, compost, bone meal, the world config, an ore vein.
  - `client/McVersionClient`: turning a model while drawing it, opening a screen, the hotbar message.
  - `block/SetpointEntityBlock`: the base of every block with a block entity (26.1 wants a codec).
  - `entity/InterpolatedVehicle`: a vehicle that glides for other players (the forklift).
  - `advancement/PlantEventTriggerBase`: the advancement trigger (`PlantEventTrigger` keeps the events).
  - 26.1 only: `mixin/FlowingFluidMixin`, so water and lava flow round Setpoint blocks as on 26.3.
- `versions/26.1/resources`: data files that 26.1 reads differently (loot tables, ore veins). **Generated** by
  `node tools/port-26.1.mjs` (gen-assets.mjs runs it at the end) from the 26.3 files; never edit them by hand.
  `resources_exclude` in `versions/26.1/gradle.properties` leaves the 26.3 originals out of the 26.1 jar.

## Adding something new

1. Write it once in `src/`, as for 26.3, and run gen-assets as usual.
2. Build and test both: `./gradlew build runGameTestServer` and `./gradlew build runGameTestServer -Pmc=26.1`.
3. If 26.1 does not compile, the new code uses something 26.1 names differently: add a method to `McVersion`
   (or `McVersionClient`) in both `versions/26.3/java` and `versions/26.1/java`, and call that from `src/`.
4. If 26.1 builds but a test fails on a data file, teach `tools/port-26.1.mjs` the difference.

GitHub builds and tests both versions on every push (build.yml); a release tag uploads both jars (publish.yml).
To drop a version, delete its folder here and its line in the build.yml matrix.
