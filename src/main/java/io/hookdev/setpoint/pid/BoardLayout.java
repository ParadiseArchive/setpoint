package io.hookdev.setpoint.pid;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where a P&ID sits on a board panel, in the pixels of the panel's texture: a steel frame, a title strip at the top,
 * and the drawing fitted into the rest. The client paints with it; the server uses the same numbers to find the
 * symbol a player clicked.
 *
 * @param texW   texture width, {@link #PIXELS_PER_BLOCK} per block of panel
 * @param texH   texture height
 * @param cell   pixels per cell of the drawing
 */
public record BoardLayout(int texW, int texH, int originX, int originY, int cell) {
    public static final int PIXELS_PER_BLOCK = 64, MARGIN = 3, TITLE = 10;

    public static BoardLayout of(PidDrawing drawing, int widthBlocks, int heightBlocks) {
        int w = widthBlocks * PIXELS_PER_BLOCK, h = heightBlocks * PIXELS_PER_BLOCK;
        int areaX = 2 * MARGIN, areaY = 2 * MARGIN + TITLE, areaW = w - 4 * MARGIN, areaH = h - 4 * MARGIN - TITLE;
        int cell = Math.max(2, Math.min(32, Math.min(areaW / Math.max(1, drawing.width()), areaH / Math.max(1, drawing.height()))));
        return new BoardLayout(w, h, areaX + (areaW - drawing.width() * cell) / 2, areaY + (areaH - drawing.height() * cell) / 2, cell);
    }

    /** The symbol at a point of the panel, given as fractions across (u) and down (v), or null. */
    public PidDrawing.@Nullable Cell cellAt(PidDrawing drawing, double u, double v) {
        double px = u * texW - originX, py = v * texH - originY;
        if (px < 0 || py < 0) return null;
        int x = (int) (px / cell), y = (int) (py / cell);
        for (PidDrawing.Cell c : drawing.cells()) if (c.x() == x && c.y() == y && c.symbol() != PidDrawing.PIPE) return c;
        return null;
    }

    /**
     * Where a point the player aims at lands on the panel, as fractions across and down ({u, v}).
     *
     * @param topLeft the board that holds the drawing
     * @param board   the board that was hit, one of the panel
     */
    public static double[] panelPoint(BlockPos topLeft, Direction facing, int widthBlocks, int heightBlocks, BlockPos board, Vec3 hit) {
        Direction right = facing.getCounterClockWise();
        int column = (board.getX() - topLeft.getX()) * right.getStepX() + (board.getZ() - topLeft.getZ()) * right.getStepZ();
        int row = topLeft.getY() - board.getY();
        double along = right.getStepX() != 0 ? hit.x - board.getX() : hit.z - board.getZ();
        if (right.getStepX() + right.getStepZ() < 0) along = 1 - along; // the panel runs towards -x or -z
        double down = 1 - (hit.y - board.getY());
        return new double[]{(column + along) / widthBlocks, (row + down) / heightBlocks};
    }
}
