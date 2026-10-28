package io.hookdev.setpoint.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.BaseEntityBlock;

/** A block with a block entity. Minecraft 26.1 wants a codec for it; Setpoint's blocks have no settings, so it is the block itself. */
public abstract class SetpointEntityBlock extends BaseEntityBlock {
    private final MapCodec<SetpointEntityBlock> codec = MapCodec.unit(this);

    protected SetpointEntityBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }
}
