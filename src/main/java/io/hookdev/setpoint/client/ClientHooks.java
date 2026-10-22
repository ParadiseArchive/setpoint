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

    private static final java.util.Map<BlockPos, FlareSound> FLARES = new java.util.HashMap<>();

    /** Every tick of a flare: its roar plays while it burns and someone is near enough to hear it. */
    public static void flareTick(io.hookdev.setpoint.block.entity.FlareBlockEntity flare) {
        FLARES.values().removeIf(FlareSound::isStopped);
        var player = Minecraft.getInstance().player;
        if (!flare.lit() || player == null || FLARES.containsKey(flare.getBlockPos())
                || player.distanceToSqr(flare.tip()) > 128 * 128) return;
        FlareSound sound = new FlareSound(flare);
        FLARES.put(flare.getBlockPos().immutable(), sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    public static void openHandbook() {
        Minecraft.getInstance().gui.setScreen(new HandbookScreen(Handbook.load()));
    }
}
