package io.hookdev.setpoint.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.ControlValveBlock;
import io.hookdev.setpoint.block.PumpBlock;
import io.hookdev.setpoint.block.PumpStatus;
import io.hookdev.setpoint.block.entity.ControlValveBlockEntity;
import io.hookdev.setpoint.block.entity.ControllerBlockEntity;
import io.hookdev.setpoint.block.entity.LevelTransmitterBlockEntity;
import io.hookdev.setpoint.block.entity.PumpBlockEntity;
import io.hookdev.setpoint.block.entity.TankBlockEntity;
import io.hookdev.setpoint.control.PidController;
import io.hookdev.setpoint.physics.PumpModel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * In-world tests, run with {@code ./gradlew runGameTestServer} (or {@code /test runall} in a dev client).
 * They all use the empty "bench" structure and build their own setup.
 */
public final class SetpointGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Setpoint.MODID);

    /** How long each test may take, in ticks. */
    private static final Map<String, Integer> MAX_TICKS = new LinkedHashMap<>();

    static {
        test("pump_moves_water", SetpointGameTests::pumpMovesWater, 200);
        test("pump_needs_run_signal", SetpointGameTests::pumpNeedsRunSignal, 200);
        test("empty_source_runs_dry", SetpointGameTests::emptySourceRunsDry, 200);
        test("suction_lift_cavitates", SetpointGameTests::suctionLiftCavitates, 200);
        test("shut_valve_blocks_flow", SetpointGameTests::shutValveBlocksFlow, 200);
        test("fail_open_valve_passes_flow", SetpointGameTests::failOpenValvePassesFlow, 300);
        test("bad_pv_sheds_to_manual", SetpointGameTests::badPvShedsToManual, 100);
        test("level_loop_holds_setpoint", SetpointGameTests::levelLoopHoldsSetpoint, 2400);
    }

    private static void test(String name, Consumer<GameTestHelper> function, int maxTicks) {
        FUNCTIONS.register(name, () -> function);
        MAX_TICKS.put(name, maxTicks);
    }

    private static final BlockPos SOURCE = new BlockPos(1, 1, 1);
    private static final BlockPos PUMP = new BlockPos(3, 1, 1);
    private static final BlockPos DESTINATION = new BlockPos(5, 1, 1);

    private SetpointGameTests() {}

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(SetpointGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Setpoint.id("default"));
        for (var function : FUNCTIONS.getEntries()) {
            event.registerTest(function.getId(), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, function.getId()),
                    new TestData<>(environment, Setpoint.id("bench"), MAX_TICKS.get(function.getId().getPath()), 0, true)));
        }
    }

    /** Tank, pipe, pump, pipe, tank in a row, with power below the pump and a redstone block on top. */
    private static void buildLine(GameTestHelper helper, boolean runSignal) {
        helper.setBlock(SOURCE, SetpointContent.TANK.get());
        helper.setBlock(2, 1, 1, SetpointContent.PIPE.get());
        helper.setBlock(PUMP, SetpointContent.PUMP.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
        helper.setBlock(4, 1, 1, SetpointContent.PIPE.get());
        helper.setBlock(DESTINATION, SetpointContent.TANK.get());
        helper.setBlock(3, 0, 1, SetpointContent.CREATIVE_GENERATOR.get());
        if (runSignal) {
            helper.setBlock(3, 2, 1, Blocks.REDSTONE_BLOCK);
        }
    }

    private static void fill(GameTestHelper helper, BlockPos tank, int amount) {
        helper.getBlockEntity(tank, TankBlockEntity.class).fluid().set(0, FluidResource.of(Fluids.WATER), amount);
    }

    private static void pumpMovesWater(GameTestHelper helper) {
        buildLine(helper, true);
        fill(helper, SOURCE, 8_000);
        helper.succeedWhen(() -> {
            TankBlockEntity source = helper.getBlockEntity(SOURCE, TankBlockEntity.class);
            TankBlockEntity destination = helper.getBlockEntity(DESTINATION, TankBlockEntity.class);
            helper.assertTrue(destination.amount() >= 2_000, "destination should have received at least 2 buckets, has " + destination.amount());
            helper.assertTrue(source.amount() + destination.amount() == 8_000, "water was created or lost");
            helper.assertTrue(helper.getBlockState(PUMP).getValue(PumpBlock.STATUS) == PumpStatus.RUNNING, "pump should show RUNNING");
        });
    }

    private static void pumpNeedsRunSignal(GameTestHelper helper) {
        buildLine(helper, false);
        fill(helper, SOURCE, 8_000);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(helper.getBlockEntity(DESTINATION, TankBlockEntity.class).amount() == 0, "pump moved water without a run signal");
            helper.assertTrue(helper.getBlockState(PUMP).getValue(PumpBlock.STATUS) == PumpStatus.OFF, "pump should be OFF");
            helper.succeed();
        });
    }

    private static void emptySourceRunsDry(GameTestHelper helper) {
        buildLine(helper, true);
        helper.succeedWhen(() -> {
            PumpModel.Result result = helper.getBlockEntity(PUMP, PumpBlockEntity.class).lastResult();
            helper.assertTrue(result != null && result.state() == PumpModel.State.DRY_RUN, "pump should be running dry");
            helper.assertTrue(helper.getBlockState(PUMP).getValue(PumpBlock.STATUS) == PumpStatus.ALARM, "pump should show ALARM");
        });
    }

    /** Source tank seven blocks below the pump: too much suction lift, the pump must cavitate and wear. */
    private static void suctionLiftCavitates(GameTestHelper helper) {
        BlockPos pump = new BlockPos(3, 8, 1);
        helper.setBlock(SOURCE, SetpointContent.TANK.get());
        for (int y = 2; y <= 8; y++) {
            helper.setBlock(1, y, 1, SetpointContent.PIPE.get());
        }
        helper.setBlock(2, 8, 1, SetpointContent.PIPE.get());
        helper.setBlock(pump, SetpointContent.PUMP.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
        helper.setBlock(4, 8, 1, SetpointContent.PIPE.get());
        helper.setBlock(5, 8, 1, SetpointContent.TANK.get());
        helper.setBlock(3, 7, 1, SetpointContent.CREATIVE_GENERATOR.get());
        helper.setBlock(3, 9, 1, Blocks.REDSTONE_BLOCK);
        fill(helper, SOURCE, 8_000);
        helper.succeedWhen(() -> {
            PumpBlockEntity entity = helper.getBlockEntity(pump, PumpBlockEntity.class);
            PumpModel.Result result = entity.lastResult();
            helper.assertTrue(result != null && result.state() == PumpModel.State.CAVITATING, "pump should cavitate");
            helper.assertTrue(entity.wear() > 0, "cavitation should wear the impeller");
        });
    }

    // ---------- M2: valves and control ----------

    private static ControlValveBlockEntity valveInDischarge(GameTestHelper helper) {
        buildLine(helper, true);
        helper.setBlock(4, 1, 1, SetpointContent.CONTROL_VALVE.get().defaultBlockState().setValue(ControlValveBlock.AXIS, Direction.Axis.X));
        fill(helper, SOURCE, 8_000);
        return helper.getBlockEntity(new BlockPos(4, 1, 1), ControlValveBlockEntity.class);
    }

    /** No controller talks to the valve, so a fail-close valve stays shut and the pump deadheads. */
    private static void shutValveBlocksFlow(GameTestHelper helper) {
        valveInDischarge(helper);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(helper.getBlockEntity(DESTINATION, TankBlockEntity.class).amount() == 0, "water got past a shut valve");
            PumpModel.Result result = helper.getBlockEntity(PUMP, PumpBlockEntity.class).lastResult();
            helper.assertTrue(result != null && result.state() == PumpModel.State.DEADHEAD, "pump should be deadheaded");
            helper.succeed();
        });
    }

    private static void failOpenValvePassesFlow(GameTestHelper helper) {
        valveInDischarge(helper).setFailOpen(true);
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockEntity(DESTINATION, TankBlockEntity.class).amount() >= 1_000,
                "a fail-open valve without signal should open and pass water"));
    }

    private static void badPvShedsToManual(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, SetpointContent.CONTROLLER.get());
        ControllerBlockEntity controller = helper.getBlockEntity(new BlockPos(1, 1, 1), ControllerBlockEntity.class);
        controller.setPvTag("LT-NOWHERE");
        controller.setMode(PidController.Mode.AUTO);
        helper.succeedWhen(() -> {
            helper.assertTrue(controller.mode() == PidController.Mode.MAN, "controller should shed to MAN on a bad PV");
            helper.assertTrue((controller.data().get(ControllerBlockEntity.DATA_ALARMS) & ControllerBlockEntity.ALARM_BAD_PV) != 0, "BAD PV alarm expected");
        });
    }

    /**
     * Two tanks, two pumps. P-2 runs a steady stream from T-2 back to T-1 through a valve held at 40 %.
     * P-1 feeds T-2 through LV-1, and LIC-1 holds the T-2 level at 50 % by throttling LV-1.
     */
    private static void levelLoopHoldsSetpoint(GameTestHelper helper) {
        BlockPos t2 = new BlockPos(1, 1, 1), t1 = new BlockPos(4, 1, 1);
        helper.setBlock(t2, SetpointContent.TANK.get());
        helper.setBlock(1, 2, 1, SetpointContent.LEVEL_TRANSMITTER.get());
        // return line: T-2 -> P-2 -> valve at 40 % -> T-1
        helper.setBlock(2, 1, 1, SetpointContent.PUMP.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
        helper.setBlock(3, 1, 1, SetpointContent.CONTROL_VALVE.get().defaultBlockState().setValue(ControlValveBlock.AXIS, Direction.Axis.X));
        helper.setBlock(t1, SetpointContent.TANK.get());
        // supply line: T-1 -> P-1 -> pipes -> LV-1 -> pipes -> T-2
        helper.setBlock(5, 1, 1, SetpointContent.PUMP.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
        helper.setBlock(6, 1, 1, SetpointContent.PIPE.get());
        helper.setBlock(6, 2, 1, SetpointContent.PIPE.get());
        helper.setBlock(6, 2, 2, SetpointContent.PIPE.get());
        helper.setBlock(5, 2, 2, SetpointContent.CONTROL_VALVE.get().defaultBlockState().setValue(ControlValveBlock.AXIS, Direction.Axis.X));
        for (int x = 4; x >= 1; x--) helper.setBlock(x, 2, 2, SetpointContent.PIPE.get());
        helper.setBlock(1, 1, 2, SetpointContent.PIPE.get());
        // power and run signals
        helper.setBlock(2, 0, 1, SetpointContent.CREATIVE_GENERATOR.get());
        helper.setBlock(5, 0, 1, SetpointContent.CREATIVE_GENERATOR.get());
        helper.setBlock(2, 1, 0, Blocks.REDSTONE_BLOCK);
        helper.setBlock(5, 1, 0, Blocks.REDSTONE_BLOCK);
        helper.setBlock(0, 1, 0, SetpointContent.CONTROLLER.get());

        fill(helper, t2, 4_000);
        fill(helper, t1, 12_000);
        LevelTransmitterBlockEntity lt = helper.getBlockEntity(new BlockPos(1, 2, 1), LevelTransmitterBlockEntity.class);
        ControlValveBlockEntity lv = helper.getBlockEntity(new BlockPos(5, 2, 2), ControlValveBlockEntity.class);
        ControlValveBlockEntity returnValve = helper.getBlockEntity(new BlockPos(3, 1, 1), ControlValveBlockEntity.class);
        ControllerBlockEntity lic = helper.getBlockEntity(new BlockPos(0, 1, 0), ControllerBlockEntity.class);
        lt.setTag("LT-TEST1");
        lv.setTag("LV-TEST1");
        lic.setTag("LIC-TEST1");
        lic.setPvTag("LT-TEST1");
        lic.setMvTag("LV-TEST1");
        lic.setAction(PidController.Action.REVERSE);
        lic.setSetpoint(50);
        lic.setMode(PidController.Mode.AUTO);
        helper.onEachTick(() -> returnValve.command(0.4, helper.getLevel().getGameTime()));

        helper.runAfterDelay(2_000, () -> {
            double pv = lic.pv();
            helper.assertTrue(Math.abs(pv - 50) < 4, String.format("level should hold at 50 %%, is %.1f %% (OP %.1f %%)", pv, lic.output()));
            helper.succeed();
        });
    }
}
