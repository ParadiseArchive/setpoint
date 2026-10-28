package io.hookdev.setpoint.item;

import io.hookdev.setpoint.McVersion;
import java.util.List;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.block.FluidPipeBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Pipe wrench. On a pipe: cut or rejoin the arm you click. On a machine: turn it a quarter. Sneak: pick the
 * machine up straight into your inventory. Works on every block in the setpoint:wrenchable tag.
 */
public class PipeWrenchItem extends Item {
    public static final TagKey<Block> WRENCHABLE = TagKey.create(Registries.BLOCK, Setpoint.id("wrenchable"));

    public PipeWrenchItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (!state.is(WRENCHABLE) || player == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (player.isSecondaryUseActive()) {
            pickUp((ServerLevel) level, pos, state, player, stack);
        } else if (state.getBlock() instanceof FluidPipeBlock) {
            cut(level, pos, state, armAt(context.getClickLocation(), pos, context.getClickedFace()), player);
        } else {
            turn(level, pos, state, player);
        }
        return InteractionResult.SUCCESS;
    }

    /** Which arm of the pipe was clicked: the side the click is furthest out on, or the face if it was the middle. */
    static Direction armAt(Vec3 click, BlockPos pos, Direction face) {
        double x = click.x - pos.getX() - 0.5, y = click.y - pos.getY() - 0.5, z = click.z - pos.getZ() - 0.5;
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (Math.max(ax, Math.max(ay, az)) < 3.5 / 16) return face; // the core of the pipe
        if (ax >= ay && ax >= az) return x > 0 ? Direction.EAST : Direction.WEST;
        if (ay >= az) return y > 0 ? Direction.UP : Direction.DOWN;
        return z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void cut(Level level, BlockPos pos, BlockState state, Direction side, Player player) {
        BlockState cut = FluidPipeBlock.refresh(state.cycle(FluidPipeBlock.CUT.get(side)), level, pos);
        level.setBlock(pos, cut, Block.UPDATE_ALL);
        boolean nowCut = cut.getValue(FluidPipeBlock.CUT.get(side));
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, nowCut ? 0.7f : 1.1f);
        say(player, "Pipe " + side.getSerializedName() + " side " + (nowCut ? "cut" : "joined"), nowCut ? ChatFormatting.GOLD : ChatFormatting.AQUA);
    }

    private static void turn(Level level, BlockPos pos, BlockState state, Player player) {
        BlockState turned = state;
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            turned = state.setValue(BlockStateProperties.HORIZONTAL_FACING, state.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise());
        } else if (state.hasProperty(BlockStateProperties.AXIS)) {
            turned = state.setValue(BlockStateProperties.AXIS, switch (state.getValue(BlockStateProperties.AXIS)) {
                case X -> Direction.Axis.Z;
                case Z -> Direction.Axis.Y;
                case Y -> Direction.Axis.X;
            });
        }
        if (turned == state) return;
        level.setBlock(pos, turned, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 0.9f);
        say(player, "Turned", ChatFormatting.AQUA);
    }

    private static void pickUp(ServerLevel level, BlockPos pos, BlockState state, Player player, ItemStack wrench) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, wrench);
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.8f, 1.0f);
        level.removeBlock(pos, false);
        for (ItemStack drop : drops) {
            if (!player.getInventory().add(drop)) McVersion.drop(player, drop);
        }
    }

    private static void say(Player player, String text, ChatFormatting colour) {
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal(text).withStyle(colour), true);
    }
}
