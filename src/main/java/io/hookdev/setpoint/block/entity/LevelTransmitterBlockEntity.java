package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.hydraulics.ForeignVessel;
import io.hookdev.setpoint.hydraulics.Vessel;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** Measures the level of the tank it touches, in percent of the tank height. */
public class LevelTransmitterBlockEntity extends InstrumentBlockEntity {
    public LevelTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.LEVEL_TRANSMITTER_ENTITY.get(), pos, state, "LT");
    }

    public @Nullable Vessel tank() {
        if (level == null) return null;
        for (Direction direction : Direction.values()) {
            Vessel vessel = ForeignVessel.at(level, worldPosition.relative(direction), direction.getOpposite());
            if (vessel != null) return vessel;
        }
        return null;
    }

    /** Level in percent, or null when the transmitter is not on a tank (a bad PV). */
    public @Nullable Double percent() {
        Vessel tank = tank();
        return tank == null ? null : tank.fillFraction() * 100.0;
    }

    @Override
    public void describe(Player player) {
        Double pv = percent();
        player.sendSystemMessage(Component.empty().append(tagLabel()).append(pv == null
                ? Component.literal("BAD PV  (not mounted on a tank)").withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.literal(String.format("LEVEL %.1f %%", pv)).withStyle(ChatFormatting.WHITE)));
    }
}
