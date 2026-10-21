package io.hookdev.setpoint.client;

import io.hookdev.setpoint.pid.PidDrawing;


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
    // an angle valve with a spring on top, and the outlet to the side
    private static final String[] SAFETY_VALVE = {
            "..#.#...", "...#....", "..#.#...", "...#....", ".#####..", "..###+++", "...#....", "..###..."};
    private static final String[] PRESSURE_TRANSMITTER = {
            "..####..", ".#....#.", "#.+++..#", "#.+..+.#", "#.+++..#", "#.+....#", ".#....#.", "..####.."};
    // a valve with its outlet open to the ground, and a drop coming out
    private static final String[] DRAIN_VALVE = {
            ".####...", "..##....", "#....#..", "##..##..", "#.##.#.+", "##..##++", "#....#++", "......+."};
    // a valve body with a bar across the outlet side: flow goes left to right only
    private static final String[] CHECK_VALVE = {
            "........", "#.....##", "##....##", "#.#...##", "#..#..##", "#.#...##", "##....##", "#.....##"};
    // a drum: a short barrel with its rolling hoops
    private static final String[] DRUM = {
            ".######.", "#......#", "########", "#......#", "#++++++#", "########", "#++++++#", ".######."};
    // an IBC: a square tote in its cage, on a pallet
    private static final String[] IBC = {
            "########", "#.#..#.#", "########", "#+#++#+#", "#+#++#+#", "########", "##.##.##", "#..#..#."};
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
            case PidDrawing.SAFETY_VALVE -> SAFETY_VALVE;
            case PidDrawing.PRESSURE_TRANSMITTER -> PRESSURE_TRANSMITTER;
            case PidDrawing.DRAIN_VALVE -> DRAIN_VALVE;
            case PidDrawing.CHECK_VALVE -> CHECK_VALVE;
            case PidDrawing.DRUM -> DRUM;
            case PidDrawing.IBC -> IBC;
            default -> null;
        };
    }

    /** Only symbols with a direction turn; vessels and instruments always stand upright. */
    private static boolean turns(int symbol) {
        return symbol == PidDrawing.PUMP || symbol == PidDrawing.CONTROL_VALVE || symbol == PidDrawing.HAND_VALVE
                || symbol == PidDrawing.HAND_VALVE_SHUT || symbol == PidDrawing.TURBINE || symbol == PidDrawing.DRAIN_VALVE || symbol == PidDrawing.CHECK_VALVE;
    }

    /** Draws a symbol with its top-left corner at (x, y), each mask pixel {@code scale} screen pixels big. */
    public static void draw(PidCanvas g, int symbol, int rotation, int x, int y, int scale, int line, int accent) {
        draw(g, symbol, rotation, x, y, scale, line, accent, -1);
    }

    /**
     * Same, but a tank with {@code fill} from 0 to 1 shows its real level: liquid up to that height inside the shell
     * instead of the fixed level of the drawing.
     */
    public static void draw(PidCanvas g, int symbol, int rotation, int x, int y, int scale, int line, int accent, float fill) {
        boolean live = fill >= 0 && PidDrawing.isVessel(symbol);
        if (live) { // the inside of the shell is rows and columns 1 to 6
            int inside = 6 * scale, height = Math.round(Math.min(1, fill) * inside);
            if (height > 0) g.fill(x + scale, y + 7 * scale - height, x + 7 * scale, y + 7 * scale, accent);
        }
        String[] mask = mask(symbol);
        if (mask == null) return;
        int turns = turns(symbol) ? rotation & 3 : 0;
        for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
            // look the pixel up in the unturned mask: turning clockwise k times
            int r = row, c = col;
            for (int k = 0; k < turns; k++) { int t = r; r = 7 - c; c = t; }
            char ch = mask[r].charAt(c);
            if (ch == '.' || (live && ch == '+')) continue;
            int colour = ch == '+' ? accent : line;
            g.fill(x + col * scale, y + row * scale, x + (col + 1) * scale, y + (row + 1) * scale, colour);
        }
    }
}
