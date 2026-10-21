package io.hookdev.setpoint.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.hookdev.setpoint.control.AlarmList.Condition;
import io.hookdev.setpoint.control.AlarmList.State;

class AlarmListTest {
    private static final Condition CAV = new Condition("P-101", "CAVITATION");
    private static final Condition HI = new Condition("LIC-101", "PV HI");

    @Test
    void aNewAlarmComesInUnacknowledged() {
        AlarmList list = new AlarmList();
        assertEquals(1, list.update(Set.of(CAV), 10));
        assertEquals(State.ACTIVE_UNACK, list.list().get(0).state());
        assertTrue(list.hasUnacknowledged());
        assertEquals(0, list.update(Set.of(CAV), 20), "a standing alarm is not new again");
    }

    @Test
    void acknowledgedAlarmStaysWhileActiveAndGoesWhenCleared() {
        AlarmList list = new AlarmList();
        list.update(Set.of(CAV), 10);
        list.acknowledgeAll();
        assertEquals(State.ACTIVE_ACK, list.list().get(0).state());
        assertFalse(list.hasUnacknowledged());
        list.update(Set.of(), 20);
        assertTrue(list.list().isEmpty());
    }

    @Test
    void clearedBeforeAcknowledgeStaysUntilAcknowledged() {
        AlarmList list = new AlarmList();
        list.update(Set.of(CAV), 10);
        list.update(Set.of(), 20);
        assertEquals(State.CLEARED_UNACK, list.list().get(0).state());
        assertTrue(list.hasUnacknowledged());
        list.acknowledgeAll();
        assertTrue(list.list().isEmpty());
    }

    @Test
    void alarmThatReturnsCountsAsNew() {
        AlarmList list = new AlarmList();
        list.update(Set.of(CAV), 10);
        list.update(Set.of(), 20);
        assertEquals(1, list.update(Set.of(CAV), 30));
        assertEquals(State.ACTIVE_UNACK, list.list().get(0).state());
        assertEquals(30, list.list().get(0).since());
    }

    @Test
    void newestFirst() {
        AlarmList list = new AlarmList();
        list.update(Set.of(CAV), 10);
        list.update(Set.of(CAV, HI), 20);
        List<AlarmList.Alarm> alarms = list.list();
        assertEquals("LIC-101", alarms.get(0).tag());
        assertEquals(2, list.unacknowledged());
    }

    @Test
    void listIsCapped() {
        AlarmList list = new AlarmList();
        for (int i = 0; i < 80; i++) list.update(Set.of(new Condition("P-" + i, "DRY RUN")), i);
        assertTrue(list.list().size() <= AlarmList.MAX_ALARMS);
    }
}
