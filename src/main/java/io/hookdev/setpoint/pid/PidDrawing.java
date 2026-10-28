package io.hookdev.setpoint.pid;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A P&ID drawing: a plan view of a plant, one symbol per cell, with the pipes between them and dashed signal
 * lines from controllers to their valves and transmitters. Stored on the P&ID item as a data component.
 *
 * @param name   what the player called it
 * @param width  cells across
 * @param height cells down
 */
public record PidDrawing(String name, int width, int height, List<Cell> cells, List<Link> links) {
    public static final String UNTITLED = "Untitled P&ID";
    public static final int MAX_NAME = 40;

    // Symbols, the way they are drawn on a real P&ID
    public static final int PIPE = 0, PUMP = 1, TANK = 2, CONTROL_VALVE = 3, HAND_VALVE = 4, HAND_VALVE_SHUT = 5, LEVEL_TRANSMITTER = 6,
            FLOW_TRANSMITTER = 7, CONTROLLER = 8, BOILER = 9, TURBINE = 10, GENERATOR = 11, INTAKE = 12, CONSOLE = 13,
            SAFETY_VALVE = 14, PRESSURE_TRANSMITTER = 15, DRAIN_VALVE = 16, CHECK_VALVE = 17, DRUM = 18, IBC = 19, FLOATING_ROOF_TANK = 20,
            ELECTROLYSER = 21, NITROGEN_GENERATOR = 22, GAS_CYLINDER = 23, GAS_SPHERE = 24, COMPRESSOR = 25, FUEL_CELL = 26, AMMONIA_REACTOR = 27, FERTILIZER_MIXER = 28, PUMPJACK = 29, COLUMN = 30, FLARE = 31,
            ESD_BUTTON = 32, ALARM_BEACON = 33, BURNER = 34, CABLE = 35, WIND_TURBINE = 36, BATTERY = 37, POWER_METER = 38;
    /** Big equipment (a column, a floating roof tank, a gas sphere) covers more cells than one: its rotation holds the footprint. */
    public static final int BIG = 256;

    /** The rotation of a big symbol that covers w by h cells, from its top-left cell. */
    public static int big(int w, int h) {
        return (Math.min(255, w) << 8) | (Math.min(255, h) << 16);
    }
    // Pipe connections in the drawing: the four sides, plus a riser going up or down
    public static final int NORTH = 1, EAST = 2, SOUTH = 4, WEST = 8, UP = 16, DOWN = 32;
    /** Power cables run in the same cells as pipes, drawn their own way: the four sides, these bits higher up. */
    public static final int CABLE_NORTH = 64, CABLE_EAST = 128, CABLE_SOUTH = 256, CABLE_WEST = 512;
    public static final int CABLE_SHIFT = 6;

    /**
     * One symbol.
     *
     * @param rotation quarter turns clockwise from pointing east (a pump's discharge, a valve's flow)
     * @param pipes    which sides pipe lines run to (NORTH, EAST, SOUTH, WEST, UP, DOWN bits), and power cables (the CABLE_ bits)
     * @param tag      the equipment tag, like P-417, or empty
     * @param at       where the block stands (BlockPos as a long), for a valve that can open and shut; {@link #NOWHERE} otherwise
     */
    public record Cell(int x, int y, int symbol, int rotation, int pipes, String tag, long at) {
        public static final long NOWHERE = Long.MIN_VALUE;

        public Cell(int x, int y, int symbol, int rotation, int pipes, String tag) {
            this(x, y, symbol, rotation, pipes, tag, NOWHERE);
        }

        /** How many cells across a big symbol covers (1 for the rest). */
        public int spanW() { return rotation >= BIG ? (rotation >> 8) & 0xFF : 1; }

        /** How many cells down. */
        public int spanH() { return rotation >= BIG ? (rotation >> 16) & 0xFF : 1; }

        /** Is drawing cell (px, py) part of this symbol? */
        public boolean covers(int px, int py) {
            return px >= x && py >= y && px < x + spanW() && py < y + spanH();
        }

        /** The key of this cell's live state: its tag, or its position for an untagged valve, or empty. */
        public String liveKey() {
            return !tag.isEmpty() ? tag : at != NOWHERE ? "@" + at : "";
        }

