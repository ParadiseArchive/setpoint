package io.hookdev.setpoint.gametest;

import java.util.function.Consumer;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.PumpBlock;
import io.hookdev.setpoint.block.PumpStatus;
import io.hookdev.setpoint.block.entity.PumpBlockEntity;
import io.hookdev.setpoint.block.entity.TankBlockEntity;
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
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * In-world tests, run with {@code ./gradlew runGameTestServer} (or {@code /test runall} in a dev client).
 * They all use the empty "bench" structure and build their own setup.
 */
public final class SetpointGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Setpoint.MODID);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PUMP_MOVES_WATER =
            FUNCTIONS.register("pump_moves_water", () -> SetpointGameTests::pumpMovesWater);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PUMP_NEEDS_RUN_SIGNAL =
            FUNCTIONS.register("pump_needs_run_signal", () -> SetpointGameTests::pumpNeedsRunSignal);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EMPTY_SOURCE_RUNS_DRY =
            FUNCTIONS.register("empty_source_runs_dry", () -> SetpointGameTests::emptySourceRunsDry);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SUCTION_LIFT_CAVITATES =
            FUNCTIONS.register("suction_lift_cavitates", () -> SetpointGameTests::suctionLiftCavitates);

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
                    new TestData<>(environment, Setpoint.id("bench"), 200, 0, true)));
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
}
