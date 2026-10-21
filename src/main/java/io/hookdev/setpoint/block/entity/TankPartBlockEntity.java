package io.hookdev.setpoint.block.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.hydraulics.Vessel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A part of a drum or IBC remembers its base block, and stands in for it: to pumps, pipes, gravity lines and
 * controllers it is the same vessel.
 */
public class TankPartBlockEntity extends BlockEntity implements Vessel {
    /** Parts that were just removed, and their base: the block finishes the job after the entity is gone. */
    private static final Map<BlockPos, BlockPos> REMOVED = new HashMap<>();
    private static final FluidStacksResourceHandler NOTHING = new FluidStacksResourceHandler(1, 0);

    private @Nullable BlockPos base;

    public TankPartBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.TANK_PART_ENTITY.get(), pos, state);
    }

    public @Nullable BlockPos base() { return base; }

    public void setBase(BlockPos base) {
        this.base = base.immutable();
        setChanged();
    }

    /** The base of a part that was just removed, once. */
    public static @Nullable BlockPos lastBase(BlockPos pos) {
        return REMOVED.remove(pos);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (base != null) REMOVED.put(pos.immutable(), base);
    }

    public @Nullable PortableTankBlockEntity tank() {
        return level != null && base != null && level.getBlockEntity(base) instanceof PortableTankBlockEntity tank ? tank : null;
    }

    // ---------- the same vessel as its base ----------
    @Override public ResourceHandler<FluidResource> fluid() { PortableTankBlockEntity t = tank(); return t == null ? NOTHING : t.fluid(); }
    @Override public FluidResource resource() { PortableTankBlockEntity t = tank(); return t == null ? FluidResource.EMPTY : t.resource(); }
    @Override public int amount() { PortableTankBlockEntity t = tank(); return t == null ? 0 : t.amount(); }
    @Override public double fillFraction() { PortableTankBlockEntity t = tank(); return t == null ? 0 : t.fillFraction(); }
    @Override public double surfaceY() { PortableTankBlockEntity t = tank(); return t == null ? getBlockPos().getY() : t.surfaceY(); }
    @Override public boolean canAccept(FluidResource incoming) { PortableTankBlockEntity t = tank(); return t != null && t.canAccept(incoming); }
    @Override public List<BlockPos> positions() { PortableTankBlockEntity t = tank(); return t == null ? List.of(getBlockPos()) : t.positions(); }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (base != null) output.putLong("base", base.asLong());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long at = input.getLongOr("base", Long.MIN_VALUE);
        base = at == Long.MIN_VALUE ? null : BlockPos.of(at);
    }
}
