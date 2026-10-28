package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.BatteryBankBlock;
import io.hookdev.setpoint.power.PowerNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A battery bank: a million FE, in and out at up to 2000 FE/t. It stores and gives back; it never makes power. A cable
 * fills it with what the machines leave over and draws on it when they need more (see {@link PowerNetwork}); a machine
 * right beside it, without a cable, it feeds itself.
 */
public class BatteryBankBlockEntity extends BlockEntity implements NamedEquipment, Describable {
    public static final int CAPACITY = 1_000_000, RATE = 2_000;

    /** The bank's charge: a battery to the cable network, so it gets what is left over and feeds machines only. */
    private final class Store extends SimpleEnergyHandler implements PowerNetwork.Storage {
        Store() {
            super(CAPACITY, RATE, RATE);
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    }

    private final Store energy = new Store();
    private final EquipmentName name = new EquipmentName("BB");
    /** FE gone in (positive) or out (negative) in the last second, per tick. */
    private int rate;
    private int before;
    private String status = "EMPTY";

    public BatteryBankBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.BATTERY_BANK_ENTITY.get(), pos, state);
    }

    public SimpleEnergyHandler energy() { return energy; }
    public int stored() { return energy.getAmountAsInt(); }
    public double fraction() { return stored() / (double) CAPACITY; }
    /** FE a tick going in (positive) or out (negative), over the last second. */
    public int rate() { return rate; }
    /** CHARGING, DISCHARGING, FULL, EMPTY or IDLE. */
    public String status() { return status; }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BatteryBankBlockEntity battery) {
        battery.feedNeighbours(level, pos);
        if (level.getGameTime() % 20 != 0) return;
        int now = battery.stored();
        battery.rate = (now - battery.before) / 20;
        battery.before = now;
        battery.status = now >= CAPACITY ? "FULL" : now == 0 ? "EMPTY" : battery.rate > 0 ? "CHARGING" : battery.rate < 0 ? "DISCHARGING" : "IDLE";
        int bars = now == 0 ? 0 : Math.min(4, 1 + (int) (battery.fraction() * 4));
        if (state.getValue(BatteryBankBlock.CHARGE) != bars) level.setBlock(pos, state.setValue(BatteryBankBlock.CHARGE, bars), Block.UPDATE_CLIENTS);
    }

    /** A machine right beside it (not a cable, which pulls by itself, and not another battery) gets fed directly. */
    private void feedNeighbours(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            int available = Math.min(RATE, energy.getAmountAsInt());
            if (available <= 0) return;
            BlockPos target = pos.relative(direction);
            if (!level.isLoaded(target) || level.getBlockState(target).getBlock() instanceof io.hookdev.setpoint.block.PowerCableBlock) continue;
            EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, target, direction.getOpposite());
            if (handler == null || handler instanceof PowerNetwork.Storage) continue;
            try (Transaction transaction = Transaction.openRoot()) {
                int sent = handler.insert(available, transaction);
                if (sent > 0) {
                    transaction.commit();
                    energy.set(energy.getAmountAsInt() - sent);
                }
            }
        }
    }

    @Override
    public void describe(Player player) {
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(status + "  ").withStyle(status.equals("EMPTY") ? ChatFormatting.GOLD : ChatFormatting.WHITE))
                .append(Component.literal(String.format("%.0f %%  %,d / %,d FE  %+d FE/t", fraction() * 100, stored(), CAPACITY, rate)).withStyle(ChatFormatting.GRAY)));
    }

    // ---------- the charge goes along in the item ----------
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (stored() > 0) components.set(SetpointContent.STORED_ENERGY.get(), stored());
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Integer stored = components.get(SetpointContent.STORED_ENERGY.get());
        if (stored != null) energy.set(Math.max(0, Math.min(CAPACITY, stored)));
        before = stored();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        name.load(input);
        before = stored();
    }
}
