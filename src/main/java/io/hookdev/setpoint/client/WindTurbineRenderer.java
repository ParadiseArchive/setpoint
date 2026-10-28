package io.hookdev.setpoint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.PowerCableBlock;
import io.hookdev.setpoint.block.VehicleModelBlock;
import io.hookdev.setpoint.block.WindTurbineBlock;
import io.hookdev.setpoint.block.entity.WindTurbineBlockEntity;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a wind turbine from its part models: the foot with its door, the tower narrowing block by block towards the
 * top, the nacelle, and the rotor in front of it, whose three blades are one short model each, stretched to their length.
 */
public class WindTurbineRenderer implements BlockEntityRenderer<WindTurbineBlockEntity, WindTurbineRenderer.State> {
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    private static final int FOOT = 0, TOWER = 1, NACELLE = 2, HUB = 3, BLADE = 4, GLAND = 5, PARTS = 6;
    /** The tower is this much narrower at the top than at the foot. */
    private static final float TAPER = 0.4f;
    /** The rotor's centre: this high above the top of the tower and this far in front of its middle, in pixels. */
    private static final float HUB_Y = 5, HUB_Z = 15;
    /** The blades' pitch, turned on their own length, and how far out from the centre they start (pixels). */
    private static final float PITCH = 12, ROOT = 3;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        float rotor;
        /** The sides of the foot (north, east, south, west) where a power cable comes in. */
        final boolean[] cables = new boolean[4];
        final BlockModelRenderState[] parts = new BlockModelRenderState[PARTS];
        {
            for (int i = 0; i < parts.length; i++) parts[i] = new BlockModelRenderState();
        }
    }

    private final BlockModelResolver models;

    public WindTurbineRenderer(BlockEntityRendererProvider.Context context) {
        this.models = context.blockModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WindTurbineBlockEntity turbine, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(turbine, state, partialTicks, cameraPosition, breakProgress);
        state.facing = turbine.getBlockState().getValue(WindTurbineBlock.FACING);
        state.rotor = turbine.rotor(partialTicks);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState next = turbine.getLevel() == null ? null : turbine.getLevel().getBlockState(turbine.getBlockPos().relative(side));
            state.cables[side.get2DDataValue()] = next != null && next.getBlock() instanceof PowerCableBlock
                    && next.getValue(PowerCableBlock.CONNECTIONS.get(side.getOpposite()));
        }
        for (int i = 0; i < PARTS; i++) {
            models.update(state.parts[i], SetpointContent.WIND_TURBINE_MODEL.get().defaultBlockState().setValue(VehicleModelBlock.PART, i), DISPLAY);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        // work as if it faces south (the rotor in front), from the bottom middle of the foot
        pose.translate(0.5f, 0, 0.5f);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!state.cables[side.get2DDataValue()]) continue;
            pose.pushPose();
            McVersionClient.rotateDegrees(pose, Axis.YP, -side.toYRot());
            part(state, GLAND, pose, collector);
            pose.popPose();
        }
        McVersionClient.rotateDegrees(pose, Axis.YP, -state.facing.toYRot());
        part(state, FOOT, pose, collector);
        for (int up = 1; up < WindTurbineBlock.TOWER; up++) {
            float width = 1 - TAPER * up / WindTurbineBlock.TOWER;
            pose.pushPose();
            pose.translate(0, up, 0);
            pose.scale(width, 1, width);
            part(state, TOWER, pose, collector);
            pose.popPose();
        }
        pose.translate(0, WindTurbineBlock.TOWER, 0);
        part(state, NACELLE, pose, collector);
        // the rotor, turning on its axle towards the front
        pose.translate(0, HUB_Y / 16, HUB_Z / 16);
        McVersionClient.rotateDegrees(pose, Axis.ZP, state.rotor);
        part(state, HUB, pose, collector);
        float length = WindTurbineBlock.BLADE - ROOT / 16;
        for (int blade = 0; blade < 3; blade++) {
            pose.pushPose();
            McVersionClient.rotateDegrees(pose, Axis.ZP, blade * 120);
            pose.translate(0, ROOT / 16, 1.5f / 16); // just in front of the spinner's back
            McVersionClient.rotateDegrees(pose, Axis.YP, PITCH);
            pose.scale(1, length, 1);
            part(state, BLADE, pose, collector);
            pose.popPose();
        }
        pose.popPose();
    }

    /** A part model, drawn from its pivot (its models are built round x = z = 8 pixels, y = 0). */
    private static void part(State state, int part, PoseStack pose, SubmitNodeCollector collector) {
        pose.pushPose();
        pose.translate(-0.5f, 0, -0.5f);
        state.parts[part].submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    /** Seen from far away, like the real thing. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(WindTurbineBlockEntity turbine) {
        BlockPos foot = turbine.getBlockPos();
        int reach = WindTurbineBlock.BLADE + 2;
        return new AABB(foot.getX() - reach, foot.getY(), foot.getZ() - reach, foot.getX() + reach + 1, foot.getY() + WindTurbineBlock.TOWER + reach, foot.getZ() + reach + 1);
    }

    @Override
    public int getViewDistance() {
        return 384;
    }
}
