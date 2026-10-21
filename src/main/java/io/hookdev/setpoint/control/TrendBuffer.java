package io.hookdev.setpoint.control;

/** The last few minutes of up to three values, one sample a second, oldest first when read. */
public final class TrendBuffer {
    public static final int SAMPLES = 300; // five minutes

    private final float[][] values;
    private int next;
    private int count;

    public TrendBuffer(int series) {
        values = new float[series][SAMPLES];
    }

    public int series() {
        return values.length;
    }

    public void add(float[] sample) {
        for (int s = 0; s < values.length; s++) values[s][next] = s < sample.length ? sample[s] : Float.NaN;
        next = (next + 1) % SAMPLES;
        count = Math.min(SAMPLES, count + 1);
    }

    /** The samples of one series in time order. */
    public float[] read(int series) {
        float[] out = new float[count];
        for (int i = 0; i < count; i++) out[i] = values[series][(next - count + i + SAMPLES) % SAMPLES];
        return out;
    }
}
