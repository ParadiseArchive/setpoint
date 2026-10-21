package io.hookdev.setpoint.network;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.entity.PalletTruckEntity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The lower key (CTRL) was pressed: the pallet truck the player is driving sets its pallet down. */
public record TruckLower() implements CustomPacketPayload {
    public static final Type<TruckLower> TYPE = new Type<>(Setpoint.id("truck_lower"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TruckLower> CODEC = StreamCodec.unit(new TruckLower());

    public static void handle(TruckLower lower, IPayloadContext context) {
        Player player = context.player();
        for (PalletTruckEntity truck : player.level().getEntitiesOfClass(PalletTruckEntity.class, player.getBoundingBox().inflate(PalletTruckEntity.REACH))) {
            if (truck.driver() == player && truck.hasPallet()) {
                truck.putDown(player);
                return;
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
