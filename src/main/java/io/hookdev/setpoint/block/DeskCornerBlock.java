package io.hookdev.setpoint.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * The corner of a desk row: a full top with panels on two sides, so a row of desks can turn round the operator. It
 * turns with the way you face when you put it down; its panels are at the back and to the operator's right.
 */
public class DeskCornerBlock extends FurnitureBlock {
    public DeskCornerBlock(BlockBehaviour.Properties properties) {
        super(properties, Shapes.or(Block.box(0, 14, 0, 16, ControlDeskBlock.TOP, 16), Block.box(0, 0, 14, 16, 14, 16), Block.box(0, 0, 0, 2, 14, 14)));
    }
}
