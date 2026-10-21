package io.hookdev.setpoint;

import net.neoforged.neoforge.common.ModConfigSpec;

/** World settings (synced config: set by the server, shared with players). Safe defaults, risk is opt-in. */
public final class SetpointConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue BOILER_LOW_LEVEL_TRIP;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("boiler");
        BOILER_LOW_LEVEL_TRIP = builder
                .comment("Boiler protection: the burner trips when the drum level gets low, so a boiler can never run dry.",
                        "Set to false for the real deal: a dry boiler overheats, and feeding water into a red-hot drum, or letting it get hotter still, makes it explode.")
                .define("lowLevelTrip", true);
        builder.pop();
        SPEC = builder.build();
    }

    private SetpointConfig() {}

    public static boolean boilerLowLevelTrip() {
        return !SPEC.isLoaded() || BOILER_LOW_LEVEL_TRIP.get();
    }
}
