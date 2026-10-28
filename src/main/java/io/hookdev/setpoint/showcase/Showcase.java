package io.hookdev.setpoint.showcase;

import java.util.ArrayList;
import java.util.List;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.ControlValveBlock;
import io.hookdev.setpoint.block.DrainValveBlock;
import io.hookdev.setpoint.block.FluidPipeBlock;
import io.hookdev.setpoint.block.FurnitureBlock;
import io.hookdev.setpoint.block.GasMakerBlock;
import io.hookdev.setpoint.block.PidBoardBlock;
import io.hookdev.setpoint.block.PumpBlock;
import io.hookdev.setpoint.block.entity.BoilerBlockEntity;
import io.hookdev.setpoint.block.entity.BurnerBlockEntity;
import io.hookdev.setpoint.block.entity.ConsoleBlockEntity;
import io.hookdev.setpoint.block.entity.ControllerBlockEntity;
import io.hookdev.setpoint.block.entity.DieselGeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.DistillationColumnBlockEntity;
import io.hookdev.setpoint.block.entity.FlareBlockEntity;
import io.hookdev.setpoint.block.entity.GasCylinderBlockEntity;
import io.hookdev.setpoint.block.entity.GasSphereBlockEntity;
import io.hookdev.setpoint.block.entity.PidBoardBlockEntity;
import io.hookdev.setpoint.block.entity.TankBlockEntity;
import io.hookdev.setpoint.gas.Gases;
import io.hookdev.setpoint.gas.Liquids;
import io.hookdev.setpoint.physics.BoilerModel;
import io.hookdev.setpoint.pid.PidCapture;
import io.hookdev.setpoint.pid.PidDrawing;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Builds a working example plant, the way the mod is meant to be played: water and steam, a wind turbine, a refinery whose column
 * stands on a steel frame so every cut runs down to its tank by itself, gases with a sphere and a flare, three control
 * loops, and a control room with the plant's own P&ID. Every product tank overflows into an export tank that a drain
 * valve empties, the way a real plant ships what it makes, so nothing ever fills up and the plant keeps running.
 * It stands in a box of 32 by 32 blocks east and south of the origin: the plant in the first 24 rows (one P&ID covers
 * it), the control room behind.
 */
public final class Showcase {
    /** The plant, as one P&ID can draw it. */
    public static final int WIDTH = 32, DEPTH = 24;
    /** The whole build, with the control room, and how high it reaches (the flare). */
    public static final int FULL_DEPTH = 32, HEIGHT = 45;
    /** The column stands this high on its steel frame. */
    private static final int LIFT = 2;

    private final ServerLevel level;
    private final BlockPos origin;
    private final List<BlockPos> pipes = new ArrayList<>();

    private Showcase(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin.immutable();
    }

    /** Builds the plant with its north-west corner at the origin (the origin is the first block above the ground). */
    public static void build(ServerLevel level, BlockPos origin) {
        new Showcase(level, origin).build();
    }

    /** Lights the flare once its pilot gas is there (right after building). */
    public static void ignite(ServerLevel level, BlockPos origin) {
        if (level.getBlockEntity(origin.offset(28, 0, 12)) instanceof FlareBlockEntity flare) flare.ignite(null);
    }

