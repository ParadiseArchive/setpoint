package io.hookdev.setpoint.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.GasSphereBlockEntity;
import io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity;
import io.hookdev.setpoint.gas.GasPortBlock;
import io.hookdev.setpoint.hydraulics.Vessel;

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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A gas sphere (a Horton sphere): a round pressure vessel on eight columns with cross bracing, like the ones that store
 * propane and hydrogen on a real plant. Eight blocks across and ten high; the sphere is seven and a half blocks across.
 * A caged ladder in the front-right corner and a bridge lead to the platform on top with its safety valves. The gas
 * goes in and out at the bottom, through a pipe to the valve station at the front: this block, where you placed it.
 * Gas pipes join the valve station. {@link GasSpherePartBlock}s fill the rest; its renderer draws all of it.
 */
public class GasSphereBlock extends BaseEntityBlock implements MultiblockBase, GasPortBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final int SIZE = 8, HIGH = 10;
    /** The shape, in pixels from the middle of the sphere's footprint and up from the ground (tools/gen-assets.mjs draws the same). */
    public static final int RADIUS = 60, EQUATOR = 88, LEG_RADIUS = 62, DECK = 152, PLATFORM_HALF = 18, BRIDGE_HALF = 6, BRIDGE_END = 68;
    /** The bridge and the ladder are in the front-right corner, at 45° from the front. */
    public static final float CORNER_ANGLE = 45;
    private static final VoxelShape STATION = Block.box(3, 0, 3, 13, 12, 13);
    private static final int COLUMN = 4;
    private static final Map<Long, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    public GasSphereBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        for (BlockPos part : parts(context.getClickedPos(), state)) {
            if (!context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return state;
    }

    /** Gas pipes join the valve station on every side but its bottom. */
    @Override
    public boolean gasPort(BlockState state, Direction side) {
        return side != Direction.DOWN;
    }

    public static Vec3 centre(BlockPos base, BlockState state) {
        return FloatingRoofTankBlock.centre(base, state);
    }

    /** Every block the sphere takes up besides this one: wherever it has something solid, and the ladder in the corner. */
    @Override
    public List<BlockPos> parts(BlockPos base, BlockState state) {
        Vec3 centre = centre(base, state);
        Direction front = state.getValue(FACING);
        List<BlockPos> parts = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE; j++) for (int y = 0; y < HIGH; y++) {
            if (i == 3 && j == 0 && y == 0) continue;
            BlockPos pos = FloatingRoofTankBlock.cell(base, state, i, j, y);
            if ((i == 0 && j == 0) || !shape(offset(pos.getX(), centre.x), offset(pos.getZ(), centre.z), y, front).isEmpty()) parts.add(pos);
        }
        return parts;
    }

    private static int offset(int block, double centre) {
        return (int) Math.floor(block - centre + 1e-6);
    }

    /**
     * The solid part of one block: the sphere, the eight columns, the pipe from the bottom of the sphere to the valve
     * station, and up top the platform and the bridge to the ladder. Columns of four pixels; dx, dz are the block's
     * corner from the middle in blocks, front is the way the sphere faces.
     */
    public static VoxelShape shape(int dx, int dz, int layer, Direction front) {
        long key = ((long) front.get2DDataValue() << 32) | ((long) (dx + 8) << 24) | ((long) (dz + 8) << 16) | layer;
        return SHAPES.computeIfAbsent(key, k -> {
            Direction right = front.getCounterClockWise();
            double ux = Math.sin(Math.toRadians(CORNER_ANGLE)), uz = Math.cos(Math.toRadians(CORNER_ANGLE));
            VoxelShape shape = Shapes.empty();
            int bottom = layer * 16, top = bottom + 16;
            for (int cx = 0; cx < 16; cx += COLUMN) for (int cz = 0; cz < 16; cz += COLUMN) {
                double wx = dx * 16 + cx + COLUMN / 2.0, wz = dz * 16 + cz + COLUMN / 2.0, r = Math.hypot(wx, wz);
                double lx = wx * right.getStepX() + wz * right.getStepZ(), lz = wx * front.getStepX() + wz * front.getStepZ();
                double along = lx * ux + lz * uz, across = Math.abs(lx * uz - lz * ux);
                List<double[]> spans = new ArrayList<>();
                if (r < RADIUS) { // the sphere
                    double half = Math.sqrt(RADIUS * RADIUS - r * r);
                    spans.add(new double[]{EQUATOR - half, EQUATOR + half});
                }
                for (int leg = 0; leg < 8; leg++) { // the columns, up to the equator
                    double a = Math.toRadians(22.5 + 45 * leg);
                    if (Math.hypot(wx - LEG_RADIUS * Math.sin(a), wz - LEG_RADIUS * Math.cos(a)) <= 2.9) spans.add(new double[]{0, EQUATOR});
                }
                if (Math.abs(lx - 8) <= 4 && lz >= 4 && lz <= 48) spans.add(new double[]{4, 12});           // the pipe along the ground
                if (Math.abs(lx - 8) <= 4 && Math.abs(lz - 8) <= 4) spans.add(new double[]{4, EQUATOR - RADIUS + 2}); // and up into the sphere
                boolean platform = Math.abs(along) <= PLATFORM_HALF && across <= PLATFORM_HALF;
                boolean bridge = along >= 0 && along <= BRIDGE_END && across <= BRIDGE_HALF;
                if (platform || bridge) spans.add(new double[]{DECK - 4, DECK});
                for (double[] span : spans) {
                    double from = Math.clamp(span[0] - bottom, 0, 16), to = Math.clamp(span[1] - bottom, 0, 16);
                    if (to > from && span[1] > bottom && span[0] < top) shape = Shapes.or(shape, Block.box(cx, from, cz, cx + COLUMN, to, cz + COLUMN));
                }
            }
            return shape.optimize();
        });
    }

    /** The shape of the sphere's block at pos. */
    public static VoxelShape shapeAt(@Nullable GasSphereBlockEntity sphere, BlockPos pos) {
        if (sphere == null) return Shapes.block();
        Vec3 centre = sphere.centre();
        return shape(offset(pos.getX(), centre.x), offset(pos.getZ(), centre.z), pos.getY() - sphere.getBlockPos().getY(),
                sphere.getBlockState().getValue(FACING));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return STATION;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level.isClientSide()) return;
        BlockPos ladder = FloatingRoofTankBlock.cell(pos, state, 0, 0, 0);
        for (BlockPos part : parts(pos, state)) {
            boolean isLadder = part.getX() == ladder.getX() && part.getZ() == ladder.getZ();
            level.setBlock(part, SetpointContent.GAS_SPHERE_PART.get().defaultBlockState().setValue(GasSpherePartBlock.LADDER, isLadder), Block.UPDATE_ALL);
            if (level.getBlockEntity(part) instanceof MultiblockPartBlockEntity be) be.setBase(pos);
        }
    }

    /** Gone: its parts go too. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        for (BlockPos part : parts(pos, state)) {
            if (level.getBlockEntity(part) instanceof MultiblockPartBlockEntity be && pos.equals(be.base())) level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Its renderer draws all of it. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GasSphereBlockEntity sphere) sphere.describe(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GasSphereBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SetpointContent.GAS_SPHERE_ENTITY.get(), GasSphereBlockEntity::serverTick);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** A comparator reads the pressure: 15 at what it is built for. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof GasSphereBlockEntity sphere ? Vessel.comparatorSignal(sphere.pressure() / GasSphereBlockEntity.MAX_PRESSURE) : 0;
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
        builder.add(FACING);
    }
}
