package io.hookdev.setpoint.block;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The rest of a pallet: the low deck, the deck with drums standing on it, or the space where the drums reach up. */
public class PalletPartBlock extends MultiblockPartBlock {
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 2);
    public static final int LOW = 0, LOADED = 1, TOP = 2;
    private static final VoxelShape DECK = Block.box(0, 0, 0, 16, 3, 16), DRUM_TOPS = Block.box(0, 0, 0, 16, 11, 16);

    public PalletPartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(KIND, LOW));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockPartBlockEntity(SetpointContent.PALLET_PART_ENTITY.get(), pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(KIND)) {
            case LOADED -> Shapes.block();
            case TOP -> DRUM_TOPS;
            default -> DECK;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }
}
