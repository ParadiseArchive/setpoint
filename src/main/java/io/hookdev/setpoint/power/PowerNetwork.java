package io.hookdev.setpoint.power;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import io.hookdev.setpoint.block.PowerCableBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * All the cables that touch each other, and the machines at their ends. A cable holds no FE: what a generator pushes into
 * any cable of the network goes straight on to the machines that want it, shared out evenly, within the same transaction.
 * Networks are worked out when FE first comes in and kept until a cable or a neighbour changes. A cable next to a
 * generator that only waits to be drained (some mods make them so) also pulls its FE, see {@link #pull}.
 */
public final class PowerNetwork {
    /** No network grows bigger than this many cables, so a huge grid cannot stall the server. */
    public static final int MAX_CABLES = 4096;
    /** What a cable says it can hold, for mods that only join blocks with room for FE. It never really holds any. */
    public static final int SHOWN_CAPACITY = 1_000;

    private static final Map<LevelReader, Map<BlockPos, PowerNetwork>> NETWORKS = new WeakHashMap<>();

    private final Set<BlockPos> cables;
    /** Every machine side next to a cable: the machine's position and the side of it that faces the cable. */
    private final List<Map.Entry<BlockPos, Direction>> ends;
    /** Guards against a loop through another mod's cable back into this network. */
    private boolean busy;
    private final Flow flow = new Flow();

    private PowerNetwork(Set<BlockPos> cables, List<Map.Entry<BlockPos, Direction>> ends) {
        this.cables = cables;
        this.ends = ends;
    }

    /** A cable or a neighbour changed: work every network out again next time. */
    public static void forget(LevelReader level) {
        synchronized (NETWORKS) {
            NETWORKS.remove(level);
        }
    }

    /** The FE handler a cable at {@code pos} shows to the blocks round it. */
    public static EnergyHandler handler(Level level, BlockPos pos) {
        return new Handler(level, pos.immutable());
    }

    public static PowerNetwork of(Level level, BlockPos pos) {
        synchronized (NETWORKS) {
            Map<BlockPos, PowerNetwork> known = NETWORKS.computeIfAbsent(level, l -> new HashMap<>());
            PowerNetwork network = known.get(pos);
            if (network == null) {
                network = trace(level, pos);
                for (BlockPos cable : network.cables) known.put(cable, network);
            }
            return network;
        }
    }

    /** Follows the cables from {@code start} and notes every machine side they touch. */
    private static PowerNetwork trace(Level level, BlockPos start) {
        Set<BlockPos> cables = new HashSet<>();
        List<Map.Entry<BlockPos, Direction>> ends = new ArrayList<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(start);
        cables.add(start);
        while (!todo.isEmpty()) {
            BlockPos cable = todo.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = cable.relative(direction);
                if (!level.isLoaded(next)) continue;
                if (level.getBlockState(next).getBlock() instanceof PowerCableBlock) {
                    if (cables.size() < MAX_CABLES && cables.add(next)) todo.add(next);
                } else if (level.getCapability(Capabilities.Energy.BLOCK, next, direction.getOpposite()) != null) {
                    ends.add(Map.entry(next, direction.getOpposite()));
                }
            }
        }
        return new PowerNetwork(cables, ends);
    }

    public int cables() {
        return cables.size();
    }

    public int machines() {
        return ends.size();
    }

    /** FE the network carried in the last whole tick. */
    public int carried(Level level) {
        long now = level.getGameTime();
        return flow.tick == now ? flow.last : flow.tick == now - 1 ? flow.amount : 0;
    }

    /** FE carried this tick, kept in step with transactions: what is rolled back was never carried. */
    private static final class Flow extends SnapshotJournal<Integer> {
        long tick = -1;
        int amount, last;

        void add(Level level, int sent, TransactionContext transaction) {
            long now = level.getGameTime();
            if (tick != now) {
                last = tick == now - 1 ? amount : 0;
                amount = 0;
                tick = now;
            }
            updateSnapshots(transaction);
            amount += sent;
        }

        @Override
        protected Integer createSnapshot() {
            return amount;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            amount = snapshot;
        }
    }

    /** FE a cable pulls from each block beside it in a tick, at most. */
    public static final int PULL_PER_SIDE = io.hookdev.setpoint.block.entity.EnergyPush.PER_SIDE;

    /**
     * The cable at {@code pos} takes FE out of every block beside it that gives it up and sends it over its network,
     * only as much as the machines on it take. Machines that only use power give none; batteries and generators do.
     */
    public static void pull(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos source = pos.relative(direction);
            if (!level.isLoaded(source) || level.getBlockState(source).getBlock() instanceof PowerCableBlock) continue;
            EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, source, direction.getOpposite());
            if (handler == null || handler instanceof Handler) continue;
            PowerNetwork network = of(level, pos);
            boolean fromStore = handler instanceof Storage; // a battery feeds machines, never another battery
            int wanted;
            // first find out how much the network takes, then take exactly that
            try (Transaction trial = Transaction.openRoot()) {
                int out = handler.extract(PULL_PER_SIDE, trial);
                wanted = out > 0 ? network.send(level, out, trial, source, !fromStore) : 0;
            }
            if (wanted <= 0) continue;
            try (Transaction transaction = Transaction.openRoot()) {
                int out = handler.extract(wanted, transaction);
                if (network.send(level, out, transaction, source, !fromStore) == out) transaction.commit();
            }
        }
    }

    /** A battery: it takes what the machines leave over, and gives it back when they need more than the generators make. */
    public interface Storage extends EnergyHandler {}

    /** Shares {@code amount} FE out over every machine that takes it, then what is left into batteries; what nobody wants stays with the sender. */
    int send(Level level, int amount, TransactionContext transaction) {
        return send(level, amount, transaction, null, true);
    }

    /** The same, but never back into the block at {@code from}, where the FE came from, and into batteries only if {@code toStores}. */
    int send(Level level, int amount, TransactionContext transaction, @org.jspecify.annotations.Nullable BlockPos from, boolean toStores) {
        if (busy || amount <= 0) return 0;
        busy = true;
        try {
            List<EnergyHandler> machines = new ArrayList<>(ends.size()), stores = new ArrayList<>();
            for (Map.Entry<BlockPos, Direction> end : ends) {
                if (end.getKey().equals(from)) continue;
                EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, end.getKey(), end.getValue());
                if (handler == null || handler instanceof Handler) continue;
                (handler instanceof Storage ? stores : machines).add(handler);
            }
            int left = share(machines, amount, transaction);
            if (toStores) left = share(stores, left, transaction);
            if (left < amount) flow.add(level, amount - left, transaction);
            return amount - left;
        } finally {
            busy = false;
        }
    }

    /** Shares {@code amount} evenly over {@code takers}, twice round so what a full one leaves goes to the rest; returns what is left. */
    private static int share(List<EnergyHandler> takers, int amount, TransactionContext transaction) {
        int left = amount;
        for (int round = 0; round < 2 && left > 0 && !takers.isEmpty(); round++) {
            int share = Math.max(1, (left + takers.size() - 1) / takers.size());
            List<EnergyHandler> hungry = new ArrayList<>(takers.size());
            for (EnergyHandler taker : takers) {
                if (left <= 0) break;
                int offer = Math.min(share, left);
                int taken = taker.insert(offer, transaction);
                left -= taken;
                if (taken == offer) hungry.add(taker);
            }
            takers = hungry;
        }
        return left;
    }

    /** What a cable shows: room for FE, never any stored, nothing to take out. */
    private record Handler(Level level, BlockPos pos) implements EnergyHandler {
        @Override
        public long getAmountAsLong() {
            return 0;
        }

        @Override
        public long getCapacityAsLong() {
            return SHOWN_CAPACITY;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return of(level, pos).send(level, amount, transaction);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
    }
}
