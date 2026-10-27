package io.hookdev.setpoint.block;

import java.util.ArrayList;
import java.util.List;

import io.hookdev.setpoint.block.entity.ConsoleBlockEntity;
import io.hookdev.setpoint.console.Consoles;
import io.hookdev.setpoint.control.AlarmList;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The control room printer. Feed it a sheet of paper and it prints the alarm log of the nearest DCS console as a
 * book: every alarm on the list with its time, tag, message and state, for the shift handover.
 */
public class PrinterBlock extends FurnitureBlock {
    public static final int RANGE = 32;
    private static final int ALARMS_PER_PAGE = 4;

    public PrinterBlock(BlockBehaviour.Properties properties, VoxelShape north) {
        super(properties, north);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(Items.PAPER)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        ConsoleBlockEntity console = Consoles.nearest(server, pos, RANGE);
        if (console == null) {
            say(player, Component.literal("No DCS console within " + RANGE + " blocks to print from.").withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Alarm log"), "Printer", 0, pages(server, console), true));
        if (!player.getInventory().add(book)) net.minecraft.world.level.block.Block.popResource(server, pos.above(), book);
        server.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 0.8f);
        say(player, Component.literal("Printed the alarm log of the console at " + where(console) + ".").withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) say(player, Component.literal("Feed it paper to print the alarm log.").withStyle(ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }

    private static void say(Player player, Component text) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(text, true);
    }

    /** A cover page, then the alarms, newest first, a few to a page. */
    public static List<Filterable<Component>> pages(ServerLevel level, ConsoleBlockEntity console) {
        List<AlarmList.Alarm> alarms = console.alarms().list();
        List<Filterable<Component>> pages = new ArrayList<>();
        long day = level.getOverworldClockTime() / 24000 + 1;
        pages.add(Filterable.passThrough(Component.literal("ALARM LOG\n\n").withStyle(ChatFormatting.BOLD)
                .append(Component.literal("Console at " + where(console) + "\nDay " + day + ", " + clock(level, level.getGameTime()) + "\n\n").withStyle(ChatFormatting.RESET))
                .append(Component.literal(alarms.isEmpty() ? "No alarms. A quiet shift." : alarms.size() + (alarms.size() == 1 ? " alarm" : " alarms") + " on the list.").withStyle(ChatFormatting.RESET))));
        for (int i = 0; i < alarms.size(); i += ALARMS_PER_PAGE) {
            var page = Component.empty();
            for (int j = i; j < Math.min(alarms.size(), i + ALARMS_PER_PAGE); j++) {
                AlarmList.Alarm alarm = alarms.get(j);
                String state = switch (alarm.state()) {
                    case ACTIVE_UNACK -> "NEW";
                    case ACTIVE_ACK -> "ACK";
                    case CLEARED_UNACK -> "CLEARED";
                };
                page.append(Component.literal(clock(level, alarm.since()) + " " + alarm.tag() + "\n").withStyle(ChatFormatting.BOLD))
                        .append(Component.literal(alarm.message() + "\n").withStyle(ChatFormatting.RESET))
                        .append(Component.literal(state + "\n\n").withStyle(alarm.state() == AlarmList.State.ACTIVE_UNACK ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GRAY));
            }
            pages.add(Filterable.passThrough(page));
        }
        return pages;
    }

    private static String where(ConsoleBlockEntity console) {
        BlockPos p = console.getBlockPos();
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    /** The time of day a game tick fell on, as the wall clock shows it (hh:mm). */
    private static String clock(ServerLevel level, long gameTime) {
        long dayTime = level.getOverworldClockTime() - (level.getGameTime() - gameTime);
        long minutes = Math.floorMod(dayTime + 6000, 24000) * 60 / 1000;
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}
