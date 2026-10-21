package io.hookdev.setpoint.pid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.hookdev.setpoint.block.BoilerBlock;
import io.hookdev.setpoint.block.ConsoleBlock;
import io.hookdev.setpoint.block.ControlValveBlock;
import io.hookdev.setpoint.block.CheckValveBlock;
import io.hookdev.setpoint.block.ControllerBlock;
import io.hookdev.setpoint.block.DrainValveBlock;
import io.hookdev.setpoint.block.CreativeGeneratorBlock;
import io.hookdev.setpoint.block.FlowTransmitterBlock;
import io.hookdev.setpoint.block.FluidPipeBlock;
import io.hookdev.setpoint.block.GeneratorBlock;
import io.hookdev.setpoint.block.HandValveBlock;
import io.hookdev.setpoint.block.LevelTransmitterBlock;
import io.hookdev.setpoint.block.PressureTransmitterBlock;
import io.hookdev.setpoint.block.PumpBlock;
import io.hookdev.setpoint.block.SafetyValveBlock;
import io.hookdev.setpoint.block.SteamTurbineBlock;
import io.hookdev.setpoint.block.TankBlock;
import io.hookdev.setpoint.block.WaterIntakeBlock;
import io.hookdev.setpoint.block.entity.BoilerBlockEntity;
import io.hookdev.setpoint.block.entity.ControllerBlockEntity;
import io.hookdev.setpoint.block.entity.InstrumentBlockEntity;
import io.hookdev.setpoint.block.entity.NamedEquipment;
import io.hookdev.setpoint.block.entity.SteamTurbineBlockEntity;
import io.hookdev.setpoint.block.entity.TankBlockEntity;
import io.hookdev.setpoint.block.entity.WaterIntakeBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws a P&ID of the Setpoint equipment in a box: a plan view where every column of blocks becomes one cell
 * showing its most important piece: process equipment, then controllers, power and consoles, then valves,
 * then instruments, then pipe. Between equals the top one wins, as seen from above. A tagged instrument hidden under
 * something bigger (a safety valve on a boiler, a transmitter on a tank) is drawn in a free cell next to it instead.
 * A box deeper than it is wide is turned a quarter, so the drawing always fits the screen.
 */
public final class PidCapture {
    public static final int MAX_LONG = 32, MAX_SHORT = 24;
    /** A drawing always covers this many blocks up from the lowest corner, so you only pick the corners on the ground. */
    public static final int HEIGHT = 10;

    /** The drawing, or why it could not be made. */
    public record Result(PidDrawing drawing, String error) {}

    private PidCapture() {}

    private record Found(int symbol, int rotation, String tag, int priority) {}

    public static Result capture(Level level, BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()), maxY = minY + HEIGHT - 1;
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        int w = maxX - minX + 1, d = maxZ - minZ + 1, h = maxY - minY + 1;
        if (Math.max(w, d) > MAX_LONG || Math.min(w, d) > MAX_SHORT) {
            return new Result(null, "Too big: " + w + " x " + d + ". A P&ID covers up to " + MAX_LONG + " x " + MAX_SHORT + " blocks.");
        }

