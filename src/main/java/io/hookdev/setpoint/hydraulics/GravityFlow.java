package io.hookdev.setpoint.hydraulics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.hookdev.setpoint.block.entity.BoilerBlockEntity;
import io.hookdev.setpoint.block.entity.FlowTransmitterBlockEntity;
import io.hookdev.setpoint.block.entity.PumpBlockEntity;
import io.hookdev.setpoint.physics.PumpModel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Liquid running downhill on its own: from a vessel through a pipe to a vessel whose level stands lower, like
 * communicating vessels. A pipe leaves a vessel at a nozzle low on the side of a block; liquid only goes out while the
 * level is above that nozzle, so a pipe on a higher block of a tall tank is its overflow. The flow follows the same pipe friction as a pump line:
 * head difference = friction * flow^2. Every vessel that can drain (a tank, a water intake) keeps one of these.
 */
public final class GravityFlow {
    /** How often the pipe runs are looked up again, in ticks. */
    public static final int RESCAN_TICKS = 20;
    /** Liquid per metre of level in a tank block, in mB: used to slow down near level, so the two levels meet and stop. */
    public static final double MB_PER_METRE = 16_000;
    private static final double NEAR_LEVEL = 0.002;

    private record Outlet(double nozzle, PipeRun.End end) {}

    private List<Outlet> outlets = List.of();
    private int rescanIn;
    private double[] carry = new double[0];

    /** Lets the liquid in {@code source} (made of {@code blocks}) run down every pipe that leads to a lower vessel. */
    public void tick(ServerLevel level, Vessel source, Collection<BlockPos> blocks) {
        if (--rescanIn <= 0) {
            rescanIn = RESCAN_TICKS;
            outlets = scan(level, blocks);
            if (carry.length != outlets.size()) carry = new double[outlets.size()];
        }
        for (int i = 0; i < outlets.size(); i++) {
            Outlet outlet = outlets.get(i);
            FluidResource resource = source.resource();
            double surface = source.surfaceY();
            if (resource.isEmpty() || source.amount() <= 0 || surface <= outlet.nozzle() + NEAR_LEVEL) { carry[i] = 0; continue; }
            Vessel destination = ForeignVessel.at(level, outlet.end().tankPos(), outlet.end().face());
            // a boiler drum is under pressure: feed it with a pump
            if (destination == null || destination instanceof BoilerBlockEntity || !destination.canAccept(resource)) { carry[i] = 0; continue; }
            double drop = surface - Math.max(destination.amount() > 0 ? destination.surfaceY() : -Double.MAX_VALUE, nozzle(outlet.end().tankPos(), outlet.end().face()));
            double k = PumpModel.FRICTION * Math.max(1, outlet.end().length()) * PumpModel.frictionFactor(PumpBlockEntity.viscosity(resource))
                    + outlet.end().valveResistance(level);
            if (drop <= NEAR_LEVEL || Double.isInfinite(k)) { carry[i] = 0; continue; }
            double flow = Math.min(PumpModel.MAX_FLOW, Math.sqrt(drop / k));
            carry[i] += flow;
            // near the same level only a trickle, so the levels meet instead of sloshing back and forth
            int batch = (int) Math.min(carry[i], drop * MB_PER_METRE / 4);
            if (batch <= 0) continue;
            int moved = transfer(source, destination, resource, batch);
            carry[i] = moved > 0 ? carry[i] - moved : 0;
            for (BlockPos meter : outlet.end().meters()) {
                if (level.getBlockEntity(meter) instanceof FlowTransmitterBlockEntity ft) ft.report(level.getGameTime(), moved);
            }
        }
    }

    /** Every pipe that leaves the vessel, with where it ends. Only pipes count: two tanks side by side do not share liquid. */
    private static List<Outlet> scan(ServerLevel level, Collection<BlockPos> blocks) {
        List<Outlet> found = new ArrayList<>();
        for (BlockPos pos : blocks) {
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (blocks.contains(next) || !level.isLoaded(next)) continue;
                if (!Piping.enters(level.getBlockState(next), side, true)) continue;
                PipeRun.End end = PipeRun.trace(level, pos, side, true);
                if (end == null || blocks.contains(end.tankPos())) continue;
                found.add(new Outlet(nozzle(pos, side), end));
            }
        }
        return found;
    }

    /** Height of the nozzle where a pipe meets a vessel block on this side: low on the side, like on a real tank. */
    static double nozzle(BlockPos pos, Direction side) {
        return switch (side) {
            case UP -> pos.getY() + 1.0;
            case DOWN -> pos.getY();
            default -> pos.getY() + 0.1;
        };
    }

    private static int transfer(Vessel source, Vessel destination, FluidResource resource, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int room;
            try (Transaction probe = Transaction.open(transaction)) {
                room = destination.fluid().insert(resource, amount, probe);
            }
            int taken = source.fluid().extract(resource, room, transaction);
            int given = destination.fluid().insert(resource, taken, transaction);
            if (taken > 0 && given == taken) {
                transaction.commit();
                return given;
            }
        }
        return 0;
    }
}
