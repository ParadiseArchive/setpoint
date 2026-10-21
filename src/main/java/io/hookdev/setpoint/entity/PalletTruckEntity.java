package io.hookdev.setpoint.entity;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.PalletBlock;
import io.hookdev.setpoint.block.PalletPartBlock;
import io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity;
import io.hookdev.setpoint.block.entity.PalletBlockEntity;
import io.hookdev.setpoint.block.entity.PortableTankBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * An electric pallet truck. Right-click to take the tiller: it follows behind you like a trailer, up steps and slabs.
 * Back it into a pallet and its forks pick the pallet up, empty or with drums on it; sneak-right-click to set the
 * pallet down. Press H for the horn. Right-click again, or walk away, to let go.
 */
public class PalletTruckEntity extends VehicleEntity {
    /** From the middle of the truck to its handle, and to the middle of its forks, in blocks. */
    public static final double HANDLE = 1.5, FORKS = 0.4;
    /** Walk further away than this and you let go. */
    public static final double REACH = 6;
    private static final EntityDataAccessor<Integer> DATA_DRIVER = SynchedEntityData.defineId(PalletTruckEntity.class, EntityDataSerializers.INT);
    /** Drums on the pallet it carries, or -1 without a pallet. */
    private static final EntityDataAccessor<Integer> DATA_DRUMS = SynchedEntityData.defineId(PalletTruckEntity.class, EntityDataSerializers.INT);
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<Integer>[] DATA_COLOURS = new EntityDataAccessor[PalletBlockEntity.MAX_DRUMS];
    static {
        for (int i = 0; i < DATA_COLOURS.length; i++) DATA_COLOURS[i] = SynchedEntityData.defineId(PalletTruckEntity.class, EntityDataSerializers.INT);
    }

    private final List<ItemStack> drums = new ArrayList<>();
    private boolean hasPallet;

