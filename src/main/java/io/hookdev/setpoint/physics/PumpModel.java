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
     * @param suctionValveK    extra resistance of valves in the suction line (head = k * Q^2), infinite when one is shut
     * @param dischargeValveK  same for the discharge line
     */
    /**
     * @param density   how heavy the liquid is compared to water (lava: 3)
     * @param viscosity how thick it is compared to water (lava: 6)
     */
    public record Inputs(double suctionSurface, double dischargeSurface, double suctionLength, double dischargeLength,
                         boolean hasSource, boolean hasDestination, double wear, double suctionValveK, double dischargeValveK,
                         double density, double viscosity) {
        /** Water. */
        public Inputs(double suctionSurface, double dischargeSurface, double suctionLength, double dischargeLength,
                      boolean hasSource, boolean hasDestination, double wear, double suctionValveK, double dischargeValveK) {
            this(suctionSurface, dischargeSurface, suctionLength, dischargeLength, hasSource, hasDestination, wear, suctionValveK, dischargeValveK, 1, 1);
        }

        /** Water, in a line without valves. */
        public Inputs(double suctionSurface, double dischargeSurface, double suctionLength, double dischargeLength,
                      boolean hasSource, boolean hasDestination, double wear) {
            this(suctionSurface, dischargeSurface, suctionLength, dischargeLength, hasSource, hasDestination, wear, 0, 0);
        }
    }

    /** Pipe friction grows with how thick the liquid is (roughly with the square root, between laminar and turbulent). */
    public static double frictionFactor(double viscosity) {
        return Math.sqrt(clamp(viscosity, 0.1, 100));
    }

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
        return npshAvailable(suctionSurface, suctionLength, 0, flow);
    }

    public static double npshAvailable(double suctionSurface, double suctionLength, double suctionValveK, double flow) {
        return npshAvailable(suctionSurface, suctionLength, suctionValveK, flow, 1, 1);
    }

    /** The air pushes a heavy liquid up less far: atmospheric pressure as head is 10.3 m of water but 3.4 m of lava. */
    public static double npshAvailable(double suctionSurface, double suctionLength, double suctionValveK, double flow, double density, double viscosity) {
        double sg = clamp(density, 0.1, 20);
        return (ATMOSPHERIC_HEAD - VAPOUR_HEAD) / sg + suctionSurface
                - (FRICTION * suctionLength * frictionFactor(viscosity) + suctionValveK) * flow * flow;
    }

    public static int power(double flow, double head) {
        return power(flow, head, 1);
    }

    /** A heavier liquid takes more power to lift the same height. */
    public static int power(double flow, double head, double density) {
        return 4 + (int) Math.round(flow * head * clamp(density, 0.1, 20) / 40.0);
    }

    public static Result solve(Inputs in) {
        double h0 = shutoffHead(in.wear());
        double sg = in.density(), mu = in.viscosity();
        // A shut suction valve starves the pump just like an empty source.
        if (!in.hasSource() || Double.isInfinite(in.suctionValveK())) {
            return new Result(State.DRY_RUN, 0, h0, 0, npshRequired(0), 1.0, power(0, h0));
        }
        if (!in.hasDestination() || Double.isInfinite(in.dischargeValveK())) {
            return new Result(State.DEADHEAD, 0, h0, npshAvailable(in.suctionSurface(), in.suctionLength(), 0, 0, sg, mu), npshRequired(0), 0, power(0, h0, sg));
        }

        // Operating point: pump curve h0 * (1 - (Q/Qmax)^2) meets system curve static + k * Q^2.
        // A thick liquid rubs harder along the pipe walls; the valves throttle the same.
        double staticHead = in.dischargeSurface() - in.suctionSurface();
        double k = FRICTION * (in.suctionLength() + in.dischargeLength()) * frictionFactor(mu) + in.suctionValveK() + in.dischargeValveK();
        double flow = 0;
        if (h0 > staticHead) {
            flow = Math.sqrt((h0 - staticHead) / (h0 / (MAX_FLOW * MAX_FLOW) + k));
        }
        if (flow <= 0) {
            return new Result(State.DEADHEAD, 0, h0, npshAvailable(in.suctionSurface(), in.suctionLength(), 0, 0, sg, mu), npshRequired(0), 0, power(0, h0, sg));
        }

        double npshA = npshAvailable(in.suctionSurface(), in.suctionLength(), in.suctionValveK(), flow, sg, mu);
        double npshR = npshRequired(flow);
        double cavitation = 0;
        State state = State.OK;
        if (npshA < npshR) {
            cavitation = clamp((npshR - npshA) / CAVITATION_FULL_SHORTFALL, 0, 1);
            flow *= 1.0 - CAVITATION_FLOW_LOSS * cavitation;
            state = State.CAVITATING;
        }
        double head = curveHead(flow, in.wear());
        return new Result(state, flow, head, npshA, npshR, cavitation, power(flow, head, sg));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