        Found[][] found = new Found[w][d];
        int[][] pipes = new int[w][d];
        List<int[]> hiddenAt = new ArrayList<>(); // x, z of tagged pieces that lost their column
        List<Found> hidden = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) for (int y = 0; y < h; y++) {
            pos.set(minX + x, minY + y, minZ + z);
            BlockState state = level.getBlockState(pos);
            Found here = classify(level, pos, state);
            if (here == null) continue;
            Found loser = here;
            if (found[x][z] == null || here.priority >= found[x][z].priority) { // ties: the top one, as seen from above
                loser = found[x][z];
                found[x][z] = here;
            }
            if (loser != null && !loser.tag.isEmpty()) { hidden.add(loser); hiddenAt.add(new int[]{x, z}); }
            if (state.getBlock() instanceof FluidPipeBlock) {
                for (Direction dir : Direction.values()) {
                    if (!state.getValue(FluidPipeBlock.CONNECTIONS.get(dir))) continue;
                    pipes[x][z] |= bit(dir);
                    // the neighbour in that direction gets a stub, so the line meets its symbol
                    int nx = x + dir.getStepX(), nz = z + dir.getStepZ();
                    if (dir.getAxis() != Direction.Axis.Y && nx >= 0 && nz >= 0 && nx < w && nz < d) pipes[nx][nz] |= bit(dir.getOpposite());
                }
            }
        }

        // hidden instruments move to a free cell beside their column (not one with a pipe through it)
        for (int i = 0; i < hidden.size(); i++) {
            Found f = hidden.get(i);
            int x = hiddenAt.get(i)[0], z = hiddenAt.get(i)[1];
            if (found[x][z] != null && found[x][z].tag.equals(f.tag)) continue; // the same thing, like a tall tank
            for (int[] step : new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
                int nx = x + step[0], nz = z + step[1];
                if (nx < 0 || nz < 0 || nx >= w || nz >= d || found[nx][nz] != null || pipes[nx][nz] != 0) continue;
                found[nx][nz] = f;
                break;
            }
        }

        boolean turn = d > w; // keep the long side across the screen
        int width = turn ? d : w, height = turn ? w : d;
        List<PidDrawing.Cell> cells = new ArrayList<>();
        Map<String, Integer> byTag = new HashMap<>();
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
            Found f = found[x][z];
            if (f == null) continue;
            int cx = turn ? d - 1 - z : x, cy = turn ? x : z;
            int rotation = (f.rotation + (turn ? 1 : 0)) % 4;
            int mask = turn ? turnClockwise(pipes[x][z]) : pipes[x][z];
            if (!f.tag.isEmpty()) byTag.putIfAbsent(f.tag, cells.size());
            cells.add(new PidDrawing.Cell(cx, cy, f.symbol, rotation, mask, f.tag));
        }

        // dashed signal lines: every controller to its valve and its transmitter
        List<PidDrawing.Link> links = new ArrayList<>();
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) for (int y = 0; y < h; y++) {
            pos.set(minX + x, minY + y, minZ + z);
            if (!(level.getBlockEntity(pos) instanceof ControllerBlockEntity controller)) continue;
            Integer from = byTag.get(controller.tag());
            if (from == null) continue;
            String measured = controller.pvSource();
            if ("LOCAL".equals(measured)) { // it measures the tank or boiler it sits against
                for (Direction dir : Direction.values()) {
                    BlockEntity next = level.getBlockEntity(pos.relative(dir));
                    if (next instanceof TankBlockEntity tank) measured = tank.tag();
                    else if (next instanceof BoilerBlockEntity boiler) measured = boiler.tag();
                }
            }
            for (String target : new String[]{controller.mvTag(), measured}) {
                Integer to = byTag.get(target);
                if (to != null && !to.equals(from)) links.add(new PidDrawing.Link(from, to));
            }
        }
        return new Result(new PidDrawing(PidDrawing.UNTITLED, width, height, cells, links), null);
    }

    private static Found classify(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        BlockEntity be = level.getBlockEntity(pos);
        String tag = be instanceof InstrumentBlockEntity instrument ? instrument.tag() : "";
        if (block instanceof PumpBlock && be instanceof NamedEquipment pump) return new Found(PidDrawing.PUMP, quarter(state.getValue(PumpBlock.FACING)), pump.tag(), 5);
        if (block instanceof TankBlock && be instanceof TankBlockEntity tank) {
            return new Found(PidDrawing.TANK, 0, tank.tag(), 5);
        }
        if (block instanceof BoilerBlock && be instanceof BoilerBlockEntity boiler) return new Found(PidDrawing.BOILER, 0, boiler.tag(), 5);
        if (block instanceof SteamTurbineBlock && be instanceof SteamTurbineBlockEntity turbine) {
            return new Found(PidDrawing.TURBINE, quarter(state.getValue(SteamTurbineBlock.FACING)), turbine.tag(), 5);
        }
        if ((block instanceof GeneratorBlock || block instanceof CreativeGeneratorBlock) && be instanceof NamedEquipment generator) {
            return new Found(PidDrawing.GENERATOR, 0, generator.tag(), 4);
        }
        if (block instanceof WaterIntakeBlock && be instanceof WaterIntakeBlockEntity intake) return new Found(PidDrawing.INTAKE, 0, intake.tag(), 5);
        if (block instanceof ConsoleBlock) return new Found(PidDrawing.CONSOLE, 0, "", 4);
        if (block instanceof ControllerBlock) return new Found(PidDrawing.CONTROLLER, 0, tag, 4);
        if (block instanceof ControlValveBlock) return new Found(PidDrawing.CONTROL_VALVE, axisQuarter(state.getValue(ControlValveBlock.AXIS)), tag, 3);
        if (block instanceof HandValveBlock) {
            return new Found(HandValveBlock.isOpen(state) ? PidDrawing.HAND_VALVE : PidDrawing.HAND_VALVE_SHUT,
                    axisQuarter(state.getValue(HandValveBlock.AXIS)), "", 3);
        }
        if (block instanceof CheckValveBlock) {
            Direction arrow = state.getValue(CheckValveBlock.FACING);
            return new Found(PidDrawing.CHECK_VALVE, arrow.getAxis() == Direction.Axis.Y ? 1 : quarter(arrow), "", 3);
        }
        if (block instanceof DrainValveBlock) {
            Direction outlet = state.getValue(DrainValveBlock.FACING);
            return new Found(PidDrawing.DRAIN_VALVE, outlet.getAxis() == Direction.Axis.Y ? 0 : quarter(outlet), "", 3);
        }
        if (block instanceof SafetyValveBlock) return new Found(PidDrawing.SAFETY_VALVE, 0, tag, 3);
        if (block instanceof PressureTransmitterBlock) return new Found(PidDrawing.PRESSURE_TRANSMITTER, 0, tag, 2);
        if (block instanceof FlowTransmitterBlock) return new Found(PidDrawing.FLOW_TRANSMITTER, axisQuarter(state.getValue(FlowTransmitterBlock.AXIS)), tag, 2);
        if (block instanceof LevelTransmitterBlock) return new Found(PidDrawing.LEVEL_TRANSMITTER, 0, tag, 2);
        if (block instanceof FluidPipeBlock) return new Found(PidDrawing.PIPE, 0, "", 1);
        return null;
    }

    /** Quarter turns clockwise from east. */
    private static int quarter(Direction facing) {
        return switch (facing) {
            case SOUTH -> 1;
            case WEST -> 2;
            case NORTH -> 3;
            default -> 0;
        };
    }

    private static int axisQuarter(Direction.Axis axis) {
        return axis == Direction.Axis.Z ? 1 : 0;
    }

    private static int bit(Direction direction) {
        return switch (direction) {
            case NORTH -> PidDrawing.NORTH;
            case EAST -> PidDrawing.EAST;
            case SOUTH -> PidDrawing.SOUTH;
            case WEST -> PidDrawing.WEST;
            case UP -> PidDrawing.UP;
            case DOWN -> PidDrawing.DOWN;
        };
    }

    private static int turnClockwise(int mask) {
        int out = mask & (PidDrawing.UP | PidDrawing.DOWN);
        if ((mask & PidDrawing.NORTH) != 0) out |= PidDrawing.EAST;
        if ((mask & PidDrawing.EAST) != 0) out |= PidDrawing.SOUTH;
        if ((mask & PidDrawing.SOUTH) != 0) out |= PidDrawing.WEST;
        if ((mask & PidDrawing.WEST) != 0) out |= PidDrawing.NORTH;
        return out;
    }
}
