package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.TankBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/** A 16 bucket atmospheric tank. Its level shows in the sight glass through the LEVEL block state. */
public class TankBlockEntity extends BlockEntity {
    public static final int CAPACITY = 16_000;

    private final FluidStacksResourceHandler fluid = new FluidStacksResourceHandler(1, CAPACITY) {
        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            onFluidChanged();
        }
    };

    public TankBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.TANK_ENTITY.get(), pos, state);
    }

    public FluidStacksResourceHandler fluid() {
        return fluid;
    }

    public FluidResource resource() {
        return fluid.getResource(0);
    }

    public int amount() {
        return fluid.getAmountAsInt(0);
    }

    public double fillFraction() {
        return amount() / (double) CAPACITY;
    }

    /** Height of the liquid surface in the world, in blocks. */
    public double surfaceY() {
        return getBlockPos().getY() + fillFraction();
    }

    public boolean canAccept(FluidResource incoming) {
        return amount() < CAPACITY && (amount() == 0 || resource().equals(incoming));
    }

    private void onFluidChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            int shown = TankBlock.levelFor(amount(), CAPACITY);
            if (state.getValue(TankBlock.LEVEL) != shown) {
                level.setBlock(worldPosition, state.setValue(TankBlock.LEVEL, shown), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        fluid.serialize(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fluid.deserialize(input);
    }
}
