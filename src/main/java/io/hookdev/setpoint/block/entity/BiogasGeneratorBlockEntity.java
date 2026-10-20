package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Digests anything organic into biogas and burns it in a small gas engine. Takes everything a
 * composter takes, plus rotten flesh. Richer waste runs it longer: a cake beats a handful of seeds.
 */
public class BiogasGeneratorBlockEntity extends GeneratorBlockEntity {
    public static final int OUTPUT = 30;
    /** Run time for waste that always composts (chance 1.0), like cake or a pumpkin pie. */
    public static final int TICKS_PER_FULL_ITEM = 200;
    private static final int SAMPLES = 16;

    public BiogasGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.BIOGAS_GENERATOR_ENTITY.get(), pos, state);
    }

    @Override
    public int output() {
        return OUTPUT;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.has(DataComponents.COMPOSTABLE) || stack.is(Items.ROTTEN_FLESH);
    }

    @Override
    protected int runTicks(ServerLevel level, ItemStack stack) {
        return (int) Math.round(TICKS_PER_FULL_ITEM * organicValue(level, stack));
    }

    /** 0 to 1: the composter chance of the item, averaged so the generator is predictable. */
    private double organicValue(ServerLevel level, ItemStack stack) {
        if (stack.is(Items.ROTTEN_FLESH)) return 0.5;
        Compostable compostable = stack.get(DataComponents.COMPOSTABLE);
        if (compostable == null) return 0;
        LootContext context = lootContext(level);
        int layers = 0;
        for (int i = 0; i < SAMPLES; i++) layers += Math.min(1, compostable.layers().get(context, 0));
        return layers / (double) SAMPLES;
    }

    @Override
    public String fuelName() {
        return "organic waste";
    }

    @Override
    protected void effects(ServerLevel level, BlockPos pos) {
        if (level.getGameTime() % 20 == 0) {
            level.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.15, 0.02, 0.15, 0.01);
        }
    }
}
