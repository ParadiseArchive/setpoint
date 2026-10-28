package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.McVersion;
import io.hookdev.setpoint.SetpointContent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Early-game power. Burns anything a furnace burns (coal is the sensible choice) to heat the hot end
 * of a Stirling engine. One engine is enough for a small pump.
 */
public class StirlingEngineBlockEntity extends GeneratorBlockEntity {
    /** FE/t as built; SetpointConfig can change it. */
    public static final int OUTPUT = 20;

    public StirlingEngineBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.STIRLING_ENGINE_ENTITY.get(), pos, state);
    }

    @Override
    public int output() {
        return io.hookdev.setpoint.SetpointConfig.stirlingEngine();
    }

    @Override
    public boolean accepts(ItemStack stack) {
        // Lava buckets would leave a bucket behind; keep it simple and solid fuels only.
        return McVersion.isFuel(level, stack) && !stack.is(Items.LAVA_BUCKET);
    }

    @Override
    protected int runTicks(ServerLevel level, ItemStack stack) {
        return McVersion.burnTicks(level, stack, lootContext(level));
    }

    @Override
    public String fuelName() {
        return "coal";
    }

    @Override
    protected void effects(ServerLevel level, BlockPos pos) {
        if ((level.getGameTime() + pos.hashCode()) % 30 == 0 && level.getRandom().nextFloat() < 0.6f) {
            level.playSound(null, pos, SetpointContent.FIRE_CRACKLE.get(), SoundSource.BLOCKS, 0.25f, 1.0f);
        }
        if (level.getGameTime() % 12 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.25, pos.getY() + 0.75, pos.getZ() + 0.5, 1, 0.03, 0.02, 0.03, 0.005);
        }
    }
}
