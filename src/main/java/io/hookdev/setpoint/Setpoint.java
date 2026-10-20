package io.hookdev.setpoint;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.hookdev.setpoint.gametest.SetpointGameTests;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Setpoint: process engineering for Minecraft. Pumps, tanks, valves and control loops
 * that behave like the real thing.
 */
@Mod(Setpoint.MODID)
public class Setpoint {
    public static final String MODID = "setpoint";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Setpoint(IEventBus modEventBus, ModContainer modContainer) {
        SetpointContent.register(modEventBus);
        SetpointGameTests.register(modEventBus);
        modEventBus.addListener(Setpoint::registerCapabilities);
        LOGGER.info("Setpoint loaded. Holding steady.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Tanks hold fluid for buckets, pumps and other mods' pipes.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SetpointContent.TANK_ENTITY.get(), (tank, side) -> tank.fluid());
        // Hoppers can feed the tank's bucket slot and take the result out below.
        event.registerBlockEntity(Capabilities.Item.BLOCK, SetpointContent.TANK_ENTITY.get(), (tank, side) -> tank.buckets());
        // Pumps take FE from any side; cables from other mods can power them.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.PUMP_ENTITY.get(), (pump, side) -> pump.energy());
        // Generators: cables can pull their FE, hoppers can feed their fuel slot.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.STIRLING_ENGINE_ENTITY.get(), (generator, side) -> generator.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.BIOGAS_GENERATOR_ENTITY.get(), (generator, side) -> generator.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SetpointContent.STIRLING_ENGINE_ENTITY.get(), (generator, side) -> generator.input());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SetpointContent.BIOGAS_GENERATOR_ENTITY.get(), (generator, side) -> generator.input());
    }
}
