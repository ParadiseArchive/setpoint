package io.hookdev.setpoint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.PalletBlock;
import io.hookdev.setpoint.block.VehicleModelBlock;
import io.hookdev.setpoint.entity.ForkliftEntity;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Draws the forklift from its part models: the body, the three mast stages and the fork carriage, each lifted as far
 * as the forks are up, the two rear wheels turned as far as it steers, and the pallet it carries with each drum in its
 * own colour.
 */
public class ForkliftRenderer extends EntityRenderer<ForkliftEntity, ForkliftRenderer.State> {
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    private static final int BODY = 0, OUTER = 1, MIDDLE = 2, INNER = 3, CARRIAGE = 4, REAR_WHEEL = 5;
    /** How far each part model was moved to fit a model's bounds (pixels; FORKLIFT_SHIFT in tools/gen-assets.mjs). */
    private static final float[][] SHIFT = { {0, -2, 0}, {0, -14, 0}, {0, -14, 0}, {0, -14, 0}, {0, 0, 26} };
    /** The forks rise this far (blocks) on the carriage alone, before the mast starts to extend. */
    private static final float FREE_LIFT = 1.25f;
    /** Where the rear wheels sit (pixels from the middle: to the side, and back), and the middle of a pallet on the forks. */
    private static final float WHEEL_X = 6, WHEEL_Z = 12, PALLET_Z = 40;

    public static class State extends EntityRenderState {
        float yaw, lift, steer;
        int drums = -1;
        final BlockModelRenderState[] parts = new BlockModelRenderState[6];
        final BlockModelRenderState pallet = new BlockModelRenderState();
        {
            for (int i = 0; i < parts.length; i++) parts[i] = new BlockModelRenderState();
        }
    }

    private final BlockModelResolver models;

    public ForkliftRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.models = context.getBlockModelResolver();
        this.shadowRadius = 1.0f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ForkliftEntity forklift, State state, float partialTicks) {
        super.extractRenderState(forklift, state, partialTicks);
        state.yaw = Mth.rotLerp(partialTicks, forklift.yRotO, forklift.getYRot());
        state.lift = forklift.lift(partialTicks);
        state.steer = forklift.wheelAngle();
        for (int i = 0; i < state.parts.length; i++) {
            models.update(state.parts[i], SetpointContent.FORKLIFT_MODEL.get().defaultBlockState().setValue(VehicleModelBlock.PART, i), DISPLAY);
        }
        state.drums = forklift.carriedDrums();
        if (state.drums >= 0) { // the pallet's front towards the forklift
            models.update(state.pallet, SetpointContent.PALLET.get().defaultBlockState()
                    .setValue(PalletBlock.FACING, Direction.SOUTH).setValue(PalletBlock.DRUMS, state.drums), DISPLAY);
            IntList tints = state.pallet.tintLayers(); // each drum in its colour
            tints.clear();
            for (int i = 0; i < 4; i++) tints.add(forklift.drumColour(i));
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180 - state.yaw); // the models' forks point north; turn them to where the forklift faces
        float extend = Math.max(0, state.lift - FREE_LIFT);
        part(state, BODY, 0, poseStack, collector);
        part(state, OUTER, 0, poseStack, collector);
        part(state, MIDDLE, extend / 2, poseStack, collector);
        part(state, INNER, extend, poseStack, collector);
        part(state, CARRIAGE, state.lift, poseStack, collector);
        for (float side : new float[]{-1, 1}) { // the rear wheels, turned about their middles
            poseStack.pushPose();
            poseStack.translate(side * WHEEL_X / 16, 0, WHEEL_Z / 16);
            poseStack.rotateDegrees(Axis.YP, -state.steer);
            poseStack.translate(-0.5f, 0, -0.5f);
            state.parts[REAR_WHEEL].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
        if (state.drums >= 0) { // the south-facing pallet model has its middle at its block's corner
            poseStack.pushPose();
            poseStack.translate(0, state.lift, -PALLET_Z / 16);
            state.pallet.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** One part model, put back where it belongs in the forklift and raised this far (blocks). */
    private static void part(State state, int part, float up, PoseStack poseStack, SubmitNodeCollector collector) {
        float[] shift = SHIFT[part];
        poseStack.pushPose();
        poseStack.translate((-8 - shift[0]) / 16, up - shift[1] / 16, (-8 - shift[2]) / 16);
        state.parts[part].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
