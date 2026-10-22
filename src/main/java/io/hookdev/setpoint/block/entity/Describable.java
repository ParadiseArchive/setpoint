package io.hookdev.setpoint.block.entity;

import net.minecraft.world.entity.player.Player;

/** A machine that tells you what it is doing when you right-click it with an empty hand. */
public interface Describable {
    void describe(Player player);
}
