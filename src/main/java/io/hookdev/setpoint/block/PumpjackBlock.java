package io.hookdev.setpoint.block;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity;
import io.hookdev.setpoint.block.entity.PumpjackBlockEntity;
import io.hookdev.setpoint.hydraulics.Vessel;
import io.hookdev.setpoint.hydraulics.VesselBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pumpjack ("jaknikker") on an oil well: power turns its crank, the walking beam nods, and the horsehead lifts crude
 * oil out of the well. One block wide, four long and three high, placed with one block: that block is the wellhead,
 * where the crude oil comes out to a pipe; the pumpjack stands behind it, away from you. It works anywhere, slowly and
 * for ever. {@link PumpjackPartBlock}s fill the rest; its renderer draws all of it and makes it nod.
 */
public class PumpjackBlock extends SetpointEntityBlock implements MultiblockBase, VesselBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    private static final VoxelShape WELLHEAD = Block.box(4, 0, 4, 12, 12, 12);

    public PumpjackBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RUNNING, false));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        for (BlockPos part : parts(context.getClickedPos(), state)) {
            if (!context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return state;
    }

    /** The block this far behind the wellhead (away from you), this high. */
    public static BlockPos cell(BlockPos base, BlockState state, int back, int up) {
        return base.relative(state.getValue(FACING).getOpposite(), back).above(up);
    }

    /** Behind the wellhead: the frame, the samson post two high, the gearbox and crank. */
    @Override
    public List<BlockPos> parts(BlockPos base, BlockState state) {
        List<BlockPos> parts = new ArrayList<>();
        parts.add(cell(base, state, 1, 0));
        for (int up = 0; up < 3; up++) parts.add(cell(base, state, 2, up));
        parts.add(cell(base, state, 3, 0));
        parts.add(cell(base, state, 3, 1));
        return parts;
    }

    /** Which shape the part at this place gets. */
    private static int kind(BlockPos base, BlockState state, BlockPos part) {
        if (part.equals(cell(base, state, 1, 0))) return PumpjackPartBlock.FRAME;
        if (part.equals(cell(base, state, 3, 0)) || part.equals(cell(base, state, 3, 1))) return PumpjackPartBlock.GEARBOX;
        return PumpjackPartBlock.POST;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level.isClientSide()) return;
        for (BlockPos part : parts(pos, state)) {
            level.setBlock(part, SetpointContent.PUMPJACK_PART.get().defaultBlockState().setValue(PumpjackPartBlock.KIND, kind(pos, state, part)), Block.UPDATE_ALL);
            if (level.getBlockEntity(part) instanceof MultiblockPartBlockEntity be) be.setBase(pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        for (BlockPos part : parts(pos, state)) {
            if (level.getBlockEntity(part) instanceof MultiblockPartBlockEntity be && pos.equals(be.base())) level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Pipes join the wellhead on every side but the back, where the pumpjack stands. */
    @Override
    public boolean takesPipe(BlockState state, Direction side) {
        return side != state.getValue(FACING).getOpposite();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return WELLHEAD;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PumpjackBlockEntity pumpjack) pumpjack.describe(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PumpjackBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, SetpointContent.PUMPJACK_ENTITY.get(), PumpjackBlockEntity::clientTick)
                : createTickerHelper(type, SetpointContent.PUMPJACK_ENTITY.get(), PumpjackBlockEntity::serverTick);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof PumpjackBlockEntity pumpjack ? Vessel.comparatorSignal(pumpjack.fillFraction()) : 0;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RUNNING);
    }
}
