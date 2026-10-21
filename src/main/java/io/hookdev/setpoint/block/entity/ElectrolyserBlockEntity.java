package io.hookdev.setpoint.block.entity;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.GasMakerBlock;
import io.hookdev.setpoint.gas.GasFlow;
import io.hookdev.setpoint.gas.GasTank;
import io.hookdev.setpoint.gas.GasVessel;
import io.hookdev.setpoint.gas.Gases;
import io.hookdev.setpoint.hydraulics.Vessel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * An electrolyser: power splits water into hydrogen and oxygen, two parts hydrogen to one part oxygen, like the real
 * thing. Water comes in at the back or from below (a pipe or a bucket), hydrogen goes out on the left, oxygen on the
 * right. Oxygen that has nowhere to go is let into the air, so the hydrogen never waits for it.
 */
public class ElectrolyserBlockEntity extends GasMakerBlockEntity implements Vessel {
    public static final int WATER_CAPACITY = 4_000;
    /** A tick of running: this much water in, this much power, this much hydrogen and oxygen out (mB at 1 bar). */
    public static final int WATER_PER_TICK = 5, ENERGY_PER_TICK = 40, HYDROGEN_PER_TICK = 100, OXYGEN_PER_TICK = 50;

    private final FluidStacksResourceHandler water = new FluidStacksResourceHandler(1, WATER_CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.isEmpty() || resource.getFluid().isSame(Fluids.WATER);
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    };
    private final GasTank oxygen = new GasTank("oxygen", OUTLET_VOLUME, OUTLET_PRESSURE, this::setChanged);
    private final GasFlow oxygenFlow = new GasFlow();
    private boolean venting;

    public ElectrolyserBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.ELECTROLYSER_ENTITY.get(), pos, state, "EL");
    }

    @Override protected Gases.Gas productGas() { return Gases.HYDROGEN; }
    @Override protected int productPerTick() { return HYDROGEN_PER_TICK; }
    @Override protected int energyPerTick() { return ENERGY_PER_TICK; }

    public GasTank oxygen() { return oxygen; }

    @Override
    protected @Nullable String missing() {
        return amount() < WATER_PER_TICK ? "NO WATER" : null;
    }

    @Override
    protected void alsoMade(ServerLevel level, BlockPos pos, BlockState state) {
        int left = amount() - WATER_PER_TICK;
        water.set(0, left > 0 ? FluidResource.of(Fluids.WATER) : FluidResource.EMPTY, left);
        venting = oxygen.insert(Gases.OXYGEN.resource(), OXYGEN_PER_TICK) < OXYGEN_PER_TICK;
        if (venting) puff(level, pos, GasMakerBlock.right(state));
    }

    @Override
    protected void alsoFlow(ServerLevel level, BlockPos pos, BlockState state) {
        oxygenFlow.tick(level, pos, oxygen, GasMakerBlock.right(state));
    }

    @Override
    public @Nullable GasVessel port(Direction side) {
        return side == GasMakerBlock.right(getBlockState()) ? oxygen : super.port(side);
    }

    // ---------- the water side: a vessel like a tank ----------
    @Override public FluidStacksResourceHandler fluid() { return water; }
    @Override public FluidResource resource() { return water.getResource(0); }
    @Override public int amount() { return water.getAmountAsInt(0); }
    @Override public double fillFraction() { return amount() / (double) WATER_CAPACITY; }

    @Override
    public boolean canAccept(FluidResource incoming) {
        return incoming.getFluid().isSame(Fluids.WATER) && amount() < WATER_CAPACITY;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        water.serialize(output);
        oxygen.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        water.deserialize(input);
        oxygen.load(input);
    }
}
