package io.hookdev.setpoint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.GasSphereBlock;
import io.hookdev.setpoint.block.VehicleModelBlock;
import io.hookdev.setpoint.block.entity.GasSphereBlockEntity;

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

import static io.hookdev.setpoint.block.GasSphereBlock.CORNER_ANGLE;
import static io.hookdev.setpoint.block.GasSphereBlock.DECK;
import static io.hookdev.setpoint.block.GasSphereBlock.EQUATOR;
import static io.hookdev.setpoint.block.GasSphereBlock.LEG_RADIUS;
import static io.hookdev.setpoint.block.GasSphereBlock.RADIUS;

/**
 * Draws a gas sphere from its part models: the shell as 12 bands of 24 plates (each plate one model, stretched to its
 * size and turned to its place), the eight columns on their feet with cross bracing between them, the pipe from the
 * bottom of the sphere to the valve station at the front, and the caged ladder in the corner with its bridge to the
 * platform on top.
 */
public class GasSphereRenderer implements BlockEntityRenderer<GasSphereBlockEntity, GasSphereRenderer.State> {
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    private static final int PLATE = 0, LEG = 1, FOOT = 2, ROD = 3, BRIDGE = 4, PLATFORM = 5, PIPE_UP = 6, PIPE_ALONG = 7, STATION = 8,
            LADDER = 9, CAGE = 10, PARTS = 11;
    /** How far each part model was moved to fit a model's bounds (pixels; SPHERE_SHIFT in tools/gen-assets.mjs). */
    private static final float[][] SHIFT = { {0, 8, 0}, {0, 0, 0}, {0, 0, 0}, {0, 8, 0}, {0, 1, 16}, {0, 1, 0}, {0, 0, 0}, {0, 0, 0},
            {-8, 0, -8}, {0, 0, -66}, {0, 0, -66} };
    private static final int BANDS = 12, SEGMENTS = 24;
    /** The cross bracing runs from this high on one column to that high on the next (pixels). */
    private static final float BRACE_LOW = 10, BRACE_HIGH = 80;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        final BlockModelRenderState[] parts = new BlockModelRenderState[PARTS];
        {
            for (int i = 0; i < parts.length; i++) parts[i] = new BlockModelRenderState();
        }
    }

    private final BlockModelResolver models;

    public GasSphereRenderer(BlockEntityRendererProvider.Context context) {
        this.models = context.blockModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GasSphereBlockEntity sphere, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(sphere, state, partialTicks, cameraPosition, breakProgress);
        state.facing = sphere.getBlockState().getValue(GasSphereBlock.FACING);
        for (int i = 0; i < PARTS; i++) {
            models.update(state.parts[i], SetpointContent.GAS_SPHERE_MODEL.get().defaultBlockState().setValue(VehicleModelBlock.PART, i), DISPLAY);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        // work as if it faces south, from the middle of its footprint: this block is half a block right and three and a half in front
        pose.translate(0.5f, 0, 0.5f);
        pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
        pose.translate(-0.5f, 0, -3.5f);

        // the shell: bands of plates from the bottom to the top, each plate as wide as its wider edge
        float step = 180f / BANDS, turn = 360f / SEGMENTS;
        float height = (float) (2 * RADIUS * Math.sin(Math.toRadians(step / 2)));
        for (int band = 0; band < BANDS; band++) {
            float low = -90 + band * step, high = low + step, middle = (low + high) / 2;
            float wide = (float) Math.max(Math.cos(Math.toRadians(low)), Math.cos(Math.toRadians(high)));
            float width = (float) (2 * RADIUS * wide * Math.tan(Math.toRadians(turn / 2)));
            for (int k = 0; k < SEGMENTS; k++) {
                pose.pushPose();
                pose.rotateDegrees(Axis.YP, k * turn);
                pose.translate(0, EQUATOR / 16f, 0);
                pose.rotateDegrees(Axis.XP, -middle);
                pose.translate(0, 0, RADIUS / 16f);
                pose.scale(width / 16, height / 16, 1);
                part(state, PLATE, 0, 0, 0, pose, collector);
                pose.popPose();
            }
        }
        // the columns on their feet, and the cross bracing between each two
        for (int leg = 0; leg < 8; leg++) {
            double a = Math.toRadians(22.5 + 45 * leg), b = Math.toRadians(22.5 + 45 * (leg + 1));
            float x = (float) (LEG_RADIUS * Math.sin(a)), z = (float) (LEG_RADIUS * Math.cos(a));
            float nx = (float) (LEG_RADIUS * Math.sin(b)), nz = (float) (LEG_RADIUS * Math.cos(b));
            part(state, FOOT, x, 0, z, pose, collector);
            for (float y : new float[]{0, 32, EQUATOR - 32}) part(state, LEG, x, y, z, pose, collector);
            rod(state, x, BRACE_LOW, z, nx, BRACE_HIGH, nz, pose, collector);
            rod(state, nx, BRACE_LOW, nz, x, BRACE_HIGH, z, pose, collector);
        }
        // the pipe from the bottom of the sphere down and out to the valve station, and the station itself
        part(state, PIPE_UP, 8, 0, 8, pose, collector);
        part(state, PIPE_ALONG, 8, 0, 8, pose, collector);
        part(state, PIPE_ALONG, 8, 0, 32, pose, collector);
        part(state, STATION, 0, 0, 48, pose, collector); // built in the station block's own pixels
        // the caged ladder in the corner, up to the bridge to the platform on top
        pose.pushPose();
        pose.rotateDegrees(Axis.YP, CORNER_ANGLE);
        for (int course = 0; course < 5; course++) {
            part(state, LADDER, 0, course * 32, 0, pose, collector);
            if (course > 0) part(state, CAGE, 0, course * 32, 0, pose, collector);
        }
        for (int piece = 0; piece < 3; piece++) part(state, BRIDGE, 0, DECK, 72 - 24 * piece, pose, collector);
        part(state, PLATFORM, 0, DECK, 0, pose, collector);
        pose.popPose();
        pose.popPose();
    }

    /** A brace rod from one point to another (pixels): the rod model lies along z, one model long, so it is turned and stretched. */
    private static void rod(State state, float x1, float y1, float z1, float x2, float y2, float z2, PoseStack pose, SubmitNodeCollector collector) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1, flat = (float) Math.hypot(dx, dz), length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        pose.pushPose();
        pose.translate(x1 / 16, y1 / 16, z1 / 16);
        pose.rotateDegrees(Axis.YP, (float) Math.toDegrees(Math.atan2(dx, dz)));
        pose.rotateDegrees(Axis.XP, (float) -Math.toDegrees(Math.atan2(dy, flat)));
        pose.scale(1, 1, length / 16);
        part(state, ROD, 0, 0, 0, pose, collector);
        pose.popPose();
    }

    /** One part model, put this far out and up (pixels) in the current frame. */
    private static void part(State state, int part, float x, float y, float z, PoseStack pose, SubmitNodeCollector collector) {
        float[] shift = SHIFT[part];
        pose.pushPose();
        pose.translate((x - 8 - shift[0]) / 16, (y - shift[1]) / 16, (z - 8 - shift[2]) / 16);
        state.parts[part].submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(GasSphereBlockEntity sphere) {
        Vec3 c = sphere.centre();
        return new AABB(c.x - 5, c.y, c.z - 5, c.x + 5, c.y + 11, c.z + 5);
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
