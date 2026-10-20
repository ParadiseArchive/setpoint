package io.hookdev.setpoint.physics;

/**
 * Centrifugal pump in a simple suction/discharge system. Pure maths, no Minecraft classes,
 * so it can be unit tested on its own.
 *
 * <p>Game units: 1 block = 1 m, flow in mB per tick. The numbers are tuned for gameplay but the
 * shapes are real: a quadratic pump curve, quadratic pipe friction, and cavitation when the
 * available NPSH drops below what the pump needs.
 */
public final class PumpModel {
    /** Head at zero flow (shutoff head) of a new pump, in m. */
    public static final double SHUTOFF_HEAD = 12.0;
    /** Flow at which the curve reaches zero head (run-out), in mB/t. */
    public static final double MAX_FLOW = 120.0;
    /** Pipe friction: head loss per metre of pipe is FRICTION * Q^2. */
    public static final double FRICTION = 4.0e-5;
    /** Atmospheric pressure as head of water, in m. */
    public static final double ATMOSPHERIC_HEAD = 10.3;
    /** Vapour pressure of water at ambient temperature as head, in m. */
    public static final double VAPOUR_HEAD = 0.25;
    /** NPSH the pump needs at zero flow, plus the extra it needs at run-out, in m. */
    public static final double NPSH_REQUIRED_BASE = 2.0;
    public static final double NPSH_REQUIRED_RISE = 6.0;
    /** NPSH shortfall (m) at which cavitation is at full strength. */
    public static final double CAVITATION_FULL_SHORTFALL = 2.0;
    /** How much of the flow cavitation can take away at full strength. */
    public static final double CAVITATION_FLOW_LOSS = 0.6;
    /** A fully worn impeller loses this fraction of its head. */
    public static final double WEAR_HEAD_LOSS = 0.3;

    public enum State {
        /** Pumping normally. */
        OK,
        /** Pumping, but the suction side cannot keep up. */
        CAVITATING,
        /** Nothing to pump: no source, or the source is empty. */
        DRY_RUN,
        /** Nowhere to pump to, or the pump cannot beat the static head. */
        DEADHEAD
    }

    /**
     * @param suctionSurface   liquid surface of the source, relative to the pump centreline (m, negative = suction lift)
     * @param dischargeSurface liquid surface of the destination, relative to the pump centreline (m)
     * @param suctionLength    pipe length on the suction side (m)
     * @param dischargeLength  pipe length on the discharge side (m)
     * @param hasSource        a source with liquid is connected
     * @param hasDestination   a destination with room is connected
     * @param wear             impeller wear, 0 (new) to 1 (worn out)
     */
    public record Inputs(double suctionSurface, double dischargeSurface, double suctionLength, double dischargeLength,
                         boolean hasSource, boolean hasDestination, double wear) {}

    /**
     * @param flow       mB/t
     * @param head       head the pump delivers at this flow (m)
     * @param npshA      NPSH available (m)
     * @param npshR      NPSH required (m)
     * @param cavitation 0 (none) to 1 (full)
     * @param power      energy use in FE/t
     */
    public record Result(State state, double flow, double head, double npshA, double npshR, double cavitation, int power) {}

    private PumpModel() {}

    public static double shutoffHead(double wear) {
        return SHUTOFF_HEAD * (1.0 - WEAR_HEAD_LOSS * clamp(wear, 0, 1));
    }

    /** Head the pump delivers at a given flow. */
    public static double curveHead(double flow, double wear) {
        double ratio = flow / MAX_FLOW;
        return Math.max(0, shutoffHead(wear) * (1.0 - ratio * ratio));
    }

    public static double npshRequired(double flow) {
        double ratio = flow / MAX_FLOW;
        return NPSH_REQUIRED_BASE + NPSH_REQUIRED_RISE * ratio * ratio;
    }

    public static double npshAvailable(double suctionSurface, double suctionLength, double flow) {
        return ATMOSPHERIC_HEAD + suctionSurface - FRICTION * suctionLength * flow * flow - VAPOUR_HEAD;
    }

    public static int power(double flow, double head) {
        return 4 + (int) Math.round(flow * head / 40.0);
    }

    public static Result solve(Inputs in) {
        double h0 = shutoffHead(in.wear());
        if (!in.hasSource()) {
            return new Result(State.DRY_RUN, 0, h0, 0, npshRequired(0), 1.0, power(0, h0));
        }
        if (!in.hasDestination()) {
            return new Result(State.DEADHEAD, 0, h0, npshAvailable(in.suctionSurface(), in.suctionLength(), 0), npshRequired(0), 0, power(0, h0));
        }

        // Operating point: pump curve h0 * (1 - (Q/Qmax)^2) meets system curve static + k * Q^2.
        double staticHead = in.dischargeSurface() - in.suctionSurface();
        double k = FRICTION * (in.suctionLength() + in.dischargeLength());
        double flow = 0;
        if (h0 > staticHead) {
            flow = Math.sqrt((h0 - staticHead) / (h0 / (MAX_FLOW * MAX_FLOW) + k));
        }
        if (flow <= 0) {
            return new Result(State.DEADHEAD, 0, h0, npshAvailable(in.suctionSurface(), in.suctionLength(), 0), npshRequired(0), 0, power(0, h0));
        }

        double npshA = npshAvailable(in.suctionSurface(), in.suctionLength(), flow);
        double npshR = npshRequired(flow);
        double cavitation = 0;
        State state = State.OK;
        if (npshA < npshR) {
            cavitation = clamp((npshR - npshA) / CAVITATION_FULL_SHORTFALL, 0, 1);
            flow *= 1.0 - CAVITATION_FLOW_LOSS * cavitation;
            state = State.CAVITATING;
        }
        double head = curveHead(flow, in.wear());
        return new Result(state, flow, head, npshA, npshR, cavitation, power(flow, head));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
