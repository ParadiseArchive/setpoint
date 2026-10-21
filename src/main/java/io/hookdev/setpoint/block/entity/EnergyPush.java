package io.hookdev.setpoint.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Power sources push their FE into every neighbour that accepts it. */
public final class EnergyPush {
    public static final int PER_SIDE = 1_000;

    private EnergyPush() {}

    public static void push(Level level, BlockPos pos, SimpleEnergyHandler energy) {
        for (Direction direction : Direction.values()) {
            int available = energy.getAmountAsInt();
            if (available <= 0) return;
            BlockPos target = pos.relative(direction);
            if (!level.isLoaded(target)) continue;
            EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, target, direction.getOpposite());
            if (handler == null || handler == energy) continue;
            try (Transaction transaction = Transaction.openRoot()) {
                int sent = handler.insert(Math.min(available, PER_SIDE), transaction);
                if (sent > 0) {
                    transaction.commit();
                    energy.set(available - sent);
                }
            }
        }
    }
}
