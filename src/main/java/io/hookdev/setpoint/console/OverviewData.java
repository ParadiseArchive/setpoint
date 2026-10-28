package io.hookdev.setpoint.console;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.hookdev.setpoint.block.entity.ConsoleBlockEntity;
import io.hookdev.setpoint.control.AlarmList;
import io.hookdev.setpoint.control.TrendBuffer;

/**
 * What an overview screen shows, read from the nearest console once a second and sent to the players around it.
 *
 * @param linked   whether a console was found at all
 * @param clock    the console's clock, hh:mm
 * @param tiles    one per tag (overview page)
 * @param alarms   the alarm list (alarm page)
 * @param unacked  alarms waiting for an ACK
 * @param trends   a few tags with their recent history (trend page)
 * @param power    what the generators make and the batteries hold (power page)
 */
public record OverviewData(boolean linked, String clock, List<Tile> tiles, List<AlarmLine> alarms, int unacked, List<Trend> trends,
                           java.util.Optional<io.hookdev.setpoint.pid.PidDrawing> pid, List<io.hookdev.setpoint.pid.PidLive> live, Power power) {
    /** Trends shown at once, and samples sent per series (one every {@link #STEP} seconds of the console's five minutes). */
    public static final int MAX_TRENDS = 4, STEP = 3;
    public static final OverviewData EMPTY = new OverviewData(false, "--:--", List.of(), List.of(), 0, List.of(), java.util.Optional.empty(), List.of(), Power.NONE);

    /**
     * The power page: FE a tick made by every generator together, the charge of every battery bank together and how fast
     * it changes, and a line per generator, battery and power meter.
     */
    public record Power(int made, long stored, long capacity, int rate, List<PowerLine> lines) {
        public static final Power NONE = new Power(0, 0, 0, 0, List.of());
        public static final Codec<Power> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("made").forGetter(Power::made), Codec.LONG.fieldOf("stored").forGetter(Power::stored),
                Codec.LONG.fieldOf("capacity").forGetter(Power::capacity), Codec.INT.fieldOf("rate").forGetter(Power::rate),
                PowerLine.CODEC.listOf().fieldOf("lines").forGetter(Power::lines)).apply(i, Power::new));
    }

    /** @param fe FE a tick: made by a generator, in (positive) or out of a battery, carried past a meter; @param percent a battery's charge, or -1 */
    public record PowerLine(String tag, String kind, int fe, int percent) {
        public static final Codec<PowerLine> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("tag").forGetter(PowerLine::tag), Codec.STRING.fieldOf("kind").forGetter(PowerLine::kind),
                Codec.INT.fieldOf("fe").forGetter(PowerLine::fe), Codec.INT.fieldOf("percent").forGetter(PowerLine::percent)).apply(i, PowerLine::new));
    }

    public record Tile(String tag, String kind, String status, int severity, String value) {
        public static final Codec<Tile> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("tag").forGetter(Tile::tag), Codec.STRING.fieldOf("kind").forGetter(Tile::kind),
                Codec.STRING.fieldOf("status").forGetter(Tile::status), Codec.INT.fieldOf("severity").forGetter(Tile::severity),
                Codec.STRING.fieldOf("value").forGetter(Tile::value)).apply(i, Tile::new));
    }

    /** @param state the ordinal of {@link AlarmList.State} */
    public record AlarmLine(String time, String tag, String message, int state) {
        public static final Codec<AlarmLine> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("time").forGetter(AlarmLine::time), Codec.STRING.fieldOf("tag").forGetter(AlarmLine::tag),
                Codec.STRING.fieldOf("message").forGetter(AlarmLine::message), Codec.INT.fieldOf("state").forGetter(AlarmLine::state)).apply(i, AlarmLine::new));
    }

    /** @param series the values 0 to 100 per series, oldest first */
    public record Trend(String tag, List<String> names, List<List<Float>> series) {
        public static final Codec<Trend> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("tag").forGetter(Trend::tag), Codec.STRING.listOf().fieldOf("names").forGetter(Trend::names),
                Codec.FLOAT.listOf().listOf().fieldOf("series").forGetter(Trend::series)).apply(i, Trend::new));
    }

    public static final Codec<OverviewData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("linked").forGetter(OverviewData::linked), Codec.STRING.fieldOf("clock").forGetter(OverviewData::clock),
            Tile.CODEC.listOf().fieldOf("tiles").forGetter(OverviewData::tiles), AlarmLine.CODEC.listOf().fieldOf("alarms").forGetter(OverviewData::alarms),
            Codec.INT.fieldOf("unacked").forGetter(OverviewData::unacked), Trend.CODEC.listOf().fieldOf("trends").forGetter(OverviewData::trends),
            io.hookdev.setpoint.pid.PidDrawing.CODEC.optionalFieldOf("pid").forGetter(OverviewData::pid),
            io.hookdev.setpoint.pid.PidLive.CODEC.listOf().optionalFieldOf("live", List.of()).forGetter(OverviewData::live),
            Power.CODEC.optionalFieldOf("power", Power.NONE).forGetter(OverviewData::power)).apply(i, OverviewData::new));

    /** Reads what a console knows right now. */
    public static OverviewData of(ConsoleBlockEntity console, long gameTime) {
        List<Tile> tiles = new ArrayList<>();
        for (Reading r : console.readings()) tiles.add(new Tile(r.tag(), r.kind(), r.status(), r.severity(), r.value()));
        List<AlarmLine> alarms = new ArrayList<>();
        for (AlarmList.Alarm a : console.alarms().list()) alarms.add(new AlarmLine(console.clock(a.since()), a.tag(), a.message(), a.state().ordinal()));
        List<Trend> trends = new ArrayList<>();
        for (Reading r : console.readings()) { // controllers come first in the readings: their PV, SP and OP are the trends that matter
            if (trends.size() >= MAX_TRENDS) break;
            TrendBuffer buffer = console.trend(r.tag());
            if (buffer == null || r.series().isEmpty()) continue;
            List<List<Float>> series = new ArrayList<>();
            for (int s = 0; s < buffer.series(); s++) {
                float[] all = buffer.read(s);
                List<Float> thinned = new ArrayList<>();
                for (int k = all.length % STEP; k < all.length; k += STEP) thinned.add(all[k]);
                series.add(thinned);
            }
            trends.add(new Trend(r.tag(), r.series(), series));
        }
        // the P&ID in the console, with the live state of everything on it (P&ID page)
        io.hookdev.setpoint.pid.PidDrawing drawing = console.drawing();
        List<io.hookdev.setpoint.pid.PidLive> live = new ArrayList<>();
        if (drawing != null) {
            for (Reading r : console.readings()) live.add(io.hookdev.setpoint.pid.PidLive.of(r));
            if (console.getLevel() != null) live.addAll(io.hookdev.setpoint.pid.PidLive.valves(console.getLevel(), drawing));
        }
        return new OverviewData(true, console.clock(gameTime), tiles, alarms, console.alarms().unacknowledged(), trends, java.util.Optional.ofNullable(drawing), live, power(console));
    }

    /** Every generator, battery bank and power meter the console sees, with what it makes, holds or carries. */
    private static Power power(ConsoleBlockEntity console) {
        if (console.getLevel() == null) return Power.NONE;
        int made = 0, rate = 0;
        long stored = 0, capacity = 0;
        List<PowerLine> lines = new ArrayList<>();
        for (Reading r : console.readings()) {
            var be = console.getLevel().getBlockEntity(r.pos());
            int fe;
            if (be instanceof io.hookdev.setpoint.block.entity.GeneratorBlockEntity generator) fe = generator.running() ? generator.output() : 0;
            else if (be instanceof io.hookdev.setpoint.block.entity.DieselGeneratorBlockEntity diesel) fe = diesel.status().equals("RUNNING") ? io.hookdev.setpoint.block.entity.DieselGeneratorBlockEntity.POWER : 0;
            else if (be instanceof io.hookdev.setpoint.block.entity.FuelCellBlockEntity cell) fe = cell.status().equals("RUNNING") ? io.hookdev.setpoint.block.entity.FuelCellBlockEntity.POWER : 0;
            else if (be instanceof io.hookdev.setpoint.block.entity.SteamTurbineBlockEntity turbine) fe = turbine.output();
            else if (be instanceof io.hookdev.setpoint.block.entity.WindTurbineBlockEntity wind) fe = wind.output();
            else if (be instanceof io.hookdev.setpoint.block.entity.BatteryBankBlockEntity battery) {
                stored += battery.stored();
                capacity += io.hookdev.setpoint.block.entity.BatteryBankBlockEntity.CAPACITY;
                rate += battery.rate();
                lines.add(new PowerLine(r.tag(), r.kind(), battery.rate(), (int) Math.round(battery.fraction() * 100)));
                continue;
            } else if (be instanceof io.hookdev.setpoint.block.entity.PowerMeterBlockEntity meter) {
                lines.add(new PowerLine(r.tag(), r.kind(), meter.reading(), -1));
                continue;
            } else continue;
            made += fe;
            lines.add(new PowerLine(r.tag(), r.kind(), fe, -1));
        }
        return new Power(made, stored, capacity, rate, lines);
    }
}