    public PalletTruckEntity(EntityType<? extends PalletTruckEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_DRIVER, -1);
        entityData.define(DATA_DRUMS, -1);
        for (EntityDataAccessor<Integer> colour : DATA_COLOURS) entityData.define(colour, PortableTankBlockEntity.DRUM_BLUE);
    }

    @Override
    protected Item getDropItem() {
        return SetpointContent.PALLET_TRUCK.get();
    }

    /** The player holding the tiller, if any. */
    public @Nullable Player driver() {
        return level().getEntity(entityData.get(DATA_DRIVER)) instanceof Player player ? player : null;
    }

    private void setDriver(@Nullable Player player) {
        entityData.set(DATA_DRIVER, player == null ? -1 : player.getId());
    }

    /** Drums on the pallet it carries, or -1 without a pallet. */
    public int carriedDrums() { return entityData.get(DATA_DRUMS); }

    public int drumColour(int index) { return entityData.get(DATA_COLOURS[index]); }

    public boolean hasPallet() { return hasPallet; }

    /** The way the tiller points. */
    public Vec3 forward() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (player.isSecondaryUseActive() && hasPallet) {
            putDown(player);
            return InteractionResult.SUCCESS_SERVER;
        }
        Player driver = driver();
        if (driver == player) {
            setDriver(null);
            say(player, "You let go of the pallet truck.");
        } else if (driver == null) {
            setDriver(player);
            say(player, "You hold the tiller: walk and it follows. Back it into a pallet to pick it up. H: horn.");
        } else {
            say(player, driver.getName().getString() + " is driving this one.");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) drive(server);
    }

    private void drive(ServerLevel level) {
        Player driver = driver();
        if (driver != null && (driver.isRemoved() || driver.level() != level || driver.distanceTo(this) > REACH)) {
            setDriver(null);
            driver = null;
        }
        Vec3 step = Vec3.ZERO;
        if (driver != null) {
            // a trailer: the tiller points at you, and the handle stays a step behind you
            Vec3 toDriver = new Vec3(driver.getX() - getX(), 0, driver.getZ() - getZ());
            double distance = toDriver.horizontalDistance();
            if (distance > 0.05) {
                Vec3 forward = toDriver.scale(1 / distance);
                setYRot((float) (Mth.atan2(-forward.x, forward.z) * Mth.RAD_TO_DEG));
                step = forward.scale(Mth.clamp(distance - (HANDLE + 0.6), -0.5, 0.6));
            }
            if (!hasPallet) couple(level, step);
        }
        double fall = onGround() ? 0 : Math.max(-2, getDeltaMovement().y - 0.08);
        setDeltaMovement(step.x, fall, step.z);
        move(MoverType.SELF, getDeltaMovement());
    }

    /** Picks up a pallet the forks are in, if there is one (the truck does this by itself while you drive it). */
    public void coupleNow() {
        if (level() instanceof ServerLevel server && !hasPallet) couple(server, Vec3.ZERO);
    }

    /** Forks running into a pallet (on its low deck): it comes up onto them, with what is on it. */
    private void couple(ServerLevel level, Vec3 step) {
        Vec3 back = forward().scale(-1);
        for (double along : new double[]{0.4, 0.9, 1.4}) {
            Vec3 at = position().add(step).add(back.scale(along)).add(0, 0.1, 0);
            BlockPos pos = BlockPos.containing(at);
            BlockState state = level.getBlockState(pos);
            BlockPos base = null;
            if (state.getBlock() instanceof PalletBlock) base = pos;
            else if (state.getBlock() instanceof PalletPartBlock && level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part) base = part.base();
            if (base == null || !(level.getBlockEntity(base) instanceof PalletBlockEntity pallet)) continue;
            drums.clear();
            drums.addAll(pallet.takeAll());
            level.removeBlock(base, false); // its parts go with it; the drums are on the forks now
            hasPallet = true;
            synced();
            level.playSound(null, base, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8f, 0.8f);
            return;
        }
    }

    /** Sneak-right-click: lower the forks and leave the pallet on the ground, square to the truck. */
    public void putDown(Player player) {
        Vec3 centre = position().subtract(forward().scale(FORKS));
        Direction facing = Direction.fromYRot(getYRot());
        int minX = Mth.floor(centre.x - 0.5), minZ = Mth.floor(centre.z - 0.5), y = Mth.floor(getY() + 0.1);
        List<BlockPos> ground = List.of(new BlockPos(minX, y, minZ), new BlockPos(minX + 1, y, minZ), new BlockPos(minX, y, minZ + 1), new BlockPos(minX + 1, y, minZ + 1));
        BlockPos base = null;
        for (BlockPos corner : ground) {
            List<BlockPos> all = new ArrayList<>(PalletBlock.footprint(corner, facing, false));
            all.add(corner);
            if (ground.containsAll(all)) base = corner;
        }
        if (base == null) return;
        for (BlockPos cell : PalletBlock.footprint(base, facing, !drums.isEmpty())) if (!level().getBlockState(cell).canBeReplaced()) { say(player, "No room to set the pallet down here."); return; }
        if (!level().getBlockState(base).canBeReplaced()) { say(player, "No room to set the pallet down here."); return; }
        PalletBlock.place(level(), base, facing, drums);
        drums.clear();
        hasPallet = false;
        synced();
        level().playSound(null, base, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8f, 0.7f);
    }

    /** Tells the players what is on the forks, to draw it. */
    private void synced() {
        entityData.set(DATA_DRUMS, hasPallet ? drums.size() : -1);
        for (int i = 0; i < DATA_COLOURS.length; i++) {
            DyedItemColor dyed = i < drums.size() ? drums.get(i).get(DataComponents.DYED_COLOR) : null;
            entityData.set(DATA_COLOURS[i], dyed == null ? PortableTankBlockEntity.DRUM_BLUE : 0xFF000000 | dyed.rgb());
        }
    }

    /** The horn: a short beep. */
    public void horn() {
        level().playSound(null, getX(), getY() + 1, getZ(), SetpointContent.PALLET_TRUCK_HORN.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
    }

    private static void say(Player player, String text) {
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY), true);
    }

    /** Broken up: the truck drops, and a pallet it carried, with its drums. */
    @Override
    public void destroy(ServerLevel level, Item dropItem) {
        super.destroy(level, dropItem);
        if (hasPallet) {
            spawnAtLocation(level, new ItemStack(SetpointContent.PALLET_ITEM.get()));
            for (ItemStack drum : drums) spawnAtLocation(level, drum);
            drums.clear();
            hasPallet = false;
        }
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    /** It rolls up a step or a slab, like you do. */
    @Override
    public float maxUpStep() {
        return 1.0f;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putBoolean("has_pallet", hasPallet);
        if (!drums.isEmpty()) output.store("drums", ItemStack.CODEC.listOf(), drums);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        hasPallet = input.getBooleanOr("has_pallet", false);
        drums.clear();
        drums.addAll(input.read("drums", ItemStack.CODEC.listOf()).orElse(List.of()));
        synced();
    }
}
