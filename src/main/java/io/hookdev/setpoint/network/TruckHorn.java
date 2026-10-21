package io.hookdev.setpoint.network;

import io.hookdev.setpoint.entity.ForkliftEntity;
import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.entity.PalletTruckEntity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The horn key (H) was pressed: the pallet truck or forklift the player is driving honks. */
public record TruckHorn() implements CustomPacketPayload {
    public static final Type<TruckHorn> TYPE = new Type<>(Setpoint.id("truck_horn"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TruckHorn> CODEC = StreamCodec.unit(new TruckHorn());

    public static void handle(TruckHorn horn, IPayloadContext context) {
        Player player = context.player();
        if (player.getVehicle() instanceof ForkliftEntity forklift && forklift.driver() == player) {
            forklift.horn();
            return;
        }
        for (PalletTruckEntity truck : player.level().getEntitiesOfClass(PalletTruckEntity.class, player.getBoundingBox().inflate(PalletTruckEntity.REACH))) {
            if (truck.driver() == player) {
                truck.horn();
                return;
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