        public static final Codec<Cell> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(Cell::x),
                Codec.INT.fieldOf("y").forGetter(Cell::y),
                Codec.INT.fieldOf("symbol").forGetter(Cell::symbol),
                Codec.INT.fieldOf("rotation").forGetter(Cell::rotation),
                Codec.INT.fieldOf("pipes").forGetter(Cell::pipes),
                Codec.STRING.fieldOf("tag").forGetter(Cell::tag),
                Codec.LONG.optionalFieldOf("at", NOWHERE).forGetter(Cell::at)
        ).apply(i, Cell::new));
        public static final StreamCodec<ByteBuf, Cell> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Cell::x, ByteBufCodecs.VAR_INT, Cell::y, ByteBufCodecs.VAR_INT, Cell::symbol,
                ByteBufCodecs.VAR_INT, Cell::rotation, ByteBufCodecs.VAR_INT, Cell::pipes, ByteBufCodecs.STRING_UTF8, Cell::tag,
                ByteBufCodecs.LONG, Cell::at, Cell::new);
    }

    /** A dashed signal line between two cells (by index), like the wiring from a controller to its valve. */
    public record Link(int from, int to) {
        public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("from").forGetter(Link::from),
                Codec.INT.fieldOf("to").forGetter(Link::to)
        ).apply(i, Link::new));
        public static final StreamCodec<ByteBuf, Link> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Link::from, ByteBufCodecs.VAR_INT, Link::to, Link::new);
    }

    public static final Codec<PidDrawing> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(PidDrawing::name),
            Codec.INT.fieldOf("width").forGetter(PidDrawing::width),
            Codec.INT.fieldOf("height").forGetter(PidDrawing::height),
            Cell.CODEC.listOf().fieldOf("cells").forGetter(PidDrawing::cells),
            Link.CODEC.listOf().fieldOf("links").forGetter(PidDrawing::links)
    ).apply(i, PidDrawing::new));
    public static final StreamCodec<ByteBuf, PidDrawing> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PidDrawing::name, ByteBufCodecs.VAR_INT, PidDrawing::width, ByteBufCodecs.VAR_INT, PidDrawing::height,
            Cell.STREAM_CODEC.apply(ByteBufCodecs.list()), PidDrawing::cells, Link.STREAM_CODEC.apply(ByteBufCodecs.list()), PidDrawing::links,
            PidDrawing::new);

    public PidDrawing named(String newName) {
        String clean = newName.strip();
        if (clean.length() > MAX_NAME) clean = clean.substring(0, MAX_NAME);
        return new PidDrawing(clean.isEmpty() ? UNTITLED : clean, width, height, cells, links);
    }

    /** Symbols that hold liquid with a level: drawn filled to it when live. */
    public static boolean isVessel(int symbol) {
        return symbol == TANK || symbol == DRUM || symbol == IBC || symbol == FLOATING_ROOF_TANK;
    }

    /** Plain words for a symbol, for tooltips. */
    public static String describe(int symbol) {
        return switch (symbol) {
            case PUMP -> "Centrifugal pump";
            case TANK -> "Tank";
            case CONTROL_VALVE -> "Control valve";
            case HAND_VALVE -> "Hand valve, open";
            case HAND_VALVE_SHUT -> "Hand valve, shut";
            case LEVEL_TRANSMITTER -> "Level transmitter";
            case FLOW_TRANSMITTER -> "Flow transmitter";
            case CONTROLLER -> "DCS controller";
            case BOILER -> "Boiler";
            case TURBINE -> "Steam turbine";
            case GENERATOR -> "Generator";
            case INTAKE -> "Water intake";
            case CONSOLE -> "DCS console";
            case SAFETY_VALVE -> "Safety valve";
            case PRESSURE_TRANSMITTER -> "Pressure transmitter";
            case DRAIN_VALVE -> "Drain valve";
            case CHECK_VALVE -> "Check valve";
            case DRUM -> "Drum";
            case IBC -> "IBC";
            case FLOATING_ROOF_TANK -> "Floating roof tank";
            case ELECTROLYSER -> "Electrolyser";
            case NITROGEN_GENERATOR -> "Nitrogen generator";
            case GAS_CYLINDER -> "Gas cylinder";
            case GAS_SPHERE -> "Gas sphere";
            case COMPRESSOR -> "Compressor";
            case FUEL_CELL -> "Fuel cell";
            case AMMONIA_REACTOR -> "Ammonia reactor";
            case FERTILIZER_MIXER -> "Fertilizer mixer";
            case PUMPJACK -> "Pumpjack";
            case COLUMN -> "Distillation column";
            case FLARE -> "Flare stack";
            case ESD_BUTTON -> "Emergency stop";
            case ALARM_BEACON -> "Alarm beacon";
            case BURNER -> "Oil and gas burner";
            default -> "Pipe";
        };
    }
}
