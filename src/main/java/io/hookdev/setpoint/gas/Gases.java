package io.hookdev.setpoint.gas;

import java.util.List;

import io.hookdev.setpoint.Setpoint;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * The gases. A gas is counted in mB at 1 bar (like normal cubic metres on a real plant): squeeze it into a vessel and
 * its pressure is how much is in it over how much room there is ({@link GasVessel#pressure()}).
 * Each has its own colour, the shoulder colour of its gas cylinder (red hydrogen, white oxygen, black nitrogen).
 */
public final class Gases {
    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Setpoint.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Setpoint.MODID);

    /** One gas: its fluid, and its colour. */
    public record Gas(String id, DeferredHolder<Fluid, GasFluid> fluid, int colour) {
        public FluidResource resource() {
            return FluidResource.of(fluid.get());
        }
    }

    public static final Gas HYDROGEN = gas("hydrogen", 0xFFC7322F, -1, 20);
    public static final Gas OXYGEN = gas("oxygen", 0xFFF2F4F7, 1, 20);
    public static final Gas NITROGEN = gas("nitrogen", 0xFF2C2E33, 1, 20);
    public static final Gas STEAM = gas("steam", 0xFFD8DDE3, 0, 100);
    public static final List<Gas> ALL = List.of(HYDROGEN, OXYGEN, NITROGEN, STEAM);

    private Gases() {}

    /** density: below 0 rises in air (only hydrogen of these); temperature in °C. */
    private static Gas gas(String id, int colour, int density, int celsius) {
        DeferredHolder<FluidType, FluidType> type = TYPES.register(id, () -> new FluidType(FluidType.Properties.create()
                .descriptionId("fluid_type." + Setpoint.MODID + "." + id).density(density).viscosity(10).temperature(273 + celsius)));
        DeferredHolder<Fluid, GasFluid> fluid = FLUIDS.register(id, () -> new GasFluid(type));
        return new Gas(id, fluid, colour);
    }

    public static boolean isGas(FluidResource resource) {
        return !resource.isEmpty() && resource.getFluid() instanceof GasFluid;
    }

    /** The gas this fluid is, or null. */
    public static Gas of(FluidResource resource) {
        for (Gas gas : ALL) if (!resource.isEmpty() && resource.getFluid() == gas.fluid().get()) return gas;
        return null;
    }

    public static int colour(FluidResource resource) {
        Gas gas = of(resource);
        return gas == null ? 0xFF8D95A5 : gas.colour();
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
