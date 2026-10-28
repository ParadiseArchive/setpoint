package io.hookdev.setpoint.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.OverviewScreenBlock;
import io.hookdev.setpoint.block.WallPanel;
import io.hookdev.setpoint.block.entity.OverviewScreenBlockEntity;

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

/** Draws a video wall from its top-left screen: the painted page over the whole wall, and its words in the game's font. */
public class OverviewScreenRenderer implements BlockEntityRenderer<OverviewScreenBlockEntity, OverviewScreenRenderer.State> {
    /** How far in front of the screens' faces the picture floats, so it never flickers with them. */
    private static final float LIFT = 0.002f;

    private record Text(float x, float y, FormattedCharSequence text, int colour) {}

    public static class State extends BlockEntityRenderState {
        @Nullable Identifier texture;
        final List<Text> texts = new ArrayList<>();
        int width, height;
        Direction facing = Direction.NORTH;
    }

    private final Font font;

    public OverviewScreenRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(OverviewScreenBlockEntity screen, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(screen, state, partialTicks, cameraPosition, breakProgress);
        state.texture = null;
        state.texts.clear();
        if (screen.getLevel() == null) return;
        state.facing = screen.getBlockState().getValue(OverviewScreenBlock.FACING);
        if (!OverviewScreenBlock.topLeft(screen.getLevel(), screen.getBlockPos(), state.facing).equals(screen.getBlockPos())) return;
        int[] size = OverviewScreenBlock.size(screen.getLevel(), screen.getBlockPos(), state.facing);
        state.width = size[0];
        state.height = size[1];
        OverviewScreenTextures.Prepared prepared = OverviewScreenTextures.prepare(screen, size[0], size[1]);
        state.texture = prepared.texture();
        for (OverviewScreenTextures.Label label : prepared.labels()) {
            int room = (int) (label.maxWidth() * OverviewScreenTextures.FONT_SCALE_INVERSE);
            if (room <= 0) continue;
            state.texts.add(new Text(label.x(), label.y(), Language.getInstance().getVisualOrder(font.substrByWidth(Component.literal(label.text()), room)), label.colour()));
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.texture == null) return;
        poseStack.pushPose();
        // work as if the screen faces south: then the wall runs right along +x and down along -y from this block
        poseStack.translate(0.5f, 0, 0.5f);
        McVersionClient.rotateDegrees(poseStack, Axis.YP, -state.facing.toYRot());
        poseStack.translate(-0.5f, 0, -0.5f);
        float z = 2 / 16f + LIFT, top = 1, bottom = 1 - state.height, right = state.width;
        int light = LightCoordsUtil.FULL_BRIGHT; // a screen: it glows, day and night
        collector.submitCustomGeometry(poseStack, RenderTypes.text(state.texture), (pose, buffer) -> {
            buffer.addVertex(pose, 0, bottom, z).setColor(-1).setUv(0, 1).setLight(light);
            buffer.addVertex(pose, right, bottom, z).setColor(-1).setUv(1, 1).setLight(light);
            buffer.addVertex(pose, right, top, z).setColor(-1).setUv(1, 0).setLight(light);
            buffer.addVertex(pose, 0, top, z).setColor(-1).setUv(0, 0).setLight(light);
        });
        float px = 1f / OverviewScreenTextures.PIXELS_PER_BLOCK, scale = px / OverviewScreenTextures.FONT_SCALE_INVERSE;
        for (Text text : state.texts) {
            poseStack.pushPose();
            poseStack.translate(text.x() * px, top - text.y() * px, z + LIFT);
            poseStack.scale(scale, -scale, scale);
            collector.submitText(poseStack, 0, 0, text.text(), false, Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT, text.colour(), 0, 0);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /** The picture reaches over the neighbouring screens, so it must not vanish when this block is just off screen. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(OverviewScreenBlockEntity screen) {
        return new AABB(screen.getBlockPos()).inflate(WallPanel.MAX_WIDTH);
    }
}
