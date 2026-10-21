package io.hookdev.setpoint.pid;

import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.hookdev.setpoint.console.Reading;

/**
 * The live state of one tag on a P&ID, the way a mimic panel shows it: plain, running, needs attention, alarm.
 * The console and the P&ID board colour their drawings with these.
 *
 * @param gauge  the first trend value (a tank's level), 0 to 100, or -1
 * @param kind   short type, like PUMP
 * @param status one or two words, like RUNNING
 * @param value  the main value, like "84 mB/t"
 */
public record PidLive(String tag, int state, float gauge, String kind, String status, String value) {
    public static final int PLAIN = 0, ACTIVE = 1, ATTENTION = 2, ALARM = 3;
    /** Statuses that mean the equipment is doing its job: drawn in cyan. */
    public static final Set<String> ACTIVE_STATUSES = Set.of("RUNNING", "STEAMING", "HEATING", "FLOWING", "AUTO", "CAS", "IN CONTROL", "OK");

    public static final Codec<PidLive> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("tag").forGetter(PidLive::tag),
            Codec.INT.fieldOf("state").forGetter(PidLive::state),
            Codec.FLOAT.fieldOf("gauge").forGetter(PidLive::gauge),
            Codec.STRING.optionalFieldOf("kind", "").forGetter(PidLive::kind),
            Codec.STRING.optionalFieldOf("status", "").forGetter(PidLive::status),
            Codec.STRING.optionalFieldOf("value", "").forGetter(PidLive::value)
    ).apply(i, PidLive::new));

    public static PidLive of(String tag, String kind, int severity, String status, String value, float gauge) {
        int state = severity == Reading.ALARM ? ALARM : severity == Reading.ATTENTION ? ATTENTION : ACTIVE_STATUSES.contains(status) ? ACTIVE : PLAIN;
        return new PidLive(tag, state, Float.isNaN(gauge) ? -1 : gauge, kind, status, value);
    }

    public static PidLive of(Reading reading) {
        return of(reading.tag(), reading.kind(), reading.severity(), reading.status(), reading.value(), reading.trend().length > 0 ? reading.trend()[0] : Float.NaN);
    }
}
