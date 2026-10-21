package io.hookdev.setpoint.network;

import java.util.ArrayList;
import java.util.List;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.pid.PidLive;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What an open control room screen shows, sent from the server twice a second: the overview rows,
 * the alarm list and the trend of the selected tag.
 */
public record ConsoleSnapshot(int containerId, List<Row> rows, List<AlarmRow> alarms, int unacknowledged,
                              String trendTag, List<String> seriesNames, List<float[]> series, List<PidLive> valves) implements CustomPacketPayload {
    public static final Type<ConsoleSnapshot> TYPE = new Type<>(Setpoint.id("console_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConsoleSnapshot> CODEC = CustomPacketPayload.codec(ConsoleSnapshot::write, ConsoleSnapshot::read);

    /** One line of the overview. {@code gauge} is the first trend value (a tank's level, a pump's flow), 0 to 100 or NaN. */
    public record Row(String tag, String kind, String status, int severity, String value, float gauge) {}

    /** One line of the alarm list; state is the ordinal of AlarmList.State. */
    public record AlarmRow(String time, String tag, String message, int state) {}

    public static ConsoleSnapshot empty(int containerId) {
        return new ConsoleSnapshot(containerId, List.of(), List.of(), 0, "", List.of(), List.of(), List.of());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(rows.size());
        for (Row r : rows) {
            buf.writeUtf(r.tag()); buf.writeUtf(r.kind()); buf.writeUtf(r.status()); buf.writeByte(r.severity()); buf.writeUtf(r.value()); buf.writeFloat(r.gauge());
        }
        buf.writeVarInt(alarms.size());
        for (AlarmRow a : alarms) {
            buf.writeUtf(a.time()); buf.writeUtf(a.tag()); buf.writeUtf(a.message()); buf.writeByte(a.state());
        }
        buf.writeVarInt(unacknowledged);
        buf.writeUtf(trendTag);
        buf.writeVarInt(seriesNames.size());
        for (String name : seriesNames) buf.writeUtf(name);
        buf.writeVarInt(series.size());
        for (float[] values : series) {
            buf.writeVarInt(values.length);
            for (float v : values) buf.writeFloat(v);
        }
        buf.writeVarInt(valves.size());
        for (PidLive v : valves) { buf.writeUtf(v.tag()); buf.writeByte(v.state()); buf.writeUtf(v.kind()); buf.writeUtf(v.status()); }
    }

    private static ConsoleSnapshot read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        List<Row> rows = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) rows.add(new Row(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readByte(), buf.readUtf(), buf.readFloat()));
        List<AlarmRow> alarms = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) alarms.add(new AlarmRow(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readByte()));
        int unacknowledged = buf.readVarInt();
        String trendTag = buf.readUtf();
        List<String> names = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) names.add(buf.readUtf());
        List<float[]> series = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) {
            float[] values = new float[buf.readVarInt()];
            for (int j = 0; j < values.length; j++) values[j] = buf.readFloat();
            series.add(values);
        }
        List<PidLive> valves = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) valves.add(new PidLive(buf.readUtf(), buf.readByte(), -1, buf.readUtf(), buf.readUtf(), ""));
        return new ConsoleSnapshot(containerId, rows, alarms, unacknowledged, trendTag, names, series, valves);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
