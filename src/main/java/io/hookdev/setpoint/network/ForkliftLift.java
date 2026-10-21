package io.hookdev.setpoint.network;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.entity.ForkliftEntity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The arrow keys of a forklift driver changed: 1 raises the forks, -1 lowers them, 0 holds them. */
public record ForkliftLift(int direction) implements CustomPacketPayload {
    public static final Type<ForkliftLift> TYPE = new Type<>(Setpoint.id("forklift_lift"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForkliftLift> CODEC = ByteBufCodecs.VAR_INT.map(ForkliftLift::new, ForkliftLift::direction).cast();

    public static void handle(ForkliftLift lift, IPayloadContext context) {
        if (context.player().getVehicle() instanceof ForkliftEntity forklift && forklift.driver() == context.player()) forklift.setLiftInput(lift.direction());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
