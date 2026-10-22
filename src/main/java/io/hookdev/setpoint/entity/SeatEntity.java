package io.hookdev.setpoint.entity;

import java.util.List;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.OfficeChairBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The invisible seat of an office chair: a player who sits down rides it. It sits on the cushion, goes away as soon as
 * nobody sits on it or the chair is gone, and is never saved.
 */
public class SeatEntity extends Entity {
    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Sits a player down on the chair at this spot, facing the way the chair faces. False when someone sits there already. */
    public static boolean sit(Level level, BlockPos chair, Player player, float yaw) {
        List<SeatEntity> taken = level.getEntitiesOfClass(SeatEntity.class, new AABB(chair));
        if (!taken.isEmpty()) return false;
        SeatEntity seat = new SeatEntity(SetpointContent.SEAT_ENTITY.get(), level);
        seat.setPos(chair.getX() + 0.5, chair.getY() + OfficeChairBlock.SEAT_HEIGHT, chair.getZ() + 0.5);
        seat.setYRot(yaw);
        level.addFreshEntity(seat);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        return player.startRiding(seat);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && (getPassengers().isEmpty() || !(level().getBlockState(blockPosition()).getBlock() instanceof OfficeChairBlock))) discard();
    }

    /** The player sits right on the cushion. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return Vec3.ZERO;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
