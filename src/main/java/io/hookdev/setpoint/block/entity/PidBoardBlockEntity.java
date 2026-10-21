package io.hookdev.setpoint.block.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.PidBoardBlock;
import io.hookdev.setpoint.client.ClientHooks;
import io.hookdev.setpoint.console.PlantScanner;
import io.hookdev.setpoint.console.Reading;
import io.hookdev.setpoint.menu.FaceplateMenu;
import io.hookdev.setpoint.pid.PidDrawing;
import io.hookdev.setpoint.pid.PidLive;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The top-left board of a panel holds the P&ID. Once a second it reads the plant around it (like a console, within
 * 32 blocks) and sends the state of every tag on the drawing to the players nearby, who draw it on the panel.
 */
public class PidBoardBlockEntity extends BlockEntity {
    public static final int SCAN_TICKS = 20;

    private ItemStack pid = ItemStack.EMPTY;
    private List<PidLive> live = List.of();
    /** Client side: goes up every time new data arrives, so the panel knows to draw itself again. */
    private int version;

    public PidBoardBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.PID_BOARD_ENTITY.get(), pos, state);
    }

    public ItemStack pid() { return pid; }
    public int version() { return version; }

    /** Alarms on the drawing right now. */
    public int alarms() {
        return (int) live.stream().filter(l -> l.state() == PidLive.ALARM).count();
    }

    public @Nullable PidDrawing drawing() {
        return pid.get(SetpointContent.PID_DRAWING.get());
    }

    /** The live state of each tag on the drawing, by tag. */
    public Map<String, PidLive> live() {
        Map<String, PidLive> map = new HashMap<>();
        for (PidLive l : live) map.put(l.tag(), l);
        return map;
    }

    /** Puts one P&ID from the stack on the board and hands back the one that was on it (or an empty stack). */
    public ItemStack insertPid(ItemStack stack) {
        ItemStack previous = pid;
        pid = stack.split(1);
        live = List.of();
        if (level instanceof ServerLevel server) scan(server);
        changed();
        light(true);
        return previous;
    }

    public ItemStack ejectPid() {
        ItemStack previous = pid;
        pid = ItemStack.EMPTY;
        live = List.of();
        changed();
        light(false);
        return previous;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Light the whole panel up (or switch it off) right away, without waiting for each board's own check. */
    private void light(boolean lit) {
        if (level == null || level.isClientSide()) return;
        Direction facing = getBlockState().getValue(PidBoardBlock.FACING);
        for (BlockPos at : PidBoardBlock.panel(level, worldPosition, facing)) {
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof PidBoardBlock && state.getValue(PidBoardBlock.LIT) != lit) level.setBlock(at, state.setValue(PidBoardBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PidBoardBlockEntity board) {
        // once a second, every board at its own moment
        if (level.getGameTime() % SCAN_TICKS != Math.floorMod(pos.hashCode(), SCAN_TICKS)) return;
        // a board glows while its panel shows a P&ID (a panel can grow or shrink, so every board checks for itself)
        BlockPos origin = PidBoardBlock.topLeft(level, pos, state.getValue(PidBoardBlock.FACING));
        boolean lit = level.getBlockEntity(origin) instanceof PidBoardBlockEntity top && top.drawing() != null;
        if (state.getValue(PidBoardBlock.LIT) != lit) level.setBlock(pos, state = state.setValue(PidBoardBlock.LIT, lit), Block.UPDATE_ALL);
        if (board.drawing() == null) return;
        List<PidLive> before = board.live;
        int alarmsBefore = board.alarms();
        board.scan((ServerLevel) level);
        if (!board.live.equals(before)) board.changed();
        if (board.alarms() != alarmsBefore) { // comparators next to the panel
            for (BlockPos at : PidBoardBlock.panel(level, pos, state.getValue(PidBoardBlock.FACING))) level.updateNeighbourForOutputSignal(at, state.getBlock());
        }
    }

    /** Reads the plant: the state of every tag on the drawing that the board can see. */
    public void scan(ServerLevel level) {
        PidDrawing drawing = drawing();
        if (drawing == null) { live = List.of(); return; }
        Map<String, Reading> readings = new HashMap<>();
        for (Reading r : PlantScanner.scan(level, worldPosition)) readings.put(r.tag(), r);
        List<PidLive> now = new ArrayList<>();
        for (PidDrawing.Cell cell : drawing.cells()) {
            Reading r = readings.get(cell.tag());
            if (r != null && now.stream().noneMatch(l -> l.tag().equals(r.tag()))) now.add(PidLive.of(r));
        }
        live = now;
    }

    /**
     * A player right-clicked a symbol: a controller opens its faceplate (it stays open while the player stands near
     * the board), a pump starts or stops, anything else reports its status.
     */
    public void operate(PidDrawing.Cell cell, Player player, BlockPos station) {
        if (!(level instanceof ServerLevel server)) return;
        if (cell.tag().isEmpty()) {
            say(player, Component.literal(PidDrawing.describe(cell.symbol())).withStyle(ChatFormatting.GRAY));
            return;
        }
        Reading reading = PlantScanner.scan(server, worldPosition).stream().filter(r -> r.tag().equals(cell.tag())).findFirst().orElse(null);
        if (reading == null) {
            say(player, Component.literal(cell.tag() + "  not seen: gone, or more than " + PlantScanner.RANGE + " blocks from the board").withStyle(ChatFormatting.GOLD));
            return;
        }
        BlockEntity target = server.getBlockEntity(reading.pos());
        if (target instanceof ControllerBlockEntity controller) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new FaceplateMenu(id, inventory, controller, station), controller.getDisplayName()),
                    controller::writeOpenData);
        } else if (target instanceof PumpBlockEntity pump) {
            pump.toggleRemoteStop(player);
            server.playSound(null, station, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, 1.2f);
            scan(server);
            changed();
        } else {
            say(player, Component.literal(reading.tag() + "  ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(reading.kind() + "  " + reading.status() + "  " + reading.value()).withStyle(ChatFormatting.WHITE)));
        }
    }

    private static void say(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(message, true);
    }

    /** Drop the P&ID when the board is broken. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !pid.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), pid);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide()) ClientHooks.boardRemoved(worldPosition);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!pid.isEmpty()) output.store("pid", ItemStack.CODEC, pid);
        if (!live.isEmpty()) output.store("live", PidLive.CODEC.listOf(), live);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pid = input.read("pid", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        live = input.read("live", PidLive.CODEC.listOf()).orElse(List.of());
        version++;
    }

    // the players nearby get the drawing and the live states
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        loadAdditional(input);
    }
}
