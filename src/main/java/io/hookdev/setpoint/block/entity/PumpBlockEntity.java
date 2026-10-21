package io.hookdev.setpoint.block.entity;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.EquipmentTag;
import io.hookdev.setpoint.block.PumpBlock;
import io.hookdev.setpoint.block.PumpStatus;
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
public class PumpBlockEntity extends BlockEntity {
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
            suction = PipeRun.trace(level, pos, out.getOpposite());
            discharge = PipeRun.trace(level, pos, out);
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
                discharge != null ? discharge.valveResistance(level) : 0));

        commanded = !level.hasNeighborSignal(pos); // runs by itself; a redstone signal is the stop button
        powered = energy.getAmountAsInt() >= result.power();
        boolean running = commanded && powered;
        if (running) {
            energy.set(energy.getAmountAsInt() - result.power());
            flowCarry += result.flow();
            int batch = (int) flowCarry;
            flowCarry -= batch;
            if (batch > 0 && source != null && destination != null) {
                transfer(source, destination, batch);
            }
            if (result.cavitation() > 0) {
                wear = Math.min(1.0, wear + result.cavitation() * WEAR_PER_TICK);
                setChanged();
            }
            effects(level, pos, result);
        } else {
            flowCarry = 0;
        }
        last = result;

        PumpStatus status = !running ? PumpStatus.OFF : result.state() == PumpModel.State.OK ? PumpStatus.RUNNING : PumpStatus.ALARM;
        if (state.getValue(PumpBlock.STATUS) != status) {
            level.setBlock(pos, state.setValue(PumpBlock.STATUS, status), Block.UPDATE_CLIENTS);
        }
    }

    private static @Nullable Vessel vesselAt(Level level, PipeRun.@Nullable End end) {
        if (end == null) return null;
        return level.getBlockEntity(end.tankPos()) instanceof Vessel vessel ? vessel : null;
    }

    /** Moves up to {@code amount} mB, all or nothing per batch. */
    private static void transfer(Vessel source, Vessel destination, int amount) {
        FluidResource resource = source.resource();
        if (resource.isEmpty()) return;
        try (Transaction transaction = Transaction.openRoot()) {
            int room;
            try (Transaction probe = Transaction.open(transaction)) {
                room = destination.fluid().insert(resource, amount, probe);
            }
            int taken = source.fluid().extract(resource, room, transaction);
            int given = destination.fluid().insert(resource, taken, transaction);
            if (taken > 0 && given == taken) {
                transaction.commit();
            }
        }
    }

    private static void effects(ServerLevel level, BlockPos pos, PumpModel.Result result) {
        long time = level.getGameTime();
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

    public void replaceImpeller(Player player) {
        wear = 0;
        setChanged();
        player.sendSystemMessage(Component.literal(EquipmentTag.of("P", worldPosition) + "  new impeller fitted").withStyle(ChatFormatting.AQUA));
    }

    /** Field operator readout, sent to the player who right-clicks the pump. */
    public void report(Player player) {
        String tag = EquipmentTag.of("P", worldPosition);
        MutableComponent line1 = Component.literal(tag + "  ").withStyle(ChatFormatting.AQUA);
        MutableComponent line2 = Component.empty();
        PumpModel.Result r = last;
        boolean running = commanded && powered;

        if (!commanded) {
            line1.append(Component.literal("STOPPED").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("  (redstone stop signal)").withStyle(ChatFormatting.DARK_GRAY));
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
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        wear = input.getDoubleOr("wear", 0);
    }
}
