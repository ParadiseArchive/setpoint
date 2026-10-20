package io.hookdev.setpoint.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.ControlValveBlock;
import io.hookdev.setpoint.block.PumpBlock;
import io.hookdev.setpoint.block.PumpStatus;
import io.hookdev.setpoint.block.entity.BiogasGeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.ControlValveBlockEntity;
import io.hookdev.setpoint.block.entity.ControllerBlockEntity;
import io.hookdev.setpoint.block.entity.LevelTransmitterBlockEntity;
import io.hookdev.setpoint.block.entity.GeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.PumpBlockEntity;
import io.hookdev.setpoint.block.entity.StirlingEngineBlockEntity;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

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
        test("pump_stops_on_redstone", SetpointGameTests::pumpStopsOnRedstone, 200);
        test("empty_source_runs_dry", SetpointGameTests::emptySourceRunsDry, 200);
        test("suction_lift_cavitates", SetpointGameTests::suctionLiftCavitates, 200);
        test("shut_valve_blocks_flow", SetpointGameTests::shutValveBlocksFlow, 200);
        test("fail_open_valve_passes_flow", SetpointGameTests::failOpenValvePassesFlow, 300);
        test("bad_pv_sheds_to_manual", SetpointGameTests::badPvShedsToManual, 100);
        test("level_loop_holds_setpoint", SetpointGameTests::levelLoopHoldsSetpoint, 2400);
        test("controller_links_itself", SetpointGameTests::controllerLinksItself, 2400);
        test("stirling_engine_runs_a_pump", SetpointGameTests::stirlingEngineRunsAPump, 400);
        test("biogas_generator_runs_on_seeds", SetpointGameTests::biogasGeneratorRunsOnSeeds, 200);
        test("generator_keeps_fuel_when_idle", SetpointGameTests::generatorKeepsFuelWhenIdle, 200);
        test("tank_bucket_slots", SetpointGameTests::tankBucketSlots, 100);
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

    /** Tank, pipe, pump, pipe, tank in a row, with power below the pump; optionally a redstone stop signal on top. */
    private static void buildLine(GameTestHelper helper, boolean stopped) {
        helper.setBlock(SOURCE, SetpointContent.TANK.get());
        helper.setBlock(2, 1, 1, SetpointContent.PIPE.get());
        helper.setBlock(PUMP, SetpointContent.PUMP.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
        helper.setBlock(4, 1, 1, SetpointContent.PIPE.get());
        helper.setBlock(DESTINATION, SetpointContent.TANK.get());
        helper.setBlock(3, 0, 1, SetpointContent.CREATIVE_GENERATOR.get());
        if (stopped) {
            helper.setBlock(3, 2, 1, Blocks.REDSTONE_BLOCK);
        }
    }

    private static void fill(GameTestHelper helper, BlockPos tank, int amount) {
        helper.getBlockEntity(tank, TankBlockEntity.class).fluid().set(0, FluidResource.of(Fluids.WATER), amount);
    }

    private static void pumpMovesWater(GameTestHelper helper) {
        buildLine(helper, false);
        fill(helper, SOURCE, 8_000);
        helper.succeedWhen(() -> {
            TankBlockEntity source = helper.getBlockEntity(SOURCE, TankBlockEntity.class);
            TankBlockEntity destination = helper.getBlockEntity(DESTINATION, TankBlockEntity.class);
            helper.assertTrue(destination.amount() >= 2_000, "destination should have received at least 2 buckets, has " + destination.amount());
            helper.assertTrue(source.amount() + destination.amount() == 8_000, "water was created or lost");
            helper.assertTrue(helper.getBlockState(PUMP).getValue(PumpBlock.STATUS) == PumpStatus.RUNNING, "pump should show RUNNING");
        });
    }

    private static void pumpStopsOnRedstone(GameTestHelper helper) {
        buildLine(helper, true);
        fill(helper, SOURCE, 8_000);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(helper.getBlockEntity(DESTINATION, TankBlockEntity.class).amount() == 0, "pump kept running with a stop signal");
            helper.assertTrue(helper.getBlockState(PUMP).getValue(PumpBlock.STATUS) == PumpStatus.OFF, "pump should be OFF");
            helper.succeed();
        });
    }

    private static void emptySourceRunsDry(GameTestHelper helper) {
        buildLine(helper, false);
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
        buildLine(helper, false);
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

    /**
     * Same two-tank loop, but nothing is configured: the controller sits against T-2,
     * must find LV-1 by itself, choose reverse action and go to AUTO at 50 %.
     */
    private static void controllerLinksItself(GameTestHelper helper) {
        BlockPos t2 = new BlockPos(1, 1, 1), t1 = new BlockPos(4, 1, 1);
        helper.setBlock(t2, SetpointContent.TANK.get());
        helper.setBlock(2, 1, 1, SetpointContent.PUMP.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
        helper.setBlock(3, 1, 1, SetpointContent.CONTROL_VALVE.get().defaultBlockState().setValue(ControlValveBlock.AXIS, Direction.Axis.X));
        helper.setBlock(t1, SetpointContent.TANK.get());
        helper.setBlock(5, 1, 1, SetpointContent.PUMP.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
        helper.setBlock(6, 1, 1, SetpointContent.PIPE.get());
        helper.setBlock(6, 2, 1, SetpointContent.PIPE.get());
        helper.setBlock(6, 2, 2, SetpointContent.PIPE.get());
        helper.setBlock(5, 2, 2, SetpointContent.CONTROL_VALVE.get().defaultBlockState().setValue(ControlValveBlock.AXIS, Direction.Axis.X));
        for (int x = 4; x >= 1; x--) helper.setBlock(x, 2, 2, SetpointContent.PIPE.get());
        helper.setBlock(1, 1, 2, SetpointContent.PIPE.get());
        helper.setBlock(2, 0, 1, SetpointContent.CREATIVE_GENERATOR.get());
        helper.setBlock(5, 0, 1, SetpointContent.CREATIVE_GENERATOR.get());
        helper.setBlock(0, 1, 1, SetpointContent.CONTROLLER.get()); // against T-2, nothing else set

        fill(helper, t2, 4_000);
        fill(helper, t1, 12_000);
        ControlValveBlockEntity lv = helper.getBlockEntity(new BlockPos(5, 2, 2), ControlValveBlockEntity.class);
        ControlValveBlockEntity returnValve = helper.getBlockEntity(new BlockPos(3, 1, 1), ControlValveBlockEntity.class);
        ControllerBlockEntity lic = helper.getBlockEntity(new BlockPos(0, 1, 1), ControllerBlockEntity.class);
        helper.onEachTick(() -> returnValve.command(0.4, helper.getLevel().getGameTime()));

        helper.runAfterDelay(2_000, () -> {
            helper.assertTrue(lic.mvTag().equals(lv.tag()), "controller should have found the feed valve, has '" + lic.mvTag() + "'");
            helper.assertTrue(lic.mode() == PidController.Mode.AUTO, "controller should be in AUTO");
            helper.assertTrue(Math.abs(lic.pv() - 50) < 4, String.format("level should hold at 50 %%, is %.1f %%", lic.pv()));
            helper.succeed();
        });
    }

    // ---------- power ----------

    /** The pump line, but powered by a Stirling engine on coal instead of the creative generator. */
    private static void stirlingEngineRunsAPump(GameTestHelper helper) {
        buildLine(helper, false);
        helper.setBlock(3, 0, 1, SetpointContent.STIRLING_ENGINE.get());
        StirlingEngineBlockEntity engine = helper.getBlockEntity(new BlockPos(3, 0, 1), StirlingEngineBlockEntity.class);
        engine.insertFromHand(new ItemStack(Items.COAL, 4));
        fill(helper, SOURCE, 8_000);
        helper.succeedWhen(() -> {
            helper.assertTrue(engine.running(), "engine should be running on coal");
            helper.assertTrue(helper.getBlockEntity(DESTINATION, TankBlockEntity.class).amount() >= 1_000, "the engine should power the pump");
        });
    }

    private static void biogasGeneratorRunsOnSeeds(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, SetpointContent.BIOGAS_GENERATOR.get());
        BiogasGeneratorBlockEntity generator = helper.getBlockEntity(new BlockPos(1, 1, 1), BiogasGeneratorBlockEntity.class);
        helper.assertTrue(generator.insertFromHand(new ItemStack(Items.WHEAT_SEEDS, 32)) == 32, "seeds should go in");
        helper.assertTrue(generator.insertFromHand(new ItemStack(Items.STONE, 1)) == 0, "stone is not organic");
        helper.succeedWhen(() -> helper.assertTrue(generator.energy().getAmountAsInt() >= 1_000, "biogas should make power from seeds"));
    }

    /** With nothing drawing power the buffer fills up and the generator stops burning, so fuel is not wasted. */
    private static void generatorKeepsFuelWhenIdle(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, SetpointContent.STIRLING_ENGINE.get());
        StirlingEngineBlockEntity engine = helper.getBlockEntity(new BlockPos(1, 1, 1), StirlingEngineBlockEntity.class);
        engine.energy().set(GeneratorBlockEntity.ENERGY_CAPACITY);
        engine.insertFromHand(new ItemStack(Items.COAL, 8));
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(engine.input().getAmountAsInt(0) == 8, "coal was burnt while the buffer was full");
            helper.assertTrue(!engine.running(), "engine should idle with a full buffer");
            helper.succeed();
        });
    }

    /** A water bucket in the top slot fills the tank; an empty bucket comes back full. */
    private static void tankBucketSlots(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, SetpointContent.TANK.get());
        TankBlockEntity tank = helper.getBlockEntity(new BlockPos(1, 1, 1), TankBlockEntity.class);
        tank.buckets().set(TankBlockEntity.SLOT_IN, ItemResource.of(Items.WATER_BUCKET), 2);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(tank.amount() == 2_000, "two water buckets should give 2000 mB, got " + tank.amount());
            helper.assertTrue(tank.buckets().getResource(TankBlockEntity.SLOT_OUT).is(Items.BUCKET)
                    && tank.buckets().getAmountAsInt(TankBlockEntity.SLOT_OUT) == 2, "two empty buckets expected in the output");
            tank.buckets().set(TankBlockEntity.SLOT_OUT, ItemResource.EMPTY, 0);
            tank.buckets().set(TankBlockEntity.SLOT_IN, ItemResource.of(Items.BUCKET), 1);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(tank.amount() == 1_000, "filling a bucket should take 1000 mB, left " + tank.amount());
            helper.assertTrue(tank.buckets().getResource(TankBlockEntity.SLOT_OUT).is(Items.WATER_BUCKET), "a water bucket expected in the output");
            helper.succeed();
        });
    }
}
