package io.hookdev.setpoint.client;

import java.util.Map;

import io.hookdev.setpoint.pid.PidDrawing;
import io.hookdev.setpoint.pid.PidLive;

/**
 * Colours a P&ID with live plant data, like a mimic panel: running equipment cyan, attention amber, alarms flashing
 * magenta, tags nobody can see grey, tanks filled to their level. Used by the console and the P&ID board.
 */
public record LiveStyle(Map<String, PidLive> live, boolean blinkOn) implements PidRenderer.Style {
    public static final int CYAN = 0xFF2DE2E6, AMBER = 0xFFFFB547, MAGENTA = 0xFFFF4F8B, MISSING = 0xFF5C6F92;

    @Override
    public int line(PidDrawing.Cell c) {
        if (c.tag().isEmpty()) return PidRenderer.LINE;
        PidLive l = live.get(c.tag());
        if (l == null) return MISSING;
        return switch (l.state()) {
            case PidLive.ALARM -> blinkOn ? MAGENTA : PidRenderer.LINE;
            case PidLive.ATTENTION -> AMBER;
            case PidLive.ACTIVE -> CYAN;
            default -> PidRenderer.LINE;
        };
    }

    @Override
    public int accent(PidDrawing.Cell c) {
        return !c.tag().isEmpty() && !live.containsKey(c.tag()) ? MISSING : PidRenderer.ACCENT;
    }

    @Override
    public float fill(PidDrawing.Cell c) {
        PidLive l = live.get(c.tag());
        return l == null || l.gauge() < 0 || c.symbol() != PidDrawing.TANK ? -1 : l.gauge() / 100f;
    }
}
