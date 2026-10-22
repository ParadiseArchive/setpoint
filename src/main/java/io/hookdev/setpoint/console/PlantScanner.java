package io.hookdev.setpoint.console;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.IbcBlock;
import io.hookdev.setpoint.block.entity.BoilerBlockEntity;
import io.hookdev.setpoint.block.entity.ControlValveBlockEntity;
import io.hookdev.setpoint.block.entity.ControllerBlockEntity;
import io.hookdev.setpoint.block.entity.CreativeGeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.FlowTransmitterBlockEntity;
import io.hookdev.setpoint.block.entity.GeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.LevelTransmitterBlockEntity;
import io.hookdev.setpoint.block.entity.FloatingRoofTankBlockEntity;
import io.hookdev.setpoint.block.entity.PortableTankBlockEntity;
import io.hookdev.setpoint.block.entity.PressureTransmitterBlockEntity;
import io.hookdev.setpoint.block.entity.PumpBlockEntity;
import io.hookdev.setpoint.block.entity.SafetyValveBlockEntity;
import io.hookdev.setpoint.block.entity.SteamTurbineBlockEntity;
import io.hookdev.setpoint.block.entity.TankBlockEntity;
import io.hookdev.setpoint.block.entity.WaterIntakeBlockEntity;
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
    private static final List<String> KIND_ORDER = List.of("LIC", "FIC", "PIC", "PUMP", "VALVE", "FT", "PT", "TANK", "IBC", "DRUM", "LT", "BOILER", "PSV", "TURBINE", "GEN", "INTAKE", "ELECTROLYSER", "N2 GEN", "COMPRESSOR", "FUEL CELL", "REACTOR", "MIXER", "SPHERE", "CYLINDER", "PUMPJACK", "COLUMN");

    private PlantScanner() {}

    /** All equipment within range, sorted by kind and tag. */
    public static List<Reading> scan(ServerLevel level, BlockPos centre) {
        List<Reading> readings = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>(); // a tall tank is several blocks but one tag
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
                if (reading != null && seen.add(reading.tag())) readings.add(reading);
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
            boolean pressure = lic.measuresPressure();
            String kind = pressure ? "PIC" : lic.measuresFlow() ? "FIC" : "LIC";
            String value = Double.isNaN(pv) ? "----" : pressure
                    ? String.format("PV %.1f bar  SP %.1f bar", pv / 100 * PressureTransmitterBlockEntity.RANGE, lic.setpoint() / 100 * PressureTransmitterBlockEntity.RANGE)
                    : "PV " + pct(pv) + "  SP " + pct(lic.setpoint());
            return new Reading(pos, lic.tag(), kind, lic.mode().name(), !alarms.isEmpty() ? Reading.ALARM : man ? Reading.ATTENTION : Reading.NORMAL,
                    value,
                    List.of("PV", "SP", "OP"), new float[]{(float) pv, (float) lic.setpoint(), (float) lic.output()}, alarms);
        }
        if (be instanceof PumpBlockEntity pump) {
            String tag = pump.tag();
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
        if (be instanceof TankBlockEntity block) {
            TankBlockEntity tank = block.bottom(); // a tall tank shows as one, under the tag of its bottom block
            pos = tank.getBlockPos();
            String height = tank.column().size() > 1 ? "  (" + tank.column().size() + " high)" : "";
            double level = tank.fillFraction() * 100;
            String what = tank.amount() == 0 ? "EMPTY" : tank.resource().getFluidType().getDescription().getString().toUpperCase();
            if (tank.losingVapour()) what = "VAPOUR LOSS";
            return new Reading(pos, tank.tag(), "TANK", what, tank.losingVapour() ? Reading.ATTENTION : Reading.NORMAL, pct(level) + height,
                    List.of("LEVEL %"), new float[]{(float) level}, List.of());
        }
        if (be instanceof PortableTankBlockEntity tank) {
            double level = tank.fillFraction() * 100;
            String what = tank.amount() == 0 ? "EMPTY" : tank.resource().getFluidType().getDescription().getString().toUpperCase();
            String kind = tank instanceof FloatingRoofTankBlockEntity ? "TANK" : tank.getBlockState().getBlock() instanceof IbcBlock ? "IBC" : "DRUM";
            return new Reading(pos, tank.tag(), kind, what, Reading.NORMAL, pct(level), List.of("LEVEL %"), new float[]{(float) level}, List.of());
        }
        if (be instanceof FlowTransmitterBlockEntity ft) {
            double flow = ft.flow();
            return new Reading(pos, ft.tag(), "FT", flow > 0 ? "FLOWING" : "NO FLOW", Reading.NORMAL, String.format("%.1f mB/t", flow),
                    List.of("FLOW %"), new float[]{(float) (flow / FlowTransmitterBlockEntity.RANGE * 100)}, List.of());
        }
        if (be instanceof PressureTransmitterBlockEntity pt) {
            Double bar = pt.bar();
            return new Reading(pos, pt.tag(), "PT", bar == null ? "BAD PV" : "OK", bar == null ? Reading.ATTENTION : Reading.NORMAL,
                    bar == null ? "----" : String.format("%.1f bar", bar), List.of("PRESS %"),
                    new float[]{bar == null ? Float.NaN : (float) (bar / PressureTransmitterBlockEntity.RANGE * 100)}, List.of());
        }
        if (be instanceof SafetyValveBlockEntity psv) {
            boolean mounted = psv.vessel() != null;
            return new Reading(pos, psv.tag(), "PSV", !mounted ? "NOT MOUNTED" : psv.lifting() ? "LIFTING" : "SHUT",
                    psv.lifting() ? Reading.ALARM : mounted ? Reading.NORMAL : Reading.ATTENTION,
                    String.format("set %.0f bar", psv.setPressure()), List.of("OPEN %"), new float[]{psv.lifting() ? 100 : 0},
                    psv.lifting() ? List.of("PSV LIFTING") : List.of());
        }
        if (be instanceof LevelTransmitterBlockEntity lt) {
            Double pv = lt.percent();
            return new Reading(pos, lt.tag(), "LT", pv == null ? "BAD PV" : "OK", pv == null ? Reading.ATTENTION : Reading.NORMAL,
                    pv == null ? "----" : pct(pv), List.of("LEVEL %"), new float[]{pv == null ? Float.NaN : pv.floatValue()}, List.of());
        }
        if (be instanceof io.hookdev.setpoint.block.entity.GasMakerBlockEntity maker) {
            String kind = maker instanceof io.hookdev.setpoint.block.entity.ElectrolyserBlockEntity ? "ELECTROLYSER" : "N2 GEN";
            double outlet = maker.product().pressure();
            int severity = maker.status().equals("RUNNING") || maker.status().equals("FULL") ? Reading.NORMAL : Reading.ATTENTION;
            return new Reading(be.getBlockPos(), maker.tag(), kind, maker.status(), severity, String.format("%.1f bar  %d mB/t", outlet, maker.made()),
                    List.of("OUTLET bar"), new float[]{(float) outlet}, List.of());
        }
        if (be instanceof io.hookdev.setpoint.block.entity.DieselGeneratorBlockEntity generator) {
            boolean ok = generator.status().equals("RUNNING") || generator.status().equals("FULL");
            double level = generator.fillFraction() * 100;
            return new Reading(be.getBlockPos(), generator.tag(), "GEN", generator.status(), ok ? Reading.NORMAL : Reading.ATTENTION,
                    generator.status().equals("RUNNING") ? io.hookdev.setpoint.block.entity.DieselGeneratorBlockEntity.POWER + " FE/t" : "0 FE/t",
                    List.of("DIESEL %"), new float[]{(float) level}, List.of());
        }
        if (be instanceof io.hookdev.setpoint.block.entity.DistillationColumnBlockEntity column) {
            boolean ok = column.status().equals("RUNNING");
            double level = column.fillFraction() * 100;
            return new Reading(be.getBlockPos(), column.tag(), "COLUMN", column.status(), ok ? Reading.NORMAL : Reading.ATTENTION,
                    column.rate() + " mB/t", List.of("FEED %"), new float[]{(float) level}, List.of());
        }
        if (be instanceof io.hookdev.setpoint.block.entity.PumpjackBlockEntity pumpjack) {
            boolean ok = pumpjack.status().equals("RUNNING") || pumpjack.status().equals("FULL");
            double level = pumpjack.fillFraction() * 100;
            return new Reading(be.getBlockPos(), pumpjack.tag(), "PUMPJACK", pumpjack.status(), ok ? Reading.NORMAL : Reading.ATTENTION, pct(level),
                    List.of("LEVEL %"), new float[]{(float) level}, List.of());
        }
        if (be instanceof io.hookdev.setpoint.block.entity.FuelCellBlockEntity cell) {
            boolean ok = cell.status().equals("RUNNING") || cell.status().equals("FULL");
            return new Reading(be.getBlockPos(), cell.tag(), "FUEL CELL", cell.status(), ok ? Reading.NORMAL : Reading.ATTENTION,
                    cell.status().equals("RUNNING") ? io.hookdev.setpoint.block.entity.FuelCellBlockEntity.POWER + " FE/t" : "0 FE/t", List.of(), new float[0], List.of());
        }
        if (be instanceof io.hookdev.setpoint.block.entity.AmmoniaReactorBlockEntity reactor) {
            boolean ok = reactor.status().equals("RUNNING") || reactor.status().equals("FULL");
            return new Reading(be.getBlockPos(), reactor.tag(), "REACTOR", reactor.status(), ok ? Reading.NORMAL : Reading.ATTENTION,
                    String.format("feed %.0f bar  %d mB/t", reactor.feedPressure(), reactor.made()), List.of("FEED bar"), new float[]{(float) reactor.feedPressure()}, List.of());
        }
        if (be instanceof io.hookdev.setpoint.block.entity.FertilizerMixerBlockEntity mixer) {
            boolean ok = mixer.status().equals("RUNNING") || mixer.status().equals("FULL");
            return new Reading(be.getBlockPos(), mixer.tag(), "MIXER", mixer.status(), ok ? Reading.NORMAL : Reading.ATTENTION, mixer.status(), List.of(), new float[0], List.of());
        }
        if (be instanceof io.hookdev.setpoint.block.entity.CompressorBlockEntity compressor) {
            int severity = compressor.status().equals("RUNNING") || compressor.status().equals("FULL") ? Reading.NORMAL : Reading.ATTENTION;
            return new Reading(be.getBlockPos(), compressor.tag(), "COMPRESSOR", compressor.status(), severity,
                    String.format("%.1f > %.1f bar  %d mB/t", compressor.suctionPressure(), compressor.dischargePressure(), compressor.flow()),
                    List.of("OUT bar"), new float[]{(float) compressor.dischargePressure()}, List.of());
        }
        if (be instanceof io.hookdev.setpoint.block.entity.GasSphereBlockEntity sphere) {
            var gas = sphere.tank().gas();
            String what = gas.isEmpty() ? "EMPTY" : gas.getFluidType().getDescription().getString().toUpperCase();
            int severity = sphere.pressure() >= io.hookdev.setpoint.block.entity.GasSphereBlockEntity.MAX_PRESSURE * 0.95 ? Reading.ATTENTION : Reading.NORMAL;
            return new Reading(be.getBlockPos(), sphere.tag(), "SPHERE", what, severity, String.format("%.2f bar", sphere.pressure()),
                    List.of("PRESS bar"), new float[]{(float) sphere.pressure()}, List.of());
        }
        if (be instanceof io.hookdev.setpoint.block.entity.GasCylinderBlockEntity cylinder) {
            var gas = cylinder.tank().gas();
            String what = gas.isEmpty() ? "EMPTY" : gas.getFluidType().getDescription().getString().toUpperCase();
            return new Reading(be.getBlockPos(), cylinder.tag(), "CYLINDER", what, Reading.NORMAL, String.format("%.1f bar", cylinder.pressure()),
                    List.of("PRESS bar"), new float[]{(float) cylinder.pressure()}, List.of());
        }
        if (be instanceof BoilerBlockEntity boiler) {
            List<String> alarms = new ArrayList<>();
            if (boiler.tripped()) alarms.add("LOW WATER TRIP");
            if (boiler.temperature() > BoilerModel.OVERHEAT) alarms.add("OVERHEAT");
            if (boiler.pressure() >= BoilerModel.DESIGN_PRESSURE) alarms.add("PRESSURE HH");
            else if (boiler.pressureTripped()) alarms.add("HP TRIP");
            String status = boiler.temperature() > BoilerModel.OVERHEAT ? "OVERHEAT" : boiler.tripped() ? "TRIPPED" : boiler.pressureTripped() ? "HP TRIP"
                    : boiler.firing() && boiler.temperature() >= BoilerModel.BOILING ? "STEAMING" : boiler.firing() ? "HEATING" : "IDLE";
            double drum = boiler.fillFraction() * 100;
            return new Reading(pos, boiler.tag(), "BOILER", status, alarms.isEmpty() ? Reading.NORMAL : Reading.ALARM,
                    String.format("%.1f bar  %.0f °C  drum %.0f %%", boiler.pressure(), boiler.temperature(), drum),
                    List.of("DRUM %", "TEMP (0-500 °C)", "PRESS (0-15 bar)"),
                    new float[]{(float) drum, (float) (boiler.temperature() / 5), (float) (boiler.pressure() / BoilerModel.PRESSURE_RANGE * 100)}, alarms);
        }
        if (be instanceof SteamTurbineBlockEntity turbine) {
            boolean running = turbine.speed() > 100;
            return new Reading(pos, turbine.tag(), "TURBINE", running ? "RUNNING" : "STOPPED", Reading.NORMAL,
                    String.format("%.0f rpm  %d FE/t", turbine.speed(), turbine.output()),
                    List.of("SPEED %"), new float[]{(float) (turbine.speed() / TurbineModel.RATED_SPEED * 100)}, List.of());
        }
        if (be instanceof WaterIntakeBlockEntity intake) {
            boolean ok = intake.submerged();
            return new Reading(pos, intake.tag(), "INTAKE", ok ? "RUNNING" : "NO WATER", ok ? Reading.NORMAL : Reading.ATTENTION,
                    intake.sources() + " water sources", List.of("BUFFER %"), new float[]{(float) (intake.fillFraction() * 100)}, List.of());
        }
        if (be instanceof CreativeGeneratorBlockEntity creative) {
            return new Reading(pos, creative.tag(), "GEN", "RUNNING", Reading.NORMAL, "endless FE", List.of("BUFFER %"), new float[]{100}, List.of());
        }
        if (be instanceof GeneratorBlockEntity generator) {
            int stored = generator.energy().getAmountAsInt();
            return new Reading(pos, generator.tag(), "GEN", generator.running() ? "RUNNING" : "IDLE", Reading.NORMAL,
                    String.format("%,d FE", stored), List.of("BUFFER %"),
                    new float[]{stored * 100f / GeneratorBlockEntity.ENERGY_CAPACITY}, List.of());
        }
        return null;
    }
}
