package io.hookdev.setpoint.block.entity;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.GasMakerBlock;
import io.hookdev.setpoint.gas.GasFlow;
import io.hookdev.setpoint.gas.GasPortBlock;
import io.hookdev.setpoint.gas.GasTank;
import io.hookdev.setpoint.gas.GasVessel;
import io.hookdev.setpoint.gas.Gases;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * A machine that turns power into a gas. The gas collects in its outlet (on your left as you face it) and flows into
 * the gas pipe there by itself; once nothing takes it and the outlet stands at {@link #OUTLET_PRESSURE}, it stops.
 */
public abstract class GasMakerBlockEntity extends BlockEntity implements NamedEquipment, GasPortBlock.Ports, Describable {
    public static final int ENERGY_CAPACITY = 4_000, ENERGY_MAX_INPUT = 400;
    /** The outlet: this much room, filled up to this pressure (bar). */
    public static final int OUTLET_VOLUME = 500;
    public static final double OUTLET_PRESSURE = 10.0;

    protected final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, ENERGY_MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final EquipmentName name;
    protected final GasTank product = new GasTank("product", OUTLET_VOLUME, OUTLET_PRESSURE, this::setChanged);
    private final GasFlow productFlow = new GasFlow();
    private String status = "IDLE";
    private int made;

    protected GasMakerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, String prefix) {
        super(type, pos, state);
        this.name = new EquipmentName(prefix);
    }

    /** What it makes, how much a tick, and the power that takes. */
    protected abstract Gases.Gas productGas();
    protected abstract int productPerTick();
    protected abstract int energyPerTick();

    /** What else it needs before it can run (null: nothing), like water for an electrolyser. */
    protected @Nullable String missing() {
        return null;
    }

    /** Everything else that happens in a tick it runs: using water, making oxygen. */
    protected void alsoMade(ServerLevel level, BlockPos pos, BlockState state) {}

    /** Every tick, running or not: other outlets let their gas go. */
    protected void alsoFlow(ServerLevel level, BlockPos pos, BlockState state) {}

    public SimpleEnergyHandler energy() { return energy; }
    public GasTank product() { return product; }
    /** RUNNING, or why not: NO POWER, FULL, NO WATER. */
    public String status() { return status; }
    /** Gas made in the last tick, mB. */
    public int made() { return made; }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    @Override
    public @Nullable GasVessel port(Direction side) {
        return side == GasMakerBlock.left(getBlockState()) ? product : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GasMakerBlockEntity maker) {
        maker.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        String blocked = io.hookdev.setpoint.control.Esd.tripped(level, pos) ? "ESD" : energy.getAmountAsInt() < energyPerTick() ? "NO POWER" : product.room() < productPerTick() ? "FULL" : missing();
        made = 0;
        if (blocked == null) {
            energy.set(energy.getAmountAsInt() - energyPerTick());
            made = product.insert(productGas().resource(), productPerTick());
            alsoMade(level, pos, state);
        }
        status = blocked == null ? "RUNNING" : blocked;
        productFlow.tick(level, pos, product, GasMakerBlock.left(state));
        alsoFlow(level, pos, state);
        boolean running = blocked == null;
        if (state.getValue(GasMakerBlock.RUNNING) != running) level.setBlock(pos, state.setValue(GasMakerBlock.RUNNING, running), Block.UPDATE_CLIENTS);
    }

    /** A little cloud out of this side: gas let into the air. */
    protected static void puff(ServerLevel level, BlockPos pos, Direction side) {
        if (level.getGameTime() % 6 != 0) return;
        double x = pos.getX() + 0.5 + side.getStepX() * 0.55, z = pos.getZ() + 0.5 + side.getStepZ() * 0.55;
        level.sendParticles(ParticleTypes.CLOUD, x, pos.getY() + 0.6, z, 1, 0.02, 0.05, 0.02, 0.01);
    }

    /** Right-click with an empty hand: what it is doing. */
    @Override
    public void describe(Player player) {
        ChatFormatting colour = status.equals("RUNNING") ? ChatFormatting.WHITE : ChatFormatting.GOLD;
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(status + "  ").withStyle(colour))
                .append(Component.literal(String.format("%s outlet %.1f bar", productGas().resource().getFluidType().getDescription().getString(), product.pressure()))
                        .withStyle(ChatFormatting.GRAY)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        name.save(output);
        product.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        name.load(input);
        product.load(input);
    }
}