    private BlockPos p(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    private void set(int x, int y, int z, BlockState state) {
        level.setBlock(p(x, y, z), state, Block.UPDATE_ALL);
    }

    private void set(int x, int y, int z, Block block) {
        set(x, y, z, block.defaultBlockState());
    }

    /** A liquid pipe, cut on the given sides so it does not join a pipe or vessel beside it. */
    private void pipe(int x, int y, int z, Direction... cut) {
        BlockState state = SetpointContent.PIPE.get().defaultBlockState();
        for (Direction d : cut) state = state.setValue(FluidPipeBlock.CUT.get(d), true);
        set(x, y, z, state);
        pipes.add(p(x, y, z));
    }

    private void gasPipe(int x, int y, int z) {
        set(x, y, z, SetpointContent.GAS_PIPE.get());
        pipes.add(p(x, y, z));
    }

    private void pump(int x, int y, int z, Direction discharge) {
        set(x, y, z, SetpointContent.PUMP.get().defaultBlockState().setValue(PumpBlock.FACING, discharge));
    }

    /** A Stirling engine with a stack of coal blocks: hours of power for whatever it touches. */
    private void stirling(int x, int y, int z) {
        set(x, y, z, SetpointContent.STIRLING_ENGINE.get());
        if (level.getBlockEntity(p(x, y, z)) instanceof io.hookdev.setpoint.block.entity.GeneratorBlockEntity engine) engine.insertFromHand(new ItemStack(net.minecraft.world.item.Items.COAL_BLOCK, 64));
    }

    /** A tank this many blocks high. */
    private void tank(int x, int z, int high) {
        for (int y = 0; y < high; y++) set(x, y, z, SetpointContent.TANK.get());
    }

    /** An export tank: what overflows into it goes out through an open drain valve, like product loaded for shipping. */
    private void export(int x, int z, Direction valve) {
        set(x, 0, z, SetpointContent.TANK.get());
        set(x + valve.getStepX(), 0, z + valve.getStepZ(), SetpointContent.DRAIN_VALVE.get().defaultBlockState()
                .setValue(DrainValveBlock.FACING, valve).setValue(DrainValveBlock.OPEN, true));
    }

    /** A multiblock, placed with one block like a player would. */
    private void multiblock(int x, int y, int z, BlockState state, ItemStack item) {
        BlockPos pos = p(x, y, z);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        state.getBlock().setPlacedBy(level, pos, level.getBlockState(pos), null, item);
    }

    private void cylinder(int x, int y, int z, Gases.@Nullable Gas gas, int amount) {
        set(x, y, z, SetpointContent.GAS_CYLINDER.get());
        if (gas != null) ((GasCylinderBlockEntity) level.getBlockEntity(p(x, y, z))).tank().set(gas.resource(), amount);
    }

    private void build() {
        clear();
        water();
        steam();
        refinery();
        products();
        gases();
        wind();
        for (BlockPos pos : pipes) level.setBlock(pos, FluidPipeBlock.refresh(level.getBlockState(pos), level, pos), Block.UPDATE_ALL);
        controlRoom();
    }

    /** Clears the space and lays a floor of stone under it all. */
    private void clear() {
        for (int x = -1; x <= WIDTH; x++) for (int z = -1; z <= FULL_DEPTH; z++) {
            for (int y = 0; y < HEIGHT; y++) set(x, y, z, Blocks.AIR);
            set(x, -1, z, Blocks.SMOOTH_STONE);
            set(x, -2, z, Blocks.STONE);
        }
    }

    /** A pond with two water intakes: one for the boiler, one for the column's cooling water. */
    private void water() {
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 5; z++) set(x, -1, z, Blocks.WATER);
        set(2, -1, 2, SetpointContent.WATER_INTAKE.get());
        set(2, -1, 4, SetpointContent.WATER_INTAKE.get());
        // P-101 feeds the boiler through LV-101; P-102 sends cooling water up to the top of the column
        pipe(2, 0, 2); pipe(3, 0, 2); pump(4, 0, 2, Direction.EAST);
        set(5, 0, 2, SetpointContent.CONTROL_VALVE.get().defaultBlockState().setValue(ControlValveBlock.AXIS, Direction.Axis.X));
        pipe(6, 0, 2, Direction.EAST); pipe(6, 1, 2); // cut towards the burner under the boiler: the water goes up into the drum
        stirling(4, 1, 2); // the boiler feed pump never runs on the turbine alone: no steam, no feed water
        pipe(2, 0, 4); pipe(3, 0, 4); pump(4, 0, 4, Direction.EAST);
        for (int x = 5; x <= 12; x++) pipe(x, 0, 4);
        for (int z = 5; z <= 11; z++) pipe(12, 0, z);
        for (int y = 1; y <= 9 + LIFT; y++) pipe(12, y, 11);
        for (int z = 12; z <= 16; z++) pipe(12, 9 + LIFT, z);
        pipe(11, 9 + LIFT, 16);
    }

