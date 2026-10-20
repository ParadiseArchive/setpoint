package io.hookdev.setpoint;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Setpoint: process engineering for Minecraft. Pumps, tanks, valves and control loops
 * that behave like the real thing.
 */
@Mod(Setpoint.MODID)
public class Setpoint {
    public static final String MODID = "setpoint";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Setpoint(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Setpoint loaded. Holding steady.");
    }
}
