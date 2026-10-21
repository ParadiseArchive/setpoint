package io.hookdev.setpoint.client;

import java.util.Map;

import io.hookdev.setpoint.pid.PidDrawing;
import io.hookdev.setpoint.pid.PidLive;

/**
 * Colours a P&ID with live plant data, like a mimic panel: running equipment cyan, attention amber, alarms flashing
 * magenta, tags nobody can see grey, tanks filled to their level, hand valves drawn open or shut as they stand.
 * Used by the console and the P&ID board.
 */
public record LiveStyle(Map<String, PidLive> live, boolean blinkOn) implements PidRenderer.Style {
    public static final int CYAN = 0xFF2DE2E6, AMBER = 0xFFFFB547, MAGENTA = 0xFFFF4F8B, MISSING = 0xFF5C6F92;

    @Override
    public int line(PidDrawing.Cell c) {
        PidLive l = live.get(c.liveKey());
        if (l == null) return c.tag().isEmpty() ? PidRenderer.LINE : MISSING; // an untagged valve out of reach stays as drawn
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
        return l == null || l.gauge() < 0 || !PidDrawing.isVessel(c.symbol()) ? -1 : l.gauge() / 100f;
    }

    @Override
    public int symbol(PidDrawing.Cell c) {
        if (c.symbol() != PidDrawing.HAND_VALVE && c.symbol() != PidDrawing.HAND_VALVE_SHUT) return c.symbol();
        PidLive l = live.get(c.liveKey());
        if (l == null) return c.symbol();
        return l.status().equals("SHUT") ? PidDrawing.HAND_VALVE_SHUT : PidDrawing.HAND_VALVE;
    }
}
