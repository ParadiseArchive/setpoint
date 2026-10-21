package io.hookdev.setpoint.console;

import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * What the control room shows for one piece of equipment.
 *
 * @param tag      equipment tag, like P-417 or LIC-203
 * @param kind     short type, like PUMP or LIC
 * @param status   one or two words, like RUNNING or NO POWER
 * @param severity {@link #NORMAL}, {@link #ATTENTION} or {@link #ALARM}: decides the colour
 * @param value    the main value in plain units, like "62.0 %" or "84 mB/t"
 * @param series   names of the trend values, like PV, SP, OP
 * @param trend    the trend values right now, 0 to 100
 * @param alarms   alarm conditions that are active right now
 */
public record Reading(BlockPos pos, String tag, String kind, String status, int severity, String value,
                      List<String> series, float[] trend, List<String> alarms) {
    public static final int NORMAL = 0, ATTENTION = 1, ALARM = 2;
}
