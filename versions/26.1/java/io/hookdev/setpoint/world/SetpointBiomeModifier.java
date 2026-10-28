package io.hookdev.setpoint.world;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/** A biome modifier as NeoForge 26.1 calls it; the shared subclass does the work in {@link #apply}. */
public abstract class SetpointBiomeModifier implements BiomeModifier {
    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        apply(biome, phase, builder);
    }

    protected abstract void apply(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder);
}
