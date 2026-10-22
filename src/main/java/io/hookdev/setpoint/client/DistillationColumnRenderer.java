package io.hookdev.setpoint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.DistillationColumnBlock;
import io.hookdev.setpoint.block.VehicleModelBlock;
import io.hookdev.setpoint.block.entity.DistillationColumnBlockEntity;

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

import static io.hookdev.setpoint.block.DistillationColumnBlock.COLUMN_Z;

/**
 * Draws a distillation column from its part models: the round shell in five courses of 24 plates, its domed head, two
 * platform rings, the reboiler on the front row, the condenser on top, and every nozzle with the colour band of what
 * flows through it.
 */
public class DistillationColumnRenderer implements BlockEntityRenderer<DistillationColumnBlockEntity, DistillationColumnRenderer.State> {
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    private static final int SHELL = 0, HEAD = 1, RING = 2, REBOILER = 3, CONDENSER = 4, NOZZLE = 5, PARTS = 13;
    /** Nozzle models in the order of their bands: feed, steam, residue, diesel, kerosene, naphtha, cooling water, LPG. */
    private static final int FEED = 0, STEAM = 1, RESIDUE = 2, DIESEL = 3, KEROSENE = 4, NAPHTHA = 5, WATER = 6, LPG = 7;
    /** How far each part model was moved to fit a model's bounds (pixels; COLUMN_SHIFT in tools/gen-assets.mjs). */
    private static final float[][] SHIFT = { {0, 0, 0}, {0, 0, 0}, {0, 0, -8}, {0, 0, 0}, {0, 0, 0}, {0, 8, 0} };

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        final BlockModelRenderState[] parts = new BlockModelRenderState[PARTS];
        {
            for (int i = 0; i < parts.length; i++) parts[i] = new BlockModelRenderState();
        }
    }

    private final BlockModelResolver models;

    public DistillationColumnRenderer(BlockEntityRendererProvider.Context context) {
        this.models = context.blockModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DistillationColumnBlockEntity column, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(column, state, partialTicks, cameraPosition, breakProgress);
        state.facing = column.getBlockState().getValue(DistillationColumnBlock.FACING);
        for (int i = 0; i < PARTS; i++) {
            models.update(state.parts[i], SetpointContent.COLUMN_MODEL.get().defaultBlockState().setValue(VehicleModelBlock.PART, i), DISPLAY);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        // work as if it faces south, from the bottom middle of the feed block
        pose.translate(0.5f, 0, 0.5f);
        pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
        for (int k = 0; k < 24; k++) {
            pose.pushPose();
            pose.translate(0, 0, COLUMN_Z / 16f);
            pose.rotateDegrees(Axis.YP, k * 15f);
            for (int course = 0; course < 5; course++) part(state, SHELL, 0, course * 32, 0, pose, collector);
            for (int y : new int[]{64, 128}) part(state, RING, 0, y, 0, pose, collector);
            pose.popPose();
        }
        part(state, HEAD, 0, 160, COLUMN_Z, pose, collector);
        part(state, CONDENSER, 0, 160, COLUMN_Z, pose, collector);
        part(state, REBOILER, 0, 0, 0, pose, collector);
        nozzle(state, FEED, 0, 8, -4, pose, collector);
        nozzle(state, STEAM, -16, 8, -4, pose, collector);
        nozzle(state, RESIDUE, 16, 8, -4, pose, collector);
        nozzle(state, DIESEL, 0, 56, -20, pose, collector);
        nozzle(state, KEROSENE, 0, 88, -20, pose, collector);
        nozzle(state, NAPHTHA, 0, 120, -20, pose, collector);
        nozzle(state, WATER, -16, 152, -20, pose, collector);
        nozzle(state, LPG, 16, 152, -20, pose, collector);
        pose.popPose();
    }

    private static void nozzle(State state, int which, float x, float y, float z, PoseStack pose, SubmitNodeCollector collector) {
        float[] shift = SHIFT[NOZZLE];
        pose.pushPose();
        pose.translate((x - 8 - shift[0]) / 16, (y - shift[1]) / 16, (z - 8 - shift[2]) / 16);
        state.parts[NOZZLE + which].submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
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
    public AABB getRenderBoundingBox(DistillationColumnBlockEntity column) {
        return new AABB(column.getBlockPos()).inflate(4.5, 0, 4.5).expandTowards(0, 12, 0);
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
