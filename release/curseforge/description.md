# Setpoint

**Build a real process plant in Minecraft, then draw it as a P&ID.**

Setpoint is a process engineering mod. Pumps, tanks, valves and controllers behave like the real thing, and every machine has its place on a P&ID (piping and instrumentation diagram), the drawing real plants are run from. Draw your plant with a P&ID sheet, hang it on the wall of your control room, and watch it come alive.

It is easy by default: machines link up and configure themselves, and plain-language messages tell you what is going on. The realistic layer (tuning, manual and cascade modes, NPSH, pressure trips) is there for those who want it.

> **Alpha.** Things will change between versions. Back up your world before updating.

## P&IDs, the heart of the mod

- **Draw your plant.** Pick two corners with a blank P&ID and get a plan view of everything in the box: every pump, tank, valve and instrument as its real P&ID symbol, pipes as lines, and the signal links from controllers to their valves.
- **Live in the control room.** Put a P&ID in the DCS console: equipment that runs lights up, alarms flash, tanks fill to their real level. Click a symbol for its trend.
- **On the wall.** Put P&ID boards side by side for a big mimic board that updates live. Click a hand valve on the board to open or shut it.

## Liquids

- **Pipes, tanks and centrifugal pumps.** Starve a pump and it cavitates. Longer pipes mean more friction and less flow. Every liquid has its own density and viscosity: lava is heavy and slow.
- **Gravity.** Liquid runs downhill by itself between vessels. A pipe on a higher block is an overflow.
- **Valves.** Hand valves, check valves, drain valves and control valves.
- **Drums and IBCs.** Containers you carry around, liquid and all, in any colour you dye them.
- **Floating roof tank.** A Hookonium tank of eight by eight by six blocks for 8000 buckets, placed with one block. Its roof floats on the liquid, rising and sinking with the level, and you can walk on it.

## Control

- **Transmitters.** Level, flow and pressure.
- **DCS controllers.** Place one against a tank and it finds its own valve and holds the level at 50 %. Open its faceplate to change the setpoint, tune the PID, or switch between AUTO, MAN and CAS.
- **DCS console.** Overview, five-minute trends and an ISA-style alarm list for everything within 32 blocks.

## Power and pressure

- **Stirling engine and biogas generator** for the start.
- **Coal-fired boiler and steam turbine.** Steam builds real pressure: the burner trips at 9 bar, a safety valve lifts at its set pressure, and a boiler without protection bursts.

## Gases

- Hydrogen, oxygen, nitrogen and steam, counted in mB at 1 bar with real pressure. Gas flows by itself from high pressure to low through steel gas pipes.
- An **electrolyser** splits water into hydrogen and oxygen, and a **nitrogen generator** takes nitrogen from the air.
- **Gas cylinders** are colour-coded like real ones. The boiler sends its steam down a gas pipe to a turbine anywhere.

## Logistics

- Wooden **pallets** for drums or an IBC.
- An electric **pallet truck** that follows you.
- A **forklift** you drive, with rear-wheel steering and forks up to six blocks high.

## Hookonium

A purple endgame ore, as rare as diamond, for the big machines.

## Help in the game

- The **Setpoint Handbook** is given on first join.
- Every item has a tooltip (hold Shift for details), and there is an advancement tab.
- Works with **Jade** (equipment status when you look at a machine), and every recipe shows in **JEI**.

## Requirements

- Minecraft 26.3
- NeoForge 26.3.0.95 or newer

## Links

- Full documentation, with every machine and recipe: https://hookdev.io/projects/minecraft-mods/setpoint/
- Bugs and ideas: https://github.com/ParadiseArchive/setpoint/issues
- Source code (MIT): https://github.com/ParadiseArchive/setpoint

## How it is made

Setpoint is designed by Hook Development: the ideas, the gameplay and the realism checks. The code and the first textures are written with Claude, an AI assistant. The pictures on this page are made by hand.
