package io.hookdev.setpoint.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * NPK fertilizer from the fertilizer mixer: much stronger than bone meal. Use it on a crop and every crop in the three
 * by three around it grows up at once.
 */
public class FertilizerItem extends Item {
    /** Each plant gets at most this many goes of bone meal. */
    private static final int GOES = 16;

    public FertilizerItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        boolean grew = false;
        for (BlockPos pos : BlockPos.betweenClosed(clicked.offset(-1, -1, -1), clicked.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BonemealableBlock plant) || !plant.isValidBonemealTarget(level, pos, state, BonemealSource.INTERACTION)) continue;
            grew = true;
            if (!(level instanceof ServerLevel server)) continue;
            BlockPos at = pos.immutable();
            for (int go = 0; go < GOES; go++) {
                BlockState now = server.getBlockState(at);
                if (!(now.getBlock() instanceof BonemealableBlock still) || !still.isValidBonemealTarget(server, at, now, BonemealSource.INTERACTION)) break;
                still.performBonemeal(server, server.getRandom(), at, now, BonemealSource.INTERACTION);
            }
            server.levelEvent(1505, at, 15); // the bone meal sparkles
        }
        if (!grew) return InteractionResult.PASS;
        if (!level.isClientSide()) context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }
}
