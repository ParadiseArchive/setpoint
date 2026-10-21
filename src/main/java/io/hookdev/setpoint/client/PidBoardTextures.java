package io.hookdev.setpoint.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.block.entity.PidBoardBlockEntity;
import io.hookdev.setpoint.pid.BoardLayout;
import io.hookdev.setpoint.pid.PidDrawing;
import io.hookdev.setpoint.pid.PidLive;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * One texture per P&ID board panel, like the texture of a map: the drawing painted on blueprint paper with its live
 * colours. It is painted again when new data arrives, when the panel changes size, and while alarms flash.
 */
public final class PidBoardTextures {
    private static final int FRAME = 0xFF3A404C, TITLE_BAR = 0xFF1E222A, HOVER = 0xFFFFFFFF;
    private static final Map<BlockPos, Board> BOARDS = new HashMap<>();

    private PidBoardTextures() {}

    private static final class Board {
        final DynamicTexture texture;
        final Identifier id;
        final int width, height;
        int version = -1, hovered = -1;
        boolean blink;

        Board(BlockPos pos, int width, int height) {
            this.width = width;
            this.height = height;
            this.texture = new DynamicTexture(() -> "P&ID board " + pos.toShortString(), width * BoardLayout.PIXELS_PER_BLOCK, height * BoardLayout.PIXELS_PER_BLOCK, true);
            this.id = Setpoint.id("pid_board/" + Math.floorMod(pos.asLong(), Long.MAX_VALUE));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }

        void close() {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    /** The symbol the player looks at, per board: framed on the panel. */
    private static final Map<BlockPos, Integer> HOVERED = new HashMap<>();

    public static void hover(BlockPos topLeft, int cellIndex) {
        if (cellIndex < 0) HOVERED.remove(topLeft);
        else HOVERED.put(topLeft.immutable(), cellIndex);
    }

    /** The texture of the panel whose top-left board this is, painted up to date. */
    public static Identifier prepare(PidBoardBlockEntity board, PidDrawing drawing, int width, int height) {
        BlockPos pos = board.getBlockPos().immutable();
        Board b = BOARDS.get(pos);
        if (b == null || b.width != width || b.height != height) {
            if (b != null) b.close();
            b = new Board(pos, width, height);
            BOARDS.put(pos, b);
        }
        Map<String, PidLive> live = board.live();
        boolean blink = (Util.getMillis() / 400) % 2 == 0;
        boolean flashing = live.values().stream().anyMatch(l -> l.state() == PidLive.ALARM);
        int hovered = HOVERED.getOrDefault(pos, -1);
        if (b.version != board.version() || (flashing && blink != b.blink) || hovered != b.hovered) {
            paint(b.texture.getPixels(), drawing, BoardLayout.of(drawing, width, height), new LiveStyle(live, blink), hovered);
            b.texture.upload();
            b.version = board.version();
            b.blink = blink;
            b.hovered = hovered;
        }
        return b.id;
    }

    private static void paint(NativeImage image, PidDrawing drawing, BoardLayout board, PidRenderer.Style style, int hovered) {
        int w = image.getWidth(), h = image.getHeight(), m = BoardLayout.MARGIN;
        PidCanvas canvas = (x0, y0, x1, y1, colour) -> {
            int ax = Math.max(0, x0), ay = Math.max(0, y0), bx = Math.min(w, x1), by = Math.min(h, y1);
            if (bx > ax && by > ay) image.fillRect(ax, ay, bx - ax, by - ay, colour);
        };
        canvas.fill(0, 0, w, h, FRAME);
        canvas.fill(m, m, w - m, m + BoardLayout.TITLE, TITLE_BAR); // the renderer writes the name here
        canvas.fill(m, m + BoardLayout.TITLE, w - m, h - m, PidRenderer.BLUEPRINT);
        PidRenderer.Layout layout = PidRenderer.Layout.of(board.originX(), board.originY(), board.cell());
        PidRenderer.draw(canvas, drawing, layout, style);
        if (hovered >= 0 && hovered < drawing.cells().size()) { // a thin frame around the symbol being looked at
            PidDrawing.Cell c = drawing.cells().get(hovered);
            int x0 = layout.originX() + c.x() * layout.cell(), y0 = layout.originY() + c.y() * layout.cell(), s = layout.cell();
            canvas.fill(x0, y0, x0 + s, y0 + 1, HOVER); canvas.fill(x0, y0 + s - 1, x0 + s, y0 + s, HOVER);
            canvas.fill(x0, y0, x0 + 1, y0 + s, HOVER); canvas.fill(x0 + s - 1, y0, x0 + s, y0 + s, HOVER);
        }
    }

    /** The board is gone: free its texture. */
    public static void remove(BlockPos pos) {
        Board b = BOARDS.remove(pos);
        if (b != null) b.close();
    }

    /** Leaving a world: free every board texture. */
    public static void clear() {
        BOARDS.values().forEach(Board::close);
        BOARDS.clear();
        HOVERED.clear();
    }
}
