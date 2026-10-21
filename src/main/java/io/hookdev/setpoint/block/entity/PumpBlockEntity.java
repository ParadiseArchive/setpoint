package io.hookdev.setpoint.block.entity;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.advancement.PlantEventTrigger;
import io.hookdev.setpoint.block.PumpBlock;
import io.hookdev.setpoint.block.PumpStatus;
import io.hookdev.setpoint.hydraulics.ForeignVessel;
import io.hookdev.setpoint.hydraulics.PipeRun;
import io.hookdev.setpoint.hydraulics.Vessel;
import io.hookdev.setpoint.physics.PumpModel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Runs the pump: finds both ends of the pipe run, solves the operating point and moves the liquid. */
public class PumpBlockEntity extends BlockEntity implements NamedEquipment {
    private final EquipmentName name = new EquipmentName("P");

    public static final int ENERGY_CAPACITY = 10_000;
    public static final int ENERGY_MAX_INPUT = 1_000;
    /** Wear per tick at full cavitation: a fully cavitating pump wears out in about 100 seconds. */
    public static final double WEAR_PER_TICK = 0.0005;
    private static final int RESCAN_TICKS = 10;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, ENERGY_MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };

    private double wear;
    private double flowCarry;
    private int rescanIn;
    private PipeRun.@Nullable End suction;
    private PipeRun.@Nullable End discharge;
    private PumpModel.@Nullable Result last;
    private boolean commanded;
    private boolean runOnSignal; // false: runs by itself and redstone stops it; true: runs only with a redstone signal
    private boolean remoteStop; // stopped from a P&ID board
    private boolean powered;

    public PumpBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.PUMP_ENTITY.get(), pos, state);
    }

    public SimpleEnergyHandler energy() {
        return energy;
    }

    /** Started (no stop signal) and enough power. */
    public boolean running() {
        return commanded && powered;
    }

    public boolean commanded() {
        return commanded;
    }

    public double wear() {
        return wear;
    }

    public PumpModel.@Nullable Result lastResult() {
        return last;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PumpBlockEntity pump) {
        pump.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        Direction out = state.getValue(PumpBlock.FACING);
        if (--rescanIn <= 0) {
            rescanIn = RESCAN_TICKS;
            suction = PipeRun.trace(level, pos, out.getOpposite(), false);
            discharge = PipeRun.trace(level, pos, out, true);
        }
        Vessel source = vesselAt(level, suction);
        Vessel destination = vesselAt(level, discharge);

        double centreline = pos.getY() + 0.5;
        boolean hasSource = source != null && source.amount() > 0;
        boolean hasDestination = destination != null && (source == null || destination.canAccept(source.resource()));
        PumpModel.Result result = PumpModel.solve(new PumpModel.Inputs(
                source != null ? source.surfaceY() - centreline : 0,
                destination != null ? destination.surfaceY() - centreline : 0,
                suction != null ? suction.length() : 0,
                discharge != null ? discharge.length() : 0,
                hasSource, hasDestination, wear,
                suction != null ? suction.valveResistance(level) : 0,
                discharge != null ? discharge.valveResistance(level) : 0,
                hasSource ? density(source.resource()) : 1, hasSource ? viscosity(source.resource()) : 1));

        // by default it runs by itself and a redstone signal is the stop button; or it runs only while it gets a signal
        commanded = runOnSignal == level.hasNeighborSignal(pos) && !remoteStop;
        powered = energy.getAmountAsInt() >= result.power();
        boolean running = commanded && powered;
        if (running) {
            energy.set(energy.getAmountAsInt() - result.power());
            flowCarry += result.flow();
            int batch = (int) flowCarry;
            if (batch > 0 && source != null && destination != null) {
                int moved = transfer(source, destination, batch);
                // Some tanks (a cauldron, some mods) only take whole buckets: keep carrying until a bucket fits.
                if (moved > 0) flowCarry -= moved;
                else if (batch >= TankBlockEntity.BUCKET) flowCarry -= batch; // not even a bucket fits: it is full
                if ((source instanceof ForeignVessel || destination instanceof ForeignVessel) && level.getGameTime() % 100 == 0) {
                    PlantEventTrigger.near(level, pos, PlantEventTrigger.FOREIGN_TANK);
                }
            } else {
                flowCarry -= batch;
            }
            if (result.cavitation() > 0) {
                wear = Math.min(1.0, wear + result.cavitation() * WEAR_PER_TICK);
                setChanged();
                if (level.getGameTime() % 40 == 0) PlantEventTrigger.near(level, pos, PlantEventTrigger.CAVITATION);
            }
            effects(level, pos, result);
            for (PipeRun.End end : new PipeRun.End[]{suction, discharge}) { // flow meters in the lines see this pump's flow
                if (end == null) continue;
                for (BlockPos meter : end.meters()) {
                    if (level.getBlockEntity(meter) instanceof FlowTransmitterBlockEntity ft) ft.report(level.getGameTime(), result.flow());
                }
            }
        } else {
            flowCarry = 0;
        }
        last = result;

        PumpStatus status = !running ? PumpStatus.OFF : result.state() == PumpModel.State.OK ? PumpStatus.RUNNING : PumpStatus.ALARM;
        if (state.getValue(PumpBlock.STATUS) != status) {
            level.setBlock(pos, state.setValue(PumpBlock.STATUS, status), Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(pos, state.getBlock()); // comparators see it start and stop
        }
    }

    private static @Nullable Vessel vesselAt(Level level, PipeRun.@Nullable End end) {
        return end == null ? null : ForeignVessel.at(level, end.tankPos(), end.face());
    }

    /** Moves up to {@code amount} mB, all or nothing per batch. */
    /** Moves up to {@code amount} mB, all or nothing; returns what moved. */
    private static int transfer(Vessel source, Vessel destination, int amount) {
        FluidResource resource = source.resource();
        if (resource.isEmpty()) return 0;
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

    private static void effects(ServerLevel level, BlockPos pos, PumpModel.Result result) {
        long time = level.getGameTime();
        // a soft motor hum; every pump starts its hum at its own moment
        if ((time + pos.hashCode()) % 80 == 0) {
            level.playSound(null, pos, SetpointContent.PUMP_RUN.get(), SoundSource.BLOCKS, 0.15f, 0.95f + level.getRandom().nextFloat() * 0.1f);
        }
        // Cavitation crackles; the worse it gets, the more often it pops.
        if (result.cavitation() > 0 && time % 4 == 0 && level.getRandom().nextFloat() < 0.35f + 0.65f * result.cavitation()) {
            float volume = 0.35f + 0.45f * (float) result.cavitation();
            level.playSound(null, pos, SetpointContent.PUMP_CAVITATE.get(), SoundSource.BLOCKS, volume, 0.9f + level.getRandom().nextFloat() * 0.2f);
        }
        if (result.cavitation() > 0 && time % 6 == 0) {
            level.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.02);
        }
        if (result.state() == PumpModel.State.DEADHEAD && time % 20 == 0) {
            // Pumping against a closed discharge heats the liquid in the casing.
            level.sendParticles(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.01);
        }
    }

    @Override
    public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    public boolean runOnSignal() { return runOnSignal; }

    /** How heavy a liquid is compared to water: from its fluid type (water 1000, lava 3000). */
    public static double density(FluidResource resource) {
        return resource.isEmpty() ? 1 : Math.max(0.1, resource.getFluidType().getDensity() / 1000.0);
    }

    /** How thick a liquid is compared to water (water 1000, lava 6000). */
    public static double viscosity(FluidResource resource) {
        return resource.isEmpty() ? 1 : Math.max(0.1, resource.getFluidType().getViscosity() / 1000.0);
    }

    public boolean remoteStop() { return remoteStop; }

    /** Started or stopped from a P&ID board, like the start/stop buttons of a motor on a DCS screen. */
    public void toggleRemoteStop(Player player) {
        remoteStop = !remoteStop;
        setChanged();
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal(tag() + "  ")
                .withStyle(ChatFormatting.AQUA).append(Component.literal(remoteStop ? "STOP from the board" : "START from the board")
                        .withStyle(remoteStop ? ChatFormatting.GOLD : ChatFormatting.WHITE)), true);
    }

    /** Sneak-right-click: switch between stop-on-signal and run-on-signal. */
    public void toggleRedstoneMode(Player player) {
        runOnSignal = !runOnSignal;
        setChanged();
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal(tag() + "  redstone: ")
                .withStyle(ChatFormatting.AQUA).append(Component.literal(runOnSignal ? "RUN ON SIGNAL (runs only with a signal)"
                        : "STOP ON SIGNAL (runs by itself, a signal stops it)").withStyle(ChatFormatting.WHITE)), true);
    }

    public void replaceImpeller(Player player) {
        wear = 0;
        setChanged();
        player.sendSystemMessage(Component.literal(tag() + "  new impeller fitted").withStyle(ChatFormatting.AQUA));
    }

    /** Field operator readout, sent to the player who right-clicks the pump. */
    public void report(Player player) {
        String tag = tag();
        MutableComponent line1 = Component.literal(tag + "  ").withStyle(ChatFormatting.AQUA);
        MutableComponent line2 = Component.empty();
        PumpModel.Result r = last;
        boolean running = commanded && powered;

        if (!commanded) {
            line1.append(Component.literal("STOPPED").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(remoteStop ? "  (stopped from a P&ID board)" : runOnSignal ? "  (no redstone run signal)" : "  (redstone stop signal)").withStyle(ChatFormatting.DARK_GRAY));
        } else if (!powered) {
            line1.append(Component.literal("NO POWER").withStyle(ChatFormatting.GOLD));
        } else if (r == null || r.state() == PumpModel.State.OK) {
            line1.append(Component.literal("RUNNING").withStyle(ChatFormatting.AQUA));
        } else {
            String alarm = switch (r.state()) {
                case CAVITATING -> "CAVITATING";
                case DRY_RUN -> "DRY RUN";
                case DEADHEAD -> "DEADHEAD";
                default -> "ALARM";
            };
            line1.append(Component.literal(alarm).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (r != null && running) {
            line1.append(Component.literal(String.format("  Q %.1f mB/t  H %.1f m", r.flow(), r.head())).withStyle(ChatFormatting.WHITE));
            line2.append(Component.literal(String.format("NPSHa %.1f m  NPSHr %.1f m  ", r.npshA(), r.npshR()))
                    .withStyle(r.npshA() < r.npshR() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        }
        line2.append(Component.literal(String.format("wear %d%%  ", Math.round(wear * 100)))
                        .withStyle(wear > 0.75 ? ChatFormatting.GOLD : ChatFormatting.GRAY))
                .append(Component.literal(String.format("%,d / %,d FE", energy.getAmountAsInt(), ENERGY_CAPACITY)).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(line1);
        player.sendSystemMessage(line2);
        if (suction == null || discharge == null) {
            player.sendSystemMessage(Component.literal(suction == null ? "no tank found on the suction side" : "no tank found on the discharge side")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        output.putDouble("wear", wear);
        output.putBoolean("run_on_signal", runOnSignal);
        output.putBoolean("remote_stop", remoteStop);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        wear = input.getDoubleOr("wear", 0);
        runOnSignal = input.getBooleanOr("run_on_signal", false);
        remoteStop = input.getBooleanOr("remote_stop", false);
        name.load(input);
    }
}
