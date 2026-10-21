package io.hookdev.setpoint.client;

/**
 * Something a P&ID can be drawn on with filled rectangles: a screen ({@code g::fill}) or the texture of a P&ID board.
 * Corners are (x0, y0) inclusive and (x1, y1) exclusive; colours are ARGB.
 */
@FunctionalInterface
public interface PidCanvas {
    void fill(int x0, int y0, int x1, int y1, int colour);
}
