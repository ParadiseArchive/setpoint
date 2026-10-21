package io.hookdev.setpoint.block.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.ConsoleBlock;
import io.hookdev.setpoint.console.PlantScanner;
import io.hookdev.setpoint.console.Reading;
import io.hookdev.setpoint.control.AlarmList;
import io.hookdev.setpoint.control.TrendBuffer;
import io.hookdev.setpoint.menu.ConsoleMenu;
import io.hookdev.setpoint.network.ConsoleSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * DCS operator console. Once a second it reads every piece of Setpoint equipment within range, keeps
 * five minutes of trends per tag and runs the alarm list. New alarms chime and make the screen flash
 * until someone acknowledges them.
 */
public class ConsoleBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SCAN_TICKS = 20;
    public static final int ALARM_REPEAT_TICKS = 160;

    private List<Reading> readings = List.of();
    private final Map<String, TrendBuffer> trends = new HashMap<>();
    private final AlarmList alarms = new AlarmList();
    private long lastChime;

    public ConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.CONSOLE_ENTITY.get(), pos, state);
    }

    public List<Reading> readings() { return readings; }
    public AlarmList alarms() { return alarms; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConsoleBlockEntity console) {
        console.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        long time = level.getGameTime();
        if (time % SCAN_TICKS == 0) scan(level, pos, time);

        boolean flashing = alarms.hasUnacknowledged();
        if (flashing && time - lastChime >= ALARM_REPEAT_TICKS) chime(level, pos, time);
        if (state.getValue(ConsoleBlock.ALARM) != flashing) {
            level.setBlock(pos, state.setValue(ConsoleBlock.ALARM, flashing), Block.UPDATE_CLIENTS);
        }
    }

    /** Reads the plant, records trends and updates the alarm list. Public for game tests. */
    public void scan(ServerLevel level, BlockPos pos, long time) {
        readings = PlantScanner.scan(level, pos);
        List<AlarmList.Condition> active = new ArrayList<>();
        Map<String, TrendBuffer> seen = new HashMap<>();
        for (Reading r : readings) {
            for (String message : r.alarms()) active.add(new AlarmList.Condition(r.tag(), message));
            TrendBuffer buffer = trends.get(r.tag());
            if (buffer == null || buffer.series() != r.trend().length) buffer = new TrendBuffer(r.trend().length);
            buffer.add(r.trend());
            seen.put(r.tag(), buffer);
        }
        trends.clear();
        trends.putAll(seen); // equipment that is gone loses its trend
        if (alarms.update(active, time) > 0) chime(level, pos, time);
    }

    private void chime(ServerLevel level, BlockPos pos, long time) {
        lastChime = time;
        level.playSound(null, pos, SetpointContent.CONSOLE_ALARM.get(), SoundSource.BLOCKS, 0.4f, 1.0f);
    }

    public void acknowledge() {
        alarms.acknowledgeAll();
        setChanged();
    }

    /** Everything an open screen needs, with the trend of the selected tag. */
    public ConsoleSnapshot snapshot(int containerId, String trendTag) {
        List<ConsoleSnapshot.Row> rows = new ArrayList<>();
        for (Reading r : readings) rows.add(new ConsoleSnapshot.Row(r.tag(), r.kind(), r.status(), r.severity(), r.value()));
        List<ConsoleSnapshot.AlarmRow> list = new ArrayList<>();
        for (AlarmList.Alarm a : alarms.list()) list.add(new ConsoleSnapshot.AlarmRow(clock(a.since()), a.tag(), a.message(), a.state().ordinal()));
        List<String> names = new ArrayList<>();
        List<float[]> series = new ArrayList<>();
        TrendBuffer buffer = trends.get(trendTag);
        Reading selected = readings.stream().filter(r -> r.tag().equals(trendTag)).findFirst().orElse(null);
        if (buffer != null && selected != null) {
            names.addAll(selected.series());
            for (int s = 0; s < buffer.series(); s++) series.add(buffer.read(s));
        }
        return new ConsoleSnapshot(containerId, rows, list, alarms.unacknowledged(), trendTag, names, series);
    }

    /** Minecraft clock time (hh:mm) of a game tick, like the timestamps on a real alarm list. */
    private String clock(long gameTime) {
        if (level == null) return "--:--";
        long dayTime = level.getOverworldClockTime() - (level.getGameTime() - gameTime);
        long t = Math.floorMod(dayTime, 24_000L);
        return String.format("%02d:%02d", (t / 1000 + 6) % 24, (t % 1000) * 60 / 1000);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ConsoleMenu(containerId, inventory, this);
    }
}
