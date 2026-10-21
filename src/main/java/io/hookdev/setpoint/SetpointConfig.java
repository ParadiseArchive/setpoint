package io.hookdev.setpoint;

import net.neoforged.neoforge.common.ModConfigSpec;

/** World settings (synced config: set by the server, shared with players). Safe defaults, risk is opt-in. */
public final class SetpointConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue BOILER_LOW_LEVEL_TRIP;
    public static final ModConfigSpec.BooleanValue GIVE_HANDBOOK;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("boiler");
        BOILER_LOW_LEVEL_TRIP = builder
                .comment("Boiler protection: the burner trips when the drum level gets low, so a boiler can never run dry.",
                        "Set to false for the real deal: a dry boiler overheats, and feeding water into a red-hot drum, or letting it get hotter still, makes it explode.")
                .define("lowLevelTrip", true);
        builder.pop();
        builder.push("handbook");
        GIVE_HANDBOOK = builder
                .comment("Give every player a Setpoint Handbook the first time they join a world.",
                        "Modpack makers with many guide books can switch this off; the handbook stays craftable (book + copper ingot).")
                .define("giveOnFirstJoin", true);
        builder.pop();
        SPEC = builder.build();
    }

    private SetpointConfig() {}

    public static boolean giveHandbook() {
        return !SPEC.isLoaded() || GIVE_HANDBOOK.get();
    }

    public static boolean boilerLowLevelTrip() {
        return !SPEC.isLoaded() || BOILER_LOW_LEVEL_TRIP.get();
    }
}
