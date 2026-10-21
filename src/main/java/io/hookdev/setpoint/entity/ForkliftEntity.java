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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * An electric counterbalance forklift. Right-click to get in; W and S drive, A and D steer the rear wheels (so the
 * back swings out, like a real one), the arrow keys raise and lower the forks, holding H sounds the horn, sneak gets you out.
 * Lower the forks to a pallet, drive them in and raise: the pallet comes up, drums and all. Lower it onto the ground
 * or onto something solid at height and it stays there.
 * <p>
 * Like a boat, the driver's game moves it and the server checks; the forks are run by the server.
 */
public class ForkliftEntity extends VehicleEntity implements HornVehicle {
    /** From the middle of the forklift to its front axle (fixed wheels) and its rear axle (steering wheels), in blocks. */
    public static final double FRONT_AXLE = 0.875, REAR_AXLE = 0.75;
    /** From the middle of the forklift to the middle of a pallet on its forks, and the forks' reach, in blocks. */
    public static final double PALLET_AHEAD = 2.5, FORKS_FROM = 1.55, FORKS_TO = 3.05;
    /** How high the forks go, and how fast they go up and down, in blocks (a tick). */
    public static final float MAX_LIFT = 6, LIFT_SPEED = 0.1f, LOWER_SPEED = 0.12f;
    /** Top speed forward and back, and with the load up high (blocks a tick); how fast it speeds up, brakes, rolls out. */
    private static final double TOP_SPEED = 0.25, TOP_REVERSE = 0.18, TOP_LOAD_HIGH = 0.08, ACCELERATE = 0.012, BRAKE = 0.04, ROLL_OUT = 0.015;
    /** How far the rear wheels turn, and how fast (degrees, degrees a tick). */
    private static final float MAX_STEER = 60, STEER_RATE = 5;
    /** The seat, from the middle of the forklift (blocks: up, and back). */
    private static final double SEAT_UP = 0.85, SEAT_BACK = 0.25;

    private static final EntityDataAccessor<Float> DATA_LIFT = SynchedEntityData.defineId(ForkliftEntity.class, EntityDataSerializers.FLOAT);
    /** Whether it carries a pallet, and what is on it (the items: every player draws them, liquid and colour). */
    private static final EntityDataAccessor<Boolean> DATA_PALLET = SynchedEntityData.defineId(ForkliftEntity.class, EntityDataSerializers.BOOLEAN);
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<ItemStack>[] DATA_LOAD = new EntityDataAccessor[PalletBlockEntity.MAX_DRUMS];
    static {
        for (int i = 0; i < DATA_LOAD.length; i++) DATA_LOAD[i] = SynchedEntityData.defineId(ForkliftEntity.class, EntityDataSerializers.ITEM_STACK);
    }
    /** The driver holds H. */
    private static final EntityDataAccessor<Boolean> DATA_HORN = SynchedEntityData.defineId(ForkliftEntity.class, EntityDataSerializers.BOOLEAN);

    private final List<ItemStack> drums = new ArrayList<>();
    private boolean hasPallet;
    /** The arrow keys: 1 up, -1 down, 0 neither (server side). */
    private int liftInput;
    /** Driving, on the driver's side: speed along the forks (blocks a tick) and the rear wheels' angle (degrees, + is left). */
    private double speed;
    private float steer, deltaYaw;
    /** The forks' height a tick ago, for smooth drawing. */
    private float liftO;
    /** How far the rear wheels look turned (degrees, + is left), on every client. */
    private float wheelAngle;

