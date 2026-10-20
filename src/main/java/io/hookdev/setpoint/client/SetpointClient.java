package io.hookdev.setpoint.client;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.SetpointContent;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only setup. Never loaded on a dedicated server. */
@Mod(value = Setpoint.MODID, dist = Dist.CLIENT)
public class SetpointClient {
    public SetpointClient(IEventBus modEventBus) {
        modEventBus.addListener(SetpointClient::registerScreens);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SetpointContent.FACEPLATE.get(), FaceplateScreen::new);
        event.register(SetpointContent.GENERATOR_MENU.get(), GeneratorScreen::new);
        event.register(SetpointContent.TANK_MENU.get(), TankScreen::new);
    }
}
