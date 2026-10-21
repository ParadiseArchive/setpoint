package io.hookdev.setpoint.block.entity;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.hydraulics.Vessel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A part of a drum or IBC stands in for its base: to pumps, pipes, gravity lines and controllers it is the same vessel.
 */
public class TankPartBlockEntity extends MultiblockPartBlockEntity implements Vessel {
    private static final FluidStacksResourceHandler NOTHING = new FluidStacksResourceHandler(1, 0);

    public TankPartBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.TANK_PART_ENTITY.get(), pos, state);
    }

    public @Nullable PortableTankBlockEntity tank() {
        return level != null && base() != null && level.getBlockEntity(base()) instanceof PortableTankBlockEntity tank ? tank : null;
    }

    // ---------- the same vessel as its base ----------
    @Override public ResourceHandler<FluidResource> fluid() { PortableTankBlockEntity t = tank(); return t == null ? NOTHING : t.fluid(); }
    @Override public FluidResource resource() { PortableTankBlockEntity t = tank(); return t == null ? FluidResource.EMPTY : t.resource(); }
    @Override public int amount() { PortableTankBlockEntity t = tank(); return t == null ? 0 : t.amount(); }
    @Override public double fillFraction() { PortableTankBlockEntity t = tank(); return t == null ? 0 : t.fillFraction(); }
    @Override public double surfaceY() { PortableTankBlockEntity t = tank(); return t == null ? getBlockPos().getY() : t.surfaceY(); }
    @Override public boolean canAccept(FluidResource incoming) { PortableTankBlockEntity t = tank(); return t != null && t.canAccept(incoming); }
    @Override public List<BlockPos> positions() { PortableTankBlockEntity t = tank(); return t == null ? List.of(getBlockPos()) : t.positions(); }
}
