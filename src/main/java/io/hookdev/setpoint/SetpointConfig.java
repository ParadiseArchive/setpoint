package io.hookdev.setpoint;

import io.hookdev.setpoint.block.entity.BiogasGeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.DieselGeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.FuelCellBlockEntity;
import io.hookdev.setpoint.block.entity.StirlingEngineBlockEntity;
import io.hookdev.setpoint.block.entity.WindTurbineBlockEntity;
import io.hookdev.setpoint.physics.TurbineModel;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * World settings (SPEC: set by the server, shared with players) and startup settings (STARTUP_SPEC: read once when
 * the game starts, for world generation). Safe defaults, risk is opt-in. Modpack makers can rebalance the power
 * numbers here; the docs, JEI, the console and Jade all show what is set.
 */
public final class SetpointConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue BOILER_LOW_LEVEL_TRIP;
    public static final ModConfigSpec.BooleanValue BOILER_HIGH_PRESSURE_TRIP;
    public static final ModConfigSpec.BooleanValue GIVE_HANDBOOK;
    public static final ModConfigSpec.IntValue STIRLING_ENGINE, BIOGAS_GENERATOR, STEAM_TURBINE, DIESEL_GENERATOR, FUEL_CELL, WIND_TURBINE;
    public static final ModConfigSpec.BooleanValue SHOWCASE;

    public static final ModConfigSpec STARTUP_SPEC;
    public static final ModConfigSpec.BooleanValue HOOKONIUM_ORE;

    /** The highest FE/t a generator may be set to: what its own buffer holds in one go. */
    public static final int MAX_OUTPUT = 4_000;
    /** The steam turbine's FE/t at full steam and full speed, as built. */
    public static final int STEAM_TURBINE_RATED = (int) Math.round(TurbineModel.MAX_STEAM * TurbineModel.FE_PER_MB);

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("boiler");
        BOILER_LOW_LEVEL_TRIP = builder
                .comment("Boiler protection: the burner trips when the drum level gets low, so a boiler can never run dry.",
                        "Set to false for the real deal: a dry boiler overheats, and feeding water into a red-hot drum, or letting it get hotter still, makes it explode.")
                .define("lowLevelTrip", true);
        BOILER_HIGH_PRESSURE_TRIP = builder
                .comment("Boiler protection: the burner trips at 9 bar and starts again at 7 bar, so steam pressure stays below the design pressure (10 bar).",
                        "Set to false and only a safety valve on the drum stands between you and a burst drum at 13 bar.")
                .define("highPressureTrip", true);
        builder.pop();
        builder.push("handbook");
        GIVE_HANDBOOK = builder
                .comment("Give every player a Setpoint Handbook the first time they join a world.",
                        "Modpack makers with many guide books can switch this off; the handbook stays craftable (book + copper ingot).")
                .define("giveOnFirstJoin", true);
        builder.pop();
        builder.comment("What each generator makes, in FE per tick, at full output. Fuel lasts as long whatever you set here,",
                "so a higher number gives more FE from the same fuel. Changes take effect at once.").push("power");
        STIRLING_ENGINE = builder.comment("Stirling engine, on any furnace fuel.")
                .defineInRange("stirlingEngine", StirlingEngineBlockEntity.OUTPUT, 1, MAX_OUTPUT);
        BIOGAS_GENERATOR = builder.comment("Biogas generator, on anything a composter takes.")
                .defineInRange("biogasGenerator", BiogasGeneratorBlockEntity.OUTPUT, 1, MAX_OUTPUT);
        STEAM_TURBINE = builder.comment("Steam turbine, at full steam and 3000 rpm; it makes less when it gets less steam.")
                .defineInRange("steamTurbine", STEAM_TURBINE_RATED, 1, MAX_OUTPUT);
        DIESEL_GENERATOR = builder.comment("Diesel generator.")
                .defineInRange("dieselGenerator", DieselGeneratorBlockEntity.POWER, 1, MAX_OUTPUT);
        FUEL_CELL = builder.comment("Fuel cell, on hydrogen and oxygen.")
                .defineInRange("fuelCell", FuelCellBlockEntity.POWER, 1, MAX_OUTPUT);
        WIND_TURBINE = builder.comment("Wind turbine, at its rated wind speed and above; less in a light wind, nothing in a calm or a storm.")
                .defineInRange("windTurbine", WindTurbineBlockEntity.RATED, 1, MAX_OUTPUT);
        builder.pop();
        builder.push("showcase");
        SHOWCASE = builder
                .comment("Allow /setpoint showcase, which builds a working example plant next to an operator (it clears the ground it builds on).",
                        "Servers can switch it off.")
                .define("allowCommand", true);
        builder.pop();
        SPEC = builder.build();

        ModConfigSpec.Builder startup = new ModConfigSpec.Builder();
        startup.push("worldgen");
        HOOKONIUM_ORE = startup
                .comment("Generate Hookonium ore in the overworld (veins like diamond). Set to false if your pack gives Hookonium another way;",
                        "it only changes chunks made after the change. Read when the game starts.")
                .define("hookoniumOre", true);
        startup.pop();
        STARTUP_SPEC = startup.build();
    }

    private SetpointConfig() {}

    public static boolean giveHandbook() {
        return !SPEC.isLoaded() || GIVE_HANDBOOK.get();
    }

    public static boolean boilerHighPressureTrip() {
        return !SPEC.isLoaded() || BOILER_HIGH_PRESSURE_TRIP.get();
    }

    public static boolean boilerLowLevelTrip() {
        return !SPEC.isLoaded() || BOILER_LOW_LEVEL_TRIP.get();
    }

    public static int stirlingEngine() {
        return SPEC.isLoaded() ? STIRLING_ENGINE.get() : StirlingEngineBlockEntity.OUTPUT;
    }

    public static int biogasGenerator() {
        return SPEC.isLoaded() ? BIOGAS_GENERATOR.get() : BiogasGeneratorBlockEntity.OUTPUT;
    }

    public static int steamTurbine() {
        return SPEC.isLoaded() ? STEAM_TURBINE.get() : STEAM_TURBINE_RATED;
    }

    public static int dieselGenerator() {
        return SPEC.isLoaded() ? DIESEL_GENERATOR.get() : DieselGeneratorBlockEntity.POWER;
    }

    public static int fuelCell() {
        return SPEC.isLoaded() ? FUEL_CELL.get() : FuelCellBlockEntity.POWER;
    }

    public static int windTurbine() {
        return SPEC.isLoaded() ? WIND_TURBINE.get() : WindTurbineBlockEntity.RATED;
    }

    public static boolean showcaseAllowed() {
        return !SPEC.isLoaded() || SHOWCASE.get();
    }

    public static boolean hookoniumOre() {
        return !STARTUP_SPEC.isLoaded() || HOOKONIUM_ORE.get();
    }
}
