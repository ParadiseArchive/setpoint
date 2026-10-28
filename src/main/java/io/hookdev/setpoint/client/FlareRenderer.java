package io.hookdev.setpoint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.FlareBlock;
import io.hookdev.setpoint.block.VehicleModelBlock;
import io.hookdev.setpoint.block.entity.FlareBlockEntity;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static io.hookdev.setpoint.block.FlareBlock.DECKS;
import static io.hookdev.setpoint.block.FlareBlock.DECK_HALF;
import static io.hookdev.setpoint.block.FlareBlock.PANEL;
import static io.hookdev.setpoint.block.FlareBlock.STACK_RADIUS;
import static io.hookdev.setpoint.block.FlareBlock.TIP_BOTTOM;
import static io.hookdev.setpoint.block.FlareBlock.TIP_TOP;
import static io.hookdev.setpoint.block.FlareBlock.TOWER_TOP;
import static io.hookdev.setpoint.block.FlareBlock.legHalf;

/**
 * Draws a flare from its part models: the stack and its tip, the derrick round it (four legs banded red and white a
 * panel at a time, girts at every panel point, cross bracing on every face, guides holding the stack), the three
 * platforms with their handrails and red aircraft lights, the caged ladder, the knock-out drum with its pipe to the
 * stack, the ignition panel, and on top the flame: as big as the gas it burns, leaning with the wind, flickering.
 */
