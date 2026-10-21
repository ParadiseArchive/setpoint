package io.hookdev.setpoint.block;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.entity.PortableTankBlockEntity;
import io.hookdev.setpoint.block.entity.TankPartBlockEntity;
import io.hookdev.setpoint.hydraulics.Vessel;
import io.hookdev.setpoint.hydraulics.VesselBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * The rest of a drum or IBC that is bigger than one block. It draws nothing itself (the base block's model reaches
 * over it), holds you up, connects to pipes, and passes buckets, pumps and breaking on to the base.
 */
public class TankPartBlock extends BaseEntityBlock implements VesselBlock {
    /** Which shape: the top of a drum, or a full block of an IBC. */
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 1);
    public static final int IBC = 0, DRUM_TOP = 1;
    private static final VoxelShape DRUM_TOP_SHAPE = Block.box(1, 0, 1, 15, 8, 15);

    public TankPartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(KIND, IBC));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TankPartBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    private static @Nullable BlockPos base(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TankPartBlockEntity part ? part.base() : null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        BlockPos base = base(level, pos);
        if (base != null && FluidUtil.interactWithFluidHandler(player, hand, level, base, hitResult.getDirection(), null)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos base = base(level, pos);
        if (!level.isClientSide() && base != null && level.getBlockEntity(base) instanceof PortableTankBlockEntity tank) tank.describe(player);
        return InteractionResult.SUCCESS;
    }

    /** Broken in creative: the whole container goes, without a drop. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos base = base(level, pos);
        if (!level.isClientSide() && player.isCreative() && base != null && level.getBlockState(base).getBlock() instanceof PortableTankBlock) {
            level.removeBlock(base, false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Any part gone (broken, blown up): the whole container breaks, and drops with its liquid inside. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BlockPos base = TankPartBlockEntity.lastBase(pos);
        if (base != null && level.getBlockState(base).getBlock() instanceof PortableTankBlock) level.destroyBlock(base, true);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        BlockPos base = base(level, pos);
        return base != null && level.getBlockEntity(base) instanceof PortableTankBlockEntity tank ? Vessel.comparatorSignal(tank.fillFraction()) : 0;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(KIND) == DRUM_TOP ? DRUM_TOP_SHAPE : Shapes.block();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }
}
