package io.hookdev.setpoint.client;

import io.hookdev.setpoint.pid.PidDrawing;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The P&ID symbols as 8x8 pixel art, after the usual P&ID conventions: a circle for a pump, a bow tie for a
 * valve (filled when shut), a bubble for a field instrument, a circle in a square for a DCS controller.
 * '#' is the line colour, '+' the accent (liquid, letters). Drawn pointing east and turned by the cell's rotation.
 */
public final class PidSymbols {
    private PidSymbols() {}

    private static final String[] PUMP = {
            "..####..", ".#....##", "#..#...#", "#..##..#", "#..###.#", "#..##..#", ".#.#..#.", "..####.."};
    private static final String[] TANK = {
            ".######.", "#......#", "#......#", "#++++++#", "#++++++#", "#++++++#", "#++++++#", ".######."};
    private static final String[] CONTROL_VALVE = {
            "..####..", ".#....#.", "...##...", "#..##..#", "##....##", "#.#..#.#", "##....##", "#......#"};
    private static final String[] HAND_VALVE = {
            ".######.", "...##...", "...##...", "#......#", "##....##", "#.#..#.#", "##....##", "#......#"};
    private static final String[] HAND_VALVE_SHUT = {
            ".######.", "...##...", "...##...", "#......#", "##....##", "###..###", "##....##", "#......#"};
    private static final String[] LEVEL_TRANSMITTER = {
            "..####..", ".#....#.", "#......#", "#.+....#", "#.+....#", "#.+++..#", ".#....#.", "..####.."};
    private static final String[] FLOW_TRANSMITTER = {
            "..####..", ".#....#.", "#.+++..#", "#.+....#", "#.++...#", "#.+....#", ".#....#.", "..####.."};
    private static final String[] CONTROLLER = {
            "########", "#.####.#", "##....##", "#..++..#", "#..++..#", "##....##", "#.####.#", "########"};
    private static final String[] BOILER = {
            ".######.", "#++++++#", "#++++++#", "#......#", "#..#...#", "#.###..#", "#.#.##.#", "########"};
    private static final String[] TURBINE = {
            "##......", "#.##....", "#...##..", "#.....##", "#.....##", "#...##..", "#.##....", "##......"};
    private static final String[] GENERATOR = {
            "..####..", ".#....#.", "#......#", "#.+..+.#", "#+.++.+#", "#......#", ".#....#.", "..####.."};
    private static final String[] INTAKE = {
            "########", "#.#.#.##", "#.#.#.##", "########", "........", ".++..++.", "+..++..+", "........"};
    private static final String[] CONSOLE = {
            "########", "#++++++#", "#+....+#", "#++++++#", "########", "...##...", "..####..", "........"};

    public static String[] mask(int symbol) {
        return switch (symbol) {
            case PidDrawing.PUMP -> PUMP;
            case PidDrawing.TANK -> TANK;
            case PidDrawing.CONTROL_VALVE -> CONTROL_VALVE;
            case PidDrawing.HAND_VALVE -> HAND_VALVE;
            case PidDrawing.HAND_VALVE_SHUT -> HAND_VALVE_SHUT;
            case PidDrawing.LEVEL_TRANSMITTER -> LEVEL_TRANSMITTER;
            case PidDrawing.FLOW_TRANSMITTER -> FLOW_TRANSMITTER;
            case PidDrawing.CONTROLLER -> CONTROLLER;
            case PidDrawing.BOILER -> BOILER;
            case PidDrawing.TURBINE -> TURBINE;
            case PidDrawing.GENERATOR -> GENERATOR;
            case PidDrawing.INTAKE -> INTAKE;
            case PidDrawing.CONSOLE -> CONSOLE;
            default -> null;
        };
    }

    /** Only symbols with a direction turn; vessels and instruments always stand upright. */
    private static boolean turns(int symbol) {
        return symbol == PidDrawing.PUMP || symbol == PidDrawing.CONTROL_VALVE || symbol == PidDrawing.HAND_VALVE
                || symbol == PidDrawing.HAND_VALVE_SHUT || symbol == PidDrawing.TURBINE;
    }

    /** Draws a symbol with its top-left corner at (x, y), each mask pixel {@code scale} screen pixels big. */
    public static void draw(GuiGraphicsExtractor g, int symbol, int rotation, int x, int y, int scale, int line, int accent) {
        String[] mask = mask(symbol);
        if (mask == null) return;
        int turns = turns(symbol) ? rotation & 3 : 0;
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
            // look the pixel up in the unturned mask: turning clockwise k times
            int r = row, c = col;
            for (int k = 0; k < turns; k++) { int t = r; r = 7 - c; c = t; }
            char ch = mask[r].charAt(c);
            if (ch == '.') continue;
            int colour = ch == '+' ? accent : line;
            g.fill(x + col * scale, y + row * scale, x + (col + 1) * scale, y + (row + 1) * scale, colour);
        }
    }
}
