package io.hookdev.setpoint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The client side of McVersion: drawing and screens in 26.1.2. versions/26.3/java has the same class for 26.3. */
public final class McVersionClient {
    private McVersionClient() {}

    /** Turns everything drawn after this round an axis, in degrees. */
    public static void rotateDegrees(PoseStack pose, Axis axis, float degrees) {
        pose.mulPose(axis.rotationDegrees(degrees));
    }

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    /** The line of text above the hotbar. */
    public static void overlayMessage(Component message) {
        Minecraft.getInstance().gui.setOverlayMessage(message, false);
    }
}
