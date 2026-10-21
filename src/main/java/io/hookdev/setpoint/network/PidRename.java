package io.hookdev.setpoint.network;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.pid.PidDrawing;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The player gave the P&ID in their hand a new name. */
public record PidRename(int hand, String name) implements CustomPacketPayload {
    public static final Type<PidRename> TYPE = new Type<>(Setpoint.id("pid_rename"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PidRename> CODEC = CustomPacketPayload.codec(PidRename::write, PidRename::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(hand);
        buf.writeUtf(name, PidDrawing.MAX_NAME * 4);
    }

    private static PidRename read(RegistryFriendlyByteBuf buf) {
        return new PidRename(buf.readVarInt(), buf.readUtf(PidDrawing.MAX_NAME * 4));
    }

    /** Server side: rename the drawing in that hand, if it is still a P&ID. */
    public static void handle(PidRename message, IPayloadContext context) {
        InteractionHand hand = message.hand() == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = context.player().getItemInHand(hand);
        PidDrawing drawing = stack.get(SetpointContent.PID_DRAWING.get());
        if (drawing != null) stack.set(SetpointContent.PID_DRAWING.get(), drawing.named(message.name()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
