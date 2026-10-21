package io.hookdev.setpoint.control;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An operator's alarm list, the way a DCS keeps it. A new alarm comes in unacknowledged. After an
 * acknowledge it stays on the list for as long as the condition lasts. If the condition clears before
 * anyone acknowledged it, the alarm stays as "returned to normal" until it is acknowledged, so no alarm
 * slips by unseen.
 */
public final class AlarmList {
    public static final int MAX_ALARMS = 50;

    public enum State { ACTIVE_UNACK, ACTIVE_ACK, CLEARED_UNACK }

    /** One alarm condition on one piece of equipment. */
    public record Condition(String tag, String message) {}

    public static final class Alarm {
        private final Condition condition;
        private State state = State.ACTIVE_UNACK;
        private long since;

        private Alarm(Condition condition, long since) {
            this.condition = condition;
            this.since = since;
        }

        public String tag() { return condition.tag(); }
        public String message() { return condition.message(); }
        public State state() { return state; }
        /** Time the alarm came in (or came back), in game ticks. */
        public long since() { return since; }
    }

    private final Map<Condition, Alarm> alarms = new LinkedHashMap<>();

    /**
     * Brings the list up to date with the conditions that are active right now.
     * @return how many alarms came in (new, or back after clearing)
     */
    public int update(Collection<Condition> active, long time) {
        int incoming = 0;
        for (Alarm alarm : new ArrayList<>(alarms.values())) {
            if (active.contains(alarm.condition)) continue;
            switch (alarm.state) {
                case ACTIVE_ACK -> alarms.remove(alarm.condition);
                case ACTIVE_UNACK -> alarm.state = State.CLEARED_UNACK;
                case CLEARED_UNACK -> { }
            }
        }
        for (Condition condition : active) {
            Alarm alarm = alarms.get(condition);
            if (alarm == null) {
                alarms.put(condition, new Alarm(condition, time));
                incoming++;
            } else if (alarm.state == State.CLEARED_UNACK) {
                alarm.state = State.ACTIVE_UNACK;
                alarm.since = time;
                incoming++;
            }
        }
        while (alarms.size() > MAX_ALARMS) { // drop the oldest returned-to-normal ones first
            Condition oldest = alarms.values().stream().filter(a -> a.state == State.CLEARED_UNACK).map(a -> a.condition)
                    .findFirst().orElse(alarms.keySet().iterator().next());
            alarms.remove(oldest);
        }
        return incoming;
    }

    public void acknowledgeAll() {
        alarms.values().removeIf(a -> a.state == State.CLEARED_UNACK);
        alarms.values().forEach(a -> a.state = State.ACTIVE_ACK);
    }

    public boolean hasUnacknowledged() {
        return alarms.values().stream().anyMatch(a -> a.state != State.ACTIVE_ACK);
    }

    public int unacknowledged() {
        return (int) alarms.values().stream().filter(a -> a.state != State.ACTIVE_ACK).count();
    }

    /** Newest first. */
    public List<Alarm> list() {
        List<Alarm> list = new ArrayList<>(alarms.values());
        list.sort((a, b) -> Long.compare(b.since, a.since));
        return list;
    }
}
