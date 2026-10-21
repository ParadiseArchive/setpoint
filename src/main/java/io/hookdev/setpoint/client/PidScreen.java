package io.hookdev.setpoint.client;

import java.util.ArrayList;
import java.util.List;

import io.hookdev.setpoint.network.PidRename;
import io.hookdev.setpoint.pid.PidDrawing;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * A P&ID: the name at the top (type to rename it), the drawing below on blueprint paper. Pipes run between the
 * symbols, dashed amber lines are signal wiring from controllers. Hover a symbol to see its tag.
 */
public class PidScreen extends Screen {
    private static final int MAX_W = 400, MAX_H = 270, PAPER_X = 8, PAPER_Y = 32;
    private static final int FRAME = 0xFF3A404C, PANEL = 0xFF1E222A, DIM = 0xFF7C869D;

    private final PidDrawing drawing;
    private final InteractionHand hand;
    private EditBox name;
    private int W, H, paperW, paperH;
    private int left, top;
    private PidRenderer.Layout layout;

    public PidScreen(PidDrawing drawing, InteractionHand hand) {
        super(Component.literal(drawing.name()));
        this.drawing = drawing;
        this.hand = hand;
    }

    @Override
    protected void init() {
        W = Math.min(MAX_W, width - 16);
        H = Math.min(MAX_H, height - 16);
        paperW = W - 2 * PAPER_X;
        paperH = H - PAPER_Y - 26;
        left = (width - W) / 2;
        top = (height - H) / 2;
        name = new EditBox(font, left + 8, top + 8, 214, 18, Component.literal("P&ID name"));
        name.setMaxLength(PidDrawing.MAX_NAME);
        name.setValue(drawing.name().equals(PidDrawing.UNTITLED) ? "" : drawing.name());
        name.setHint(Component.literal("Name this P&ID"));
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(left + W - 70, top + 7, 62, 20).build());
        setInitialFocus(name);

        int cell = PidRenderer.fitCell(drawing, paperW, paperH, 24);
        layout = PidRenderer.centred(drawing, left + PAPER_X, top + PAPER_Y, paperW, paperH, cell);
    }

    /** Send the new name when the screen closes, however it closes. */
    @Override
    public void removed() {
        super.removed();
        String typed = name.getValue();
        String current = drawing.name().equals(PidDrawing.UNTITLED) ? "" : drawing.name();
        if (!typed.strip().equals(current)) {
            ClientPacketDistributor.sendToServer(new PidRename(hand == InteractionHand.OFF_HAND ? 1 : 0, typed));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(left, top, left + W, top + H, FRAME);
        g.fill(left + 2, top + 2, left + W - 2, top + H - 2, PANEL);
        int px = left + PAPER_X, py = top + PAPER_Y;
        g.fill(px - 1, py - 1, px + paperW + 1, py + paperH + 1, 0xFF000000);
        g.fill(px, py, px + paperW, py + paperH, PidRenderer.BLUEPRINT);
        PidRenderer.draw(g, drawing, layout, PidRenderer.Style.PLAIN);

        g.text(font, drawing.width() + " x " + drawing.height() + "  ·  " + drawing.cells().size() + " symbols  ·  hover a symbol for its tag",
                left + 8, top + H - 18, DIM, false);
        String stamp = "SETPOINT P&ID";
        g.text(font, stamp, left + W - 8 - font.width(stamp), top + H - 18, PidRenderer.ACCENT, false);

        super.extractRenderState(g, mouseX, mouseY, a);

        PidDrawing.Cell hovered = PidRenderer.cellAt(drawing, layout, mouseX, mouseY);
        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            if (!hovered.tag().isEmpty()) lines.add(Component.literal(hovered.tag()));
            lines.add(Component.literal(PidDrawing.describe(hovered.symbol())));
            g.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
