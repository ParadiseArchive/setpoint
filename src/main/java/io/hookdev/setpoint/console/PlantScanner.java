package io.hookdev.setpoint.console;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.EquipmentTag;
import io.hookdev.setpoint.block.entity.BoilerBlockEntity;
import io.hookdev.setpoint.block.entity.ControlValveBlockEntity;
import io.hookdev.setpoint.block.entity.ControllerBlockEntity;
import io.hookdev.setpoint.block.entity.GeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.LevelTransmitterBlockEntity;
import io.hookdev.setpoint.block.entity.PumpBlockEntity;
import io.hookdev.setpoint.block.entity.SteamTurbineBlockEntity;
import io.hookdev.setpoint.block.entity.TankBlockEntity;
import io.hookdev.setpoint.control.PidController;
import io.hookdev.setpoint.physics.BoilerModel;
import io.hookdev.setpoint.physics.PumpModel;
import io.hookdev.setpoint.physics.TurbineModel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** Finds the Setpoint equipment around a console and reads it the way a DCS would show it. */
public final class PlantScanner {
    /** How far a console sees, in blocks, in every direction. */
    public static final int RANGE = 32;
    private static final List<String> KIND_ORDER = List.of("LIC", "PUMP", "VALVE", "TANK", "LT", "BOILER", "TURBINE", "GEN");

    private PlantScanner() {}

