package io.hookdev.setpoint.advancement;

import io.hookdev.setpoint.SetpointContent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Advancements for things that happen in a plant, like a pump cavitating or a loop holding its setpoint.
 * In an advancement: {"trigger": "setpoint:plant_event", "conditions": {"event": "cavitation"}}.
 */
public class PlantEventTrigger extends PlantEventTriggerBase {
    public static final String CAVITATION = "cavitation", LOOP_HOLDING = "loop_holding", TURBINE_RATED = "turbine_rated",
            ALARM_ACKNOWLEDGED = "alarm_acknowledged", VALVE_SHUT = "valve_shut", INTAKE_RUNNING = "intake_running",
            TALL_TANK = "tall_tank", FLOW_HOLDING = "flow_holding", FOREIGN_TANK = "foreign_tank", PID_DRAWN = "pid_drawn",
            PID_ON_CONSOLE = "pid_on_console", PSV_LIFTED = "psv_lifted", PRESSURE_HOLDING = "pressure_holding",
            FLARE_LIT = "flare_lit", SMOKELESS = "smokeless";
    /** Players this close to the equipment count as having seen it happen. */
    public static final double NEARBY = 16;

    /** Every player near {@code pos} sees the event. */
    public static void near(ServerLevel level, BlockPos pos, String event) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= NEARBY * NEARBY) {
                SetpointContent.PLANT_EVENT.get().trigger(player, event);
            }
        }
    }
}
