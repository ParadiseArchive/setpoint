package io.hookdev.setpoint.block.entity;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.DrainValveBlock;
import io.hookdev.setpoint.hydraulics.ForeignVessel;
import io.hookdev.setpoint.hydraulics.Vessel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Lets liquid run out of the tank behind it while the valve is open. The liquid is gone: it soaks into the ground. */
public class DrainValveBlockEntity extends BlockEntity {
    /** What an open drain lets out, in mB per tick: a bucket every five seconds. */
    public static final int RATE = 10;

    private int drained;

    public DrainValveBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.DRAIN_VALVE_ENTITY.get(), pos, state);
    }

    /** Liquid let out last tick, in mB. */
    public int drained() { return drained; }

    /** The tank this valve sits on: the block behind its outlet. */
    public @Nullable Vessel vessel() {
        if (level == null) return null;
        Direction outlet = getBlockState().getValue(DrainValveBlock.FACING);
        return ForeignVessel.at(level, worldPosition.relative(outlet.getOpposite()), outlet);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DrainValveBlockEntity valve) {
        valve.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        drained = 0;
        if (!DrainValveBlock.isOpen(state)) return;
        Vessel vessel = vessel();
        if (vessel == null) return;
        FluidResource resource = vessel.resource();
        if (resource.isEmpty()) return;
        try (Transaction transaction = Transaction.openRoot()) {
            drained = vessel.fluid().extract(resource, RATE, transaction);
            transaction.commit();
        }
        if (drained > 0) effects(level, pos, state, resource);
    }

    private static void effects(ServerLevel level, BlockPos pos, BlockState state, FluidResource resource) {
        Direction outlet = state.getValue(DrainValveBlock.FACING);
        double x = pos.getX() + 0.5 + outlet.getStepX() * 0.4, y = pos.getY() + 0.4 + outlet.getStepY() * 0.4, z = pos.getZ() + 0.5 + outlet.getStepZ() * 0.4;
        boolean lava = resource.getFluid().is(FluidTags.LAVA);
        if (level.getGameTime() % 3 == 0) {
            level.sendParticles(lava ? ParticleTypes.FALLING_LAVA : ParticleTypes.FALLING_WATER, x, y, z, 1, 0.04, 0.0, 0.04, 0.0);
        }
        if ((level.getGameTime() + pos.hashCode()) % 60 == 0) {
            level.playSound(null, pos, lava ? SoundEvents.LAVA_POP : SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, SoundSource.BLOCKS, 0.4f, 1.0f);
        }
    }
}
