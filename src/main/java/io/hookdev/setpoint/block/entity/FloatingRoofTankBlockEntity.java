package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.FloatingRoofTankBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static io.hookdev.setpoint.block.FloatingRoofTankBlock.DECK_HIGH;
import static io.hookdev.setpoint.block.FloatingRoofTankBlock.DECK_LOW;
import static io.hookdev.setpoint.block.FloatingRoofTankBlock.PONTOON;
import static io.hookdev.setpoint.block.FloatingRoofTankBlock.PONTOON_UP;
import static io.hookdev.setpoint.block.FloatingRoofTankBlock.WALL;

/**
 * What is inside a floating roof tank, and where its roof floats: the deck height in pixels, from the level. Players
 * get the deck height too, to draw the roof and to stand on it.
 */
public class FloatingRoofTankBlockEntity extends PortableTankBlockEntity {
    /** The rolling ladder: its length, where it hangs from the rim (pixels from the middle, and up), and its direction. */
    public static final float LADDER_LENGTH = 84, LADDER_PIVOT = 58, LADDER_TOP = 97, LADDER_ANGLE = 45;

    /** The top of the roof deck, in pixels up from the floor of the tank. */
    private int deck = DECK_LOW;
    /** Client side: the deck as drawn, catching up with the real one a little each tick. */
    private float shown = -1, shownBefore = -1;

    public FloatingRoofTankBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.FLOATING_ROOF_TANK_ENTITY.get(), pos, state);
    }

    public int deck() { return deck; }

    /** Where the deck floats for this level: on its legs when empty, just under the rim when full. */
    public static int deckFor(double fill) {
        return DECK_LOW + (int) Math.round(Math.clamp(fill, 0, 1) * (DECK_HIGH - DECK_LOW));
    }

    public float shownDeck(float partialTicks) {
        return shown < 0 ? deck : Mth.lerp(partialTicks, shownBefore, shown);
    }

    /** Hookonium steel takes no paint. */
    @Override
    public boolean dye(DyeColor dye) {
        return false;
    }

    public Vec3 centre() {
        return FloatingRoofTankBlock.centre(worldPosition, getBlockState());
    }

    /** Somewhere in pixels from the middle of the tank, turned so z points out of its front and x to its right. */
    private double[] local(Vec3 at) {
        Vec3 c = centre();
        Direction front = getBlockState().getValue(FloatingRoofTankBlock.FACING), right = front.getCounterClockWise();
        double wx = (at.x - c.x) * 16, wz = (at.z - c.z) * 16;
        return new double[]{wx * right.getStepX() + wz * right.getStepZ(), (at.y - worldPosition.getY()) * 16, wx * front.getStepX() + wz * front.getStepZ()};
    }

    /** How far the rolling ladder reaches in from where it hangs, along the floor (pixels). */
    public static double ladderRun(float deck) {
        double drop = LADDER_TOP - deck;
        return Math.sqrt(Math.max(0, LADDER_LENGTH * LADDER_LENGTH - drop * drop));
    }

    /** On the rolling ladder: close to it, between the roof and the rim. */
    public boolean onRollingLadder(Vec3 at) {
        double[] p = local(at);
        if (p[1] < deck - 4 || p[1] > LADDER_TOP + 12) return false;
        double ux = Math.sin(Math.toRadians(LADDER_ANGLE)), uz = Math.cos(Math.toRadians(LADDER_ANGLE));
        double along = Math.clamp(p[0] * ux + p[2] * uz, LADDER_PIVOT - ladderRun(deck), LADDER_PIVOT);
        return Math.hypot(p[0] - along * ux, p[2] - along * uz) <= 8;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FloatingRoofTankBlockEntity tank) {
        PortableTankBlockEntity.serverTick(level, pos, state, tank);
        int next = deckFor(tank.fillFraction());
        if (next == tank.deck) return;
        int was = tank.deck;
        tank.deck = next;
        if (next > was) tank.lift(was);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, FloatingRoofTankBlockEntity tank) {
        if (tank.shown < 0) tank.shown = tank.deck;
        tank.shownBefore = tank.shown;
        tank.shown += Math.clamp(tank.deck - tank.shown, -0.25f, 0.25f);
    }

    /**
     * The roof came up: whatever stands on it comes up with it, or it would sink into the roof. Each side moves what
     * it is in charge of (the server its mobs and items, a player's game that player).
     */
    private void lift(int was) {
        if (level == null) return;
        Vec3 c = centre();
        double floor = worldPosition.getY(), r = WALL / 16.0;
        AABB over = new AABB(c.x - r, floor + was / 16.0 - 0.1, c.z - r, c.x + r, floor + (deck + PONTOON_UP) / 16.0 + 0.1, c.z + r);
        for (Entity entity : level.getEntities((Entity) null, over, e -> e.isLocalInstanceAuthoritative() && !e.isPassenger())) {
            double px = Math.hypot(entity.getX() - c.x, entity.getZ() - c.z) * 16 + entity.getBbWidth() * 8;
            if (px - entity.getBbWidth() * 16 > WALL) continue;
            boolean pontoon = px >= PONTOON;
            double top = floor + (pontoon ? deck + PONTOON_UP : deck) / 16.0, before = floor + (pontoon ? was + PONTOON_UP : was) / 16.0;
            if (entity.getY() < top && entity.getY() >= before - 0.1) entity.setPos(entity.getX(), top, entity.getZ());
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("deck", deck);
        return tag;
    }

    @Override
    protected void readShownFluid(ValueInput input) {
        super.readShownFluid(input);
        int was = deck;
        deck = input.getIntOr("deck", deck);
        if (deck > was) lift(was);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        deck = deckFor(fillFraction());
    }
}
