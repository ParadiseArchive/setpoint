package io.hookdev.setpoint.block.entity;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.GasMakerBlock;
import io.hookdev.setpoint.gas.GasDraw;
import io.hookdev.setpoint.gas.GasFlow;
import io.hookdev.setpoint.gas.GasPortBlock;
import io.hookdev.setpoint.gas.GasTank;
import io.hookdev.setpoint.gas.GasVessel;
import io.hookdev.setpoint.gas.Gases;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * An ammonia converter (Haber-Bosch): nitrogen from the gas line on your left and hydrogen from the one on your right,
 * one to three, over a hot catalyst become ammonia, out on top. Like the real one it wants pressure: it runs at full
 * speed with its feed at {@link #FULL_PRESSURE} bar and slower below that, so a compressor on the feed pays off.
 */
public class AmmoniaReactorBlockEntity extends BlockEntity implements NamedEquipment, Describable, GasPortBlock.Ports {
    /** At full speed a tick takes this much nitrogen (and three times the hydrogen) and makes twice as much ammonia. */
    public static final int NITROGEN_PER_TICK = 20;
    public static final double FULL_PRESSURE = 100.0;
    /** The ammonia outlet on top: this much room, filled up to this pressure. */
    public static final int OUTLET_VOLUME = 500;
    public static final double OUTLET_PRESSURE = 20.0;
    public static final int ENERGY_CAPACITY = 4_000, ENERGY_MAX_INPUT = 400;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, ENERGY_MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final GasTank ammonia = new GasTank("ammonia", OUTLET_VOLUME, OUTLET_PRESSURE, this::setChanged);
    private final GasFlow ammoniaFlow = new GasFlow();
    private final GasDraw nitrogen = new GasDraw(), hydrogen = new GasDraw();
    private final EquipmentName name = new EquipmentName("R");
    private String status = "IDLE";
    private int made;
    private double feed;

    public AmmoniaReactorBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.AMMONIA_REACTOR_ENTITY.get(), pos, state);
    }

    public SimpleEnergyHandler energy() { return energy; }
    public GasTank ammonia() { return ammonia; }
    /** RUNNING, or why not: NO NITROGEN, NO HYDROGEN, LOW PRESSURE, NO POWER, FULL. */
    public String status() { return status; }
    /** Ammonia made in the last tick, mB at 1 bar. */
    public int made() { return made; }
    /** The lower of its two feed pressures, in bar. */
    public double feedPressure() { return feed; }

    /** Power for a tick that takes this much nitrogen, FE. */
    public static int power(int nitrogen) {
        return nitrogen == 0 ? 0 : 5 + 2 * nitrogen;
    }

    @Override
    public @Nullable GasVessel port(Direction side) {
        return side == Direction.UP ? ammonia : null;
    }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AmmoniaReactorBlockEntity reactor) {
        reactor.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        made = 0;
        GasVessel n2 = nitrogen.fullest(level, pos, GasMakerBlock.left(state), Gases.NITROGEN.resource());
        GasVessel h2 = hydrogen.fullest(level, pos, GasMakerBlock.right(state), Gases.HYDROGEN.resource());
        feed = n2 == null || h2 == null ? 0 : Math.min(n2.pressure(), h2.pressure());
        int unit = (int) Math.floor(NITROGEN_PER_TICK * Math.min(1, feed / FULL_PRESSURE));
        if (n2 == null) status = "NO NITROGEN";
        else if (h2 == null) status = "NO HYDROGEN";
        else if (unit == 0 || n2.amount() < unit || h2.amount() < 3 * unit) status = "LOW PRESSURE";
        else if (ammonia.room() < 2 * unit) status = "FULL";
        else if (energy.getAmountAsInt() < power(unit)) status = "NO POWER";
        else {
            n2.extract(unit);
            h2.extract(3 * unit);
            made = ammonia.insert(Gases.AMMONIA.resource(), 2 * unit);
            energy.set(energy.getAmountAsInt() - power(unit));
            status = "RUNNING";
        }
        ammoniaFlow.tick(level, pos, ammonia, Direction.UP);
        boolean running = made > 0;
        if (state.getValue(GasMakerBlock.RUNNING) != running) level.setBlock(pos, state.setValue(GasMakerBlock.RUNNING, running), Block.UPDATE_CLIENTS);
    }

    @Override
    public void describe(Player player) {
        ChatFormatting colour = status.equals("RUNNING") ? ChatFormatting.WHITE : ChatFormatting.GOLD;
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(status + "  ").withStyle(colour))
                .append(Component.literal(String.format("feed %.0f bar, %d mB/t ammonia, outlet %.1f bar", feed, made, ammonia.pressure())).withStyle(ChatFormatting.GRAY)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        ammonia.save(output);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        ammonia.load(input);
        name.load(input);
    }
}
