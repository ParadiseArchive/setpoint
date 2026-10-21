package io.hookdev.setpoint.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.PidBoardBlock;
import io.hookdev.setpoint.block.entity.PidBoardBlockEntity;
import io.hookdev.setpoint.pid.BoardLayout;
import io.hookdev.setpoint.pid.PidDrawing;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws the P&ID over the whole panel, from its top-left board, like a map in an item frame. */
public class PidBoardRenderer implements BlockEntityRenderer<PidBoardBlockEntity, PidBoardRenderer.State> {
    /** How far in front of the board face the drawing floats, so it never flickers with it. */
    private static final float LIFT = 0.002f;
    /** Font pixels per texture pixel: the title is 9 font pixels high in a strip of 10 texture pixels, so about 1.3. */
    private static final float TITLE_SCALE_INVERSE = 1.3f;

    public static class State extends BlockEntityRenderState {
        @Nullable Identifier texture;
        @Nullable FormattedCharSequence title;
        int width, height;
        Direction facing = Direction.NORTH;
    }

    private final Font font;

    public PidBoardRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PidBoardBlockEntity board, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(board, state, partialTicks, cameraPosition, breakProgress);
        PidDrawing drawing = board.drawing();
        state.texture = null;
        if (drawing == null || board.getLevel() == null) return;
        state.facing = board.getBlockState().getValue(PidBoardBlock.FACING);
        int[] size = PidBoardBlock.size(board.getLevel(), board.getBlockPos(), state.facing);
        state.width = size[0];
        state.height = size[1];
        state.texture = PidBoardTextures.prepare(board, drawing, size[0], size[1]);
        int titleRoom = (int) ((size[0] * BoardLayout.PIXELS_PER_BLOCK - 4 * BoardLayout.MARGIN) * TITLE_SCALE_INVERSE);
        state.title = Language.getInstance().getVisualOrder(font.substrByWidth(Component.literal(drawing.name()), titleRoom));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.texture == null) return;
        poseStack.pushPose();
        // work as if the board faces south: then the panel runs right along +x and down along -y from this block
        poseStack.translate(0.5f, 0, 0.5f);
        poseStack.rotateDegrees(Axis.YP, -state.facing.toYRot());
        poseStack.translate(-0.5f, 0, -0.5f);
        float z = 1 / 16f + LIFT, top = 1, bottom = 1 - state.height, right = state.width;
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.text(state.texture), (pose, buffer) -> {
            buffer.addVertex(pose, 0, bottom, z).setColor(-1).setUv(0, 1).setLight(light);
            buffer.addVertex(pose, right, bottom, z).setColor(-1).setUv(1, 1).setLight(light);
            buffer.addVertex(pose, right, top, z).setColor(-1).setUv(1, 0).setLight(light);
            buffer.addVertex(pose, 0, top, z).setColor(-1).setUv(0, 0).setLight(light);
        });
        if (state.title != null) { // the name, in the title strip at the top
            float px = 1f / BoardLayout.PIXELS_PER_BLOCK, scale = px / TITLE_SCALE_INVERSE;
            poseStack.pushPose();
            poseStack.translate(2 * BoardLayout.MARGIN * px, top - (BoardLayout.MARGIN + 1.5f) * px, z + LIFT);
            poseStack.scale(scale, -scale, scale);
            collector.submitText(poseStack, 0, 0, state.title, false, Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT, 0xFFD9DEEA, 0, 0);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** The drawing reaches over the neighbouring boards, so it must not vanish when this block is just off screen. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(PidBoardBlockEntity board) {
        return new AABB(board.getBlockPos()).inflate(PidBoardBlock.MAX_WIDTH);
    }
}
