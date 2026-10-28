package io.hookdev.setpoint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import org.jspecify.annotations.Nullable;

/**
 * The few things Minecraft 26.1.2 does differently from 26.3. The rest of Setpoint calls these, so it is the same
 * code for every Minecraft version; versions/26.3/java has the same class for 26.3. See versions/README.md.
 */
public final class McVersion {
    /** The config that the server sets and shares with its players. */
    public static final ModConfig.Type WORLD_CONFIG = ModConfig.Type.SERVER;
    /** Pushed by a piston, the block pops off as an item. */
    public static final PushReaction POPPED = PushReaction.DESTROY;

    private McVersion() {}

    /** Drops an item at the player's feet, on the server only. */
    public static void drop(Player player, ItemStack stack) {
        if (!player.level().isClientSide()) player.drop(stack, false);
    }

    /** Whether the item burns in a furnace. */
    public static boolean isFuel(@Nullable Level level, ItemStack stack) {
        return level != null && level.fuelValues().isFuel(stack);
    }

    /** How many ticks the item burns in a furnace. */
    public static int burnTicks(ServerLevel level, ItemStack stack, LootContext context) {
        return stack.getBurnTime(RecipeType.SMELTING, level.fuelValues());
    }

    /** Whether a composter takes the item. */
    public static boolean isCompostable(ItemStack stack) {
        return stack.typeHolder().getData(NeoForgeDataMaps.COMPOSTABLES) != null;
    }

    /** 0 to 1: how often the item fills a composter layer (in 26.1 a fixed chance, so already predictable). */
    public static double compostChance(ItemStack stack, LootContext context, int samples) {
        Compostable compostable = stack.typeHolder().getData(NeoForgeDataMaps.COMPOSTABLES);
        return compostable == null ? 0 : compostable.chance();
    }

    /** Whether bone meal would grow this plant, as if a player used it. */
    public static boolean canBonemeal(BonemealableBlock plant, LevelReader level, BlockPos pos, BlockState state) {
        return plant.isValidBonemealTarget(level, pos, state);
    }

    /** Grows the plant once, as bone meal from a player does. */
    public static void bonemeal(BonemealableBlock plant, ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        plant.performBonemeal(level, random, pos, state);
    }

    /** Places a configured world generation feature (an ore vein) at {@code pos}; false if it is not registered. */
    public static boolean placeFeature(ServerLevel level, Identifier id, BlockPos pos) {
        ConfiguredFeature<?, ?> feature = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).getValue(id);
        if (feature == null) return false;
        feature.place(level, level.getChunkSource().getGenerator(), level.getRandom(), pos);
        return true;
    }
}
