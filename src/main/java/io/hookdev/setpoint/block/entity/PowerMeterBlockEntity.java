package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.PowerMeterBlock;
import io.hookdev.setpoint.power.PowerNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A power meter's reading: the FE a tick its cable network carries, taken twice a second. */
public class PowerMeterBlockEntity extends BlockEntity implements NamedEquipment, Describable {
    /** The lights on the display: one from a trickle, all four from this much. */
    public static final int[] STEPS = {1, 25, 100, 400};

    private final EquipmentName name = new EquipmentName("JI");
    private int reading, cables, machines;

    public PowerMeterBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.POWER_METER_ENTITY.get(), pos, state);
    }

    /** FE a tick on its network. */
    public int reading() { return reading; }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PowerMeterBlockEntity meter) {
        if ((level.getGameTime() + pos.hashCode()) % 10 != 0) return;
        PowerNetwork network = PowerNetwork.of(level, pos);
        meter.reading = network.carried(level);
        meter.cables = network.cables();
        meter.machines = network.machines();
        int lights = 0;
        for (int step : STEPS) if (meter.reading >= step) lights++;
        if (state.getValue(PowerMeterBlock.LOAD) != lights) level.setBlock(pos, state.setValue(PowerMeterBlock.LOAD, lights), Block.UPDATE_CLIENTS);
    }

    @Override
    public void describe(Player player) {
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(reading + " FE/t  ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(cables + " cables, " + machines + " machines on its network").withStyle(ChatFormatting.GRAY)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        name.load(input);
    }
}
