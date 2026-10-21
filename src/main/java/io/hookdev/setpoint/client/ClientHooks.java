package io.hookdev.setpoint.client;

import net.minecraft.client.Minecraft;

/** Client-only actions called from common code. Only ever run on the client, so this class never loads on a server. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openHandbook() {
        Minecraft.getInstance().gui.setScreen(new HandbookScreen(Handbook.load()));
    }
}
