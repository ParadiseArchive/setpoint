package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.hydraulics.PressureVessel;
import io.hookdev.setpoint.physics.BoilerModel;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** Measures the pressure of the closed vessel it touches, like a boiler drum. Range 0 to 15 bar. */
public class PressureTransmitterBlockEntity extends InstrumentBlockEntity {
    /** 100 % of the measuring range, in bar. */
    public static final double RANGE = BoilerModel.PRESSURE_RANGE;

    public PressureTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.PRESSURE_TRANSMITTER_ENTITY.get(), pos, state, "PT");
    }

    public @Nullable PressureVessel vessel() {
        if (level == null) return null;
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof PressureVessel vessel) return vessel;
        }
        return null;
    }

    /** Pressure in bar, or null when the transmitter is not on a closed vessel (a bad PV). */
    public @Nullable Double bar() {
        PressureVessel vessel = vessel();
        return vessel == null ? null : vessel.pressure();
    }

    /** Pressure in percent of the range, or null. */
    public @Nullable Double percent() {
        Double bar = bar();
        return bar == null ? null : Math.min(100, bar / RANGE * 100.0);
    }

    @Override
    public void describe(Player player) {
        Double bar = bar();
        player.sendSystemMessage(Component.empty().append(tagLabel()).append(bar == null
                ? Component.literal("BAD PV  (not mounted on a boiler)").withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.literal(String.format("PRESSURE %.1f bar", bar)).withStyle(ChatFormatting.WHITE)));
    }
}
