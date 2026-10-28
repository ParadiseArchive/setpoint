package io.hookdev.setpoint.gas;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.Setpoint;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * The process liquids: crude oil from the pumpjack, and what the distillation column makes of it. Each has the density
 * and viscosity of the real thing (relative to water, as pumps feel them) and its own colour. Crude oil and naphtha are
 * volatile: in a tank with a fixed roof they slowly lose vapour, unless a nitrogen blanket sits on top of them.
 */
public final class Liquids {
    /** One liquid: its fluid, its colour, and whether it gives off vapour. */
    public record Liquid(String id, DeferredHolder<Fluid, LiquidFluid> fluid, int colour, boolean volatile_) {
        public FluidResource resource() {
            return FluidResource.of(fluid.get());
        }
    }

    public static final Liquid CRUDE_OIL = liquid("crude_oil", 0xFF2B241E, 870, 8_000, true);
    public static final Liquid NAPHTHA = liquid("naphtha", 0xFFE6D9A6, 700, 600, true);
    public static final Liquid KEROSENE = liquid("kerosene", 0xFFC9DDEA, 800, 1_500, false);
    public static final Liquid DIESEL = liquid("diesel", 0xFFC9962C, 840, 3_500, false);
    public static final Liquid RESIDUE = liquid("residue", 0xFF141212, 1_050, 60_000, false);
    public static final List<Liquid> ALL = List.of(CRUDE_OIL, NAPHTHA, KEROSENE, DIESEL, RESIDUE);

    /**
     * The common tags other mods put their oils in (PneumaticCraft, Immersive Petroleum and others use these names).
     * Setpoint's liquids are in them too, and Setpoint's machines take any liquid in them: oil is oil.
     */
    public static final TagKey<Fluid> CRUDE_OIL_TAG = common("crude_oil"), DIESEL_TAG = common("diesel"),
            KEROSENE_TAG = common("kerosene"), NAPHTHA_TAG = common("naphtha"), GASOLINE_TAG = common("gasoline");

    private Liquids() {}

    private static TagKey<Fluid> common(String name) {
        return TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", name));
    }

    /** Crude oil, ours or another mod's. */
    public static boolean isCrude(FluidResource resource) {
        return !resource.isEmpty() && (resource.getFluid() == CRUDE_OIL.fluid().get() || resource.getFluid().builtInRegistryHolder().is(CRUDE_OIL_TAG));
    }

    /** Diesel, ours or another mod's. */
    public static boolean isDiesel(FluidResource resource) {
        return !resource.isEmpty() && (resource.getFluid() == DIESEL.fluid().get() || resource.getFluid().builtInRegistryHolder().is(DIESEL_TAG));
    }

    /** density in kg/m³ and viscosity with water at 1000, like NeoForge's own. */
    private static Liquid liquid(String id, int colour, int density, int viscosity, boolean volatile_) {
        DeferredHolder<FluidType, FluidType> type = Gases.TYPES.register(id, () -> new FluidType(FluidType.Properties.create()
                .descriptionId("fluid_type." + Setpoint.MODID + "." + id).density(density).viscosity(viscosity)));
        DeferredHolder<Fluid, LiquidFluid> fluid = Gases.FLUIDS.register(id, () -> new LiquidFluid(type));
        return new Liquid(id, fluid, colour, volatile_);
    }

    /** The liquid this fluid is, or null. */
    public static @Nullable Liquid of(Fluid fluid) {
        for (Liquid liquid : ALL) if (fluid == liquid.fluid().get()) return liquid;
        return null;
    }

    /** Gives off vapour in a fixed-roof tank. */
    public static boolean isVolatile(FluidResource resource) {
        Liquid liquid = resource.isEmpty() ? null : of(resource.getFluid());
        return liquid != null && liquid.volatile_();
    }

    /** Called with the gases, so all of them are in the registers before those are handed to the game. */
    static void load() {}
}
