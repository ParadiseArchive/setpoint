package io.hookdev.setpoint.client;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.menu.ConsoleMenu;
import io.hookdev.setpoint.network.ConsoleSnapshot;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client-only setup. Never loaded on a dedicated server. */
@Mod(value = Setpoint.MODID, dist = Dist.CLIENT)
public class SetpointClient {
    public SetpointClient(IEventBus modEventBus) {
        modEventBus.addListener(SetpointClient::registerScreens);
        modEventBus.addListener(SetpointClient::registerPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(SetpointClient::tooltip);
    }

    /** Every Setpoint item explains itself in plain words; Shift shows the details. */
    private static void tooltip(ItemTooltipEvent event) {
        var id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (!id.getNamespace().equals(Setpoint.MODID)) return;
        String key = "tooltip.setpoint." + id.getPath();
        if (!Language.getInstance().has(key)) return;
        var lines = event.getToolTip();
        for (String line : I18n.get(key).split("\n")) lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        if (!Language.getInstance().has(key + ".more")) return;
        if (Minecraft.getInstance().hasShiftDown()) {
            for (String line : I18n.get(key + ".more").split("\n")) lines.add(Component.literal(line).withStyle(ChatFormatting.DARK_AQUA));
        } else {
            lines.add(Component.literal("Hold Shift for details").withStyle(ChatFormatting.DARK_GRAY));
        }
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
