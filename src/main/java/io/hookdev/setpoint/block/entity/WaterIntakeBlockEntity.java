package io.hookdev.setpoint.block.entity;

import java.util.List;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.advancement.PlantEventTrigger;
import io.hookdev.setpoint.hydraulics.GravityFlow;
import io.hookdev.setpoint.hydraulics.Vessel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A strainer you put in a river, lake or the sea. With at least two water source blocks next to it, it never
 * runs dry: pumps can draw from it like from a tank that is always full. Pumps cannot push into it.
 */
public class WaterIntakeBlockEntity extends BlockEntity implements Vessel, NamedEquipment {
    private final EquipmentName name = new EquipmentName("WI");
    private final GravityFlow gravity = new GravityFlow();

    /** How much a pump can take in one tick, refilled every tick while the intake sits in water. */
    public static final int BUFFER = 4_000;
    public static final int SOURCES_NEEDED = 2;

    private final FluidStacksResourceHandler water = new FluidStacksResourceHandler(1, BUFFER) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.isEmpty() || resource.getFluid().isSame(Fluids.WATER);
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            // nothing to save: the intake refills itself
        }
    };

    private int sources;
    private int drawn; // taken out since the last refill, for the advancement

    public WaterIntakeBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.WATER_INTAKE_ENTITY.get(), pos, state);
    }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    public int sources() { return sources; }
    public boolean submerged() { return sources >= SOURCES_NEEDED; }

    // ---------- vessel ----------
    @Override public FluidStacksResourceHandler fluid() { return water; }
    @Override public FluidResource resource() { return water.getResource(0); }
    @Override public int amount() { return water.getAmountAsInt(0); }
    @Override public double fillFraction() { return amount() / (double) BUFFER; }
    /** The surface of the water it sits in. */
    @Override public double surfaceY() { return getBlockPos().getY() + 1.0; }
    @Override public boolean canAccept(FluidResource incoming) { return false; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WaterIntakeBlockEntity intake) {
        intake.tick((ServerLevel) level, pos);
    }

    private void tick(ServerLevel level, BlockPos pos) {
        if (level.getGameTime() % 20 == 0) {
            int count = 0;
            for (Direction direction : Direction.values()) {
                if (level.getFluidState(pos.relative(direction)).isSourceOfType(Fluids.WATER)) count++;
            }
            sources = count;
        }
        int before = amount();
        if (before < BUFFER) drawn += BUFFER - before;
        water.set(0, submerged() ? FluidResource.of(Fluids.WATER) : FluidResource.EMPTY, submerged() ? BUFFER : 0);
        gravity.tick(level, this, List.of(pos)); // river water runs down a pipe to a lower tank by itself
        if (drawn >= 1_000) { // a bucket's worth pumped out of the river
            drawn = 0;
            PlantEventTrigger.near(level, pos, PlantEventTrigger.INTAKE_RUNNING);
        }
    }

    public void report(Player player) {
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                .append(submerged()
                        ? Component.literal("IN WATER").withStyle(ChatFormatting.AQUA)
                        : Component.literal("DRY").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.format("  %d water source blocks around it, needs %d", sources, SOURCES_NEEDED))
                        .withStyle(ChatFormatting.GRAY)));
    }
}
