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
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * All the cables that touch each other, and the machines at their ends. A cable holds no FE: what a generator pushes into
 * any cable of the network goes straight on to the machines that want it, shared out evenly, within the same transaction.
 * Networks are worked out when FE first comes in and kept until a cable or a neighbour changes.
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

    /** Shares {@code amount} FE out over every machine that takes it; what nobody wants stays with the sender. */
    int send(Level level, int amount, TransactionContext transaction) {
        if (busy || amount <= 0) return 0;
        busy = true;
        try {
            List<EnergyHandler> takers = new ArrayList<>(ends.size());
            for (Map.Entry<BlockPos, Direction> end : ends) {
                EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, end.getKey(), end.getValue());
                if (handler != null && !(handler instanceof Handler)) takers.add(handler);
            }
            int left = amount;
            // twice round: what a full machine leaves over goes to the ones that still have room
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
            if (left < amount) flow.add(level, amount - left, transaction);
            return amount - left;
        } finally {
            busy = false;
        }
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
