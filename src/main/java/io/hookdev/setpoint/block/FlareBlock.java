package io.hookdev.setpoint.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.FlareBlockEntity;
import io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity;
import io.hookdev.setpoint.gas.GasPortBlock;

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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A flare stack: the tallest thing on a plant. A steel riser forty blocks high in a derrick, a lattice tower that
 * narrows towards the top, banded red and white for aircraft, with three platforms and a caged ladder up the inside.
 * At the foot stands the knock-out drum, and in front of it the flare's own block, where you placed it: the ignition
 * panel with three gas connections. The flare header comes in at the front, pilot gas on your left, steam on your
 * right. {@link FlarePartBlock}s fill the rest; its renderer draws all of it, and the flame on top.
 */
public class FlareBlock extends SetpointEntityBlock implements MultiblockBase, GasPortBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** In blocks: the tower reaches this far either side of the stack, the stack stands this far behind this block, and it is this high. */
    public static final int HALF = 3, BACK = 7, HIGH = 40;
    /**
     * The shape, in pixels from the middle of the stack and up from the ground (FlareRenderer and tools/gen-assets.mjs
     * draw the same): the derrick's legs stand this far out at the foot and at the top, the stack and the tip.
     */
    public static final int LEG_LOW = 48, LEG_HIGH = 22, TOWER_TOP = 576, PANEL = 96, STACK_RADIUS = 5, TIP_BOTTOM = 592, TIP_TOP = 640;
    /** The platforms: their decks, and how far each reaches from the stack (a square). */
    public static final int[] DECKS = {192, 384, 576}, DECK_HALF = {48, 40, 40};
    /** The knock-out drum lies along the ground from the tower to this block; a pipe runs from its back end to the stack. */
    public static final int DRUM_RADIUS = 12, DRUM_Y = 18, DRUM_BACK = 56, DRUM_FRONT = 104, PIPE_Y = 40, PIPE_RADIUS = 4, PIPE_Z = 64;
    /** The caged ladder: in the block to the right of the stack (seen from this block), up to one block above the top deck. */
    public static final int LADDER_I = 1, LADDER_TOP = 36;
    /** The tip's blocks, which light up while it burns. */
    public static final int LIT_FROM = 37;
    private static final VoxelShape STATION = Block.box(2, 0, 2, 14, 14, 14);
    private static final int COLUMN = 4;
    private static final Map<Long, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    public FlareBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        for (BlockPos part : parts(context.getClickedPos(), state)) {
            if (!context.getLevel().isInWorldBounds(part) || !context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return state;
    }

    /** The flare header comes in at the front. */
    public static Direction header(BlockState state) {
        return state.getValue(FACING);
    }

    /** Pilot gas (LPG or hydrogen) on your left as you face it. */
    public static Direction pilot(BlockState state) {
        return state.getValue(FACING).getClockWise();
    }

    /** Steam, to burn without smoke, on your right. */
    public static Direction steam(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    @Override
    public boolean gasPort(BlockState state, Direction side) {
        return side == header(state) || side == pilot(state) || side == steam(state);
    }

    /**
     * One block of the flare: i across (to your right as you face this block), j back towards the stack (the stack
     * stands at j = 0 and this block at j = {@link #BACK}), y up.
     */
    public static BlockPos cell(BlockPos base, BlockState state, int i, int j, int y) {
        Direction front = state.getValue(FACING);
        return base.relative(front, j - BACK).relative(front.getCounterClockWise(), i).above(y);
    }

    public static BlockPos stack(BlockPos base, BlockState state) {
        return cell(base, state, 0, 0, 0);
    }

    public static boolean isLadder(int i, int j, int y) {
        return i == LADDER_I && j == 0 && y <= LADDER_TOP;
    }

    /** A block of the flare besides this one, and where it is in the flare. */
    public record Part(BlockPos pos, int i, int j, int y) {
        public boolean ladder() { return isLadder(i, j, y); }
        /** The top of the stack, which lights up while the flare burns. */
        public boolean tip() { return i == 0 && j == 0 && y >= LIT_FROM; }
    }

    /** Every block the flare takes up besides this one: wherever it has something solid, and the ladder. */
    public static List<Part> partCells(BlockPos base, BlockState state) {
        Direction front = state.getValue(FACING);
        BlockPos stack = stack(base, state);
        List<Part> parts = new ArrayList<>();
        for (int i = -HALF; i <= HALF; i++) for (int j = -HALF; j <= BACK; j++) for (int y = 0; y < HIGH; y++) {
            if (i == 0 && j == BACK && y == 0) continue;
            BlockPos pos = cell(base, state, i, j, y);
            if (isLadder(i, j, y) || !shape(pos.getX() - stack.getX(), pos.getZ() - stack.getZ(), y, front).isEmpty()) parts.add(new Part(pos, i, j, y));
        }
        return parts;
    }

    @Override
    public List<BlockPos> parts(BlockPos base, BlockState state) {
        return partCells(base, state).stream().map(Part::pos).toList();
    }

    /** How far the derrick's legs stand from the stack at this height, in pixels. */
    public static double legHalf(double y) {
        return LEG_LOW + (LEG_HIGH - LEG_LOW) * Math.clamp(y / TOWER_TOP, 0, 1);
    }

    /** The hole in each deck where the ladder comes through. */
    private static boolean ladderHole(double lx, double lz) {
        return lx > 8 && lx < 24 && Math.abs(lz) < 8;
    }

    /**
     * The solid part of one block, in columns of four pixels: the stack, the derrick's legs, the platform decks with a
     * handrail you cannot jump over, the knock-out drum and its pipe to the stack. dx, dz are the block's offset from the
     * stack's block in blocks, front is the way the flare faces. The bracing has no collision: you walk through the tower.
     */
    public static VoxelShape shape(int dx, int dz, int layer, Direction front) {
        long key = ((long) front.get2DDataValue() << 32) | ((long) (dx + 16) << 24) | ((long) (dz + 16) << 16) | layer;
        return SHAPES.computeIfAbsent(key, k -> {
            Direction right = front.getCounterClockWise();
            VoxelShape shape = Shapes.empty();
            int bottom = layer * 16, top = bottom + 16;
            double leg = legHalf(bottom + 8);
            for (int cx = 0; cx < 16; cx += COLUMN) for (int cz = 0; cz < 16; cz += COLUMN) {
                double wx = dx * 16 + cx + COLUMN / 2.0 - 8, wz = dz * 16 + cz + COLUMN / 2.0 - 8;
                double lx = wx * right.getStepX() + wz * right.getStepZ(), lz = wx * front.getStepX() + wz * front.getStepZ();
                List<double[]> spans = new ArrayList<>();
                if (Math.abs(lx) < STACK_RADIUS && Math.abs(lz) < STACK_RADIUS) spans.add(new double[]{0, TIP_TOP}); // the stack and its tip
                if (bottom < TOWER_TOP && Math.abs(Math.abs(lx) - leg) <= 3 && Math.abs(Math.abs(lz) - leg) <= 3) {
                    spans.add(new double[]{bottom, Math.min(top, TOWER_TOP)});                                   // a leg
                }
                double out = Math.max(Math.abs(lx), Math.abs(lz));
                for (int d = 0; d < DECKS.length; d++) {
                    if (out < DECK_HALF[d] && !ladderHole(lx, lz)) spans.add(new double[]{DECKS[d] - 4, DECKS[d]});       // the deck
                    if (out > DECK_HALF[d] - 4 && out < DECK_HALF[d]) spans.add(new double[]{DECKS[d], DECKS[d] + 24});   // its handrail
                }
                if (Math.abs(lx) < DRUM_RADIUS && lz > DRUM_BACK && lz < DRUM_FRONT) {                                     // the drum, on its saddles
                    spans.add(new double[]{0, DRUM_Y + Math.sqrt(DRUM_RADIUS * DRUM_RADIUS - lx * lx)});
                }
                if (Math.abs(lx) < PIPE_RADIUS && Math.abs(lz - PIPE_Z) < PIPE_RADIUS) spans.add(new double[]{DRUM_Y, PIPE_Y + PIPE_RADIUS});
                if (Math.abs(lx) < PIPE_RADIUS && lz > 0 && lz < PIPE_Z) spans.add(new double[]{PIPE_Y - PIPE_RADIUS, PIPE_Y + PIPE_RADIUS});
                for (double[] span : spans) {
                    double from = Math.clamp(span[0] - bottom, 0, 16), to = Math.clamp(span[1] - bottom, 0, 16);
                    if (to > from) shape = Shapes.or(shape, Block.box(cx, from, cz, cx + COLUMN, to, cz + COLUMN));
                }
            }
            return shape.optimize();
        });
    }

    /** The shape of the flare's block at pos. */
    public static VoxelShape shapeAt(@Nullable FlareBlockEntity flare, BlockPos pos) {
        if (flare == null) return Shapes.block();
        BlockPos stack = flare.stack();
        return shape(pos.getX() - stack.getX(), pos.getZ() - stack.getZ(), pos.getY() - flare.getBlockPos().getY(), flare.getBlockState().getValue(FACING));
    }

    /** The rungs of a ladder block: three pixels on the side towards the stack, to climb by walking into them. */
    public static VoxelShape rungs(@Nullable FlareBlockEntity flare) {
        if (flare == null) return Shapes.empty();
        return switch (flare.getBlockState().getValue(FACING).getClockWise()) {
            case WEST -> Block.box(0, 0, 0, 3, 16, 16);
            case EAST -> Block.box(13, 0, 0, 16, 16, 16);
            case NORTH -> Block.box(0, 0, 0, 16, 16, 3);
            default -> Block.box(0, 0, 13, 16, 16, 16);
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return STATION;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level.isClientSide()) return;
        for (Part part : partCells(pos, state)) {
            level.setBlock(part.pos(), SetpointContent.FLARE_PART.get().defaultBlockState()
                    .setValue(FlarePartBlock.LADDER, part.ladder()).setValue(FlarePartBlock.TIP, part.tip()), Block.UPDATE_ALL);
            if (level.getBlockEntity(part.pos()) instanceof MultiblockPartBlockEntity be) be.setBase(pos);
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

    /** Right-click: light it, or see how it burns; sneak-right-click: the pressure above which it takes gas off the header. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FlareBlockEntity flare) {
            if (player.isSecondaryUseActive()) flare.nextSetPressure(player);
            else if (!flare.lit()) flare.ignite(player);
            else flare.describe(player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlareBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, SetpointContent.FLARE_ENTITY.get(), FlareBlockEntity::clientTick)
                : createTickerHelper(type, SetpointContent.FLARE_ENTITY.get(), FlareBlockEntity::serverTick);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** A comparator reads how hard it burns: 15 at a thousand mB a tick. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof FlareBlockEntity flare ? flare.comparatorSignal() : 0;
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
