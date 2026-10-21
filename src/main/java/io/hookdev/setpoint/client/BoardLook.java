package io.hookdev.setpoint.client;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.PidBoardBlock;
import io.hookdev.setpoint.block.entity.PidBoardBlockEntity;
import io.hookdev.setpoint.pid.BoardLayout;
import io.hookdev.setpoint.pid.PidDrawing;
import io.hookdev.setpoint.pid.PidLive;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Looking at a P&ID board: the symbol under the crosshair gets a frame, and its tag, type, status and value show
 * above the hotbar, like hovering a symbol on a DCS screen.
 */
public final class BoardLook {
    private static @Nullable BlockPos lastBoard;
    private static int lastCell = -1;
    private static int age;

    private BoardLook() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        BlockPos board = null;
        int cell = -1;
        PidDrawing.Cell target = null;
        PidBoardBlockEntity origin = null;
        if (mc.level != null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockState state = mc.level.getBlockState(hit.getBlockPos());
            if (state.getBlock() instanceof PidBoardBlock) {
                Direction facing = state.getValue(PidBoardBlock.FACING);
                BlockPos topLeft = PidBoardBlock.topLeft(mc.level, hit.getBlockPos(), facing);
                if (mc.level.getBlockEntity(topLeft) instanceof PidBoardBlockEntity be && be.drawing() != null) {
                    int[] size = PidBoardBlock.size(mc.level, topLeft, facing);
                    double[] uv = BoardLayout.panelPoint(topLeft, facing, size[0], size[1], hit.getBlockPos(), hit.getLocation());
                    target = BoardLayout.of(be.drawing(), size[0], size[1]).cellAt(be.drawing(), uv[0], uv[1]);
                    board = topLeft;
                    origin = be;
                    cell = target == null ? -1 : be.drawing().cells().indexOf(target);
                }
            }
        }
        if (lastBoard != null && (!lastBoard.equals(board) || cell < 0)) PidBoardTextures.hover(lastBoard, -1);
        if (board != null) PidBoardTextures.hover(board, cell);
        boolean changed = cell != lastCell || (board != null && !board.equals(lastBoard));
        lastBoard = board;
        lastCell = cell;
        if (target == null || origin == null) return;
        if (!changed && ++age % 10 != 0) return; // keep the line up to date while looking
        mc.gui.hud.setOverlayMessage(describe(target, origin.live().get(target.liveKey())), false);
    }

    private static Component describe(PidDrawing.Cell cell, @Nullable PidLive live) {
        MutableComponent line = Component.empty();
        if (!cell.tag().isEmpty()) line.append(Component.literal(cell.tag() + "  ").withStyle(ChatFormatting.AQUA));
        if (live != null && cell.tag().isEmpty()) { // a hand or drain valve
            boolean open = live.status().equals("OPEN");
            return line.append(Component.literal(PidDrawing.describe(cell.symbol()).replace(", open", "").replace(", shut", "")).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("  " + live.status()).withStyle(open ? ChatFormatting.AQUA : ChatFormatting.GOLD))
                    .append(Component.literal("  (right-click: " + (open ? "shut" : "open") + ")").withStyle(ChatFormatting.DARK_GRAY));
        }
        line.append(Component.literal(PidDrawing.describe(cell.symbol())).withStyle(ChatFormatting.GRAY));
        if (live != null) {
            ChatFormatting colour = live.state() == PidLive.ALARM ? ChatFormatting.LIGHT_PURPLE : live.state() == PidLive.ATTENTION ? ChatFormatting.GOLD : ChatFormatting.WHITE;
            line.append(Component.literal("  " + live.status()).withStyle(colour))
                    .append(Component.literal("  " + live.value()).withStyle(ChatFormatting.WHITE));
            if (live.kind().equals("PUMP")) line.append(Component.literal("  (right-click: start / stop)").withStyle(ChatFormatting.DARK_GRAY));
            if (live.kind().endsWith("IC")) line.append(Component.literal("  (right-click: faceplate)").withStyle(ChatFormatting.DARK_GRAY));
        } else if (!cell.tag().isEmpty()) {
            line.append(Component.literal("  not seen: gone, or out of range").withStyle(ChatFormatting.DARK_GRAY));
        }
        return line;
    }
}
