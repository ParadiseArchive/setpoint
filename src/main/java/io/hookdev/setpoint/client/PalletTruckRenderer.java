package io.hookdev.setpoint.client;

import net.minecraft.world.item.ItemStack;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.PalletBlock;
import io.hookdev.setpoint.entity.PalletTruckEntity;

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
 * Draws the pallet truck from its block model, turned the way it faces, and the pallet it carries on its forks with
 * each drum in its own colour (or an IBC with its liquid).
 */
public class PalletTruckRenderer extends EntityRenderer<PalletTruckEntity, PalletTruckRenderer.State> {
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    /** Where the entity stands in the truck model (pixels): the middle of the truck, on the ground. */
    private static final float PIVOT_X = 8, PIVOT_Z = 8;
    /** The middle of the forks in the model, and how high a pallet rides on them (pixels). */
    private static final float FORKS_Z = 14.5f, LIFT = 4;

    public static class State extends EntityRenderState {
        float yaw;
        int drums = -1;
        final BlockModelRenderState truck = new BlockModelRenderState();
        final BlockModelRenderState pallet = new BlockModelRenderState();
    }

    private final BlockModelResolver models;

    public PalletTruckRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.models = context.getBlockModelResolver();
        this.shadowRadius = 0.7f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PalletTruckEntity truck, State state, float partialTicks) {
        super.extractRenderState(truck, state, partialTicks);
        state.yaw = Mth.rotLerp(partialTicks, truck.yRotO, truck.getYRot());
        models.update(state.truck, SetpointContent.PALLET_TRUCK_MODEL.get().defaultBlockState(), DISPLAY);
        state.drums = truck.carriedDrums();
        if (state.drums >= 0) {
            List<ItemStack> load = truck.load();
            models.update(state.pallet, PalletBlock.stateFor(Direction.NORTH, load), DISPLAY);
            PalletLook.tints(state.pallet.tintLayers(), load); // each drum in its colour, or the IBC with its liquid
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180 - state.yaw); // the model's tiller points north; turn it to where the truck faces
        poseStack.translate(-PIVOT_X / 16, 0, -PIVOT_Z / 16);
        state.truck.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        if (state.drums >= 0) { // the pallet model is two blocks square: its middle over the middle of the forks
            poseStack.pushPose();
            poseStack.translate((PIVOT_X - 16) / 16, LIFT / 16, (FORKS_Z - 16) / 16);
            state.pallet.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
