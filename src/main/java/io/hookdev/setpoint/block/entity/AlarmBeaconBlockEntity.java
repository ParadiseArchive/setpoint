package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.AlarmBeaconBlock;
import io.hookdev.setpoint.console.Consoles;
import io.hookdev.setpoint.console.PlantScanner;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Once a second the beacon asks the consoles around it whether an alarm waits for an ACK; while one does it sounds. */
public class AlarmBeaconBlockEntity extends BlockEntity {
    public static final int CHECK_TICKS = 20, HORN_TICKS = 40;

    public AlarmBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.ALARM_BEACON_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AlarmBeaconBlockEntity beacon) {
        long time = level.getGameTime();
        if (time % CHECK_TICKS == Math.floorMod(pos.hashCode(), CHECK_TICKS)) {
            boolean alarm = Consoles.near((ServerLevel) level, pos, PlantScanner.RANGE).stream().anyMatch(c -> c.alarms().hasUnacknowledged());
            if (alarm != state.getValue(AlarmBeaconBlock.ALARM)) {
                // a new alarm sounds again even if the last one was silenced
                level.setBlock(pos, state = state.setValue(AlarmBeaconBlock.ALARM, alarm).setValue(AlarmBeaconBlock.SILENCED, false), Block.UPDATE_ALL);
            }
        }
        if (state.getValue(AlarmBeaconBlock.ALARM) && !state.getValue(AlarmBeaconBlock.SILENCED) && time % HORN_TICKS == 0) {
            level.playSound(null, pos, SetpointContent.ALARM_HORN.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
        }
    }
}
