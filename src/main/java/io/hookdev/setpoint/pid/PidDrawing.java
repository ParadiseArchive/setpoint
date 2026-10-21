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
            SAFETY_VALVE = 14, PRESSURE_TRANSMITTER = 15;
    // Pipe connections in the drawing: the four sides, plus a riser going up or down
    public static final int NORTH = 1, EAST = 2, SOUTH = 4, WEST = 8, UP = 16, DOWN = 32;

    /**
     * One symbol.
     *
     * @param rotation quarter turns clockwise from pointing east (a pump's discharge, a valve's flow)
     * @param pipes    which sides pipe lines run to (NORTH, EAST, SOUTH, WEST, UP, DOWN bits)
     * @param tag      the equipment tag, like P-417, or empty
     */
    public record Cell(int x, int y, int symbol, int rotation, int pipes, String tag) {
        public static final Codec<Cell> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(Cell::x),
                Codec.INT.fieldOf("y").forGetter(Cell::y),
                Codec.INT.fieldOf("symbol").forGetter(Cell::symbol),
                Codec.INT.fieldOf("rotation").forGetter(Cell::rotation),
                Codec.INT.fieldOf("pipes").forGetter(Cell::pipes),
                Codec.STRING.fieldOf("tag").forGetter(Cell::tag)
        ).apply(i, Cell::new));
        public static final StreamCodec<ByteBuf, Cell> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Cell::x, ByteBufCodecs.VAR_INT, Cell::y, ByteBufCodecs.VAR_INT, Cell::symbol,
                ByteBufCodecs.VAR_INT, Cell::rotation, ByteBufCodecs.VAR_INT, Cell::pipes, ByteBufCodecs.STRING_UTF8, Cell::tag,
                Cell::new);
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
            default -> "Pipe";
        };
    }
}
