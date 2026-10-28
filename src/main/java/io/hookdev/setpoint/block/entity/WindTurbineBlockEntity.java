package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.WindTurbineBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * A wind turbine: the wind turns the rotor and the generator in the nacelle makes FE for free, but never steadily.
 * The wind blows harder in rain and harder still in a thunderstorm, gusts come and go, and it is a little stronger
 * higher up. Like a real turbine it starts at {@link #CUT_IN}, gives its full {@link #RATED} FE/t from
 * {@link #RATED_WIND}, and in a storm above {@link #CUT_OUT} it feathers its blades and stops until the wind drops.
 * The wind is worked out from the weather and the game time alone, so the client turns the rotor in step without being told.
 */
public class WindTurbineBlockEntity extends BlockEntity implements NamedEquipment, Describable {
    public static final int RATED = 100, ENERGY_CAPACITY = 20_000;
    /** Wind speeds in m/s: it starts turning, it gives full power, it stops for a storm. */
    public static final double CUT_IN = 3, RATED_WIND = 10, CUT_OUT = 25;
    /** The rotor's speed at full power, in turns a minute: slow and steady, like the real thing. */
    public static final double RATED_RPM = 16;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, 0, EnergyPush.PER_SIDE * 6) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final EquipmentName name = new EquipmentName("WT");
    private String status = "CALM";
    private int output;
    private double wind;
    /** Client side: the rotor's angle in degrees and its speed in rpm, which follows the wind slowly. */
    private float rotor, rotorBefore, rpm;

    public WindTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.WIND_TURBINE_ENTITY.get(), pos, state);
    }

    public SimpleEnergyHandler energy() { return energy; }
    /** RUNNING, or why not: CALM, STORM STOP, FULL, ESD. */
    public String status() { return status; }
    /** FE made in the last tick. */
    public int output() { return output; }
    /** The wind at the hub in the last tick, in m/s. */
    public double windSpeed() { return wind; }

    public float rotor(float partialTicks) {
        return rotorBefore + (rotor - rotorBefore) * partialTicks;
    }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    /**
     * The wind at the hub of a turbine at {@code pos}: 8 m/s on a clear day, 12 in rain, 17 in a thunderstorm, with slow
     * gusts on top (each turbine its own, so a wind farm does not turn in step) and a little more the higher the hub.
     */
    public static double wind(Level level, BlockPos pos) {
        double weather = level.isThundering() ? 17 : level.isRaining() ? 12 : 8;
        double t = level.getGameTime(), s = Math.floorMod(pos.asLong() * 31, 1000) / 100.0;
        double gust = 1 + 0.18 * Math.sin(t / 431 + s) + 0.12 * Math.sin(t / 157 + 2 * s) + 0.06 * Math.sin(t / 41 + 3 * s);
        int hub = pos.getY() + WindTurbineBlock.TOWER;
        double height = Math.max(0.8, Math.min(1.2, 0.85 + (hub - 64) / 400.0));
        return weather * gust * height;
    }

    /** FE a tick at this wind: nothing below cut-in, growing with the cube of the wind, full from rated, nothing in a storm. */
    public static int power(double wind) {
        if (wind < CUT_IN || wind > CUT_OUT) return 0;
        int rated = io.hookdev.setpoint.SetpointConfig.windTurbine();
        if (wind >= RATED_WIND) return rated;
        double cube = (wind * wind * wind - CUT_IN * CUT_IN * CUT_IN) / (RATED_WIND * RATED_WIND * RATED_WIND - CUT_IN * CUT_IN * CUT_IN);
        return (int) Math.round(rated * cube);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WindTurbineBlockEntity turbine) {
        turbine.wind = wind(level, pos);
        boolean esd = io.hookdev.setpoint.control.Esd.tripped(level, pos);
        int made = esd ? 0 : power(turbine.wind);
        int stored = turbine.energy.getAmountAsInt();
        if (esd) turbine.status = "ESD";
        else if (turbine.wind > CUT_OUT) turbine.status = "STORM STOP";
        else if (made == 0) turbine.status = "CALM";
        else if (stored + made > ENERGY_CAPACITY) turbine.status = "FULL";
        else turbine.status = "RUNNING";
        turbine.output = turbine.status.equals("RUNNING") ? made : 0;
        if (turbine.output > 0) turbine.energy.set(stored + turbine.output);
        EnergyPush.push(level, pos, turbine.energy);
        boolean stopped = esd || turbine.wind > CUT_OUT;
        if (state.getValue(WindTurbineBlock.STOPPED) != stopped) level.setBlock(pos, state.setValue(WindTurbineBlock.STOPPED, stopped), Block.UPDATE_CLIENTS);
    }

    /** The rotor follows the wind slowly, as a heavy rotor does: a few seconds to speed up, longer to run down. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, WindTurbineBlockEntity turbine) {
        double wind = wind(level, pos);
        float target = state.getValue(WindTurbineBlock.STOPPED) || wind < CUT_IN ? 0 : (float) (RATED_RPM * Math.min(wind, RATED_WIND) / RATED_WIND);
        turbine.rpm += (target - turbine.rpm) * (target > turbine.rpm ? 0.01f : 0.004f);
        turbine.rotorBefore = turbine.rotor;
        turbine.rotor += turbine.rpm * 0.3f; // rpm to degrees a tick: 360 / 60 / 20
        if (turbine.rotor >= 360) { turbine.rotor -= 360; turbine.rotorBefore -= 360; }
    }

    @Override
    public void describe(Player player) {
        ChatFormatting colour = status.equals("RUNNING") || status.equals("FULL") ? ChatFormatting.WHITE : ChatFormatting.GOLD;
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(status + "  ").withStyle(colour))
                .append(Component.literal(String.format("wind %.1f m/s  %d FE/t  stored %d FE", wind, output, energy.getAmountAsInt())).withStyle(ChatFormatting.GRAY)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        name.load(input);
    }
}
