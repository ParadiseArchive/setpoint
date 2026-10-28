package io.hookdev.setpoint.block;

import net.minecraft.world.level.block.BaseEntityBlock;

/** A block with a block entity. In 26.3 just a BaseEntityBlock; 26.1 also wants a codec (versions/26.1/java). */
public abstract class SetpointEntityBlock extends BaseEntityBlock {
    protected SetpointEntityBlock(Properties properties) {
        super(properties);
    }
}