    public ForkliftEntity(EntityType<? extends ForkliftEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_LIFT, 0f);
        entityData.define(DATA_PALLET, false);
        for (EntityDataAccessor<ItemStack> item : DATA_LOAD) entityData.define(item, ItemStack.EMPTY);
        entityData.define(DATA_HORN, false);
    }

    @Override
    protected Item getDropItem() {
        return SetpointContent.FORKLIFT.get();
    }

    /** How high the forks are (blocks above the wheels). */
    public float lift() { return entityData.get(DATA_LIFT); }

    /** The forks' height between the last tick and this one. */
    public float lift(float partialTicks) { return Mth.lerp(partialTicks, liftO, lift()); }

    private void setLift(float lift) { entityData.set(DATA_LIFT, Mth.clamp(lift, 0, MAX_LIFT)); }

    /** How many drums (or IBCs) are on the pallet it carries, or -1 without a pallet. */
    public int carriedDrums() { return entityData.get(DATA_PALLET) ? load().size() : -1; }

    /** What is on the pallet it carries, as every player sees it. */
    public List<ItemStack> load() {
        List<ItemStack> load = new ArrayList<>();
        for (EntityDataAccessor<ItemStack> item : DATA_LOAD) if (!entityData.get(item).isEmpty()) load.add(entityData.get(item));
        return load;
    }

    public boolean hasPallet() { return hasPallet; }

    /** The rear wheels' angle (degrees, + is steering left), on the driver's side. */
    public float steer() { return steer; }

    /** How far the rear wheels look turned (degrees, + is steering left): the driver's steering, or read from how it moves. */
    public float wheelAngle() { return wheelAngle; }

    /** The way the forks point. */
    public Vec3 forward() {
        return direction(getYRot());
    }

    private static Vec3 direction(float yaw) {
        return new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
    }

    /** The driver, if one sits in it. */
    public @Nullable Player driver() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return driver();
    }

    /** The arrow keys of the driver (from the client): 1 raises, -1 lowers, 0 holds. */
    public void setLiftInput(int input) {
        liftInput = Mth.clamp(input, -1, 1);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isSecondaryUseActive() || isVehicle()) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!player.startRiding(this)) return InteractionResult.PASS;
        say(player, "W/S: drive. A/D: steer. Arrow keys: forks up and down. Hold H: horn. Sneak: get out.");
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        liftInput = 0;
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }

    @Override
    public void tick() {
        if (getHurtTime() > 0) setHurtTime(getHurtTime() - 1);
        if (getDamage() > 0) setDamage(getDamage() - 1);
        liftO = lift();
        super.tick();
        if (isLocalInstanceAuthoritative()) {
            if (level().isClientSide()) drive();
            else rollOut();
            double fall = onGround() ? -0.01 : Math.max(-2, getDeltaMovement().y - 0.08);
            setDeltaMovement(getDeltaMovement().x, fall, getDeltaMovement().z);
            move(MoverType.SELF, getDeltaMovement());
        } else {
            setDeltaMovement(Vec3.ZERO);
            deltaYaw = 0;
        }
        if (level() instanceof ServerLevel server) forks(server);
        else showSteering();
    }

    /** On a client: the driver's own steering; for anyone watching, worked out from how the forklift turns as it rolls. */
    private void showSteering() {
        if (isLocalInstanceAuthoritative()) {
            wheelAngle = steer;
            return;
        }
        double rolled = new Vec3(getX() - xo, 0, getZ() - zo).dot(forward());
        if (Math.abs(rolled) < 0.005) return;
        double turned = Mth.wrapDegrees(getYRot() - yRotO) * Mth.DEG_TO_RAD;
        float angle = (float) (Math.asin(Mth.clamp(-turned * (FRONT_AXLE + REAR_AXLE) / rolled, -1, 1)) * Mth.RAD_TO_DEG);
        wheelAngle += (Mth.clamp(angle, -MAX_STEER, MAX_STEER) - wheelAngle) * 0.5f;
    }

    /** On the driver's side: the pedal and the steering, then where that takes the forklift. */
    private void drive() {
        Player driver = driver();
        float pedal = driver == null ? 0 : Math.signum(driver.zza), turn = driver == null ? 0 : Math.signum(driver.xxa);
        double top = lift() > 1.5f && hasPalletOnForks() ? TOP_LOAD_HIGH : TOP_SPEED;
        if (pedal > 0) speed = speed < 0 ? Math.min(0, speed + BRAKE) : Math.min(top, speed + ACCELERATE);
        else if (pedal < 0) speed = speed > 0 ? Math.max(0, speed - BRAKE) : Math.max(-Math.min(top, TOP_REVERSE), speed - ACCELERATE);
        else speed = Math.abs(speed) <= ROLL_OUT ? 0 : speed - Math.signum(speed) * ROLL_OUT;
        if (horizontalCollision) speed *= 0.5;
        float target = turn * MAX_STEER;
        steer += Mth.clamp(target - steer, -STEER_RATE, STEER_RATE);
        // the front wheels roll straight on; the rear wheels roll the way they are turned (turned right to go left)
        Vec3 ahead = forward();
        Vec3 front = position().add(ahead.scale(FRONT_AXLE)).add(ahead.scale(speed));
        Vec3 rear = position().subtract(ahead.scale(REAR_AXLE)).add(direction(getYRot() + steer).scale(speed));
        Vec3 axis = front.subtract(rear);
        float yaw = getYRot();
        if (axis.horizontalDistanceSqr() > 1.0E-6) yaw = (float) (Mth.atan2(-axis.x, axis.z) * Mth.RAD_TO_DEG);
        deltaYaw = Mth.wrapDegrees(yaw - getYRot());
        Vec3 to = front.subtract(direction(yaw).scale(FRONT_AXLE));
        setYRot(yaw);
        setDeltaMovement(to.x - getX(), getDeltaMovement().y, to.z - getZ());
    }

    /** Nobody drives: it stops. */
    private void rollOut() {
        speed = 0;
        steer = 0;
        deltaYaw = 0;
        setDeltaMovement(0, getDeltaMovement().y, 0);
    }

    private boolean hasPalletOnForks() {
        return carriedDrums() >= 0;
    }

    /** The forks go up or down; raised under a pallet they take it, lowered onto a floor they leave it there. */
    private void forks(ServerLevel level) {
        if (driver() == null) {
            liftInput = 0;
            if (hornOn()) setHorn(false);
        }
        if (liftInput > 0) {
            if (!hasPallet) pickUp(level);
            setLift(lift() + LIFT_SPEED);
        } else if (liftInput < 0) {
            float lowered = Math.max(0, lift() - LOWER_SPEED);
            if (hasPallet && setDown(level, lowered)) liftInput = 0;
            setLift(lowered);
        }
    }

    /** The world height of the forks' underside. */
    private double forkY() {
        return getY() + lift();
    }

    /** The pallet the forks are in, at the forks' height, if any. */
    private @Nullable BlockPos palletAtForks(Level level) {
        Vec3 ahead = forward();
        for (double along = FORKS_FROM + 0.15; along <= FORKS_TO; along += 0.4) {
            Vec3 at = position().add(ahead.scale(along));
            BlockPos pos = BlockPos.containing(at.x, forkY() + 0.05, at.z);
            BlockState state = level.getBlockState(pos);
            BlockPos base = null;
            if (state.getBlock() instanceof PalletBlock) base = pos;
            else if (state.getBlock() instanceof PalletPartBlock && level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part) base = part.base();
            if (base != null && Math.abs(forkY() - base.getY()) < 0.25) return base; // in its deck, not among its drums
        }
        return null;
    }

    /** Raising the forks in a pallet: it comes up onto them, with what is on it. */
    private void pickUp(ServerLevel level) {
        BlockPos base = palletAtForks(level);
        if (base == null || !(level.getBlockEntity(base) instanceof PalletBlockEntity pallet)) return;
        drums.clear();
        drums.addAll(pallet.takeAll());
        level.removeBlock(base, false); // its parts go with it; the drums are on the forks now
        hasPallet = true;
        synced();
        level.playSound(null, base, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8f, 0.8f);
    }

    /** Picks up a pallet the forks are in, if there is one (raising the forks does this). */
    public void pickUpNow() {
        if (level() instanceof ServerLevel server && !hasPallet) pickUp(server);
    }

    /**
     * Lowering a pallet to this height: once its underside reaches the top of a block layer with something solid under
     * all four corners (the ground, a shelf), it stays there, square to the forklift. True if it was set down.
     */
    private boolean setDown(ServerLevel level, float lowered) {
        double bottom = getY() + lowered;
        int y = Mth.floor(bottom + 0.05);
        if (bottom - y > 0.12) return false; // still above that layer's floor
        Vec3 centre = position().add(forward().scale(PALLET_AHEAD));
        Direction facing = Direction.fromYRot(getYRot()).getOpposite(); // its front towards the forklift
        BlockPos base = null;
        int minX = Mth.floor(centre.x - 0.5), minZ = Mth.floor(centre.z - 0.5);
        List<BlockPos> cells = List.of(new BlockPos(minX, y, minZ), new BlockPos(minX + 1, y, minZ), new BlockPos(minX, y, minZ + 1), new BlockPos(minX + 1, y, minZ + 1));
        for (BlockPos corner : cells) {
            List<BlockPos> all = new ArrayList<>(PalletBlock.footprint(corner, facing, false));
            all.add(corner);
            if (cells.containsAll(all)) base = corner;
        }
        if (base == null) return false;
        for (BlockPos cell : cells) {
            BlockPos under = cell.below();
            if (level.getBlockState(under).getCollisionShape(level, under).isEmpty()) return false; // nothing to stand on yet: keep lowering
        }
        List<BlockPos> room = new ArrayList<>(PalletBlock.footprint(base, facing, !drums.isEmpty()));
        room.add(base);
        for (BlockPos cell : room) {
            if (!level.getBlockState(cell).canBeReplaced()) {
                if (driver() != null) say(driver(), "No room to set the pallet down here.");
                return false;
            }
        }
        PalletBlock.place(level, base, facing, drums);
        drums.clear();
        hasPallet = false;
        synced();
        level.playSound(null, base, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8f, 0.7f);
        return true;
    }

    /** Lowers the forks all the way, setting a pallet down where it lands (the down arrow does this step by step). */
    public void lowerNow() {
        if (!(level() instanceof ServerLevel server)) return;
        for (float lift = lift(); lift > 0; lift = Math.max(0, lift - LOWER_SPEED)) {
            if (hasPallet && setDown(server, lift)) { setLift(lift); return; }
        }
        if (hasPallet) setDown(server, 0);
        setLift(0);
    }

    /** Sets the forks to a height at once (tests, and a forklift put down by its item). */
    public void raiseTo(float lift) {
        setLift(lift);
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

    /** The horn: one steady note while the driver holds H, lower than the pallet truck's. */
    @Override
    public SoundEvent hornSound() { return SetpointContent.FORKLIFT_HORN.get(); }

    private static void say(Player player, String text) {
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY), true);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0, SEAT_UP, -SEAT_BACK).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    /** Steering turns the driver with the forklift. */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        if (passenger.isLocalInstanceAuthoritative()) {
            passenger.setYRot(passenger.getYRot() + deltaYaw);
            passenger.setYHeadRot(passenger.getYHeadRot() + deltaYaw);
        }
        if (passenger instanceof LivingEntity living) living.setYBodyRot(getYRot());
    }

    /** Out to the left, where the step is, or else the right. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 ahead = forward(), left = new Vec3(ahead.z, 0, -ahead.x);
        for (Vec3 side : new Vec3[]{left, left.scale(-1)}) {
            Vec3 at = position().add(side.scale(1.2));
            BlockPos pos = BlockPos.containing(at.x, getY() + 0.5, at.z);
            for (BlockPos spot : new BlockPos[]{pos, pos.below(), pos.above()}) {
                double floor = level().getBlockFloorHeight(spot);
                if (!DismountHelper.isBlockFloorValid(floor)) continue;
                Vec3 target = new Vec3(at.x, spot.getY() + floor, at.z);
                if (DismountHelper.canDismountTo(level(), target, passenger, Pose.STANDING)) return target;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    /** Broken up: the forklift drops, and a pallet it carried, with its drums. */
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

    /** You bump into it like into a boat. */
    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return true;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return (other.canBeCollidedWith(this) || other.isPushable()) && !isPassengerOfSameVehicle(other);
    }

    /** It rolls up a step or a slab, not a full block. */
    @Override
    public float maxUpStep() {
        return 0.6f;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putFloat("lift", lift());
        output.putBoolean("has_pallet", hasPallet);
        if (!drums.isEmpty()) output.store("drums", ItemStack.CODEC.listOf(), drums);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        setLift(input.getFloatOr("lift", 0));
        hasPallet = input.getBooleanOr("has_pallet", false);
        drums.clear();
        drums.addAll(input.read("drums", ItemStack.CODEC.listOf()).orElse(List.of()));
        synced();
    }
}
