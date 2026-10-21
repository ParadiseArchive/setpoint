package io.hookdev.setpoint.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.hookdev.setpoint.block.entity.ControlValveBlockEntity;
import io.hookdev.setpoint.block.entity.ControllerBlockEntity;
import io.hookdev.setpoint.block.entity.FlowTransmitterBlockEntity;
import io.hookdev.setpoint.block.entity.InstrumentBlockEntity;
import io.hookdev.setpoint.block.entity.LevelTransmitterBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Handheld field communicator. Sneak-click a transmitter, a valve and (for cascade) a master
 * controller to collect them, then click the controller to wire up the loop. The devices get
 * the loop number of the controller: LT-123, LV-123 for LIC-123. A plain click reads a device.
 */
public class FieldCommunicatorItem extends Item {
    private static final Map<UUID, List<BlockPos>> PENDING = new ConcurrentHashMap<>();

    public FieldCommunicatorItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !(level.getBlockEntity(context.getClickedPos()) instanceof InstrumentBlockEntity device)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        List<BlockPos> pending = PENDING.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
        pending.removeIf(pos -> !(level.getBlockEntity(pos) instanceof InstrumentBlockEntity));

        if (player.isSecondaryUseActive()) {
            BlockPos pos = context.getClickedPos().immutable();
            if (!pending.contains(pos)) pending.add(pos);
            Component note = Component.literal("+ " + device.tag() + "  (" + pending.size() + " collected, now click a controller)")
                    .withStyle(ChatFormatting.AQUA);
            if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(note, true); // above the hotbar
            return InteractionResult.SUCCESS;
        }

        if (device instanceof ControllerBlockEntity controller && !pending.isEmpty()) {
            link(level, controller, pending, player);
            pending.clear();
            return InteractionResult.SUCCESS;
        }

        device.describe(player);
        return InteractionResult.SUCCESS;
    }

    private static void link(Level level, ControllerBlockEntity controller, List<BlockPos> devices, Player player) {
        String loop = controller.loopNumber();
        controller.setAutoLink(false); // linked by hand from now on
        List<String> done = new ArrayList<>();
        for (BlockPos pos : devices) {
            if (!(level.getBlockEntity(pos) instanceof InstrumentBlockEntity device) || device == controller) continue;
            if (device instanceof FlowTransmitterBlockEntity) {
                device.setTag("FT-" + loop);
                controller.setPvTag(device.tag());
                if (controller.tag().startsWith("LIC-")) controller.setTag("FIC-" + loop); // it controls a flow now
                done.add("PV " + device.tag());
            } else if (device instanceof LevelTransmitterBlockEntity) {
                device.setTag("LT-" + loop);
                controller.setPvTag(device.tag());
                done.add("PV " + device.tag());
            } else if (device instanceof ControlValveBlockEntity) {
                device.setTag("LV-" + loop);
                controller.setMvTag(device.tag());
                done.add("MV " + device.tag());
            } else if (device instanceof ControllerBlockEntity master) {
                controller.setCasTag(master.tag());
                done.add("CAS " + master.tag());
            }
        }
        player.sendSystemMessage(Component.literal(controller.tag() + " linked: " + (done.isEmpty() ? "nothing" : String.join(", ", done)))
                .withStyle(ChatFormatting.AQUA));
    }
}
