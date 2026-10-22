package io.hookdev.setpoint.block.entity;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.ColumnPartBlock;
import io.hookdev.setpoint.gas.GasPortBlock;
import io.hookdev.setpoint.gas.GasVessel;
import io.hookdev.setpoint.hydraulics.Vessel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A part of a distillation column. A nozzle stands in for what flows through it: a product outlet is a vessel of that
 * product (pumps take from it), the cooling water inlet a vessel of water (pumps fill it), the LPG outlet a gas vessel.
 */
public class ColumnPartBlockEntity extends MultiblockPartBlockEntity implements Vessel, GasPortBlock.Ports {
    private static final FluidStacksResourceHandler NOTHING = new FluidStacksResourceHandler(1, 0);

    public ColumnPartBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.COLUMN_PART_ENTITY.get(), pos, state);
    }

    public @Nullable DistillationColumnBlockEntity column() {
        return level != null && base() != null && level.getBlockEntity(base()) instanceof DistillationColumnBlockEntity column ? column : null;
    }

    private int port() {
        return getBlockState().getValue(ColumnPartBlock.PORT);
    }

    /** The tank behind this nozzle, if it is a liquid one. */
    private FluidStacksResourceHandler tank() {
        DistillationColumnBlockEntity column = column();
        if (column == null) return NOTHING;
        if (port() == ColumnPartBlock.WATER) return column.water();
        FluidStacksResourceHandler product = column.product(port());
        return product == null ? NOTHING : product;
    }

    @Override public ResourceHandler<FluidResource> fluid() { return tank(); }
    @Override public FluidResource resource() { return tank().getResource(0); }
    @Override public int amount() { return tank().getAmountAsInt(0); }
    @Override public double fillFraction() { return amount() / (double) DistillationColumnBlockEntity.PRODUCT_CAPACITY; } // the water tank is as big

    /** Only the cooling water inlet takes liquid in. */
    @Override
    public boolean canAccept(FluidResource incoming) {
        return port() == ColumnPartBlock.WATER && incoming.getFluid().isSame(Fluids.WATER) && amount() < DistillationColumnBlockEntity.WATER_CAPACITY;
    }

    @Override public List<BlockPos> positions() { return List.of(getBlockPos()); }

    @Override
    public @Nullable GasVessel port(Direction side) {
        DistillationColumnBlockEntity column = column();
        return column != null && port() == ColumnPartBlock.LPG ? column.lpg() : null;
    }

    /** Right-click a nozzle: what it is and what is in it. */
    public void describe(Player player) {
        String what = ColumnPartBlock.PORT_NAMES[port()];
        String detail = port() == ColumnPartBlock.LPG ? (column() == null ? "" : String.format("%.1f bar", column().lpg().pressure()))
                : port() == ColumnPartBlock.STEAM ? "a gas pipe from a boiler's top"
                : String.format("%.1f B", amount() / 1000.0);
        player.sendSystemMessage(Component.literal(what + "  ").withStyle(ChatFormatting.AQUA).append(Component.literal(detail).withStyle(ChatFormatting.GRAY)));
    }
}
