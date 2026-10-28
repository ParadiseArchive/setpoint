package io.hookdev.setpoint.block;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity;
import io.hookdev.setpoint.block.entity.WindTurbineBlockEntity;

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
 * A wind turbine: a slender white tower thirty blocks high with the nacelle on top and three long blades that turn in
 * the wind. Placed with one block: that is the foot of the tower, with its door, where the FE comes out to a cable or a
 * machine. The rotor faces you. {@link WindTurbinePartBlock}s fill the tower and the nacelle. Its renderer draws all of it and turns the rotor.
 */
public class WindTurbineBlock extends SetpointEntityBlock implements MultiblockBase {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Set by the server when the blades are feathered (a storm, an ESD): the rotor slows to a stop. */
    public static final BooleanProperty STOPPED = BooleanProperty.create("stopped");
    /** The hub is this many blocks above the foot; the blades reach this far from it. */
    public static final int TOWER = 30, BLADE = 11;
    private static final VoxelShape FOOT = Block.box(2, 0, 2, 14, 16, 14);

    public WindTurbineBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STOPPED, false));
    }

    /** Needs room for the tower, the nacelle and the whole circle the blades sweep. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        BlockPos base = context.getClickedPos();
        if (base.getY() + TOWER + BLADE + 1 > context.getLevel().getMaxY()) return null;
        for (BlockPos part : parts(base, state)) {
            if (!context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        for (BlockPos swept : rotor(base, state)) {
            if (!context.getLevel().getBlockState(swept).canBeReplaced(context)) return null;
        }
        return state;
    }

    /** The block this far in front of the foot (towards the rotor), this high. */
    public static BlockPos cell(BlockPos base, BlockState state, int front, int up) {
        return base.relative(state.getValue(FACING), front).above(up);
    }

    /** The tower, then on top of it the nacelle: over the tower, the hub in front, the generator behind. */
    @Override
    public List<BlockPos> parts(BlockPos base, BlockState state) {
        List<BlockPos> parts = new ArrayList<>();
        for (int up = 1; up < TOWER; up++) parts.add(cell(base, state, 0, up));
        parts.add(cell(base, state, -1, TOWER));
        parts.add(cell(base, state, 0, TOWER));
        parts.add(cell(base, state, 1, TOWER));
        return parts;
    }

    /** The blocks the blades sweep through, in front of the tower (the hub itself is a part). */
    public static List<BlockPos> rotor(BlockPos base, BlockState state) {
        List<BlockPos> swept = new ArrayList<>();
        Direction side = state.getValue(FACING).getClockWise();
        BlockPos hub = cell(base, state, 1, TOWER);
        for (int across = -BLADE; across <= BLADE; across++) for (int up = -BLADE; up <= BLADE; up++) {
            if ((across != 0 || up != 0) && across * across + up * up <= BLADE * BLADE) swept.add(hub.relative(side, across).above(up));
        }
        return swept;
    }

    private static int kind(BlockPos base, BlockState state, BlockPos part) {
        return part.getY() - base.getY() < TOWER ? WindTurbinePartBlock.TOWER : WindTurbinePartBlock.NACELLE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level.isClientSide()) return;
        for (BlockPos part : parts(pos, state)) {
            level.setBlock(part, SetpointContent.WIND_TURBINE_PART.get().defaultBlockState().setValue(WindTurbinePartBlock.KIND, kind(pos, state, part)), Block.UPDATE_ALL);
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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FOOT;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WindTurbineBlockEntity turbine) turbine.describe(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindTurbineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, SetpointContent.WIND_TURBINE_ENTITY.get(), WindTurbineBlockEntity::clientTick)
                : createTickerHelper(type, SetpointContent.WIND_TURBINE_ENTITY.get(), WindTurbineBlockEntity::serverTick);
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
        builder.add(FACING, STOPPED);
    }
}
