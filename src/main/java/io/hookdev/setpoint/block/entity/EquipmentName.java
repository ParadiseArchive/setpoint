package io.hookdev.setpoint.block.entity;

import java.util.Locale;

import io.hookdev.setpoint.block.EquipmentTag;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The tag of a machine: P-417 from its position, until a player names it with a name tag (P-FEED).
 * Machines keep one of these and implement {@link NamedEquipment}.
 */
public final class EquipmentName {
    public static final int MAX_LENGTH = 16;

    private final String prefix;
    private String custom = "";

    public EquipmentName(String prefix) {
        this.prefix = prefix;
    }

    public String tag(BlockPos pos) {
        return custom.isEmpty() ? EquipmentTag.of(prefix, pos) : custom;
    }

    /** Sets the name the way a tag is written: capitals, dashes for spaces, at most 16 characters. False if it was empty. */
    public boolean rename(String name) {
        String clean = clean(name);
        if (clean.isEmpty()) return false;
        custom = clean;
        return true;
    }

    public static String clean(String name) {
        String clean = name.trim().toUpperCase(Locale.ROOT).replace(' ', '-');
        return clean.length() > MAX_LENGTH ? clean.substring(0, MAX_LENGTH) : clean;
    }

    public void save(ValueOutput output) {
        if (!custom.isEmpty()) output.putString("name", custom);
    }

    public void load(ValueInput input) {
        custom = input.getStringOr("name", "");
    }
}
