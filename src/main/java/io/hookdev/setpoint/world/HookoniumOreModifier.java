package io.hookdev.setpoint.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.hookdev.setpoint.SetpointConfig;
import io.hookdev.setpoint.SetpointContent;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Adds the Hookonium veins to the overworld, like NeoForge's add_features, unless worldgen.hookoniumOre is switched
 * off in the startup config. In data/setpoint/neoforge/biome_modifier/hookonium_ore.json, type setpoint:hookonium_ore.
 */
public final class HookoniumOreModifier extends SetpointBiomeModifier {
    public static final MapCodec<HookoniumOreModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(m -> m.biomes),
            PlacedFeature.LIST_CODEC.fieldOf("features").forGetter(m -> m.features),
            Decoration.CODEC.fieldOf("step").forGetter(m -> m.step)
    ).apply(i, HookoniumOreModifier::new));

    private final HolderSet<Biome> biomes;
    private final HolderSet<PlacedFeature> features;
    private final Decoration step;

    public HookoniumOreModifier(HolderSet<Biome> biomes, HolderSet<PlacedFeature> features, Decoration step) {
        this.biomes = biomes;
        this.features = features;
        this.step = step;
    }

    @Override
    protected void apply(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD || !SetpointConfig.hookoniumOre() || !biomes.contains(biome)) return;
        features.forEach(feature -> builder.getGenerationSettings().addFeature(step, feature));
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return SetpointContent.HOOKONIUM_ORE_MODIFIER.get();
    }
}
