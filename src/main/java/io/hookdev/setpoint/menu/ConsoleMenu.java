package io.hookdev.setpoint.menu;

import io.hookdev.setpoint.McVersion;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.advancement.PlantEventTrigger;
import io.hookdev.setpoint.block.entity.ConsoleBlockEntity;
import io.hookdev.setpoint.block.entity.ControllerBlockEntity;
import io.hookdev.setpoint.console.Reading;
import io.hookdev.setpoint.network.ConsoleSnapshot;
import io.hookdev.setpoint.pid.PidDrawing;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The control room screen's menu. The server sends a snapshot twice a second; buttons acknowledge
 * alarms, pick the trend tag, open a controller's faceplate from here, or take the P&ID out.
 * The P&ID's drawing comes along once, when the screen opens.
 */
public class ConsoleMenu extends AbstractContainerMenu {
    public static final int BUTTON_ACK = 0, BUTTON_EJECT = 1, BUTTON_TREND = 1000, BUTTON_FACEPLATE = 2000;
    private static final int SEND_TICKS = 10;

    private final @Nullable ConsoleBlockEntity console; // server side only
    private final @Nullable ServerPlayer player;
    private String trendTag = "";
    private int age;
    private ConsoleSnapshot snapshot; // client side: the latest one received
    private @Nullable PidDrawing drawing;

    /** Client side. */
    public ConsoleMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(SetpointContent.CONSOLE_MENU.get(), containerId);
        this.console = null;
        this.player = null;
        this.snapshot = ConsoleSnapshot.empty(containerId);
        this.drawing = buf.readBoolean() ? PidDrawing.STREAM_CODEC.decode(buf) : null;
    }

    /** Server side. */
    public ConsoleMenu(int containerId, Inventory inventory, ConsoleBlockEntity console) {
        super(SetpointContent.CONSOLE_MENU.get(), containerId);
        this.console = console;
        this.player = inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        this.snapshot = ConsoleSnapshot.empty(containerId);
        this.drawing = console.drawing();
    }

    /** The P&ID in the console, or null. */
    public @Nullable PidDrawing drawing() { return drawing; }

    public ConsoleSnapshot snapshot() { return snapshot; }
    public void receive(ConsoleSnapshot snapshot) { this.snapshot = snapshot; }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (console != null && player != null && age++ % SEND_TICKS == 0) {
            PacketDistributor.sendToPlayer(player, console.snapshot(containerId, trendTag));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == BUTTON_EJECT) drawing = null; // on both sides, so the screen drops the tab right away
        if (console == null) return true;
        List<Reading> readings = console.readings();
        if (buttonId == BUTTON_ACK) {
            if (console.alarms().hasUnacknowledged() && player instanceof ServerPlayer serverPlayer) {
                SetpointContent.PLANT_EVENT.get().trigger(serverPlayer, PlantEventTrigger.ALARM_ACKNOWLEDGED);
            }
            console.acknowledge();
        } else if (buttonId == BUTTON_EJECT) {
            ItemStack pid = console.ejectPid();
            if (!pid.isEmpty() && !player.getInventory().add(pid)) McVersion.drop(player, pid);
        } else if (buttonId >= BUTTON_FACEPLATE) {
            int row = buttonId - BUTTON_FACEPLATE;
            if (row < readings.size() && console.getLevel() != null
                    && console.getLevel().getBlockEntity(readings.get(row).pos()) instanceof ControllerBlockEntity controller) {
                player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new FaceplateMenu(id, inventory, controller, console.getBlockPos()),
                        controller.getDisplayName()), controller::writeOpenData);
            }
        } else if (buttonId >= BUTTON_TREND) {
            int row = buttonId - BUTTON_TREND;
            if (row < readings.size()) trendTag = readings.get(row).tag();
        }
        age = 0; // answer right away
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (console == null) return true;
        return !console.isRemoved() && player.distanceToSqr(console.getBlockPos().getX() + 0.5, console.getBlockPos().getY() + 0.5, console.getBlockPos().getZ() + 0.5) <= 64;
    }
}