    /** All equipment within range, sorted by kind and tag. */
    public static List<Reading> scan(ServerLevel level, BlockPos centre) {
        List<Reading> readings = new ArrayList<>();
        int minX = (centre.getX() - RANGE) >> 4, maxX = (centre.getX() + RANGE) >> 4;
        int minZ = (centre.getZ() - RANGE) >> 4, maxZ = (centre.getZ() + RANGE) >> 4;
        for (int cx = minX; cx <= maxX; cx++) for (int cz = minZ; cz <= maxZ; cz++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                BlockPos pos = be.getBlockPos();
                if (Math.abs(pos.getX() - centre.getX()) > RANGE || Math.abs(pos.getY() - centre.getY()) > RANGE
                        || Math.abs(pos.getZ() - centre.getZ()) > RANGE) continue;
                Reading reading = read(be);
                if (reading != null) readings.add(reading);
            }
        }
        readings.sort(Comparator.comparingInt((Reading r) -> KIND_ORDER.indexOf(r.kind())).thenComparing(Reading::tag));
        return readings;
    }

    private static String pct(double v) {
        return String.format("%.1f %%", v);
    }

    public static @Nullable Reading read(BlockEntity be) {
        BlockPos pos = be.getBlockPos();
        if (be instanceof ControllerBlockEntity lic) {
            int a = lic.alarms();
            List<String> alarms = new ArrayList<>();
            if ((a & ControllerBlockEntity.ALARM_BAD_PV) != 0) alarms.add("BAD PV");
            if ((a & ControllerBlockEntity.ALARM_HI) != 0) alarms.add("PV HI");
            if ((a & ControllerBlockEntity.ALARM_LO) != 0) alarms.add("PV LO");
            double pv = lic.pv();
            boolean man = lic.mode() == PidController.Mode.MAN;
            return new Reading(pos, lic.tag(), "LIC", lic.mode().name(), !alarms.isEmpty() ? Reading.ALARM : man ? Reading.ATTENTION : Reading.NORMAL,
                    Double.isNaN(pv) ? "----" : "PV " + pct(pv) + "  SP " + pct(lic.setpoint()),
                    List.of("PV", "SP", "OP"), new float[]{(float) pv, (float) lic.setpoint(), (float) lic.output()}, alarms);
        }
        if (be instanceof PumpBlockEntity pump) {
            String tag = EquipmentTag.of("P", pos);
            PumpModel.Result r = pump.lastResult();
            List<String> alarms = new ArrayList<>();
            String status;
            int severity = Reading.NORMAL;
            if (!pump.commanded()) {
                status = "STOPPED";
            } else if (!pump.running()) {
                status = "NO POWER";
                alarms.add("NO POWER");
            } else if (r != null && r.state() == PumpModel.State.DRY_RUN) {
                status = "DRY RUN";
                alarms.add("DRY RUN");
            } else if (r != null && r.state() == PumpModel.State.CAVITATING) {
                status = "CAVITATING";
                alarms.add("CAVITATION");
            } else if (r != null && r.state() == PumpModel.State.DEADHEAD) {
                status = "NO FLOW";
                severity = Reading.ATTENTION; // a closed control valve does this; not an alarm
            } else {
                status = "RUNNING";
            }
            if (!alarms.isEmpty()) severity = Reading.ALARM;
            double flow = pump.running() && r != null ? r.flow() : 0;
            return new Reading(pos, tag, "PUMP", status, severity, String.format("%.0f mB/t", flow),
                    List.of("FLOW %"), new float[]{(float) (flow / PumpModel.MAX_FLOW * 100)}, alarms);
        }
        if (be instanceof ControlValveBlockEntity valve) {
            double position = valve.position() * 100;
            String status = valve.tripped() ? "TRIPPED" : !valve.signalOk() ? "NO SIGNAL" : "IN CONTROL";
            List<String> alarms = valve.tripped() ? List.of("TRIPPED") : List.of();
            return new Reading(pos, valve.tag(), "VALVE", status,
                    valve.tripped() ? Reading.ALARM : valve.signalOk() ? Reading.NORMAL : Reading.ATTENTION,
                    pct(position), List.of("POS %"), new float[]{(float) position}, alarms);
        }
        if (be instanceof TankBlockEntity tank) {
            double level = tank.fillFraction() * 100;
            String what = tank.amount() == 0 ? "EMPTY" : tank.resource().getFluidType().getDescription().getString().toUpperCase();
            return new Reading(pos, EquipmentTag.of("T", pos), "TANK", what, Reading.NORMAL, pct(level),
                    List.of("LEVEL %"), new float[]{(float) level}, List.of());
        }
        if (be instanceof LevelTransmitterBlockEntity lt) {
            Double pv = lt.percent();
            return new Reading(pos, lt.tag(), "LT", pv == null ? "BAD PV" : "OK", pv == null ? Reading.ATTENTION : Reading.NORMAL,
                    pv == null ? "----" : pct(pv), List.of("LEVEL %"), new float[]{pv == null ? Float.NaN : pv.floatValue()}, List.of());
        }
        if (be instanceof BoilerBlockEntity boiler) {
            List<String> alarms = new ArrayList<>();
            if (boiler.tripped()) alarms.add("LOW WATER TRIP");
            if (boiler.temperature() > BoilerModel.OVERHEAT) alarms.add("OVERHEAT");
            String status = boiler.temperature() > BoilerModel.OVERHEAT ? "OVERHEAT" : boiler.tripped() ? "TRIPPED"
                    : boiler.firing() && boiler.temperature() >= BoilerModel.BOILING ? "STEAMING" : boiler.firing() ? "HEATING" : "IDLE";
            double drum = boiler.fillFraction() * 100;
            return new Reading(pos, boiler.tag(), "BOILER", status, alarms.isEmpty() ? Reading.NORMAL : Reading.ALARM,
                    String.format("%.0f °C  drum %.0f %%", boiler.temperature(), drum),
                    List.of("DRUM %", "TEMP (0-500 °C)", "STEAM %"),
                    new float[]{(float) drum, (float) (boiler.temperature() / 5), boiler.steam() * 100f / BoilerBlockEntity.STEAM_CAPACITY}, alarms);
        }
        if (be instanceof SteamTurbineBlockEntity turbine) {
            boolean running = turbine.speed() > 100;
            return new Reading(pos, turbine.tag(), "TURBINE", running ? "RUNNING" : "STOPPED", Reading.NORMAL,
                    String.format("%.0f rpm  %d FE/t", turbine.speed(), turbine.output()),
                    List.of("SPEED %"), new float[]{(float) (turbine.speed() / TurbineModel.RATED_SPEED * 100)}, List.of());
        }
        if (be instanceof GeneratorBlockEntity generator) {
            int stored = generator.energy().getAmountAsInt();
            return new Reading(pos, EquipmentTag.of("G", pos), "GEN", generator.running() ? "RUNNING" : "IDLE", Reading.NORMAL,
                    String.format("%,d FE", stored), List.of("BUFFER %"),
                    new float[]{stored * 100f / GeneratorBlockEntity.ENERGY_CAPACITY}, List.of());
        }
        return null;
    }
}
