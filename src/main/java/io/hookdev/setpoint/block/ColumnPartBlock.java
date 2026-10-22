package io.hookdev.setpoint.block;

import io.hookdev.setpoint.block.entity.ColumnPartBlockEntity;
import io.hookdev.setpoint.gas.GasPortBlock;
import io.hookdev.setpoint.hydraulics.VesselBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The rest of a distillation column: the shell, the reboiler, the condenser, and the nozzles on its front where the
 * pipes go (each one a vessel of its own product, or a gas connection). Right-click a nozzle to read what it is.
 */
public class ColumnPartBlock extends MultiblockPartBlock implements VesselBlock, GasPortBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PORT = IntegerProperty.create("port", 0, 7);
    /** Where in the column this block is: x 0..2 (left to right), back 0..3 (the front row is 0), up 0..10. */
    public static final IntegerProperty X = IntegerProperty.create("x", 0, 2), BACK = IntegerProperty.create("back", 0, 3),
            UP = IntegerProperty.create("up", 0, 10);
    public static final int NONE = 0, STEAM = 1, RESIDUE = 2, DIESEL = 3, KEROSENE = 4, NAPHTHA = 5, WATER = 6, LPG = 7;
    public static final String[] PORT_NAMES = {"", "STEAM IN", "RESIDUE OUT", "DIESEL OUT", "KEROSENE OUT", "NAPHTHA OUT", "COOLING WATER IN", "LPG OUT"};

    public ColumnPartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PORT, NONE)
                .setValue(X, 1).setValue(BACK, 1).setValue(UP, 0));
    }

    public static boolean liquid(int port) {
        return port == RESIDUE || port == DIESEL || port == KEROSENE || port == NAPHTHA || port == WATER;
    }

    @Override
    public boolean takesPipe(BlockState state, Direction side) {
        return liquid(state.getValue(PORT)) && side == state.getValue(FACING);
    }

    @Override
    public boolean gasPort(BlockState state, Direction side) {
        int port = state.getValue(PORT);
        return (port == STEAM || port == LPG) && side == state.getValue(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DistillationColumnBlock.shape(state.getValue(FACING), state.getValue(X) - 1, state.getValue(BACK), state.getValue(UP));
    }

    /** A nozzle tells you what it is; the rest of the column reads the column. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(PORT) == NONE) return super.useWithoutItem(state, level, pos, player, hitResult);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ColumnPartBlockEntity part) part.describe(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ColumnPartBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PORT, X, BACK, UP);
    }
}
