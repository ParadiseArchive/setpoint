package io.hookdev.setpoint.item;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.entity.PalletTruckEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** Right-click the ground to set the pallet truck down, its tiller towards you. */
public class PalletTruckItem extends Item {
    public PalletTruckItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos at = context.getClickedPos().relative(context.getClickedFace());
        PalletTruckEntity truck = SetpointContent.PALLET_TRUCK_ENTITY.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (truck == null) return InteractionResult.FAIL;
        Player player = context.getPlayer();
        float yaw = player == null ? 0 : player.getYRot() + 180; // the tiller points back at you
        truck.snapTo(new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5), yaw, 0);
        if (!level.noCollision(truck, truck.getBoundingBox())) return InteractionResult.FAIL;
        level.addFreshEntity(truck);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
