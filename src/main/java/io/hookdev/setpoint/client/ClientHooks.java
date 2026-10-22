package io.hookdev.setpoint.client;

import io.hookdev.setpoint.pid.PidDrawing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;

/** Client-only actions called from common code. Only ever run on the client, so this class never loads on a server. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openPid(PidDrawing drawing, InteractionHand hand) {
        Minecraft.getInstance().gui.setScreen(new PidScreen(drawing, hand));
    }

    public static void boardRemoved(BlockPos pos) {
        PidBoardTextures.remove(pos);
    }

    public static void overviewRemoved(BlockPos pos) {
        OverviewScreenTextures.remove(pos);
    }

    public static void openHandbook() {
        Minecraft.getInstance().gui.setScreen(new HandbookScreen(Handbook.load()));
    }
}
