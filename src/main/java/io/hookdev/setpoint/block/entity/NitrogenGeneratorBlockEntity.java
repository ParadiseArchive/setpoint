package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.GasMakerBlock;
import io.hookdev.setpoint.gas.Gases;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A nitrogen generator (PSA): it squeezes air through two columns of sieve that hold the oxygen back, taking turns, and
 * lets the nitrogen through. Only needs power. Nitrogen goes out on the left; the air with the oxygen in it is blown off
 * on the right.
 */
public class NitrogenGeneratorBlockEntity extends GasMakerBlockEntity {
    public static final int ENERGY_PER_TICK = 20, NITROGEN_PER_TICK = 60;

    public NitrogenGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.NITROGEN_GENERATOR_ENTITY.get(), pos, state, "NG");
    }

    @Override protected Gases.Gas productGas() { return Gases.NITROGEN; }
    @Override protected int productPerTick() { return NITROGEN_PER_TICK; }
    @Override protected int energyPerTick() { return ENERGY_PER_TICK; }

    @Override
    protected void alsoMade(ServerLevel level, BlockPos pos, BlockState state) {
        puff(level, pos, GasMakerBlock.right(state));
    }
}
