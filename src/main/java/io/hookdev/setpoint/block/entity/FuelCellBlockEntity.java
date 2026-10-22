package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.GasMakerBlock;
import io.hookdev.setpoint.gas.GasDraw;
import io.hookdev.setpoint.gas.Gases;
import io.hookdev.setpoint.hydraulics.Vessel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A fuel cell: hydrogen from the gas line on your left and oxygen from the one on your right become power and water,
 * the electrolyser the other way round. It gives back a little over half the power the electrolyser took, like a real
 * one. The water collects inside, for a pipe at the back or below; with nowhere to go it leaves as vapour.
 */
public class FuelCellBlockEntity extends BlockEntity implements NamedEquipment, Describable, Vessel {
    /** A tick of running: this much hydrogen and oxygen in (mB at 1 bar), this much power and water out. */
    public static final int HYDROGEN_PER_TICK = 100, OXYGEN_PER_TICK = 50, POWER = 24, WATER_PER_TICK = 5;
    public static final int ENERGY_CAPACITY = 4_000, WATER_CAPACITY = 4_000;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, 0, EnergyPush.PER_SIDE * 6) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final FluidStacksResourceHandler water = new FluidStacksResourceHandler(1, WATER_CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.isEmpty() || resource.getFluid().isSame(Fluids.WATER);
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    };
    private final EquipmentName name = new EquipmentName("FC");
    private final GasDraw hydrogen = new GasDraw(), oxygen = new GasDraw();
    private String status = "IDLE";

    public FuelCellBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.FUEL_CELL_ENTITY.get(), pos, state);
    }

    public SimpleEnergyHandler energy() { return energy; }
    /** RUNNING, or why not: NO HYDROGEN, NO OXYGEN, FULL (nothing takes its power). */
    public String status() { return status; }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FuelCellBlockEntity cell) {
        cell.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean running = false;
        var h2 = hydrogen.fullest(level, pos, GasMakerBlock.left(state), Gases.HYDROGEN.resource());
        var o2 = oxygen.fullest(level, pos, GasMakerBlock.right(state), Gases.OXYGEN.resource());
        if (energy.getAmountAsInt() + POWER > ENERGY_CAPACITY) status = "FULL";
        else if (h2 == null || h2.amount() < HYDROGEN_PER_TICK) status = "NO HYDROGEN";
        else if (o2 == null || o2.amount() < OXYGEN_PER_TICK) status = "NO OXYGEN";
        else {
            h2.extract(HYDROGEN_PER_TICK);
            o2.extract(OXYGEN_PER_TICK);
            energy.set(energy.getAmountAsInt() + POWER);
            int room = WATER_CAPACITY - amount();
            if (room >= WATER_PER_TICK) water.set(0, FluidResource.of(Fluids.WATER), amount() + WATER_PER_TICK);
            else if (level.getGameTime() % 6 == 0) { // full of water: it goes up as vapour
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 1, 0.1, 0.02, 0.1, 0.01);
            }
            status = "RUNNING";
            running = true;
        }
        EnergyPush.push(level, pos, energy);
        if (state.getValue(GasMakerBlock.RUNNING) != running) level.setBlock(pos, state.setValue(GasMakerBlock.RUNNING, running), Block.UPDATE_CLIENTS);
    }

    @Override
    public void describe(Player player) {
        ChatFormatting colour = status.equals("RUNNING") ? ChatFormatting.WHITE : ChatFormatting.GOLD;
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(status + "  ").withStyle(colour))
                .append(Component.literal(String.format("%d FE/t, %,d FE stored", status.equals("RUNNING") ? POWER : 0, energy.getAmountAsInt())).withStyle(ChatFormatting.GRAY)));
    }

    // ---------- the water it makes: a vessel a pump can empty ----------
    @Override public FluidStacksResourceHandler fluid() { return water; }
    @Override public FluidResource resource() { return water.getResource(0); }
    @Override public int amount() { return water.getAmountAsInt(0); }
    @Override public double fillFraction() { return amount() / (double) WATER_CAPACITY; }
    @Override public boolean canAccept(FluidResource incoming) { return false; }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        water.serialize(output);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        water.deserialize(input);
        name.load(input);
    }
}
