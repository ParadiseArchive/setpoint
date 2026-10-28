package io.hookdev.setpoint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.neoforged.fml.config.ModConfig;
import org.jspecify.annotations.Nullable;

/**
 * The few things Minecraft 26.3 does differently from 26.1. The rest of Setpoint calls these, so it is the same
 * code for every Minecraft version; versions/26.1/java has the same class for 26.1.2. See versions/README.md.
 */
public final class McVersion {
    /** The config that the server sets and shares with its players. */
    public static final ModConfig.Type WORLD_CONFIG = ModConfig.Type.SYNCED;
    /** Pushed by a piston, the block pops off as an item. */
    public static final PushReaction POPPED = PushReaction.POPPED;

    private McVersion() {}

    /** Drops an item at the player's feet, on the server only. */
    public static void drop(Player player, ItemStack stack) {
        player.drop(stack, false, Prediction.SERVER_ONLY);
    }

    /** Whether the item burns in a furnace. */
    public static boolean isFuel(@Nullable Level level, ItemStack stack) {
        return stack.has(DataComponents.COOKING_FUEL);
    }

    /** How many ticks the item burns in a furnace. */
    public static int burnTicks(ServerLevel level, ItemStack stack, LootContext context) {
        return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
    }

    /** Whether a composter takes the item. */
    public static boolean isCompostable(ItemStack stack) {
        return stack.has(DataComponents.COMPOSTABLE);
    }

    /** 0 to 1: how often the item fills a composter layer, averaged over {@code samples} tries so it is predictable. */
    public static double compostChance(ItemStack stack, LootContext context, int samples) {
        Compostable compostable = stack.get(DataComponents.COMPOSTABLE);
        if (compostable == null) return 0;
        int layers = 0;
        for (int i = 0; i < samples; i++) layers += Math.min(1, compostable.layers().get(context, 0));
        return layers / (double) samples;
    }

    /** Whether bone meal would grow this plant, as if a player used it. */
    public static boolean canBonemeal(BonemealableBlock plant, LevelReader level, BlockPos pos, BlockState state) {
        return plant.isValidBonemealTarget(level, pos, state, BonemealSource.INTERACTION);
    }

    /** Grows the plant once, as bone meal from a player does. */
    public static void bonemeal(BonemealableBlock plant, ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        plant.performBonemeal(level, random, pos, state, BonemealSource.INTERACTION);
    }

    /** Places a configured world generation feature (an ore vein) at {@code pos}; false if it is not registered. */
    public static boolean placeFeature(ServerLevel level, Identifier id, BlockPos pos) {
        Feature feature = level.registryAccess().lookupOrThrow(Registries.FEATURE).getValue(id);
        if (feature == null) return false;
        feature.place(level, level.getChunkSource().getGenerator(), level.getRandom(), pos);
        return true;
    }
}