public class FlareRenderer implements BlockEntityRenderer<FlareBlockEntity, FlareRenderer.State> {
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    private static final int STACK = 0, TIP = 1, LEG = 2, LEG_RED = 3, ROD = 4, RAIL = 5, DECK = 6, DRUM = 7, HEAD = 8, PIPE = 9, STATION = 10,
            FLAME = 11, FLAME_CORE = 12, LAMP = 13, LADDER = 14, CAGE = 15, PARTS = 16;
    /** How far each part model was moved to fit a model's bounds (pixels; FLARE_SHIFT in tools/gen-assets.mjs). */
    private static final float[][] SHIFT = { {0, 0, 0}, {0, -16, 0}, {0, 8, 0}, {0, 8, 0}, {0, 8, 0}, {0, 8, 0}, {0, 2, 0}, {0, 0, 0},
            {0, 0, 0}, {0, 8, 0}, {-8, 0, -8}, {0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, -66}, {0, 0, -66} };

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        boolean lit;
        float load, lean, windYaw, time;
        final BlockModelRenderState[] parts = new BlockModelRenderState[PARTS];
        {
            for (int i = 0; i < parts.length; i++) parts[i] = new BlockModelRenderState();
        }
    }

    private final BlockModelResolver models;

    public FlareRenderer(BlockEntityRendererProvider.Context context) {
        this.models = context.blockModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FlareBlockEntity flare, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(flare, state, partialTicks, cameraPosition, breakProgress);
        state.facing = flare.getBlockState().getValue(FlareBlock.FACING);
        state.lit = flare.lit();
        state.load = flare.shownLoad(partialTicks);
        long time = flare.getLevel() == null ? 0 : flare.getLevel().getGameTime();
        state.time = time + partialTicks;
        state.windYaw = FlareBlockEntity.windYaw(time);
        state.lean = flare.getLevel() == null ? 0 : FlareBlockEntity.windLean(flare.getLevel(), time);
        for (int i = 0; i < PARTS; i++) {
            models.update(state.parts[i], SetpointContent.FLARE_MODEL.get().defaultBlockState().setValue(VehicleModelBlock.PART, i), DISPLAY);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        // work as if it faces south, from the middle of the stack: this block is the stack's seventh block in front of it
        pose.translate(0.5f, 0, 0.5f);
        pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
        pose.translate(0, 0, -FlareBlock.BACK);

        // the stack, a block at a time, and its tip
        for (int y = 0; y < TIP_BOTTOM; y += 16) part(state, STACK, 0, y, 0, pose, collector);
        part(state, TIP, 0, TIP_BOTTOM, 0, pose, collector);

        // the derrick, a panel at a time: four legs (red and white in turn, red at the top), cross bracing on each face
        int[][] corners = { {1, 1}, {-1, 1}, {-1, -1}, {1, -1} };
        for (int k = 0; k * PANEL < TOWER_TOP; k++) {
            float y0 = k * PANEL, y1 = y0 + PANEL, w0 = (float) legHalf(y0), w1 = (float) legHalf(y1);
            int leg = k % 2 == 1 ? LEG_RED : LEG;
            for (int c = 0; c < 4; c++) {
                int[] a = corners[c], b = corners[(c + 1) % 4];
                rod(state, leg, a[0] * w0, y0, a[1] * w0, a[0] * w1, y1, a[1] * w1, 1, pose, collector);
                rod(state, ROD, a[0] * w0, y0, a[1] * w0, b[0] * w1, y1, b[1] * w1, 1, pose, collector);
                rod(state, ROD, b[0] * w0, y0, b[1] * w0, a[0] * w1, y1, a[1] * w1, 1, pose, collector);
                rod(state, ROD, a[0] * w1, y1, a[1] * w1, b[0] * w1, y1, b[1] * w1, 1.5f, pose, collector); // the girt on top of the panel
            }
            // guides hold the stack in the middle at every panel point; on the east, where the ladder climbs, the guide
            // stops at the ladder's rail and holds that instead of running through the cage
            for (int[] d : new int[][]{ {1, 0}, {-1, 0}, {0, 1}, {0, -1} }) {
                float reach = d[0] == 1 ? LADDER_RAIL : w1;
                rod(state, ROD, d[0] * STACK_RADIUS, y1, d[1] * STACK_RADIUS, d[0] * reach, y1, d[1] * reach, 1, pose, collector);
            }
        }

        // the platforms, the ladder up through them, and red lights for aircraft at the corners of the top two
        for (int d = 0; d < DECKS.length; d++) platform(state, DECKS[d], DECK_HALF[d], d > 0, pose, collector);
        pose.pushPose();
        pose.translate(-62 / 16f, 0, 0);
        pose.rotateDegrees(Axis.YP, 90);
        for (int course = 0; course * 32 <= TOWER_TOP; course++) {
            part(state, LADDER, 0, course * 32, 0, pose, collector);
            boolean landing = course * 32 == DECKS[0] || course * 32 == DECKS[1] || course * 32 == DECKS[2];
            if (course > 0 && !landing) part(state, CAGE, 0, course * 32, 0, pose, collector);
        }
        pose.popPose();

        // the knock-out drum on its saddles, its two heads, the pipe from its back end up and over to the stack
        part(state, DRUM, 0, 0, 80, pose, collector);
        part(state, HEAD, 0, 0, FlareBlock.DRUM_FRONT, pose, collector);
        pose.pushPose();
        pose.translate(0, 0, FlareBlock.DRUM_BACK / 16f);
        pose.rotateDegrees(Axis.YP, 180);
        part(state, HEAD, 0, 0, 0, pose, collector);
        pose.popPose();
        pose.pushPose();
        pose.translate(0, (FlareBlock.DRUM_Y + 10) / 16f, FlareBlock.PIPE_Z / 16f);
        pose.rotateDegrees(Axis.XP, -90);
        part(state, PIPE, 0, 0, 0, pose, collector);
        pose.popPose();
        pose.pushPose();
        pose.translate(0, FlareBlock.PIPE_Y / 16f, FlareBlock.PIPE_Z / 16f);
        pose.rotateDegrees(Axis.YP, 180);
        for (int n = 0; n * 16 < FlareBlock.PIPE_Z; n++) part(state, PIPE, 0, 0, n * 16, pose, collector);
        pose.popPose();
        // the ignition panel, in its own block's pixels
        part(state, STATION, -8, 0, FlareBlock.BACK * 16 - 8, pose, collector);

        if (state.lit) flame(state, pose, collector);
        pose.popPose();
    }

    /** A deck of grating tiles round the stack, a gap for the ladder, the handrail along its edge. */
    private static void platform(State state, int y, int half, boolean lights, PoseStack pose, SubmitNodeCollector collector) {
        for (int x = -half; x < half; x += 8) for (int z = -half; z < half; z += 8) {
            float cx = x + 4, cz = z + 4;
            if (Math.abs(cx) < 8 && Math.abs(cz) < 8) continue;          // the stack goes through
            if (cx > 8 && cx < 24 && Math.abs(cz) < 8) continue;         // the ladder comes up
            part(state, DECK, cx, y, cz, pose, collector);
        }
        int[][] corners = { {1, 1}, {-1, 1}, {-1, -1}, {1, -1} };
        float h = half - 0.5f;
        for (int c = 0; c < 4; c++) {
            int[] a = corners[c], b = corners[(c + 1) % 4];
            for (int k = 0; k < 6; k++) { // posts along the side
                float t = k / 6f, x = h * (a[0] + (b[0] - a[0]) * t), z = h * (a[1] + (b[1] - a[1]) * t);
                rod(state, RAIL, x, y, z, x, y + 12, z, 1, pose, collector);
            }
            for (float rail : new float[]{6, 12}) rod(state, RAIL, a[0] * h, y + rail, a[1] * h, b[0] * h, y + rail, b[1] * h, 1, pose, collector);
            if (lights) part(state, LAMP, a[0] * h, y + 12, a[1] * h, pose, collector);
        }
    }

    /** The flame on the tip: crossed sheets of fire stretched to its size, a bright core inside, leaning downwind. */
    private static void flame(State state, PoseStack pose, SubmitNodeCollector collector) {
        float t = state.time;
        float height = FlareBlockEntity.flameHeight(state.load) * (1 + 0.06f * (float) Math.sin(t * 0.9) + 0.04f * (float) Math.sin(t * 2.3 + 1));
        float width = FlareBlockEntity.flameWidth(height) * (1 + 0.05f * (float) Math.sin(t * 1.7));
        pose.pushPose();
        pose.translate(0, TIP_TOP / 16f, 0);
        pose.rotateDegrees(Axis.YP, state.facing.toYRot());  // back to the world, where the wind blows
        pose.rotateDegrees(Axis.YP, -state.windYaw);
        pose.rotateDegrees(Axis.XP, state.lean);
        pose.rotateDegrees(Axis.YP, t * 0.7f);               // turn slowly, so the sheets of fire never line up
        pose.scale(width, height, width);
        part(state, FLAME, 0, 0, 0, pose, collector);
        pose.scale(0.55f, 0.6f, 0.55f);
        pose.rotateDegrees(Axis.YP, 30);
        part(state, FLAME_CORE, 0, 0, 0, pose, collector);
        pose.popPose();
    }

    /** A rod-like part from one point to another (pixels): its model lies along z, one block long, so it is turned and stretched. */
    private static void rod(State state, int model, float x1, float y1, float z1, float x2, float y2, float z2, float thick,
                            PoseStack pose, SubmitNodeCollector collector) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1, flat = (float) Math.hypot(dx, dz), length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        pose.pushPose();
        pose.translate(x1 / 16, y1 / 16, z1 / 16);
        pose.rotateDegrees(Axis.YP, (float) Math.toDegrees(Math.atan2(dx, dz)));
        pose.rotateDegrees(Axis.XP, (float) -Math.toDegrees(Math.atan2(dy, flat)));
        pose.scale(thick, thick, length / 16);
        part(state, model, 0, 0, 0, pose, collector);
        pose.popPose();
    }

    /** The ladder's rails stand this far east of the stack's middle (pixels); its cage reaches out to 19. */
    private static final float LADDER_RAIL = 9;

    /** One part model, put this far out and up (pixels) in the current frame. */
    private static void part(State state, int part, float x, float y, float z, PoseStack pose, SubmitNodeCollector collector) {
        float[] shift = SHIFT[part];
        pose.pushPose();
        pose.translate((x - 8 - shift[0]) / 16, (y - shift[1]) / 16, (z - 8 - shift[2]) / 16);
        state.parts[part].submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    /** Seen from far away and from below the horizon, like the real thing. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(FlareBlockEntity flare) {
        BlockPos stack = flare.stack();
        return new AABB(stack.getX() - 8, stack.getY(), stack.getZ() - 8, stack.getX() + 9, stack.getY() + FlareBlock.HIGH + 18, stack.getZ() + 9);
    }

    @Override
    public int getViewDistance() {
        return 384;
    }
}
