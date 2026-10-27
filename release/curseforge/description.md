# Setpoint

![Setpoint: process engineering you can play](https://raw.githubusercontent.com/ParadiseArchive/setpoint/main/.github/readme/banner.png)

**Build a real process plant in Minecraft, then draw it as a P&ID.**

Setpoint is a process engineering mod. Pumps, tanks, valves and controllers behave like the real thing, and every machine has its place on a P&ID (piping and instrumentation diagram), the drawing real plants are run from. Draw your plant with a P&ID sheet, hang it on the wall of your control room, and watch it come alive.

It is easy by default: machines link up and configure themselves, and plain-language messages tell you what is going on. The realistic layer (tuning, manual and cascade modes, NPSH, pressure trips) is there for those who want it.

## P&IDs, the heart of the mod

- **Draw your plant.** Pick two corners with a blank P&ID and get a plan view of everything in the box: every pump, tank, valve and instrument as its real P&ID symbol, pipes as lines, and the signal links from controllers to their valves.
- **Live in the control room.** Put a P&ID in the DCS console: equipment that runs lights up, alarms flash, tanks fill to their real level. Click a symbol for its trend.
- **On the wall.** Put P&ID boards side by side for a big mimic board that updates live, and run the plant from it: open a controller's faceplate, start and stop pumps, open and shut hand valves.

![A P&ID of a small steam plant](https://raw.githubusercontent.com/ParadiseArchive/setpoint/main/.github/readme/pid.png)

![The same plant live on a P&ID board](https://raw.githubusercontent.com/ParadiseArchive/setpoint/main/.github/readme/board.png)

## Liquids

- **Pipes, tanks and centrifugal pumps.** Starve a pump and it cavitates. Longer pipes mean more friction and less flow. Every liquid has its own density and viscosity: lava is heavy and slow.
- **Gravity.** Liquid runs downhill by itself between vessels. A pipe on a higher block is an overflow.
- **Valves.** Hand valves, check valves, drain valves and control valves.
- **Drums and IBCs.** Containers you carry around, liquid and all, in any colour you dye them.
- **Floating roof tank.** A tank of eight by eight by six blocks for 8000 buckets, placed with one block. Its roof floats on the liquid, rising and sinking with the level, and you can walk on it.

![Water intake, pump, valves, tanks, drum, IBC and floating roof tank](https://raw.githubusercontent.com/ParadiseArchive/setpoint/main/.github/readme/shelf-liquids.png)

## Control

- **Transmitters.** Level, flow and pressure.
- **DCS controllers.** Place one against a tank and it finds its own valve and holds the level at 50 %. Open its faceplate to change the setpoint, tune the PID, or switch between AUTO, MAN and CAS.
- **DCS console.** Overview, five-minute trends and an ISA-style alarm list for everything within 32 blocks.

![Transmitters, controller, field communicator, DCS console, P&ID board, alarm beacon, emergency stop and a control room](https://raw.githubusercontent.com/ParadiseArchive/setpoint/main/.github/readme/shelf-control.png)

## The control room

- **Video wall.** Overview screens side by side make one wall of up to 8 by 6 blocks, showing the plant overview, the alarm list, the trends or your P&ID live.
- **Alarm beacon.** An amber light that turns and sounds while an alarm waits for an ACK.
- **Emergency stop.** A red mushroom button that shuts the whole plant down at once.
- **Radios.** Pick one up and the field operator calls in what needs a look, worst first.
- **Console desk.** The DCS console built into a desk, with its three screens on top.
- **Printer.** Feed it paper and it prints the alarm log of the nearest console as a book, for the shift handover.
- **Furniture.** Control desks, operator screens, a 24-hour chair you can sit in, a raised floor, ceiling lights, a wall clock with the time of day, filing cabinets, a coffee machine, an exit sign, a blast door and wired glass.

![The DCS console overview](https://raw.githubusercontent.com/ParadiseArchive/setpoint/main/.github/readme/console.png)

![A controller faceplate](https://raw.githubusercontent.com/ParadiseArchive/setpoint/main/.github/readme/faceplate.png)

## Power

- **Stirling engine and biogas generator** for the start.
- **Coal-fired boiler and steam turbine.** Steam builds real pressure: the burner trips at 9 bar, a safety valve lifts at its set pressure, and a boiler without protection bursts.
- **Oil and gas burner.** Set it under a boiler and it fires it on naphtha, kerosene, diesel, residue, LPG or hydrogen. Other mods' pipes on top of a boiler take its steam.
- **Diesel generator** at the top of the power ladder, on diesel from your own refinery.
- **Fuel cell.** Hydrogen and oxygen back into power: with an electrolyser and a gas sphere it makes a battery.

![Stirling engine, biogas generator, boiler, safety valve, steam turbine, diesel generator and fuel cell](https://raw.githubusercontent.com/ParadiseArchive/setpoint/main/.github/readme/shelf-power.png)

## Gases

- Hydrogen, oxygen, nitrogen, steam, ammonia and LPG, counted in mB at 1 bar with real pressure. Gas flows by itself from high pressure to low through steel gas pipes.
- An **electrolyser** splits water into hydrogen and oxygen, and a **nitrogen generator** takes nitrogen from the air.
- **Gas cylinders** are colour-coded like real ones, and a **gas sphere** on eight columns holds 3520 buckets of gas at 16 bar.
- A **compressor** pushes gas from one line into another, up to 200 bar.
- **Gas valves** shut a line, and a **gas control valve** with a pressure transmitter and a controller holds the pressure of a line.
- Oxygen piped into a boiler makes its coal last longer. An **ammonia reactor** turns nitrogen and hydrogen into ammonia, and like a real one it wants 100 bar. A **fertilizer mixer** makes fertilizer from ammonia and bone meal that grows every crop in a three by three at once.

## Refinery

- A **pumpjack** nods on its well anywhere you put it and pumps crude oil out of the ground.
- A **distillation column**, eleven blocks high, splits crude oil like a real crude unit, with steam in its reboiler and cooling water in its condenser: LPG at the top, then naphtha, kerosene and diesel, and residue at the bottom.
- Crude oil and naphtha give off vapour in an ordinary tank: blanket them with nitrogen, or keep them in a floating roof tank.

![Electrolyser, nitrogen generator, compressor, gas cylinders, ammonia reactor, fertilizer mixer, gas sphere, pumpjack, distillation column and flare stack](https://raw.githubusercontent.com/ParadiseArchive/setpoint/main/.github/readme/shelf-gases.png)

## The flare stack

The tallest thing in the mod: a flare forty blocks high in a red and white derrick, placed with one block. It burns what the plant cannot keep: safety valves piped to its header blow off into it, and it takes the excess off every vessel on the header above its set pressure. A pilot flame keeps it lit, the flame grows with the gas up to sixteen blocks and leans with the wind, and LPG smokes black unless you blow steam into it. Climb the caged ladder to the top platform, right under the flame.

## Logistics

- Wooden **pallets** for drums or an IBC.
- An electric **pallet truck** that follows you.
- A **forklift** you drive, with rear-wheel steering and forks up to six blocks high.
- **Pallet racking** to store loaded pallets up high.

![Pallet, pallet truck, forklift and pallet racking](https://raw.githubusercontent.com/ParadiseArchive/setpoint/main/.github/readme/shelf-logistics.png)

## Factory blocks

Build round your plant with steel grating, grating stairs, handrails, caged ladders, steel I-beams, checker plate, hazard stripes, corrugated steel and industrial lamps.

## Hookonium

A purple endgame ore, as rare as diamond, for the big machines.

## Help in the game

- The **Setpoint Handbook** is given on first join.
- Every item has a tooltip (hold Shift for details), and there is an advancement tab.
- Works with **Jade** (equipment status when you look at a machine). In **JEI** every recipe shows, and every machine has a tab with what goes in and what comes out.

## Requirements

- Minecraft 26.3
- NeoForge 26.3.0.95 or newer

## Links

- Full documentation, with every machine and recipe: https://hookdev.io/projects/minecraft-mods/setpoint/
- Bugs and ideas: https://github.com/ParadiseArchive/setpoint/issues
- Source code (MIT): https://github.com/ParadiseArchive/setpoint

## How it is made

Setpoint is designed by Hook Development: the ideas, the gameplay and the realism checks. The code and the first textures are written with Claude, an AI assistant.

*Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.*
