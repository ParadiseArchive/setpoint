package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.ControlValveBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Control valve. Follows the controller output at a limited stroke speed. Without a signal,
 * or with a redstone trip signal, it drives to its fail position: closed (FC) or open (FO).
 */
public class ControlValveBlockEntity extends InstrumentBlockEntity {
    /** Resistance when fully open: head loss = K_OPEN / position^2 * Q^2. */
    public static final double K_OPEN = 2.0e-4;
    /** Full stroke takes 100 ticks (5 seconds). */
    public static final double STROKE_PER_TICK = 0.01;
    /** The valve fails safe when it has not heard from its controller for this many ticks. */
    public static final int SIGNAL_TIMEOUT = 40;

    private double position;
    private double command;
    private long lastCommandTick = -1;
    private boolean failOpen;
    private boolean tripped;
    private boolean signalOk;

    public ControlValveBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.CONTROL_VALVE_ENTITY.get(), pos, state, "LV");
    }

    /** Opening, 0 (shut) to 1 (fully open). */
    public double position() {
        return position;
    }

    public boolean failOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
        setChanged();
    }

    /** Called by the controller every tick with its output (0 to 1). */
    public void command(double opening, long gameTime) {
        command = Math.max(0, Math.min(1, opening));
        lastCommandTick = gameTime;
    }

    /** Extra hydraulic resistance for the pump model; infinite when shut. */
    public double resistance() {
        return position < 0.005 ? Double.POSITIVE_INFINITY : K_OPEN / (position * position);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ControlValveBlockEntity valve) {
        valve.tripped = level.hasNeighborSignal(pos);
        valve.signalOk = valve.lastCommandTick >= 0 && level.getGameTime() - valve.lastCommandTick <= SIGNAL_TIMEOUT;
        double target = valve.tripped || !valve.signalOk ? (valve.failOpen ? 1 : 0) : valve.command;
        double step = Math.max(-STROKE_PER_TICK, Math.min(STROKE_PER_TICK, target - valve.position));
        if (step != 0) {
            valve.position = Math.abs(target - valve.position) < 1e-6 ? target : valve.position + step;
            valve.setChanged();
        }
        int shown = (int) Math.round(valve.position * 4);
        if (state.getValue(ControlValveBlock.OPEN) != shown) {
            level.setBlock(pos, state.setValue(ControlValveBlock.OPEN, shown), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void describe(Player player) {
        String status = tripped ? "TRIPPED" : !signalOk ? "NO SIGNAL" : "IN CONTROL";
        player.sendSystemMessage(Component.empty().append(tagLabel())
                .append(Component.literal(String.format("POS %d %%  ", Math.round(position * 100))).withStyle(ChatFormatting.WHITE))
                .append(Component.literal((failOpen ? "FO" : "FC") + "  ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(status).withStyle(tripped || !signalOk ? ChatFormatting.GOLD : ChatFormatting.GRAY)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("position", position);
        output.putBoolean("fail_open", failOpen);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        position = input.getDoubleOr("position", 0);
        failOpen = input.getBooleanOr("fail_open", false);
    }
}
