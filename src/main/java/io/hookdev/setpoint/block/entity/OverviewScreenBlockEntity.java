package io.hookdev.setpoint.block.entity;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.OverviewScreenBlock;
import io.hookdev.setpoint.client.ClientHooks;
import io.hookdev.setpoint.console.Consoles;
import io.hookdev.setpoint.console.OverviewData;
import io.hookdev.setpoint.console.PlantScanner;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The top-left screen of a video wall holds its page and, once a second, copies what the nearest console sees, for the
 * players around it to draw. The other screens of the wall only show their part of it.
 */
public class OverviewScreenBlockEntity extends BlockEntity {
    public static final int OVERVIEW = 0, ALARMS = 1, TRENDS = 2, PAGES = 3;
    public static final String[] PAGE_NAMES = {"PLANT OVERVIEW", "ALARMS", "TRENDS"};
    public static final int SCAN_TICKS = 20;

    private int page = OVERVIEW;
    private OverviewData data = OverviewData.EMPTY;
    /** Client side: goes up every time new data arrives, so the wall knows to draw itself again. */
    private int version;

    public OverviewScreenBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.OVERVIEW_SCREEN_ENTITY.get(), pos, state);
    }

    public int page() { return page; }
    public OverviewData data() { return data; }
    public int version() { return version; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OverviewScreenBlockEntity screen) {
        if (level.getGameTime() % SCAN_TICKS != Math.floorMod(pos.hashCode(), SCAN_TICKS)) return;
        if (!OverviewScreenBlock.topLeft(level, pos, state.getValue(OverviewScreenBlock.FACING)).equals(pos)) {
            if (screen.data.linked()) { screen.data = OverviewData.EMPTY; screen.changed(); } // it was the top-left once
            return;
        }
        screen.read((ServerLevel) level);
    }

    /** Copies what the nearest console sees. Public for game tests. */
    public void read(ServerLevel level) {
        ConsoleBlockEntity console = Consoles.nearest(level, worldPosition, PlantScanner.RANGE);
        OverviewData now = console == null ? OverviewData.EMPTY : OverviewData.of(console, level.getGameTime());
        if (!now.equals(data)) {
            data = now;
            changed();
        }
    }

    /** Right-click: the next page, on the whole wall. */
    public void nextPage(Player player, BlockPos clicked) {
        page = (page + 1) % PAGES;
        if (level instanceof ServerLevel server) {
            read(server);
            server.playSound(null, clicked, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.3f, 1.5f);
        }
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal("Overview screen: " + PAGE_NAMES[page]), true);
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide()) ClientHooks.overviewRemoved(worldPosition);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("page", page);
        if (data.linked()) output.store("data", OverviewData.CODEC, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        page = Math.floorMod(input.getIntOr("page", OVERVIEW), PAGES);
        data = input.read("data", OverviewData.CODEC).orElse(OverviewData.EMPTY);
        version++;
    }

    // the players nearby get the page and what the console sees
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
