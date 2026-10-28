package io.hookdev.setpoint.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.FloatingRoofTankBlock;
import io.hookdev.setpoint.block.VehicleModelBlock;
import io.hookdev.setpoint.block.entity.FloatingRoofTankBlockEntity;

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

import static io.hookdev.setpoint.block.entity.FloatingRoofTankBlockEntity.LADDER_ANGLE;
import static io.hookdev.setpoint.block.entity.FloatingRoofTankBlockEntity.LADDER_LENGTH;
import static io.hookdev.setpoint.block.entity.FloatingRoofTankBlockEntity.LADDER_PIVOT;
import static io.hookdev.setpoint.block.entity.FloatingRoofTankBlockEntity.LADDER_TOP;

/**
 * Draws a floating roof tank from its part models, all around its middle: 24 shell plates in three courses, the curb
 * angle, wind girder and handrail on top, the caged ladder and platform in the front-right corner, the inlet nozzle
 * at the front, and the roof (deck, pontoons, rim seal, manholes) at the height it floats, with the rolling ladder
 * from the rim down to it.
 */
public class FloatingRoofTankRenderer implements BlockEntityRenderer<FloatingRoofTankBlockEntity, FloatingRoofTankRenderer.State> {
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    private static final int SHELL = 0, TOP = 1, RAIL = 2, PONTOON = 3, DECK = 4, MANHOLE = 5, ROLLING_LADDER = 6, PLATFORM = 7, LADDER = 8,
            CAGE = 9, NOZZLE = 10, PARTS = 11;
    /** How far each part model was moved to fit a model's bounds (pixels; TANK_SHIFT in tools/gen-assets.mjs). */
    private static final float[][] SHIFT = { {0, 0, -60}, {0, -76, -56}, {0, -96, -56}, {0, 2, -40}, {0, 0, 0}, {0, 0, 0}, {0, 0, 16},
            {0, -80, -60}, {0, 0, -66}, {0, 0, -66}, {0, 0, -52} };
    private static final int SEGMENTS = 24;
    /** The plates around the rim with the rolling ladder's platform: no handrail there. */
    private static final int LADDER_SEGMENT = Math.round(LADDER_ANGLE / (360f / SEGMENTS));
    /** The deck plates (8 pixels square) under the roof, and where the manholes sit on it (pixels from the middle). */
    private static final List<float[]> DECK_TILES = new ArrayList<>();
    private static final float[][] MANHOLES = { {0, 0}, {-22, 14}, {18, -24} };
    static {
        for (int x = -60; x < 60; x += 8) for (int z = -60; z < 60; z += 8) {
            double far = Math.hypot(Math.max(Math.abs(x), Math.abs(x + 8)), Math.max(Math.abs(z), Math.abs(z + 8)));
            if (far < 60) DECK_TILES.add(new float[]{x + 4, z + 4});
        }
    }

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        float deck;
        final BlockModelRenderState[] parts = new BlockModelRenderState[PARTS];
        {
            for (int i = 0; i < parts.length; i++) parts[i] = new BlockModelRenderState();
        }
    }

    private final BlockModelResolver models;

    public FloatingRoofTankRenderer(BlockEntityRendererProvider.Context context) {
        this.models = context.blockModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FloatingRoofTankBlockEntity tank, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tank, state, partialTicks, cameraPosition, breakProgress);
        state.facing = tank.getBlockState().getValue(FloatingRoofTankBlock.FACING);
        state.deck = tank.shownDeck(partialTicks);
        for (int i = 0; i < PARTS; i++) {
            models.update(state.parts[i], SetpointContent.FLOATING_ROOF_TANK_MODEL.get().defaultBlockState().setValue(VehicleModelBlock.PART, i), DISPLAY);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // work as if the tank faces south, from its middle: this block is half a block right and three and a half in front of it
        poseStack.translate(0.5f, 0, 0.5f);
        McVersionClient.rotateDegrees(poseStack, Axis.YP, -state.facing.toYRot());
        poseStack.translate(-0.5f, 0, -3.5f);
        float step = 360f / SEGMENTS;
        for (int k = 0; k < SEGMENTS; k++) {
            for (int course = 0; course < 3; course++) part(state, SHELL, k * step, 0, course * 32, 0, poseStack, collector);
            part(state, TOP, k * step, 0, 0, 0, poseStack, collector);
            if (k != LADDER_SEGMENT) part(state, RAIL, k * step, 0, 0, 0, poseStack, collector);
            part(state, PONTOON, k * step, 0, state.deck, 0, poseStack, collector);
        }
        for (float[] tile : DECK_TILES) part(state, DECK, 0, tile[0], state.deck, tile[1], poseStack, collector);
        for (float[] at : MANHOLES) part(state, MANHOLE, 0, at[0], state.deck, at[1], poseStack, collector);
        // the corner: caged ladder up to a platform over the rim
        for (int course = 0; course < 3; course++) {
            part(state, LADDER, LADDER_ANGLE, 0, course * 32, 0, poseStack, collector);
            if (course > 0) part(state, CAGE, LADDER_ANGLE, 0, course * 32, 0, poseStack, collector);
        }
        part(state, PLATFORM, LADDER_ANGLE, 0, 0, 0, poseStack, collector);
        part(state, NOZZLE, 0, 8, 0, 0, poseStack, collector);
        // the rolling ladder: hinged at the platform, its wheels on the roof, steeper the lower the roof floats
        float drop = LADDER_TOP - state.deck, pitch = (float) Math.toDegrees(Math.asin(Math.min(1, drop / LADDER_LENGTH)));
        poseStack.pushPose();
        McVersionClient.rotateDegrees(poseStack, Axis.YP, LADDER_ANGLE);
        poseStack.translate(0, LADDER_TOP / 16, LADDER_PIVOT / 16);
        McVersionClient.rotateDegrees(poseStack, Axis.XP, -pitch);
        for (int piece = 0; piece < 3; piece++) part(state, ROLLING_LADDER, 0, 0, 0, -28 * piece, poseStack, collector);
        poseStack.popPose();
        poseStack.popPose();
    }

    /** One part model, turned this far about the middle of the tank, then put this far out and up (pixels). */
    private static void part(State state, int part, float angle, float x, float y, float z, PoseStack poseStack, SubmitNodeCollector collector) {
        float[] shift = SHIFT[part];
        poseStack.pushPose();
        if (angle != 0) McVersionClient.rotateDegrees(poseStack, Axis.YP, angle);
        poseStack.translate((x - 8 - shift[0]) / 16, (y - shift[1]) / 16, (z - 8 - shift[2]) / 16);
        state.parts[part].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /** Big: it must not vanish when its base block is off screen, and you see it from far away. */
    @Override
    public AABB getRenderBoundingBox(FloatingRoofTankBlockEntity tank) {
        Vec3 c = tank.centre();
        return new AABB(c.x - 5, c.y, c.z - 5, c.x + 5, c.y + 7.5, c.z + 5);
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
