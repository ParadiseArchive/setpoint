package io.hookdev.setpoint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.PumpjackBlock;
import io.hookdev.setpoint.block.VehicleModelBlock;
import io.hookdev.setpoint.block.entity.PumpjackBlockEntity;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a pumpjack from its part models and makes it nod: the crank turns with the counterweights, the pitman arms
 * pull the back of the walking beam down and up, the beam rocks on the samson post, and the horsehead lifts the
 * polished rod out of the wellhead and lowers it again.
 */
public class PumpjackRenderer implements BlockEntityRenderer<PumpjackBlockEntity, PumpjackRenderer.State> {
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    private static final int FRAME = 0, POST = 1, BEAM_FRONT = 2, BEAM_BACK = 3, CRANK = 4, GEARBOX = 5, WELLHEAD = 6, ROD = 7, PARTS = 8;
    /** How far each part model was moved to fit a model's bounds (pixels; PUMPJACK_SHIFT in tools/gen-assets.mjs). */
    private static final float[][] SHIFT = { {0, 0, 32}, {0, -12, 32}, {0, 0, -12}, {0, 0, 12}, {0, 0, 0}, {0, 0, 44}, {0, 0, 0}, {0, 8, 0} };
    /** Where things turn, in pixels from the bottom middle of the wellhead (z towards the front): the beam's pivot, the crank's axle. */
    public static final float PIVOT_Y = 40, PIVOT_Z = -32, AXLE_Y = 20, AXLE_Z = -48;
    /** The beam reaches this far in front of its pivot to the horsehead, and this far behind to the pitman arms. */
    public static final float BEAM_FRONT_LENGTH = 33, BEAM_BACK_LENGTH = 20, CRANK_RADIUS = 8, TILT = 12, ARM_X = 7;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        float crank;
        final BlockModelRenderState[] parts = new BlockModelRenderState[PARTS];
        {
            for (int i = 0; i < parts.length; i++) parts[i] = new BlockModelRenderState();
        }
    }

    private final BlockModelResolver models;

    public PumpjackRenderer(BlockEntityRendererProvider.Context context) {
        this.models = context.blockModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PumpjackBlockEntity pumpjack, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(pumpjack, state, partialTicks, cameraPosition, breakProgress);
        state.facing = pumpjack.getBlockState().getValue(PumpjackBlock.FACING);
        state.crank = pumpjack.crank(partialTicks);
        for (int i = 0; i < PARTS; i++) {
            models.update(state.parts[i], SetpointContent.PUMPJACK_MODEL.get().defaultBlockState().setValue(VehicleModelBlock.PART, i), DISPLAY);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        // work as if it faces south (the wellhead in front, the rest behind it), from the bottom middle of the wellhead
        pose.translate(0.5f, 0, 0.5f);
        McVersionClient.rotateDegrees(pose, Axis.YP, -state.facing.toYRot());
        double phi = Math.toRadians(state.crank);
        float tilt = (float) (-TILT * Math.sin(phi)); // crank pin up: the back of the beam up, the horsehead down
        double t = Math.toRadians(tilt);
        part(state, FRAME, 0, 0, 0, pose, collector);
        part(state, POST, 0, 0, 0, pose, collector);
        part(state, GEARBOX, 0, 0, 0, pose, collector);
        part(state, WELLHEAD, 0, 0, 0, pose, collector);
        // the walking beam, rocking on the samson post
        pose.pushPose();
        pose.translate(0, PIVOT_Y / 16, PIVOT_Z / 16);
        McVersionClient.rotateDegrees(pose, Axis.XP, -tilt);
        part(state, BEAM_FRONT, 0, 0, 0, pose, collector);
        part(state, BEAM_BACK, 0, 0, 0, pose, collector);
        pose.popPose();
        // the cranks with their counterweights, and the pitman arms up to the back of the beam
        float pinZ = (float) (AXLE_Z + CRANK_RADIUS * Math.cos(phi)), pinY = (float) (AXLE_Y + CRANK_RADIUS * Math.sin(phi));
        float backZ = (float) (PIVOT_Z - BEAM_BACK_LENGTH * Math.cos(t)), backY = (float) (PIVOT_Y - BEAM_BACK_LENGTH * Math.sin(t));
        for (float x : new float[]{-ARM_X, ARM_X}) {
            pose.pushPose();
            pose.translate(x / 16, AXLE_Y / 16, AXLE_Z / 16);
            McVersionClient.rotateDegrees(pose, Axis.XP, 90 - state.crank);
            part(state, CRANK, 0, 0, 0, pose, collector);
            pose.popPose();
            rod(state, x, pinY, pinZ, x, backY, backZ, pose, collector);
        }
        // the polished rod, hanging from the horsehead into the wellhead
        float headY = (float) (PIVOT_Y + BEAM_FRONT_LENGTH * Math.sin(t)) - 10;
        rod(state, 0, 12, 0, 0, headY, 0, pose, collector);
        pose.popPose();
    }

    /** A rod from one point to another (pixels): the rod model lies along z, one model long, so it is turned and stretched. */
    private static void rod(State state, float x1, float y1, float z1, float x2, float y2, float z2, PoseStack pose, SubmitNodeCollector collector) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1, flat = (float) Math.hypot(dx, dz), length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        pose.pushPose();
        pose.translate(x1 / 16, y1 / 16, z1 / 16);
        McVersionClient.rotateDegrees(pose, Axis.YP, (float) Math.toDegrees(Math.atan2(dx, dz)));
        McVersionClient.rotateDegrees(pose, Axis.XP, (float) -Math.toDegrees(Math.atan2(dy, flat)));
        pose.scale(1, 1, length / 16);
        part(state, ROD, 0, 0, 0, pose, collector);
        pose.popPose();
    }

    private static void part(State state, int part, float x, float y, float z, PoseStack pose, SubmitNodeCollector collector) {
        float[] shift = SHIFT[part];
        pose.pushPose();
        pose.translate((x - 8 - shift[0]) / 16, (y - shift[1]) / 16, (z - 8 - shift[2]) / 16);
        state.parts[part].submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(PumpjackBlockEntity pumpjack) {
        return new AABB(pumpjack.getBlockPos()).inflate(4.5, 0, 4.5).expandTowards(0, 4, 0);
    }
}
