package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * Early-game power. Burns anything a furnace burns (coal is the sensible choice) to heat the hot end
 * of a Stirling engine. One engine is enough for a small pump.
 */
public class StirlingEngineBlockEntity extends GeneratorBlockEntity {
    public static final int OUTPUT = 20;

    public StirlingEngineBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.STIRLING_ENGINE_ENTITY.get(), pos, state);
    }

    @Override
    public int output() {
        return OUTPUT;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        // Lava buckets would leave a bucket behind; keep it simple and solid fuels only.
        return stack.has(DataComponents.COOKING_FUEL) && !stack.is(Items.LAVA_BUCKET);
    }

    @Override
    protected int runTicks(ServerLevel level, ItemStack stack) {
        return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, lootContext(level), 0);
    }

    @Override
    protected String fuelName() {
        return "coal";
    }

    @Override
    protected void effects(ServerLevel level, BlockPos pos) {
        if (level.getGameTime() % 12 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.25, pos.getY() + 0.75, pos.getZ() + 0.5, 1, 0.03, 0.02, 0.03, 0.005);
        }
    }
}
