package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.GasMakerBlock;
import io.hookdev.setpoint.gas.Liquids;
import io.hookdev.setpoint.hydraulics.Vessel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A diesel generator: the top of the power ladder, running on diesel from your own distillation column. Diesel comes in
 * through a pipe at the back, the sides or below; it powers every block it touches.
 */
public class DieselGeneratorBlockEntity extends BlockEntity implements Vessel, NamedEquipment, Describable {
    public static final int DIESEL_CAPACITY = 4_000, ENERGY_CAPACITY = 20_000;
    /** A tick of running: this much diesel for this much power. */
    public static final int DIESEL_PER_TICK = 4, POWER = 80;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, 0, EnergyPush.PER_SIDE * 6) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final FluidStacksResourceHandler diesel = new FluidStacksResourceHandler(1, DIESEL_CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.isEmpty() || resource.getFluid() == Liquids.DIESEL.fluid().get();
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    };
    private final EquipmentName name = new EquipmentName("G");
    private String status = "IDLE";

    public DieselGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.DIESEL_GENERATOR_ENTITY.get(), pos, state);
    }

    public SimpleEnergyHandler energy() { return energy; }
    /** RUNNING, or why not: NO DIESEL, FULL (nothing takes its power). */
    public String status() { return status; }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DieselGeneratorBlockEntity generator) {
        ServerLevel server = (ServerLevel) level;
        boolean running = false;
        if (io.hookdev.setpoint.control.Esd.tripped(level, pos)) generator.status = "ESD";
        else if (generator.energy.getAmountAsInt() + POWER > ENERGY_CAPACITY) generator.status = "FULL";
        else if (generator.amount() < DIESEL_PER_TICK) generator.status = "NO DIESEL";
        else {
            int left = generator.amount() - DIESEL_PER_TICK;
            generator.diesel.set(0, left > 0 ? Liquids.DIESEL.resource() : FluidResource.EMPTY, left);
            generator.energy.set(generator.energy.getAmountAsInt() + POWER);
            generator.status = "RUNNING";
            running = true;
        }
        EnergyPush.push(level, pos, generator.energy);
        if (state.getValue(GasMakerBlock.RUNNING) != running) level.setBlock(pos, state.setValue(GasMakerBlock.RUNNING, running), Block.UPDATE_CLIENTS);
        if (running && level.getGameTime() % 5 == 0) { // exhaust out of the stack
            server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.05, 0.02, 0.05, 0.01);
        }
        if (running && (level.getGameTime() + pos.hashCode()) % 30 == 0) {
            level.playSound(null, pos, SetpointContent.PUMP_RUN.get(), SoundSource.BLOCKS, 0.25f, 0.6f);
        }
    }

    @Override
    public void describe(Player player) {
        ChatFormatting colour = status.equals("RUNNING") ? ChatFormatting.WHITE : ChatFormatting.GOLD;
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(status + "  ").withStyle(colour))
                .append(Component.literal(String.format("diesel %.1f B, %,d FE stored", amount() / 1000.0, energy.getAmountAsInt())).withStyle(ChatFormatting.GRAY)));
    }

    // ---------- the fuel tank: a vessel a pump fills with diesel ----------
    @Override public FluidStacksResourceHandler fluid() { return diesel; }
    @Override public FluidResource resource() { return diesel.getResource(0); }
    @Override public int amount() { return diesel.getAmountAsInt(0); }
    @Override public double fillFraction() { return amount() / (double) DIESEL_CAPACITY; }

    @Override
    public boolean canAccept(FluidResource incoming) {
        return incoming.getFluid() == Liquids.DIESEL.fluid().get() && amount() < DIESEL_CAPACITY;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        diesel.serialize(output);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        diesel.deserialize(input);
        name.load(input);
    }
}
