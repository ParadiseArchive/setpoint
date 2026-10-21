package io.hookdev.setpoint.block.entity;

/** A machine with a tag a player can change with a name tag, like a pump, tank or boiler. Instruments have their own. */
public interface NamedEquipment {
    String tag();

    /** Gives the machine a new tag. False if the name was empty. */
    boolean rename(String name);
}
