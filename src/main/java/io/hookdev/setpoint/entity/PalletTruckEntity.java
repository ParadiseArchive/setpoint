package io.hookdev.setpoint.entity;

import net.minecraft.sounds.SoundEvent;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.PalletBlock;
import io.hookdev.setpoint.block.PalletPartBlock;
import io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity;
import io.hookdev.setpoint.block.entity.PalletBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * An electric pallet truck. Right-click to take the tiller: it follows behind you like a trailer, up steps and slabs.
 * Back it into a pallet and its forks pick the pallet up, empty or with drums on it; press CTRL to set the pallet
 * down (sneak-right-click works too). Hold H for the horn. Right-click again, or walk away, to let go.
 */
public class PalletTruckEntity extends VehicleEntity implements HornVehicle {
    /** From the middle of the truck to its handle, and to the middle of its forks, in blocks. */
    public static final double HANDLE = 1.5, FORKS = 0.4;
    /** Walk further away than this and you let go. */
    public static final double REACH = 6;
    /** After setting a pallet down, this long before the forks take one up again (and only once they are out of it). */
    public static final int COUPLE_COOLDOWN = 120;
    private static final EntityDataAccessor<Integer> DATA_DRIVER = SynchedEntityData.defineId(PalletTruckEntity.class, EntityDataSerializers.INT);
    /** Whether it carries a pallet, and what is on it (the items: every player draws them, liquid and colour). */
    private static final EntityDataAccessor<Boolean> DATA_PALLET = SynchedEntityData.defineId(PalletTruckEntity.class, EntityDataSerializers.BOOLEAN);
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<ItemStack>[] DATA_LOAD = new EntityDataAccessor[PalletBlockEntity.MAX_DRUMS];
    static {
        for (int i = 0; i < DATA_LOAD.length; i++) DATA_LOAD[i] = SynchedEntityData.defineId(PalletTruckEntity.class, EntityDataSerializers.ITEM_STACK);
    }
    /** The driver holds H. */
    private static final EntityDataAccessor<Boolean> DATA_HORN = SynchedEntityData.defineId(PalletTruckEntity.class, EntityDataSerializers.BOOLEAN);

    private final List<ItemStack> drums = new ArrayList<>();
    private boolean hasPallet;
    private int cooldown;
    private boolean forksInPallet;

    public PalletTruckEntity(EntityType<? extends PalletTruckEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_DRIVER, -1);
        entityData.define(DATA_PALLET, false);
        for (EntityDataAccessor<ItemStack> item : DATA_LOAD) entityData.define(item, ItemStack.EMPTY);
        entityData.define(DATA_HORN, false);
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

    /** How many drums (or IBCs) are on the pallet it carries, or -1 without a pallet. */
    public int carriedDrums() { return entityData.get(DATA_PALLET) ? load().size() : -1; }

    /** What is on the pallet it carries, as every player sees it. */
    public List<ItemStack> load() {
        List<ItemStack> load = new ArrayList<>();
        for (EntityDataAccessor<ItemStack> item : DATA_LOAD) if (!entityData.get(item).isEmpty()) load.add(entityData.get(item));
        return load;
    }

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
            say(player, "You hold the tiller: walk and it follows. Back it into a pallet to pick it up. Hold H: horn.");
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
        if (cooldown > 0) cooldown--;
        Player driver = driver();
        if (driver != null && (driver.isRemoved() || driver.level() != level || driver.distanceTo(this) > REACH)) {
            setDriver(null);
            driver = null;
        }
        if (driver == null && hornOn()) setHorn(false);
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
        if (level() instanceof ServerLevel server && !hasPallet) {
            cooldown = 0;
            forksInPallet = false;
            couple(server, Vec3.ZERO);
        }
    }

    /** The pallet the forks are in, if any. */
    private @Nullable BlockPos palletAtForks(ServerLevel level, Vec3 step) {
        Vec3 back = forward().scale(-1);
        for (double along : new double[]{0.4, 0.9, 1.4}) {
            Vec3 at = position().add(step).add(back.scale(along)).add(0, 0.1, 0);
            BlockPos pos = BlockPos.containing(at);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof PalletBlock) return pos;
            if (state.getBlock() instanceof PalletPartBlock && level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part && part.base() != null) return part.base();
        }
        return null;
    }

    /** Forks running into a pallet (on its low deck): it comes up onto them, with what is on it. */
    private void couple(ServerLevel level, Vec3 step) {
        BlockPos base = palletAtForks(level, step);
        if (base == null) forksInPallet = false; // out of the pallet it just set down: free to take one up again
        if (cooldown > 0 || forksInPallet || base == null || !(level.getBlockEntity(base) instanceof PalletBlockEntity pallet)) return;
        drums.clear();
        drums.addAll(pallet.takeAll());
        level.removeBlock(base, false); // its parts go with it; the drums are on the forks now
        hasPallet = true;
        synced();
        level.playSound(null, base, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8f, 0.8f);
    }

    /** CTRL (or sneak-right-click): lower the forks and leave the pallet on the ground, square to the truck. */
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
        cooldown = COUPLE_COOLDOWN;
        forksInPallet = true;
        synced();
        level().playSound(null, base, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8f, 0.7f);
    }

    /** Tells the players what is on the forks, to draw it. */
    private void synced() {
        entityData.set(DATA_PALLET, hasPallet);
        for (int i = 0; i < DATA_LOAD.length; i++) entityData.set(DATA_LOAD[i], i < drums.size() ? drums.get(i).copy() : ItemStack.EMPTY);
    }

    @Override
    public boolean hornOn() { return entityData.get(DATA_HORN); }

    @Override
    public void setHorn(boolean on) { entityData.set(DATA_HORN, on); }

    /** The horn: one steady note while the driver holds H. */
    @Override
    public SoundEvent hornSound() { return SetpointContent.PALLET_TRUCK_HORN.get(); }

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