    /** The boiler on its burner, the steam header, and the steam turbine that powers both water pumps. */
    private void steam() {
        set(7, 0, 2, SetpointContent.BURNER.get().defaultBlockState().setValue(GasMakerBlock.FACING, Direction.NORTH));
        set(7, 1, 2, SetpointContent.BOILER.get());
        BoilerBlockEntity boiler = (BoilerBlockEntity) level.getBlockEntity(p(7, 1, 2));
        boiler.fluid().set(0, FluidResource.of(Fluids.WATER), 6_000);
        boiler.setTemperature(BoilerModel.BOILING);
        boiler.setSteam(1_500);
        ((BurnerBlockEntity) level.getBlockEntity(p(7, 0, 2))).fluid().set(0, Liquids.RESIDUE.resource(), BurnerBlockEntity.CAPACITY);
        // LIC on the drum (it finds LV-101 in the feed by itself), a pressure transmitter and its PIC on the burner
        set(7, 1, 1, SetpointContent.CONTROLLER.get());
        set(8, 1, 2, SetpointContent.PRESSURE_TRANSMITTER.get());
        set(9, 1, 2, SetpointContent.CONTROLLER.get());
        ((ControllerBlockEntity) level.getBlockEntity(p(9, 1, 2))).setSetpoint(25); // 3.75 bar
        // the steam header: west to the turbine, east and south to the column's reboiler
        gasPipe(7, 2, 2);
        for (int x = 6; x >= 4; x--) gasPipe(x, 2, 2);
        gasPipe(4, 2, 3); gasPipe(4, 1, 3);
        set(4, 0, 3, SetpointContent.STEAM_TURBINE.get());
        for (int x = 8; x <= 11; x++) gasPipe(x, 2, 2);
        for (int z = 3; z <= 15; z++) gasPipe(11, 2, z);
    }

    /**
     * A wind turbine on the west side, its rotor turned away from the plant, and a cable buried under the floor to the
     * two water pumps: it helps the steam turbine, which shares its power with them over the same cable.
     */
    private void wind() {
        multiblock(0, 0, 20, SetpointContent.WIND_TURBINE.get().defaultBlockState().setValue(io.hookdev.setpoint.block.WindTurbineBlock.FACING, Direction.WEST),
                new ItemStack(SetpointContent.WIND_TURBINE_ITEM.get()));
        cable(1, 0, 20);
        cable(1, -1, 20);
        for (int x = 2; x <= 5; x++) cable(x, -1, 20);
        for (int z = 19; z >= 4; z--) cable(5, -1, z);
        for (int z = 4; z >= 2; z--) cable(4, -1, z); // under P-102, the steam turbine and P-101
    }

    private void cable(int x, int y, int z) {
        set(x, y, z, SetpointContent.POWER_CABLE.get());
    }

