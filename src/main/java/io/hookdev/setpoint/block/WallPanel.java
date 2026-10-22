package io.hookdev.setpoint.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Wall panels of one kind (P&ID boards, overview screens) side by side and below each other make one big panel, up to
 * {@link #MAX_WIDTH} x {@link #MAX_HEIGHT} blocks. Its top-left block holds what the whole panel shows. The panels face
 * {@link BlockStateProperties#HORIZONTAL_FACING}; "right" is to your right as you stand in front of them.
 */
public final class WallPanel {
    public static final int MAX_WIDTH = 8, MAX_HEIGHT = 6;

    private WallPanel() {}

    /** To the right of a panel, as you stand in front of it. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static boolean is(BlockGetter level, BlockPos pos, Block kind, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.is(kind) && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    /** The top-left block of the panel this block belongs to. */
    public static BlockPos topLeft(BlockGetter level, BlockPos pos, Block kind, Direction facing) {
        BlockPos at = pos;
        for (int i = 0; i < MAX_HEIGHT && is(level, at.above(), kind, facing); i++) at = at.above();
        Direction left = right(facing).getOpposite();
        for (int i = 0; i < MAX_WIDTH && is(level, at.relative(left), kind, facing); i++) at = at.relative(left);
        return at;
    }

    /** Width and height of the panel that starts at this top-left block, in blocks. */
    public static int[] size(BlockGetter level, BlockPos topLeft, Block kind, Direction facing) {
        Direction right = right(facing);
        int width = 1;
        while (width < MAX_WIDTH && is(level, topLeft.relative(right, width), kind, facing)) width++;
        int height = 1;
        rows:
        while (height < MAX_HEIGHT) {
            for (int x = 0; x < width; x++) if (!is(level, topLeft.below(height).relative(right, x), kind, facing)) break rows;
            height++;
        }
        return new int[]{width, height};
    }

    /** Every block of the panel that starts at this top-left block. */
    public static List<BlockPos> blocks(BlockGetter level, BlockPos topLeft, Block kind, Direction facing) {
        int[] size = size(level, topLeft, kind, facing);
        List<BlockPos> all = new ArrayList<>();
        for (int x = 0; x < size[0]; x++) for (int y = 0; y < size[1]; y++) all.add(topLeft.relative(right(facing), x).below(y));
        return all;
    }
}
