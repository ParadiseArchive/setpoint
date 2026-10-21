package io.hookdev.setpoint.block.entity;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.physics.PumpModel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Flow transmitter: sits in a pipe line and measures what flows through it. Every pump whose pipe run passes
 * through it reports its flow each tick; the reading lags one tick so it never depends on tick order.
 */
public class FlowTransmitterBlockEntity extends InstrumentBlockEntity {
    /** 100 % of the measuring range: one pump running free. */
    public static final double RANGE = PumpModel.MAX_FLOW;

    private long tick = Long.MIN_VALUE;
    private double sum;
    private double previous;

    public FlowTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.FLOW_TRANSMITTER_ENTITY.get(), pos, state, "FT");
    }

    /** A pump reports the flow through this meter for this game tick. */
    public void report(long gameTime, double flow) {
        if (gameTime != tick) {
            previous = gameTime == tick + 1 ? sum : 0;
            tick = gameTime;
            sum = 0;
        }
        sum += flow;
    }

    /** Flow in mB per tick, over the last complete tick. */
    public double flow() {
        if (level == null) return 0;
        long now = level.getGameTime();
        if (now == tick) return previous;
        if (now == tick + 1) return sum;
        return 0;
    }

    /** Flow as a percentage of the range; null when the meter is not in a pipe line. */
    public @Nullable Double percent() {
        return flow() / RANGE * 100.0;
    }

    @Override
    public void describe(Player player) {
        player.sendSystemMessage(Component.empty().append(tagLabel())
                .append(Component.literal(String.format("FLOW %.1f mB/t", flow())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(String.format("  (%.0f %% of %d)", flow() / RANGE * 100, (int) RANGE)).withStyle(ChatFormatting.GRAY)));
    }
}
