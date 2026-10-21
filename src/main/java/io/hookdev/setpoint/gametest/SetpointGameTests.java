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
import io.hookdev.setpoint.block.ConsoleBlock;
import io.hookdev.setpoint.block.CheckValveBlock;
import io.hookdev.setpoint.block.DrainValveBlock;
import io.hookdev.setpoint.block.IbcBlock;
import io.hookdev.setpoint.block.PidBoardBlock;
import io.hookdev.setpoint.block.SafetyValveBlock;
import io.hookdev.setpoint.block.entity.PidBoardBlockEntity;
import io.hookdev.setpoint.block.entity.PortableTankBlockEntity;
import io.hookdev.setpoint.block.entity.DrainValveBlockEntity;
import io.hookdev.setpoint.block.entity.SafetyValveBlockEntity;
import io.hookdev.setpoint.block.FluidPipeBlock;
import io.hookdev.setpoint.block.HandValveBlock;
import io.hookdev.setpoint.block.entity.BoilerBlockEntity;
import io.hookdev.setpoint.block.entity.ConsoleBlockEntity;
import io.hookdev.setpoint.control.AlarmList;
import io.hookdev.setpoint.block.entity.ControlValveBlockEntity;
import io.hookdev.setpoint.block.entity.ControllerBlockEntity;
import io.hookdev.setpoint.block.entity.LevelTransmitterBlockEntity;
import io.hookdev.setpoint.block.entity.GeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.PumpBlockEntity;
import io.hookdev.setpoint.block.entity.SteamTurbineBlockEntity;
import io.hookdev.setpoint.block.entity.StirlingEngineBlockEntity;
import io.hookdev.setpoint.block.entity.TankBlockEntity;
import io.hookdev.setpoint.control.PidController;
import io.hookdev.setpoint.physics.BoilerModel;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
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
        test("boiler_and_turbine_make_power", SetpointGameTests::boilerAndTurbineMakePower, 800);
        test("low_drum_level_trips_the_burner", SetpointGameTests::lowDrumLevelTripsTheBurner, 200);
        test("dry_boiler_bursts_without_protection", SetpointGameTests::dryBoilerBurstsWithoutProtection, 100);
        test("water_on_a_red_hot_drum_flashes", SetpointGameTests::waterOnARedHotDrumFlashes, 100);
        test("controller_holds_the_drum_level", SetpointGameTests::controllerHoldsTheDrumLevel, 2400);
        test("hookonium_needs_a_diamond_pickaxe", SetpointGameTests::hookoniumNeedsADiamondPickaxe, 20);
        test("raw_hookonium_smelts_into_an_ingot", SetpointGameTests::rawHookoniumSmeltsIntoAnIngot, 400);
        test("hookonium_generates_in_the_overworld", SetpointGameTests::hookoniumGeneratesInTheOverworld, 20);
        test("machines_drop_themselves", SetpointGameTests::machinesDropThemselves, 20);
        test("console_raises_and_acknowledges_alarms", SetpointGameTests::consoleRaisesAndAcknowledgesAlarms, 200);
        test("comparator_reads_levels", SetpointGameTests::comparatorReadsLevels, 20);
        test("water_intake_feeds_a_pump", SetpointGameTests::waterIntakeFeedsAPump, 300);
        test("shut_hand_valve_blocks_flow", SetpointGameTests::shutHandValveBlocksFlow, 300);
        test("wrench_cuts_joins_and_turns", SetpointGameTests::wrenchCutsJoinsAndTurns, 20);
        test("stacked_tanks_act_as_one", SetpointGameTests::stackedTanksActAsOne, 100);
        test("pump_fills_a_tall_tank", SetpointGameTests::pumpFillsATallTank, 400);
        test("flow_loop_holds_its_setpoint", SetpointGameTests::flowLoopHoldsItsSetpoint, 2400);
        test("pump_fills_a_cauldron", SetpointGameTests::pumpFillsACauldron, 300);
        test("pid_draws_the_plant", SetpointGameTests::pidDrawsThePlant, 100);
        test("blank_pid_is_used_up_in_survival", SetpointGameTests::blankPidIsUsedUpInSurvival, 20);
        test("console_shows_a_pid_live", SetpointGameTests::consoleShowsAPidLive, 100);
        test("pid_board_panel_shows_a_pid_live", SetpointGameTests::pidBoardPanelShowsAPidLive, 100);
        test("pid_board_shows_and_turns_a_hand_valve", SetpointGameTests::pidBoardShowsAndTurnsAHandValve, 100);
        test("pump_fills_an_ibc", SetpointGameTests::pumpFillsAnIbc, 300);
        test("a_broken_drum_keeps_its_liquid", SetpointGameTests::aBrokenDrumKeepsItsLiquid, 40);
        test("an_ibc_takes_eight_blocks_and_breaks_whole", SetpointGameTests::anIbcTakesEightBlocksAndBreaksWhole, 40);
        test("high_pressure_trips_the_burner", SetpointGameTests::highPressureTripsTheBurner, 400);
        test("safety_valve_saves_the_boiler", SetpointGameTests::safetyValveSavesTheBoiler, 300);
        test("overpressure_bursts_an_unprotected_boiler", SetpointGameTests::overpressureBurstsAnUnprotectedBoiler, 100);
        test("pressure_controller_holds_the_boiler", SetpointGameTests::pressureControllerHoldsTheBoiler, 600);
        test("pid_shows_the_safety_valve_on_a_boiler", SetpointGameTests::pidShowsTheSafetyValveOnABoiler, 20);
        test("equipment_takes_a_new_tag", SetpointGameTests::equipmentTakesANewTag, 40);
        test("pump_can_run_on_a_signal", SetpointGameTests::pumpCanRunOnASignal, 100);
        test("drain_valve_empties_a_tank", SetpointGameTests::drainValveEmptiesATank, 100);
        test("pump_moves_lava_slower_and_harder", SetpointGameTests::pumpMovesLavaSlowerAndHarder, 200);
        test("connected_tanks_find_one_level", SetpointGameTests::connectedTanksFindOneLevel, 400);
        test("a_high_pipe_is_an_overflow", SetpointGameTests::aHighPipeIsAnOverflow, 600);
        test("check_valve_lets_one_way_through", SetpointGameTests::checkValveLetsOneWayThrough, 200);
        test("controller_holds_a_gravity_fed_tank", SetpointGameTests::controllerHoldsAGravityFedTank, 1600);
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
        helper.getBlockEntity(tank, TankBlockEntity.class).own().set(0, FluidResource.of(Fluids.WATER), amount);
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

    // ---------- steam ----------

    private static BoilerBlockEntity boiler(GameTestHelper helper, BlockPos pos, int water, int coal) {
        helper.setBlock(pos, SetpointContent.BOILER.get());
        BoilerBlockEntity boiler = helper.getBlockEntity(pos, BoilerBlockEntity.class);
        if (water > 0) boiler.fluid().set(0, FluidResource.of(Fluids.WATER), water);
        if (coal > 0) boiler.insertFromHand(new ItemStack(Items.COAL, coal));
        return boiler;
    }

    private static void boilerAndTurbineMakePower(GameTestHelper helper) {
        BoilerBlockEntity boiler = boiler(helper, new BlockPos(1, 1, 1), 8_000, 8);
        helper.setBlock(2, 1, 1, SetpointContent.STEAM_TURBINE.get());
        SteamTurbineBlockEntity turbine = helper.getBlockEntity(new BlockPos(2, 1, 1), SteamTurbineBlockEntity.class);
        helper.succeedWhen(() -> {
            helper.assertTrue(boiler.temperature() >= 100, "boiler should be boiling");
            helper.assertTrue(turbine.speed() > 2_500, String.format("turbine should be near rated speed, is %.0f rpm", turbine.speed()));
            helper.assertTrue(turbine.energy().getAmountAsInt() > 0, "turbine should make FE");
        });
    }

    private static void lowDrumLevelTripsTheBurner(GameTestHelper helper) {
        BoilerBlockEntity boiler = boiler(helper, new BlockPos(1, 1, 1), 400, 4);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(boiler.tripped(), "5 % drum level should trip the burner");
            helper.assertFalse(boiler.firing(), "a tripped burner must not fire");
            helper.assertTrue(boiler.fuel().getAmountAsInt(0) == 4, "no coal should be burnt while tripped");
            helper.succeed();
        });
    }

    private static void dryBoilerBurstsWithoutProtection(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 1);
        BoilerBlockEntity boiler = boiler(helper, pos, 0, 4);
        boiler.setProtectionOverride(false);
        boiler.setTemperature(440);
        helper.succeedWhen(() -> helper.assertBlockNotPresent(SetpointContent.BOILER.get(), pos));
    }

    private static void waterOnARedHotDrumFlashes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 1);
        BoilerBlockEntity boiler = boiler(helper, pos, 0, 0);
        boiler.setProtectionOverride(false);
        boiler.setTemperature(300);
        boiler.fluid().set(0, FluidResource.of(Fluids.WATER), 1_000);
        helper.succeedWhen(() -> helper.assertBlockNotPresent(SetpointContent.BOILER.get(), pos));
    }

    // ---------- pressure ----------

    /** With nothing taking the steam, the pressure climbs until the trip stops the burner at 9 bar, below the design pressure. */
    private static void highPressureTripsTheBurner(GameTestHelper helper) {
        BoilerBlockEntity boiler = boiler(helper, new BlockPos(1, 1, 1), 6_000, 16);
        boiler.setTemperature(BoilerModel.BOILING);
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(boiler.pressureTripped(), String.format("the burner should have tripped on pressure, at %.1f bar", boiler.pressure()));
            helper.assertFalse(boiler.firing(), "a tripped burner must not fire");
            helper.assertTrue(boiler.pressure() < BoilerModel.DESIGN_PRESSURE, String.format("pressure should stay below 10 bar, is %.1f", boiler.pressure()));
            helper.succeed();
        });
    }

    /** No trip, a boiler near its design pressure: the safety valve on top lifts and keeps the drum in one piece. */
    private static void safetyValveSavesTheBoiler(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 1);
        BoilerBlockEntity boiler = boiler(helper, pos, 6_000, 16);
        boiler.setPressureTripOverride(false);
        boiler.setTemperature(BoilerModel.BOILING);
        boiler.setSteam(3_800); // 9.5 bar
        helper.setBlock(pos.above(), SetpointContent.SAFETY_VALVE.get().defaultBlockState().setValue(SafetyValveBlock.FACING, Direction.UP));
        SafetyValveBlockEntity psv = helper.getBlockEntity(pos.above(), SafetyValveBlockEntity.class);
        boolean[] lifted = {false};
        double[] peak = {0};
        helper.onEachTick(() -> {
            if (psv.lifting()) lifted[0] = true;
            if (helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof BoilerBlockEntity b) peak[0] = Math.max(peak[0], b.pressure());
        });
        helper.runAfterDelay(200, () -> {
            helper.assertBlockPresent(SetpointContent.BOILER.get(), pos);
            helper.assertTrue(lifted[0], "the safety valve should have lifted");
            helper.assertTrue(peak[0] < BoilerModel.DESIGN_PRESSURE + 0.5, String.format("pressure should top out at 10 bar, peaked at %.1f", peak[0]));
            helper.succeed();
        });
    }

    /** No trip, no safety valve: the pressure runs up to 13 bar and the drum bursts. */
    private static void overpressureBurstsAnUnprotectedBoiler(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 1);
        BoilerBlockEntity boiler = boiler(helper, pos, 6_000, 16);
        boiler.setPressureTripOverride(false);
        boiler.setTemperature(BoilerModel.BOILING);
        boiler.setSteam(5_000); // 12.5 bar
        helper.succeedWhen(() -> helper.assertBlockNotPresent(SetpointContent.BOILER.get(), pos));
    }

    /** A pressure transmitter on a boiler and a controller against it: a PIC that holds 7.5 bar by turning the burner down. */
    private static void pressureControllerHoldsTheBoiler(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 1);
        BoilerBlockEntity boiler = boiler(helper, pos, 6_000, 16);
        boiler.setTemperature(BoilerModel.BOILING);
        boiler.setSteam(2_000); // 5 bar
        helper.setBlock(3, 1, 2, SetpointContent.PRESSURE_TRANSMITTER.get());
        helper.setBlock(3, 1, 3, SetpointContent.CONTROLLER.get());
        ControllerBlockEntity pic = helper.getBlockEntity(new BlockPos(3, 1, 3), ControllerBlockEntity.class);
        helper.runAfterDelay(500, () -> {
            helper.assertTrue(pic.mvTag().equals(boiler.tag()), "the controller should drive the boiler's burner, has '" + pic.mvTag() + "'");
            helper.assertTrue(pic.measuresPressure(), "the controller should measure pressure");
            helper.assertTrue(pic.mode() == PidController.Mode.AUTO, "the controller should be in AUTO");
            helper.assertTrue(boiler.commanded(), "the burner should follow the controller");
            helper.assertFalse(boiler.pressureTripped(), String.format("the boiler should never reach the trip, at %.1f bar", boiler.pressure()));
            helper.assertTrue(Math.abs(boiler.pressure() - 7.5) < 0.6, String.format("pressure should hold near 7.5 bar, is %.1f", boiler.pressure()));
            helper.succeed();
        });
    }

    // ---------- basics ----------

    /** A name tag gives a pump and a tall tank their own tags; the console and the P&ID use them. */
    private static void equipmentTakesANewTag(GameTestHelper helper) {
        buildLine(helper, false);
        helper.setBlock(1, 2, 1, SetpointContent.TANK.get()); // the source tank is two high now
        PumpBlockEntity pump = helper.getBlockEntity(PUMP, PumpBlockEntity.class);
        helper.assertTrue(pump.rename("p feed"), "the pump should take the name");
        helper.assertTrue(helper.getBlockEntity(new BlockPos(1, 2, 1), TankBlockEntity.class).rename("T-Raw water"), "the tank should take the name");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(pump.tag().equals("P-FEED"), "the pump's tag should be P-FEED, is " + pump.tag());
            String tank = helper.getBlockEntity(SOURCE, TankBlockEntity.class).tag();
            helper.assertTrue(tank.equals("T-RAW-WATER"), "a tall tank has one tag, from any of its blocks: " + tank);
            var reading = io.hookdev.setpoint.console.PlantScanner.read(pump);
            helper.assertTrue(reading != null && reading.tag().equals("P-FEED"), "the console should read P-FEED");
            var drawing = io.hookdev.setpoint.pid.PidCapture.capture(helper.getLevel(), helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(6, 2, 2))).drawing();
            helper.assertTrue(drawing.cells().stream().anyMatch(c -> c.tag().equals("P-FEED")), "the P&ID should use P-FEED");
            helper.succeed();
        });
    }

    /** Switched to run-on-signal, the pump stands still until it gets redstone. */
    private static void pumpCanRunOnASignal(GameTestHelper helper) {
        buildLine(helper, false);
        fill(helper, SOURCE, 8_000);
        PumpBlockEntity pump = helper.getBlockEntity(PUMP, PumpBlockEntity.class);
        pump.toggleRedstoneMode(helper.makeMockPlayer(GameType.SURVIVAL));
        helper.runAfterDelay(20, () -> {
            helper.assertFalse(pump.running(), "without a signal the pump should stand still");
            helper.setBlock(3, 2, 1, Blocks.REDSTONE_BLOCK);
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(pump.running(), "with a signal the pump should run");
                helper.succeed();
            });
        });
    }

    /** Lava goes through the same line as water, but slower and for more power: it is three times as heavy and six times as thick. */
    private static void pumpMovesLavaSlowerAndHarder(GameTestHelper helper) {
        buildLine(helper, false);
        helper.getBlockEntity(SOURCE, TankBlockEntity.class).own().set(0, FluidResource.of(Fluids.LAVA), 8_000);
        PumpBlockEntity pump = helper.getBlockEntity(PUMP, PumpBlockEntity.class);
        helper.runAfterDelay(60, () -> {
            TankBlockEntity destination = helper.getBlockEntity(DESTINATION, TankBlockEntity.class);
            helper.assertTrue(destination.amount() > 0 && destination.resource().getFluid().isSame(Fluids.LAVA), "lava should arrive in the second tank");
            var lava = pump.lastResult();
            var water = io.hookdev.setpoint.physics.PumpModel.solve(new io.hookdev.setpoint.physics.PumpModel.Inputs(0, 0, 1, 1, true, true, 0));
            helper.assertTrue(lava != null && lava.flow() < water.flow(), "lava should flow slower than water");
            helper.assertTrue(lava.power() / lava.flow() > water.power() / water.flow(), "lava should take more power per mB");
            helper.succeed();
        });
    }

    // ---------- gravity ----------

    /** Two tanks joined by a pipe at the bottom: the full one runs into the empty one until both stand at the same level. */
    private static void connectedTanksFindOneLevel(GameTestHelper helper) {
        helper.setBlock(SOURCE, SetpointContent.TANK.get());
        helper.setBlock(2, 1, 1, SetpointContent.PIPE.get());
        helper.setBlock(3, 1, 1, SetpointContent.PIPE.get());
        helper.setBlock(4, 1, 1, SetpointContent.PIPE.get());
        helper.setBlock(DESTINATION, SetpointContent.TANK.get());
        fill(helper, SOURCE, 12_000);
        helper.runAfterDelay(300, () -> {
            int a = helper.getBlockEntity(SOURCE, TankBlockEntity.class).amount(), b = helper.getBlockEntity(DESTINATION, TankBlockEntity.class).amount();
            helper.assertTrue(a + b == 12_000, "no liquid should get lost, have " + (a + b));
            helper.assertTrue(Math.abs(a - b) <= 400, "both tanks should stand at about the same level: " + a + " and " + b);
            helper.succeed();
        });
    }

    /** A tank two blocks high with a pipe from its top block to a low tank: everything above that nozzle runs off. */
    private static void aHighPipeIsAnOverflow(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, SetpointContent.TANK.get());
        helper.setBlock(1, 2, 1, SetpointContent.TANK.get());
        helper.setBlock(2, 2, 1, SetpointContent.PIPE.get());
        helper.setBlock(3, 2, 1, SetpointContent.PIPE.get());
        helper.setBlock(3, 1, 1, SetpointContent.PIPE.get());
        helper.setBlock(4, 1, 1, SetpointContent.PIPE.get());
        helper.setBlock(5, 1, 1, SetpointContent.TANK.get());
        helper.setBlock(5, 2, 1, SetpointContent.TANK.get());
        fill(helper, new BlockPos(1, 1, 1), 16_000);
        fill(helper, new BlockPos(1, 2, 1), 14_000);
        helper.runAfterDelay(500, () -> {
            int left = helper.getBlockEntity(new BlockPos(1, 1, 1), TankBlockEntity.class).amount();
            int over = helper.getBlockEntity(new BlockPos(5, 1, 1), TankBlockEntity.class).amount();
            helper.assertTrue(left + over == 30_000, "no liquid should get lost");
            helper.assertTrue(Math.abs(left - 17_600) <= 600, "the tank should drain down to its overflow nozzle (17.6 B), has " + left);
            helper.succeed();
        });
    }

    /** A high tank feeds a low one through a control valve, no pump: the controller finds the valve and holds the level. */
    private static void controllerHoldsAGravityFedTank(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, Blocks.STONE);
        helper.setBlock(1, 2, 1, SetpointContent.TANK.get());
        helper.setBlock(2, 2, 1, SetpointContent.PIPE.get());
        helper.setBlock(3, 2, 1, SetpointContent.PIPE.get());
        helper.setBlock(3, 1, 1, SetpointContent.CONTROL_VALVE.get().defaultBlockState().setValue(ControlValveBlock.AXIS, Direction.Axis.Y));
        helper.setBlock(4, 0, 1, SetpointContent.PIPE.get());
        helper.setBlock(3, 0, 1, SetpointContent.PIPE.get());
        helper.setBlock(5, 0, 1, SetpointContent.TANK.get());
        helper.setBlock(5, 0, 2, SetpointContent.CONTROLLER.get());
        helper.setBlock(5, 1, 1, SetpointContent.DRAIN_VALVE.get().defaultBlockState().setValue(DrainValveBlock.FACING, Direction.UP).setValue(DrainValveBlock.OPEN, true));
        helper.onEachTick(() -> fill(helper, new BlockPos(1, 2, 1), 16_000)); // an endless high tank
        ControllerBlockEntity lic = helper.getBlockEntity(new BlockPos(5, 0, 2), ControllerBlockEntity.class);
        ControlValveBlockEntity lv = helper.getBlockEntity(new BlockPos(3, 1, 1), ControlValveBlockEntity.class);
        helper.runAfterDelay(1_400, () -> {
            helper.assertTrue(lic.mvTag().equals(lv.tag()), "the controller should find the valve in the gravity line, has '" + lic.mvTag() + "'");
            helper.assertTrue(lic.mode() == PidController.Mode.AUTO, "the controller should be in AUTO");
            helper.assertTrue(Math.abs(lic.pv() - 50) < 6, String.format("the level should hold at 50 %%, is %.1f %%", lic.pv()));
            helper.succeed();
        });
    }

    /** A check valve pointing from the empty tank to the full one: nothing runs back. Turned around, the liquid runs. */
    private static void checkValveLetsOneWayThrough(GameTestHelper helper) {
        BlockPos fullA = new BlockPos(1, 1, 0), emptyA = new BlockPos(3, 1, 0), fullB = new BlockPos(1, 1, 2), emptyB = new BlockPos(3, 1, 2);
        for (BlockPos tank : new BlockPos[]{fullA, emptyA, fullB, emptyB}) helper.setBlock(tank, SetpointContent.TANK.get());
        fill(helper, fullA, 8_000);
        fill(helper, fullB, 8_000);
        helper.setBlock(2, 1, 0, SetpointContent.CHECK_VALVE.get().defaultBlockState().setValue(CheckValveBlock.FACING, Direction.WEST)); // towards the full one
        helper.setBlock(2, 1, 2, SetpointContent.CHECK_VALVE.get().defaultBlockState().setValue(CheckValveBlock.FACING, Direction.EAST)); // towards the empty one
        helper.runAfterDelay(150, () -> {
            helper.assertTrue(helper.getBlockEntity(emptyA, TankBlockEntity.class).amount() == 0, "the check valve should stop the flow against its arrow");
            helper.assertTrue(helper.getBlockEntity(emptyB, TankBlockEntity.class).amount() > 2_000, "the check valve should let the flow through along its arrow");
            helper.succeed();
        });
    }

    /** An open drain valve lets a tank run out; a shut one keeps it full. */
    private static void drainValveEmptiesATank(GameTestHelper helper) {
        BlockPos open = new BlockPos(1, 1, 1), shut = new BlockPos(4, 1, 1);
        for (BlockPos tank : new BlockPos[]{open, shut}) {
            helper.setBlock(tank, SetpointContent.TANK.get());
            fill(helper, tank, 1_000);
        }
        helper.setBlock(1, 1, 0, SetpointContent.DRAIN_VALVE.get().defaultBlockState().setValue(DrainValveBlock.FACING, Direction.NORTH).setValue(DrainValveBlock.OPEN, true));
        helper.setBlock(4, 1, 0, SetpointContent.DRAIN_VALVE.get().defaultBlockState().setValue(DrainValveBlock.FACING, Direction.NORTH));
        helper.runAfterDelay(50, () -> {
            int left = helper.getBlockEntity(open, TankBlockEntity.class).amount();
            helper.assertTrue(left <= 1_000 - 40 * DrainValveBlockEntity.RATE, "the open drain should have let at least 400 mB out, " + left + " mB left");
            helper.assertTrue(helper.getBlockEntity(shut, TankBlockEntity.class).amount() == 1_000, "the shut drain should keep the tank full");
            helper.succeed();
        });
    }

    /** Seen from above a safety valve hides under its boiler, so the P&ID draws it in the free cell next to it. */
    private static void pidShowsTheSafetyValveOnABoiler(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 1);
        boiler(helper, pos, 0, 0);
        helper.setBlock(pos.above(), SetpointContent.SAFETY_VALVE.get().defaultBlockState().setValue(SafetyValveBlock.FACING, Direction.UP));
        helper.runAfterDelay(2, () -> {
            var drawing = io.hookdev.setpoint.pid.PidCapture.capture(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 0)), helper.absolutePos(new BlockPos(4, 1, 2))).drawing();
            var symbols = drawing.cells().stream().map(io.hookdev.setpoint.pid.PidDrawing.Cell::symbol).toList();
            helper.assertTrue(symbols.contains(io.hookdev.setpoint.pid.PidDrawing.BOILER), "the boiler should be drawn: " + symbols);
            helper.assertTrue(symbols.contains(io.hookdev.setpoint.pid.PidDrawing.SAFETY_VALVE), "the safety valve should be drawn beside it: " + symbols);
            helper.succeed();
        });
    }

    /** Tank, pump, control valve, boiler; a controller against the boiler finds the feed valve and holds the drum at 50 %. */
    private static void controllerHoldsTheDrumLevel(GameTestHelper helper) {
        helper.setBlock(SOURCE, SetpointContent.TANK.get());
        helper.setBlock(2, 1, 1, SetpointContent.PUMP.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
        helper.setBlock(3, 1, 1, SetpointContent.CONTROL_VALVE.get().defaultBlockState().setValue(ControlValveBlock.AXIS, Direction.Axis.X));
        helper.setBlock(2, 0, 1, SetpointContent.CREATIVE_GENERATOR.get());
        BoilerBlockEntity boiler = boiler(helper, new BlockPos(4, 1, 1), 2_400, 16);
        helper.setBlock(4, 2, 1, SetpointContent.STEAM_TURBINE.get());
        helper.setBlock(4, 1, 0, SetpointContent.CONTROLLER.get());
        ControllerBlockEntity lic = helper.getBlockEntity(new BlockPos(4, 1, 0), ControllerBlockEntity.class);
        ControlValveBlockEntity lv = helper.getBlockEntity(new BlockPos(3, 1, 1), ControlValveBlockEntity.class);
        helper.onEachTick(() -> fill(helper, SOURCE, 16_000)); // an endless supply of feed water

        helper.runAfterDelay(2_000, () -> {
            helper.assertTrue(lic.mvTag().equals(lv.tag()), "controller should have found the feed valve, has '" + lic.mvTag() + "'");
            helper.assertTrue(lic.mode() == PidController.Mode.AUTO, "controller should be in AUTO");
            helper.assertTrue(Math.abs(lic.pv() - 50) < 5, String.format("drum level should hold at 50 %%, is %.1f %%", lic.pv()));
            helper.assertFalse(boiler.tripped(), "boiler should not trip");
            helper.succeed();
        });
    }

    // ---------- ores ----------

    private static void hookoniumNeedsADiamondPickaxe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        for (Block ore : new Block[]{SetpointContent.HOOKONIUM_ORE.get(), SetpointContent.DEEPSLATE_HOOKONIUM_ORE.get()}) {
            BlockState state = ore.defaultBlockState();
            helper.assertTrue(state.requiresCorrectToolForDrops(), "hookonium ore should need the right tool");
            helper.assertFalse(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(state), "an iron pickaxe must not be enough");
            helper.assertTrue(new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(state), "a diamond pickaxe should mine it");
            var drops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(pos), null, null, new ItemStack(Items.DIAMOND_PICKAXE));
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(SetpointContent.RAW_HOOKONIUM.get()), "the ore should drop raw hookonium, got " + drops);
        }
        helper.succeed();
    }

    private static void rawHookoniumSmeltsIntoAnIngot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.FURNACE);
        FurnaceBlockEntity furnace = helper.getBlockEntity(pos, FurnaceBlockEntity.class);
        furnace.setItem(0, new ItemStack(SetpointContent.RAW_HOOKONIUM.get()));
        furnace.setItem(1, new ItemStack(Items.COAL));
        helper.succeedWhen(() -> helper.assertTrue(furnace.getItem(2).is(SetpointContent.HOOKONIUM_INGOT.get()), "the furnace should make a hookonium ingot"));
    }

    /** The biome modifier adds the veins to the overworld, and a vein really turns deepslate into hookonium ore. */
    private static void hookoniumGeneratesInTheOverworld(GameTestHelper helper) {
        var level = helper.getLevel();
        var plains = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS).value();
        boolean added = plains.getGenerationSettings().features().stream()
                .anyMatch(set -> set.stream().anyMatch(feature -> feature.is(Setpoint.id("ore_hookonium_small"))));
        helper.assertTrue(added, "hookonium veins should be part of overworld generation");

        for (int x = 0; x < 7; x++) for (int y = 1; y < 10; y++) for (int z = 0; z < 3; z++) helper.setBlock(x, y, z, Blocks.DEEPSLATE);
        Feature vein = level.registryAccess().lookupOrThrow(Registries.FEATURE).getValue(Setpoint.id("ore_hookonium_small"));
        helper.assertTrue(vein != null, "the vein feature should be registered");
        for (int i = 0; i < 40; i++) {
            vein.place(level, level.getChunkSource().getGenerator(), level.getRandom(), helper.absolutePos(new BlockPos(3, 5, 1)));
        }
        int ore = 0;
        for (int x = 0; x < 7; x++) for (int y = 1; y < 10; y++) for (int z = 0; z < 3; z++) {
            if (helper.getBlockState(new BlockPos(x, y, z)).is(SetpointContent.DEEPSLATE_HOOKONIUM_ORE.get())) ore++;
        }
        helper.assertTrue(ore > 0, "veins in deepslate should place deepslate hookonium ore");
        helper.succeed();
    }

    /** Every block comes back when you break it with a pickaxe. */
    private static void machinesDropThemselves(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (var block : SetpointContent.BLOCKS.getEntries()) {
            if (block.get() == SetpointContent.HOOKONIUM_ORE.get() || block.get() == SetpointContent.DEEPSLATE_HOOKONIUM_ORE.get()) continue;
            if (block.get() == SetpointContent.TANK_PART.get()) continue; // the rest of a drum or IBC: its base drops
            var drops = Block.getDrops(block.get().defaultBlockState(), helper.getLevel(), pos, null, null, new ItemStack(Items.DIAMOND_PICKAXE));
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.get().asItem()), block.getId() + " should drop itself, got " + drops);
        }
        helper.succeed();
    }

    // ---------- control room ----------

    /** A pump on an empty tank runs dry: the console sees it, raises an alarm, flashes, and calms down after an acknowledge. */
    private static void consoleRaisesAndAcknowledgesAlarms(GameTestHelper helper) {
        buildLine(helper, false); // source tank left empty
        BlockPos pos = new BlockPos(3, 1, 2);
        helper.setBlock(pos, SetpointContent.CONSOLE.get());
        ConsoleBlockEntity console = helper.getBlockEntity(pos, ConsoleBlockEntity.class);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(console.readings().stream().anyMatch(r -> r.kind().equals("PUMP") && r.status().equals("DRY RUN")),
                    "the console should list the pump as running dry, has " + console.readings());
            helper.assertTrue(console.readings().stream().filter(r -> r.kind().equals("TANK")).count() >= 2, "both tanks should be listed");
            helper.assertTrue(console.alarms().list().stream().anyMatch(a -> a.message().equals("DRY RUN") && a.state() == AlarmList.State.ACTIVE_UNACK),
                    "a new DRY RUN alarm expected");
            helper.assertBlockProperty(pos, ConsoleBlock.ALARM, true);
            console.acknowledge();
            helper.runAfterDelay(5, () -> {
                helper.assertFalse(console.alarms().hasUnacknowledged(), "everything should be acknowledged");
                helper.assertBlockProperty(pos, ConsoleBlock.ALARM, false);
                helper.succeed();
            });
        });
    }

    /** A comparator reads tanks and boiler drums: 0 empty, 15 full. */
    private static void comparatorReadsLevels(GameTestHelper helper) {
        BlockPos tankPos = new BlockPos(1, 1, 1), boilerPos = new BlockPos(4, 1, 1);
        helper.setBlock(tankPos, SetpointContent.TANK.get());
        boiler(helper, boilerPos, 4_000, 0);
        var level = helper.getLevel();
        helper.assertTrue(level.getBlockState(helper.absolutePos(tankPos)).getAnalogOutputSignal(level, helper.absolutePos(tankPos), Direction.NORTH) == 0, "an empty tank gives 0");
        fill(helper, tankPos, 16_000);
        helper.assertTrue(level.getBlockState(helper.absolutePos(tankPos)).getAnalogOutputSignal(level, helper.absolutePos(tankPos), Direction.NORTH) == 15, "a full tank gives 15");
        int drum = level.getBlockState(helper.absolutePos(boilerPos)).getAnalogOutputSignal(level, helper.absolutePos(boilerPos), Direction.NORTH);
        helper.assertTrue(drum == 8, "a half-full drum gives 8, got " + drum);
        helper.succeed();
    }

    // ---------- foundations: intake, hand valve, wrench ----------

    /** An intake sitting in water feeds a pump, with no tank on the suction side. */
    private static void waterIntakeFeedsAPump(GameTestHelper helper) {
        buildLine(helper, false);
        helper.setBlock(SOURCE, SetpointContent.WATER_INTAKE.get());
        helper.setBlock(1, 1, 0, Blocks.WATER);
        helper.setBlock(1, 1, 2, Blocks.WATER);
        helper.setBlock(1, 0, 1, Blocks.WATER);
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockEntity(DESTINATION, TankBlockEntity.class).amount() >= 1_000,
                "the pump should fill the tank from the intake"));
    }

    /** A shut hand valve in the discharge line stops the flow; opening it lets water through. */
    private static void shutHandValveBlocksFlow(GameTestHelper helper) {
        buildLine(helper, false);
        BlockPos valve = new BlockPos(4, 1, 1);
        helper.setBlock(valve, SetpointContent.HAND_VALVE.get().defaultBlockState().setValue(HandValveBlock.AXIS, Direction.Axis.X).setValue(HandValveBlock.OPEN, false));
        fill(helper, SOURCE, 8_000);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(helper.getBlockEntity(DESTINATION, TankBlockEntity.class).amount() == 0, "water got past a shut hand valve");
            helper.setBlock(valve, helper.getBlockState(valve).setValue(HandValveBlock.OPEN, true));
            helper.succeedWhen(() -> helper.assertTrue(helper.getBlockEntity(DESTINATION, TankBlockEntity.class).amount() >= 1_000,
                    "water should flow once the valve is open"));
        });
    }

    private static void wrench(GameTestHelper helper, BlockPos pos, Vec3 offset) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack wrench = new ItemStack(SetpointContent.PIPE_WRENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        BlockPos abs = helper.absolutePos(pos);
        Vec3 click = Vec3.atCenterOf(abs).add(offset);
        wrench.getItem().onItemUseFirst(wrench, new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, wrench,
                new BlockHitResult(click, Direction.UP, abs, false)));
    }

    /** Clicking a pipe arm cuts both sides of the joint and clicking it again joins them; clicking a pump turns it. */
    private static void wrenchCutsJoinsAndTurns(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(2, 1, 1);
        helper.setBlock(a, SetpointContent.PIPE.get());
        helper.setBlock(b, SetpointContent.PIPE.get());
        helper.assertTrue(helper.getBlockState(a).getValue(FluidPipeBlock.CONNECTIONS.get(Direction.EAST)), "pipes should join");
        wrench(helper, a, new Vec3(0.4, 0, 0)); // the east arm of pipe a
        helper.assertFalse(helper.getBlockState(a).getValue(FluidPipeBlock.CONNECTIONS.get(Direction.EAST)), "pipe a should be cut on the east side");
        helper.assertFalse(helper.getBlockState(b).getValue(FluidPipeBlock.CONNECTIONS.get(Direction.WEST)), "pipe b should let go too");
        wrench(helper, a, new Vec3(0.4, 0, 0));
        helper.assertTrue(helper.getBlockState(b).getValue(FluidPipeBlock.CONNECTIONS.get(Direction.WEST)), "the joint should be back");

        BlockPos pump = new BlockPos(4, 1, 1);
        helper.setBlock(pump, SetpointContent.PUMP.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.NORTH));
        wrench(helper, pump, Vec3.ZERO);
        helper.assertTrue(helper.getBlockState(pump).getValue(PumpBlock.FACING) == Direction.EAST, "the pump should have turned to the east");
        helper.succeed();
    }

    // ---------- foundations: tall tanks, flow control, other mods' tanks ----------

    /** Water put in the top of a two-high tank settles to the bottom; the column counts as one tank. */
    private static void stackedTanksActAsOne(GameTestHelper helper) {
        BlockPos bottom = new BlockPos(1, 1, 1), top = new BlockPos(1, 2, 1);
        helper.setBlock(bottom, SetpointContent.TANK.get());
        helper.setBlock(top, SetpointContent.TANK.get());
        fill(helper, top, 8_000);
        helper.succeedWhen(() -> {
            TankBlockEntity low = helper.getBlockEntity(bottom, TankBlockEntity.class), high = helper.getBlockEntity(top, TankBlockEntity.class);
            helper.assertTrue(low.blockAmount() == 8_000 && high.blockAmount() == 0, "the water should settle to the bottom tank");
            helper.assertTrue(Math.abs(high.fillFraction() - 0.25) < 1e-9, "the column is a quarter full, got " + high.fillFraction());
            var level = helper.getLevel();
            int signal = level.getBlockState(helper.absolutePos(top)).getAnalogOutputSignal(level, helper.absolutePos(top), Direction.NORTH);
            helper.assertTrue(signal == 4, "a comparator on the top tank reads the column, got " + signal);
        });
    }

    /** A pump filling the bottom of a full two-high tank pushes the level up into the top tank. */
    private static void pumpFillsATallTank(GameTestHelper helper) {
        buildLine(helper, false);
        helper.setBlock(DESTINATION.above(), SetpointContent.TANK.get());
        fill(helper, SOURCE, 8_000);
        fill(helper, DESTINATION, 15_500);
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockEntity(DESTINATION.above(), TankBlockEntity.class).blockAmount() > 0,
                "the top tank should get water once the bottom is full"));
    }

    /** Pump, flow transmitter, control valve; a controller against the transmitter holds 50 % flow (60 mB/t). */
    private static void flowLoopHoldsItsSetpoint(GameTestHelper helper) {
        helper.setBlock(SOURCE, SetpointContent.TANK.get());
        helper.setBlock(2, 1, 1, SetpointContent.PUMP.get().defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
        helper.setBlock(3, 1, 1, SetpointContent.FLOW_TRANSMITTER.get().defaultBlockState().setValue(io.hookdev.setpoint.block.FlowTransmitterBlock.AXIS, Direction.Axis.X));
        helper.setBlock(4, 1, 1, SetpointContent.CONTROL_VALVE.get().defaultBlockState().setValue(ControlValveBlock.AXIS, Direction.Axis.X));
        helper.setBlock(DESTINATION, SetpointContent.TANK.get());
        helper.setBlock(2, 0, 1, SetpointContent.CREATIVE_GENERATOR.get());
        helper.setBlock(3, 1, 0, SetpointContent.CONTROLLER.get());
        ControllerBlockEntity fic = helper.getBlockEntity(new BlockPos(3, 1, 0), ControllerBlockEntity.class);
        helper.onEachTick(() -> { // an endless source and a bottomless destination, so only the valve sets the flow
            fill(helper, SOURCE, 16_000);
            fill(helper, DESTINATION, 0);
        });
        helper.runAfterDelay(2_000, () -> {
            helper.assertTrue(fic.measuresFlow(), "the controller should measure the flow transmitter");
            helper.assertTrue(fic.mode() == PidController.Mode.AUTO, "the flow loop should be in AUTO");
            helper.assertTrue(Math.abs(fic.pv() - 50) < 4, String.format("flow should hold at 50 %%, is %.1f %%", fic.pv()));
            helper.succeed();
        });
    }

    /** Pumps work with any fluid storage, even a vanilla cauldron. */
    private static void pumpFillsACauldron(GameTestHelper helper) {
        buildLine(helper, false);
        helper.setBlock(DESTINATION, Blocks.CAULDRON);
        fill(helper, SOURCE, 8_000);
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.WATER_CAULDRON, DESTINATION));
    }

    // ---------- P&IDs ----------

    /** A small loop drawn as a P&ID: the right symbols, the pump pointing the right way, and the controller's signal lines. */
    private static void pidDrawsThePlant(GameTestHelper helper) {
        buildLine(helper, false);
        helper.setBlock(4, 1, 1, SetpointContent.CONTROL_VALVE.get().defaultBlockState().setValue(ControlValveBlock.AXIS, Direction.Axis.X));
        helper.setBlock(5, 1, 2, SetpointContent.CONTROLLER.get()); // against the destination tank
        helper.runAfterDelay(40, () -> {
            var result = io.hookdev.setpoint.pid.PidCapture.capture(helper.getLevel(), helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(6, 2, 2)));
            var drawing = result.drawing();
            helper.assertTrue(drawing != null, "the area should be drawable: " + result.error());
            helper.assertTrue(drawing.width() == 7 && drawing.height() == 3, "a 7 x 3 drawing expected, got " + drawing.width() + " x " + drawing.height());
            var symbols = drawing.cells().stream().map(io.hookdev.setpoint.pid.PidDrawing.Cell::symbol).toList();
            helper.assertTrue(symbols.stream().filter(sym -> sym == io.hookdev.setpoint.pid.PidDrawing.TANK).count() == 2, "two tanks expected: " + symbols);
            var pump = drawing.cells().stream().filter(c -> c.symbol() == io.hookdev.setpoint.pid.PidDrawing.PUMP).findFirst().orElse(null);
            helper.assertTrue(pump != null && pump.rotation() == 0, "the pump should point east");
            helper.assertTrue(symbols.contains(io.hookdev.setpoint.pid.PidDrawing.CONTROL_VALVE), "the control valve should be drawn");
            helper.assertTrue(symbols.contains(io.hookdev.setpoint.pid.PidDrawing.CONTROLLER), "the controller should be drawn");
            helper.assertTrue(drawing.links().size() == 2, "signal lines to the valve and the tank expected, got " + drawing.links().size());
            helper.succeed();
        });
    }

    /**
     * A P&ID goes into a console with a right-click. Every tag on the drawing is one the console reads, so the live
     * view can colour each symbol; breaking the console gives the P&ID back.
     */
    private static void consoleShowsAPidLive(GameTestHelper helper) {
        buildLine(helper, false);
        BlockPos pos = new BlockPos(3, 1, 2);
        helper.setBlock(pos, SetpointContent.CONSOLE.get());
        helper.runAfterDelay(40, () -> {
            var drawing = io.hookdev.setpoint.pid.PidCapture.capture(helper.getLevel(), helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(6, 2, 2))).drawing();
            ItemStack pid = new ItemStack(SetpointContent.PID.get());
            pid.set(SetpointContent.PID_DRAWING.get(), drawing);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, pid);
            BlockPos abs = helper.absolutePos(pos);
            helper.getBlockState(pos).useItemOn(pid, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
            ConsoleBlockEntity console = helper.getBlockEntity(pos, ConsoleBlockEntity.class);
            helper.assertTrue(console.drawing() != null, "the console should hold the P&ID");
            helper.assertTrue(pid.isEmpty(), "the P&ID should have left the hand");
            var tags = console.readings().stream().map(io.hookdev.setpoint.console.Reading::tag).toList();
            for (var cell : console.drawing().cells()) {
                helper.assertTrue(cell.tag().isEmpty() || tags.contains(cell.tag()), cell.tag() + " is on the drawing but the console does not read it: " + tags);
            }
            helper.destroyBlock(pos);
            helper.assertItemEntityPresent(SetpointContent.PID.get());
            helper.succeed();
        });
    }

    /**
     * Four boards make a 2 x 2 panel. A P&ID clicked onto the bottom-right one goes to the top-left one, which reads
     * the plant and knows the running pump; breaking it gives the P&ID back.
     */
    private static void pidBoardPanelShowsAPidLive(GameTestHelper helper) {
        buildLine(helper, false);
        fill(helper, SOURCE, 8_000);
        BlockState board = SetpointContent.PID_BOARD.get().defaultBlockState().setValue(PidBoardBlock.FACING, Direction.NORTH);
        for (int x = 2; x <= 3; x++) for (int y = 3; y <= 4; y++) helper.setBlock(x, y, 0, board);
        helper.runAfterDelay(40, () -> {
            var drawing = io.hookdev.setpoint.pid.PidCapture.capture(helper.getLevel(), helper.absolutePos(new BlockPos(0, 0, 1)), helper.absolutePos(new BlockPos(6, 2, 1))).drawing();
            ItemStack pid = new ItemStack(SetpointContent.PID.get());
            pid.set(SetpointContent.PID_DRAWING.get(), drawing);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, pid);
            // facing north, the right as you look at it is west: the bottom-right board is the low one at x = 2
            BlockPos bottomRight = new BlockPos(2, 3, 0), abs = helper.absolutePos(bottomRight);
            helper.getBlockState(bottomRight).useItemOn(pid, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
            BlockPos topLeft = new BlockPos(3, 4, 0);
            PidBoardBlockEntity origin = helper.getBlockEntity(topLeft, PidBoardBlockEntity.class);
            helper.assertTrue(origin.drawing() != null, "the top-left board should hold the P&ID");
            int[] size = PidBoardBlock.size(helper.getLevel(), helper.absolutePos(topLeft), Direction.NORTH);
            helper.assertTrue(size[0] == 2 && size[1] == 2, "the panel should be 2 x 2, is " + size[0] + " x " + size[1]);
            for (int x = 2; x <= 3; x++) for (int y = 3; y <= 4; y++) helper.assertBlockProperty(new BlockPos(x, y, 0), PidBoardBlock.LIT, true); // the whole panel glows
            var pump = origin.live().get(helper.getBlockEntity(PUMP, PumpBlockEntity.class).tag());
            helper.assertTrue(pump != null && pump.state() == io.hookdev.setpoint.pid.PidLive.ACTIVE, "the board should show the pump running, has " + origin.live());
            // the middle of the bottom-right board is three quarters across and down the panel
            double[] uv = io.hookdev.setpoint.pid.BoardLayout.panelPoint(helper.absolutePos(topLeft), Direction.NORTH, 2, 2, abs, Vec3.atCenterOf(abs));
            helper.assertTrue(Math.abs(uv[0] - 0.75) < 1e-6 && Math.abs(uv[1] - 0.75) < 1e-6, "panel point should be 0.75, 0.75, is " + uv[0] + ", " + uv[1]);
            // right-clicking the pump on the board stops it
            var pumpCell = origin.drawing().cells().stream().filter(c -> c.symbol() == io.hookdev.setpoint.pid.PidDrawing.PUMP).findFirst().orElseThrow();
            origin.operate(pumpCell, player, abs);
            helper.runAfterDelay(25, () -> {
                helper.assertFalse(helper.getBlockEntity(PUMP, PumpBlockEntity.class).running(), "the pump should stop from the board");
                helper.destroyBlock(topLeft);
                helper.assertItemEntityPresent(SetpointContent.PID.get());
                helper.succeed();
            });
        });
    }

    /** A hand valve on the drawing shows open or shut as it stands, and clicking it on the board turns it. */
    private static void pidBoardShowsAndTurnsAHandValve(GameTestHelper helper) {
        buildLine(helper, false);
        helper.setBlock(4, 1, 1, SetpointContent.HAND_VALVE.get().defaultBlockState().setValue(HandValveBlock.AXIS, Direction.Axis.X));
        BlockState board = SetpointContent.PID_BOARD.get().defaultBlockState().setValue(PidBoardBlock.FACING, Direction.NORTH);
        helper.setBlock(3, 3, 0, board);
        helper.runAfterDelay(20, () -> {
            var drawing = io.hookdev.setpoint.pid.PidCapture.capture(helper.getLevel(), helper.absolutePos(new BlockPos(0, 0, 1)), helper.absolutePos(new BlockPos(6, 2, 1))).drawing();
            var valve = drawing.cells().stream().filter(c -> c.symbol() == io.hookdev.setpoint.pid.PidDrawing.HAND_VALVE).findFirst().orElseThrow();
            helper.assertTrue(valve.at() == helper.absolutePos(new BlockPos(4, 1, 1)).asLong(), "the drawing should remember where the hand valve stands");
            ItemStack pid = new ItemStack(SetpointContent.PID.get());
            pid.set(SetpointContent.PID_DRAWING.get(), drawing);
            PidBoardBlockEntity origin = helper.getBlockEntity(new BlockPos(3, 3, 0), PidBoardBlockEntity.class);
            origin.insertPid(pid);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            helper.assertTrue("OPEN".equals(origin.live().get(valve.liveKey()).status()), "the board should show the valve open");
            origin.operate(valve, player, helper.absolutePos(new BlockPos(3, 3, 0)));
            helper.assertFalse(HandValveBlock.isOpen(helper.getBlockState(new BlockPos(4, 1, 1))), "clicking it on the board should shut the valve");
            helper.assertTrue("SHUT".equals(origin.live().get(valve.liveKey()).status()), "the board should show the valve shut");
            helper.succeed();
        });
    }

    /** A pump fills an IBC; you see the level through the tote. */
    private static void pumpFillsAnIbc(GameTestHelper helper) {
        buildLine(helper, false);
        helper.setBlock(DESTINATION, SetpointContent.IBC.get());
        fill(helper, SOURCE, 16_000);
        helper.succeedWhen(() -> {
            PortableTankBlockEntity ibc = helper.getBlockEntity(DESTINATION, PortableTankBlockEntity.class);
            helper.assertTrue(ibc.amount() >= 8_000, "the IBC should fill up, has " + ibc.amount());
            helper.assertTrue(helper.getBlockState(DESTINATION).getValue(IbcBlock.LEVEL) >= 3, "the IBC should show its level");
        });
    }

    /**
     * Placed, an IBC takes two by two by two blocks; a bucket on a part fills the whole IBC; breaking any part breaks
     * all of it and drops one IBC.
     */
    private static void anIbcTakesEightBlocksAndBreaksWhole(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 1);
        BlockState state = SetpointContent.IBC.get().defaultBlockState().setValue(IbcBlock.FACING, Direction.NORTH);
        helper.setBlock(base, state);
        BlockPos abs = helper.absolutePos(base);
        state.getBlock().setPlacedBy(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), null, new ItemStack(SetpointContent.IBC_ITEM.get()));
        var parts = ((IbcBlock) state.getBlock()).parts(abs, state);
        helper.assertTrue(parts.size() == 7, "seven parts expected");
        for (BlockPos part : parts) helper.assertTrue(helper.getLevel().getBlockState(part).is(SetpointContent.TANK_PART.get()), "a part should stand at " + part);
        BlockPos farTop = abs.east().above().south();
        var vessel = io.hookdev.setpoint.hydraulics.ForeignVessel.at(helper.getLevel(), farTop, null);
        helper.assertTrue(vessel != null && vessel.positions().size() == 8, "a part should be the same vessel as the whole IBC");
        helper.getBlockEntity(base, PortableTankBlockEntity.class).fluid().set(0, FluidResource.of(Fluids.WATER), 5_000);
        helper.assertTrue(vessel.amount() == 5_000, "the part should see the IBC's water");
        helper.getLevel().destroyBlock(farTop, true);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(abs).isAir(), "breaking a part should break the base");
            for (BlockPos part : parts) helper.assertTrue(helper.getLevel().getBlockState(part).isAir(), "every part should be gone, not " + part);
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(abs).inflate(3));
            helper.assertTrue(drops.stream().filter(e -> e.getItem().is(SetpointContent.IBC_ITEM.get())).count() == 1, "one IBC should drop, got " + drops);
            var dropped = drops.stream().filter(e -> e.getItem().is(SetpointContent.IBC_ITEM.get())).findFirst().orElseThrow().getItem();
            var contents = dropped.get(SetpointContent.FLUID_CONTENTS.get());
            helper.assertTrue(contents != null && contents.copy().getAmount() == 5_000, "the IBC should drop with its water");
            helper.succeed();
        });
    }

    /** Break a drum with lava in it: the drum item carries the lava, and placing it back puts the lava back. */
    private static void aBrokenDrumKeepsItsLiquid(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 1);
        helper.setBlock(pos, SetpointContent.DRUM.get());
        helper.getBlockEntity(pos, PortableTankBlockEntity.class).fluid().set(0, FluidResource.of(Fluids.LAVA), 1_500);
        PortableTankBlockEntity full = helper.getBlockEntity(pos, PortableTankBlockEntity.class);
        var drops = Block.getDrops(helper.getBlockState(pos), helper.getLevel(), helper.absolutePos(pos), full, null, new ItemStack(Items.IRON_PICKAXE));
        ItemStack drum = drops.stream().filter(i -> i.is(SetpointContent.DRUM_ITEM.get())).findFirst().orElse(ItemStack.EMPTY);
        var contents = drum.get(SetpointContent.FLUID_CONTENTS.get());
        helper.assertTrue(contents != null && contents.copy().getAmount() == 1_500 && contents.copy().getFluid().isSame(Fluids.LAVA),
                "the dropped drum should hold the 1500 mB of lava, drops: " + drops);
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, SetpointContent.DRUM.get());
        PortableTankBlockEntity placed = helper.getBlockEntity(pos, PortableTankBlockEntity.class);
        placed.applyComponentsFromItemStack(drum);
        helper.assertTrue(placed.amount() == 1_500, "placed back, the drum should hold the lava again, has " + placed.amount());
        helper.succeed();
    }

    /** Two corners on the ground draw everything up to ten blocks high, and in survival one sheet is used up. */
    private static void blankPidIsUsedUpInSurvival(GameTestHelper helper) {
        helper.setBlock(3, 6, 1, SetpointContent.TANK.get()); // well above the corners you click
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack sheets = new ItemStack(SetpointContent.BLANK_PID.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, sheets);
        for (BlockPos corner : new BlockPos[]{new BlockPos(0, 1, 0), new BlockPos(6, 1, 2)}) {
            BlockPos abs = helper.absolutePos(corner);
            sheets.getItem().useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, sheets,
                    new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
        }
        helper.assertTrue(sheets.getCount() == 1, "one blank sheet should be used up, " + sheets.getCount() + " left");
        ItemStack drawn = ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(SetpointContent.PID.get())) drawn = player.getInventory().getItem(slot);
        }
        var drawing = drawn.get(SetpointContent.PID_DRAWING.get());
        helper.assertTrue(drawing != null && drawing.cells().stream().anyMatch(c -> c.symbol() == io.hookdev.setpoint.pid.PidDrawing.TANK),
                "the tank five blocks up should be in the drawing");
        helper.succeed();
    }
}
