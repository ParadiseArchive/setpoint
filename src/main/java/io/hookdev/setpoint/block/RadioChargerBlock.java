package io.hookdev.setpoint.block;

import java.util.List;

import io.hookdev.setpoint.console.PlantScanner;
import io.hookdev.setpoint.console.Reading;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A charger rack with the shift's radios. Pick one up (right-click) and the field operator calls in what they see out
 * in the plant: how much runs, and what needs a look, within 32 blocks.
 */
public class RadioChargerBlock extends FurnitureBlock {
    /** At most this many pieces of equipment are called in by name. */
    public static final int MAX_CALLS = 4;

    public RadioChargerBlock(BlockBehaviour.Properties properties, VoxelShape north) {
        super(properties, north);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel server) {
            server.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3f, 1.6f);
            server.playSound(null, pos, SoundEvents.SAND_STEP, SoundSource.BLOCKS, 0.4f, 1.8f); // a burst of static
            for (Component line : report(PlantScanner.scan(server, pos))) player.sendSystemMessage(line);
        }
        return InteractionResult.SUCCESS;
    }

    /** What the field operator says about these readings: a summary, then what is wrong, worst first. */
    public static List<Component> report(List<Reading> readings) {
        long alarm = readings.stream().filter(r -> r.severity() == Reading.ALARM).count();
        long attention = readings.stream().filter(r -> r.severity() == Reading.ATTENTION).count();
        Component radio = Component.literal("[radio] ").withStyle(ChatFormatting.DARK_GREEN);
        if (readings.isEmpty()) {
            return List.of(radio.copy().append(Component.literal("Field here. Nothing out here to look after yet.").withStyle(ChatFormatting.GRAY)));
        }
        String summary = "Field here. Walked " + readings.size() + (readings.size() == 1 ? " unit: " : " units: ")
                + (alarm + attention == 0 ? "all normal, nothing to report." : alarm + " in alarm, " + attention + " to keep an eye on.");
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(radio.copy().append(Component.literal(summary).withStyle(alarm > 0 ? ChatFormatting.RED : attention > 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN)));
        readings.stream().filter(r -> r.severity() != Reading.NORMAL)
                .sorted((a, b) -> Integer.compare(b.severity(), a.severity()))
                .limit(MAX_CALLS)
                .forEach(r -> lines.add(radio.copy().append(Component.literal(r.tag() + ": " + (r.alarms().isEmpty() ? r.status() : String.join(", ", r.alarms())) + ", " + r.value())
                        .withStyle(r.severity() == Reading.ALARM ? ChatFormatting.RED : ChatFormatting.GOLD))));
        return lines;
    }
}
