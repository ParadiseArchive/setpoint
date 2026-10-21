package io.hookdev.setpoint.gas;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** A plain gas vessel: one gas, a volume and the pressure it is built for. Saved under its own name. */
public class GasTank implements GasVessel {
    private final String name;
    private final int volume;
    private final double maxPressure;
    private final Runnable changed;
    private FluidResource gas = FluidResource.EMPTY;
    private int amount;

    public GasTank(String name, int volume, double maxPressure, Runnable changed) {
        this.name = name;
        this.volume = volume;
        this.maxPressure = maxPressure;
        this.changed = changed;
    }

    @Override public FluidResource gas() { return amount == 0 ? FluidResource.EMPTY : gas; }
    @Override public int amount() { return amount; }
    @Override public int volume() { return volume; }
    @Override public double maxPressure() { return maxPressure; }

    /** How much more fits before it reaches its pressure. */
    public int room() {
        return Math.max(0, (int) (maxPressure * volume) - amount);
    }

    @Override
    public boolean canTake(FluidResource incoming) {
        return Gases.isGas(incoming) && (amount == 0 || gas.equals(incoming));
    }

    @Override
    public int insert(FluidResource incoming, int wanted) {
        if (wanted <= 0 || !canTake(incoming)) return 0;
        int moved = Math.min(wanted, room());
        if (moved <= 0) return 0;
        gas = incoming;
        amount += moved;
        changed.run();
        return moved;
    }

    @Override
    public int extract(int wanted) {
        int moved = Math.min(Math.max(0, wanted), amount);
        if (moved == 0) return 0;
        amount -= moved;
        if (amount == 0) gas = FluidResource.EMPTY;
        changed.run();
        return moved;
    }

    /** Sets what is in it outright (loading, an item put back down, tests). */
    public void set(FluidResource newGas, int newAmount) {
        amount = Math.max(0, newAmount);
        gas = amount == 0 ? FluidResource.EMPTY : newGas;
        changed.run();
    }

    public void save(ValueOutput output) {
        if (amount == 0) return;
        output.putString(name + "_gas", BuiltInRegistries.FLUID.getKey(gas.getFluid()).toString());
        output.putInt(name + "_amount", amount);
    }

    public void load(ValueInput input) {
        Identifier id = Identifier.tryParse(input.getStringOr(name + "_gas", "minecraft:empty"));
        Fluid fluid = id == null ? null : BuiltInRegistries.FLUID.getValue(id);
        amount = input.getIntOr(name + "_amount", 0);
        gas = fluid == null || amount == 0 ? FluidResource.EMPTY : FluidResource.of(fluid);
        if (gas.isEmpty()) amount = 0;
    }
}
