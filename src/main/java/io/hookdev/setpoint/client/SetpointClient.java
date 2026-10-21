package io.hookdev.setpoint.client;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.menu.ConsoleMenu;
import io.hookdev.setpoint.network.ConsoleSnapshot;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client-only setup. Never loaded on a dedicated server. */
@Mod(value = Setpoint.MODID, dist = Dist.CLIENT)
public class SetpointClient {
    public SetpointClient(IEventBus modEventBus) {
        modEventBus.addListener(SetpointClient::registerScreens);
        modEventBus.addListener(SetpointClient::registerPayloadHandlers);
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ConsoleSnapshot.TYPE, (snapshot, context) -> {
            if (context.player().containerMenu instanceof ConsoleMenu menu && menu.containerId == snapshot.containerId()) menu.receive(snapshot);
        });
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SetpointContent.FACEPLATE.get(), FaceplateScreen::new);
        event.register(SetpointContent.GENERATOR_MENU.get(), GeneratorScreen::new);
        event.register(SetpointContent.TANK_MENU.get(), TankScreen::new);
        event.register(SetpointContent.BOILER_MENU.get(), BoilerScreen::new);
        event.register(SetpointContent.CONSOLE_MENU.get(), ConsoleScreen::new);
    }
}
