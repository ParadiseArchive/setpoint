package io.hookdev.setpoint.block;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity;
import io.hookdev.setpoint.block.entity.PalletBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wooden pallet, two by two blocks. Right-click it with a drum to load it: up to four, strapped together with a
 * green band (three stand in a triangle). Sneak-right-click with an empty hand to take the last one off. The drums
 * keep their liquid and colour; break the pallet and everything drops.
 */
public class PalletBlock extends BaseEntityBlock implements MultiblockBase {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Drums on it. */
    public static final IntegerProperty DRUMS = IntegerProperty.create("drums", 0, PalletBlockEntity.MAX_DRUMS);
    private static final VoxelShape EMPTY = Block.box(0, 0, 0, 16, 3, 16);

    public PalletBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DRUMS, 0));
    }

    /** The pallet reaches one block clockwise from the front and one block back; with drums on it, one block up too. */
    @Override
    public List<BlockPos> parts(BlockPos base, BlockState state) {
        return parts(base, state.getValue(FACING), state.getValue(DRUMS) > 0);
    }

    /** The other blocks of a pallet whose base is here, facing this way, with or without drums. */
    public static List<BlockPos> footprint(BlockPos base, Direction front, boolean loaded) {
        return parts(base, front, loaded);
    }

    /** Sets a pallet down here, with these drums on it (a pallet truck does this). */
    public static void place(Level level, BlockPos base, Direction facing, List<ItemStack> drums) {
        int count = Math.min(PalletBlockEntity.MAX_DRUMS, drums.size());
        BlockState state = SetpointContent.PALLET.get().defaultBlockState().setValue(FACING, facing).setValue(DRUMS, count);
        level.setBlock(base, state, Block.UPDATE_ALL);
        if (level.getBlockEntity(base) instanceof PalletBlockEntity pallet) for (int i = 0; i < count; i++) pallet.add(drums.get(i));
        placeParts(level, base, state);
    }

    private static List<BlockPos> parts(BlockPos base, Direction front, boolean loaded) {
        Direction side = front.getClockWise(), back = front.getOpposite();
        List<BlockPos> parts = new ArrayList<>();
        for (int y = 0; y < (loaded ? 2 : 1); y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            if (x + y + z > 0) parts.add(base.relative(side, x).above(y).relative(back, z));
        }
        return parts;
    }

    /** The blocks above the pallet, where the drums stand. */
    private static List<BlockPos> above(BlockPos base, Direction front) {
        return parts(base, front, true).stream().filter(p -> p.getY() > base.getY()).toList();
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        for (BlockPos part : parts(context.getClickedPos(), state)) {
            if (!context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (!level.isClientSide()) placeParts(level, pos, state);
    }

    /** Puts the parts where they belong for this many drums, and the right shape on each. */
    private static void placeParts(Level level, BlockPos base, BlockState state) {
        boolean loaded = state.getValue(DRUMS) > 0;
        for (BlockPos part : parts(base, state.getValue(FACING), loaded)) {
            int kind = part.getY() > base.getY() ? PalletPartBlock.TOP : loaded ? PalletPartBlock.LOADED : PalletPartBlock.LOW;
            BlockState want = SetpointContent.PALLET_PART.get().defaultBlockState().setValue(PalletPartBlock.KIND, kind);
            if (level.getBlockState(part) != want) level.setBlock(part, want, Block.UPDATE_ALL);
            if (level.getBlockEntity(part) instanceof MultiblockPartBlockEntity be && !base.equals(be.base())) be.setBase(base);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        for (BlockPos part : parts(pos, state)) removePart(level, part, pos);
    }

    private static void removePart(Level level, BlockPos part, BlockPos base) {
        if (level.getBlockEntity(part) instanceof MultiblockPartBlockEntity be && base.equals(be.base())) {
            be.release();
            level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Right-click with a drum: put it on. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(SetpointContent.DRUM_ITEM.get())) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof PalletBlockEntity pallet)) return InteractionResult.SUCCESS;
        int drums = state.getValue(DRUMS);
        if (drums >= PalletBlockEntity.MAX_DRUMS) {
            say(player, "The pallet is full: four drums.");
            return InteractionResult.SUCCESS;
        }
        if (drums == 0) {
            for (BlockPos up : above(pos, state.getValue(FACING))) {
                if (!level.getBlockState(up).canBeReplaced()) {
                    say(player, "The drums need room above the pallet.");
                    return InteractionResult.SUCCESS;
                }
            }
        }
        pallet.add(stack.copyWithCount(1));
        stack.consume(1, player);
        BlockState next = state.setValue(DRUMS, drums + 1);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        placeParts(level, pos, next);
        level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.6f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    /** Sneak-right-click with an empty hand: take the last drum off. An empty hand otherwise reads what is on it. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof PalletBlockEntity pallet)) return InteractionResult.SUCCESS;
        if (!player.isSecondaryUseActive()) {
            pallet.describe(player);
            return InteractionResult.SUCCESS;
        }
        ItemStack drum = pallet.takeLast();
        if (drum.isEmpty()) return InteractionResult.SUCCESS;
        if (!player.getInventory().add(drum)) player.drop(drum, false, Prediction.SERVER_ONLY);
        int drums = state.getValue(DRUMS) - 1;
        BlockState next = state.setValue(DRUMS, drums);
        if (drums == 0) for (BlockPos up : above(pos, state.getValue(FACING))) removePart(level, up, pos);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        placeParts(level, pos, next);
        level.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.BLOCKS, 0.5f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    private static void say(Player player, String text) {
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY), true);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PalletBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(DRUMS) > 0 ? Shapes.block() : EMPTY;
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
        builder.add(FACING, DRUMS);
    }
}
