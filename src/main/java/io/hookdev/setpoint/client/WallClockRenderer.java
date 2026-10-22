package io.hookdev.setpoint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.VehicleModelBlock;
import io.hookdev.setpoint.block.WallClockBlock;
import io.hookdev.setpoint.block.entity.WallClockBlockEntity;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Turns the hands of a wall clock to the time of day: the short one round in twelve hours, the long one every hour. */
public class WallClockRenderer implements BlockEntityRenderer<WallClockBlockEntity, WallClockRenderer.State> {
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    /** Part models of the control-room model block. */
    public static final int HOUR_HAND = 0, MINUTE_HAND = 1;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        float hours, minutes;
        final BlockModelRenderState hour = new BlockModelRenderState(), minute = new BlockModelRenderState();
    }

    private final BlockModelResolver models;

    public WallClockRenderer(BlockEntityRendererProvider.Context context) {
        this.models = context.blockModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WallClockBlockEntity clock, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(clock, state, partialTicks, cameraPosition, breakProgress);
        state.facing = clock.getBlockState().getValue(WallClockBlock.FACING);
        long dayTime = clock.getLevel() == null ? 6000 : clock.getLevel().getOverworldClockTime();
        double t = Math.floorMod(dayTime, 24_000L) + partialTicks;
        double hoursOfDay = (t / 1000 + 6) % 24; // tick 0 is six in the morning
        state.hours = (float) (hoursOfDay % 12);
        state.minutes = (float) ((hoursOfDay % 1) * 60);
        var base = SetpointContent.CONTROL_ROOM_MODEL.get().defaultBlockState();
        models.update(state.hour, base.setValue(VehicleModelBlock.PART, HOUR_HAND), DISPLAY);
        models.update(state.minute, base.setValue(VehicleModelBlock.PART, MINUTE_HAND), DISPLAY);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        // work as if the clock faces south, on the north side of its block
        pose.translate(0.5f, 0, 0.5f);
        pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
        pose.translate(-0.5f, 0, -0.5f);
        hand(state.hour, state.hours / 12 * 360, 0.0f, state, pose, collector);
        hand(state.minute, state.minutes / 60 * 360, 0.5f, state, pose, collector);
        pose.popPose();
    }

    /** A hand pointing up in its model, turned clockwise (as you look at the face) about the middle of the face. */
    private static void hand(BlockModelRenderState hand, float degrees, float lift, State state, PoseStack pose, SubmitNodeCollector collector) {
        pose.pushPose();
        pose.translate(0.5f, 0.5f, (1.5f + lift) / 16);
        pose.rotateDegrees(Axis.ZP, -degrees);
        pose.translate(-0.5f, -0.5f, 0);
        hand.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
