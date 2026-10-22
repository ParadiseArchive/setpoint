package io.hookdev.setpoint.physics;

/**
 * A reciprocating gas compressor. Each stroke takes in a fixed volume of gas at the suction pressure, so the denser the
 * gas coming in, the more it moves; squeezing it up to the discharge pressure takes work that grows with how many
 * times higher that is (the logarithm of the pressure ratio, as for a real compressor kept cool).
 */
public final class CompressorModel {
    /** Gas taken in per tick, in mB at the suction pressure: at 1 bar suction that is this many mB at 1 bar. */
    public static final double DISPLACEMENT = 60;
    /** The most it moves in a tick (mB at 1 bar), whatever the suction pressure. */
    public static final int MAX_FLOW = 600;
    /** The highest pressure it pushes to, in bar: enough to fill a gas cylinder. */
    public static final double MAX_DISCHARGE = 200;
    /** It cannot pull the suction line lower than this, in bar. */
    public static final double MIN_SUCTION = 0.2;
    /** Power just to keep it turning, and per 100 mB squeezed to twice its pressure, FE per tick. */
    public static final double IDLE_POWER = 10, POWER_PER_DOUBLING = 8;
    public static final int ENERGY_CAPACITY = 10_000, ENERGY_MAX_INPUT = 1_000;

    private CompressorModel() {}

    /** Gas it moves in a tick (mB at 1 bar) from this suction pressure. */
    public static int flow(double suction) {
        if (suction <= MIN_SUCTION) return 0;
        return (int) Math.min(MAX_FLOW, Math.floor(DISPLACEMENT * suction));
    }

    /** Power it needs (FE per tick) to move this flow from one pressure to the other. */
    public static int power(int flow, double suction, double discharge) {
        double ratio = Math.max(1, discharge / Math.max(MIN_SUCTION, suction));
        return (int) Math.ceil(IDLE_POWER + POWER_PER_DOUBLING * flow / 100.0 * (Math.log(ratio) / Math.log(2)));
    }
}