    /** The pumpjack with its crude pump, both run by a diesel generator, and the column on its steel frame. */
    private void refinery() {
        multiblock(17, 0, 6, SetpointContent.PUMPJACK.get().defaultBlockState().setValue(io.hookdev.setpoint.block.PumpjackBlock.FACING, Direction.EAST),
                new ItemStack(SetpointContent.PUMPJACK_ITEM.get()));
        pump(17, 0, 7, Direction.SOUTH); // straight out of the wellhead
        set(16, 0, 7, SetpointContent.DIESEL_GENERATOR.get().defaultBlockState().setValue(GasMakerBlock.FACING, Direction.WEST));
        ((DieselGeneratorBlockEntity) level.getBlockEntity(p(16, 0, 7))).fluid().set(0, Liquids.DIESEL.resource(), DieselGeneratorBlockEntity.DIESEL_CAPACITY);
        pipe(17, 0, 8, Direction.WEST); pipe(17, 0, 9, Direction.WEST);
        for (int z = 10; z <= 13; z++) pipe(17, 0, z);
        for (int x = 16; x >= 10; x--) pipe(x, 0, 13);
        pipe(10, 1, 13); pipe(10, 2, 13); pipe(10, 2, 14);
        pipe(10, 2, 15, Direction.WEST);
        // the steel frame under the column
        BlockState beam = SetpointContent.STEEL_BEAM.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int x = 9; x <= 11; x++) for (int z = 16; z <= 19; z++) for (int y = 0; y < LIFT; y++) set(x, y, z, beam);
        BlockState column = SetpointContent.DISTILLATION_COLUMN.get().defaultBlockState().setValue(io.hookdev.setpoint.block.DistillationColumnBlock.FACING, Direction.NORTH);
        multiblock(10, LIFT, 16, column, new ItemStack(SetpointContent.DISTILLATION_COLUMN_ITEM.get()));
        DistillationColumnBlockEntity c = (DistillationColumnBlockEntity) level.getBlockEntity(p(10, LIFT, 16));
        c.fluid().set(0, Liquids.CRUDE_OIL.resource(), 6_000);
        c.water().set(0, FluidResource.of(Fluids.WATER), 4_000);
    }

    /** Every cut runs down out of the column by itself to its tank; what is too much overflows to export. */
    private void products() {
        // residue: to a tank beside the column; the burner draws from its bottom, the rest overflows to export
        pipe(9, LIFT, 15, Direction.EAST); pipe(8, LIFT, 15);
        tank(7, 15, 4);
        pipe(6, 0, 15); pipe(6, -1, 15);
        for (int z = 15; z >= 2; z--) pipe(6, -2, z);
        pipe(7, -2, 2); pipe(7, -1, 2);
        pipe(7, 1, 14); pipe(7, 1, 13);
        export(7, 13, Direction.NORTH);
        // diesel (three up the column): to a tall tank that feeds the generator; overflow to export
        int d = 3 + LIFT;
        for (int z = 16; z >= 13; z--) pipe(10, d, z);
        for (int x = 11; x <= 16; x++) pipe(x, d, 13);
        for (int z = 12; z >= 10; z--) pipe(16, d, z);
        tank(16, 9, 8);
        for (int y = 0; y <= 1; y++) ((TankBlockEntity) level.getBlockEntity(p(16, y, 9))).own().set(0, Liquids.DIESEL.resource(), 16_000);
        pipe(16, 0, 8, Direction.EAST, Direction.WEST);
        pipe(16, 4, 8); pipe(15, 4, 8);
        for (int y = 3; y >= 1; y--) pipe(15, y, 8);
        export(15, 8, Direction.WEST);
        // kerosene (five up): to a tall tank; overflow to export
        int k = 5 + LIFT;
        for (int z = 16; z >= 8; z--) pipe(10, k, z);
        for (int y = k - 1; y >= 0; y--) pipe(10, y, 8);
        pipe(9, 0, 8); pipe(8, 0, 8);
        tank(7, 8, 8);
        pipe(6, 6, 8);
        for (int y = 5; y >= 1; y--) pipe(6, y, 8, Direction.EAST);
        export(6, 8, Direction.WEST);
        // naphtha (seven up): to a tall tank under a nitrogen blanket, it is volatile; overflow to export
        int n = 7 + LIFT;
        for (int z = 16; z >= 6; z--) pipe(10, n, z);
        for (int y = n - 1; y >= 0; y--) pipe(10, y, 6);
        pipe(9, 0, 6); pipe(8, 0, 6);
        tank(7, 6, 8);
        gasPipe(7, 8, 6);
        cylinder(8, 8, 6, Gases.NITROGEN, 150_000);
        pipe(6, 6, 6);
        for (int y = 5; y >= 1; y--) pipe(6, y, 6, Direction.EAST);
        export(6, 6, Direction.WEST);
    }

    /** LPG off the top of the column to the gas sphere and the flare; a pressure control valve lets it down to fuel gas for the burner. */
    private void gases() {
        int top = 9 + LIFT;
        gasPipe(9, top, 16); gasPipe(9, top + 1, 16); gasPipe(9, top + 1, 15); gasPipe(9, top + 1, 14);
        for (int x = 10; x <= 28; x++) gasPipe(x, top + 1, 14);
        for (int y = top; y >= 0; y--) gasPipe(19, y, 14);
        multiblock(19, 0, 15, SetpointContent.GAS_SPHERE.get().defaultBlockState().setValue(io.hookdev.setpoint.block.GasSphereBlock.FACING, Direction.NORTH),
                new ItemStack(SetpointContent.GAS_SPHERE_ITEM.get()));
        // already at 8 bar of LPG: the column alone would take hours to fill it, and the flare (set at 8) burns what goes over
        ((GasSphereBlockEntity) level.getBlockEntity(p(19, 0, 15))).tank().set(Gases.LPG.resource(), 8 * GasSphereBlockEntity.VOLUME);
        // PV: from the LPG line down to 7.5 bar in a fuel gas cylinder, PT against the low side, the PIC against the PT
        set(20, 0, 14, SetpointContent.GAS_CONTROL_VALVE.get().defaultBlockState().setValue(ControlValveBlock.AXIS, Direction.Axis.X));
        gasPipe(21, 0, 14);
        cylinder(22, 0, 14, null, 0);
        set(21, 0, 13, SetpointContent.PRESSURE_TRANSMITTER.get());
        set(21, 0, 12, SetpointContent.CONTROLLER.get());
        // fuel gas under the floor to the burner, which burns it when the residue runs short
        gasPipe(21, -1, 14); gasPipe(21, -1, 13);
        for (int x = 20; x >= 7; x--) gasPipe(x, -1, 13);
        for (int z = 12; z >= 3; z--) gasPipe(7, -1, z);
        gasPipe(7, 0, 3);
        // the flare: its header at the front (north), pilot LPG on its left (east), steam from the boiler on its right so it burns clean
        gasPipe(28, top + 1, 13); gasPipe(28, top + 1, 12); gasPipe(28, top + 1, 11);
        for (int y = top; y >= 0; y--) gasPipe(28, y, 11);
        multiblock(28, 0, 12, SetpointContent.FLARE.get().defaultBlockState().setValue(io.hookdev.setpoint.block.FlareBlock.FACING, Direction.NORTH),
                new ItemStack(SetpointContent.FLARE_ITEM.get()));
        cylinder(29, 0, 12, Gases.LPG, 200_000);
        // steam from the boiler header to the flare tip
        for (int x = 12; x <= 27; x++) gasPipe(x, 2, 12);
        gasPipe(27, 1, 12); gasPipe(27, 0, 12);
    }

    /** Behind the plant: a P&ID wall, a video wall, the console desk, the printer and the rest. */
    private void controlRoom() {
        for (int x = 2; x <= 22; x++) for (int z = 25; z <= 31; z++) set(x, -1, z, SetpointContent.RAISED_FLOOR.get());
        for (int x = 4; x <= 20; x++) for (int y = 0; y <= 6; y++) set(x, y, 24, SetpointContent.CORRUGATED_STEEL.get());
        BlockState board = SetpointContent.PID_BOARD.get().defaultBlockState().setValue(PidBoardBlock.FACING, Direction.SOUTH);
        for (int x = 5; x <= 12; x++) for (int y = 1; y <= 6; y++) set(x, y, 25, board);
        BlockState screen = SetpointContent.OVERVIEW_SCREEN.get().defaultBlockState().setValue(io.hookdev.setpoint.block.OverviewScreenBlock.FACING, Direction.SOUTH);
        for (int x = 14; x <= 17; x++) for (int y = 2; y <= 3; y++) set(x, y, 25, screen);
        set(13, 7, 24, SetpointContent.ALARM_BEACON.get().defaultBlockState().setValue(io.hookdev.setpoint.block.AlarmBeaconBlock.FACING, Direction.UP));
        BlockState desk = SetpointContent.CONTROL_DESK.get().defaultBlockState().setValue(FurnitureBlock.FACING, Direction.SOUTH);
        set(9, 0, 28, desk);
        set(10, 0, 28, SetpointContent.CONSOLE_DESK.get().defaultBlockState().setValue(io.hookdev.setpoint.block.ConsoleBlock.FACING, Direction.SOUTH));
        set(11, 0, 28, desk);
        set(11, 1, 28, SetpointContent.ESD_BUTTON.get().defaultBlockState().setValue(io.hookdev.setpoint.block.EsdButtonBlock.FACING, Direction.UP));
        set(9, 1, 28, SetpointContent.PRINTER.get().defaultBlockState().setValue(FurnitureBlock.FACING, Direction.SOUTH));
        set(10, 0, 29, SetpointContent.OFFICE_CHAIR.get().defaultBlockState().setValue(FurnitureBlock.FACING, Direction.NORTH));
        set(16, 0, 29, SetpointContent.COFFEE_MACHINE.get().defaultBlockState().setValue(FurnitureBlock.FACING, Direction.SOUTH));
        // the plant's own P&ID, in the console and on the wall
        PidDrawing drawn = PidCapture.capture(level, p(0, -1, 0), p(WIDTH - 1, -1, DEPTH - 1)).drawing();
        if (drawn == null) return;
        PidDrawing drawing = new PidDrawing("Showcase plant", drawn.width(), drawn.height(), drawn.cells(), drawn.links());
        if (level.getBlockEntity(p(10, 0, 28)) instanceof ConsoleBlockEntity console) console.insertPid(pid(drawing));
        BlockPos topLeft = PidBoardBlock.topLeft(level, p(5, 1, 25), Direction.SOUTH);
        if (level.getBlockEntity(topLeft) instanceof PidBoardBlockEntity wall) wall.insertPid(pid(drawing));
    }

    private static ItemStack pid(PidDrawing drawing) {
        ItemStack pid = new ItemStack(SetpointContent.PID.get());
        pid.set(SetpointContent.PID_DRAWING.get(), drawing);
        return pid;
    }
}
