package io.hookdev.setpoint.block.entity;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.advancement.PlantEventTrigger;
import io.hookdev.setpoint.block.SafetyValveBlock;
import io.hookdev.setpoint.hydraulics.PressureVessel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Safety valve on a closed vessel. Shut until the pressure reaches its set pressure, then it pops open and
 * blows off steam faster than a boiler can make it, until the pressure is {@link #BLOWDOWN} bar lower.
 * The last line of protection: it works without power, wiring or config.
 */
public class SafetyValveBlockEntity extends InstrumentBlockEntity {
    /** What an open valve lets out, in mB per tick: three boilers' worth, so it always wins. */
    public static final int RELIEF_RATE = 60;
    /** It shuts again this far below its set pressure, in bar. */
    public static final double BLOWDOWN = 1.0;
    /** Set pressures to choose from, in bar. */
    public static final double[] SETTINGS = {6, 8, 10};

    private double setPressure = 10;
    private boolean lifting;
    private int vented;

    public SafetyValveBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.SAFETY_VALVE_ENTITY.get(), pos, state, "PSV");
    }

    public double setPressure() { return setPressure; }
    public boolean lifting() { return lifting; }
    /** Steam let out last tick, in mB. */
    public int vented() { return vented; }

    public void setSetPressure(double bar) {
        setPressure = bar;
        setChanged();
    }

    /** The vessel this valve sits on: the block behind its outlet. */
    public @Nullable PressureVessel vessel() {
        if (level == null) return null;
        Direction outlet = getBlockState().getValue(SafetyValveBlock.FACING);
        return level.getBlockEntity(worldPosition.relative(outlet.getOpposite())) instanceof PressureVessel vessel ? vessel : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SafetyValveBlockEntity valve) {
        valve.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        PressureVessel vessel = vessel();
        double pressure = vessel == null ? 0 : vessel.pressure();
        boolean wasLifting = lifting;
        lifting = vessel != null && (lifting ? pressure > setPressure - BLOWDOWN : pressure >= setPressure);
        vented = lifting ? vessel.vent(RELIEF_RATE) : 0;

        if (lifting && !wasLifting) {
            level.playSound(null, pos, SetpointContent.SAFETY_VALVE_LIFT.get(), SoundSource.BLOCKS, 0.5f, 1.0f);
            PlantEventTrigger.near(level, pos, PlantEventTrigger.PSV_LIFTED);
        }
        if (lifting) {
            Direction outlet = state.getValue(SafetyValveBlock.FACING);
            double x = pos.getX() + 0.5 + outlet.getStepX() * 0.6, y = pos.getY() + 0.5 + outlet.getStepY() * 0.6, z = pos.getZ() + 0.5 + outlet.getStepZ() * 0.6;
            if (level.getGameTime() % 2 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, x, y, z, 2, 0.05, 0.05, 0.05, 0.0);
                level.sendParticles(ParticleTypes.CLOUD, x, y, z, 0, outlet.getStepX(), outlet.getStepY() + 0.2, outlet.getStepZ(), 0.25);
            }
            if ((level.getGameTime() + pos.hashCode()) % 30 == 0) {
                level.playSound(null, pos, SetpointContent.SAFETY_VALVE_LIFT.get(), SoundSource.BLOCKS, 0.3f, 0.9f);
            }
        }
        if (state.getValue(SafetyValveBlock.LIFTING) != lifting) {
            level.setBlock(pos, state.setValue(SafetyValveBlock.LIFTING, lifting), Block.UPDATE_ALL);
        }
    }

    /** Sneak-right-click: the next set pressure. */
    public void nextSetPressure(Player player) {
        int next = 0;
        for (int i = 0; i < SETTINGS.length; i++) if (SETTINGS[i] == setPressure) next = (i + 1) % SETTINGS.length;
        setSetPressure(SETTINGS[next]);
        Component message = Component.empty().append(tagLabel()).append(Component.literal(String.format(
                "set to %.0f bar: lifts at %.0f, shuts at %.0f", setPressure, setPressure, setPressure - BLOWDOWN)).withStyle(ChatFormatting.WHITE));
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(message, true);
    }

    @Override
    public void describe(Player player) {
        PressureVessel vessel = vessel();
        Component state = vessel == null
                ? Component.literal("NOT ON A BOILER  (put it on the drum)").withStyle(ChatFormatting.LIGHT_PURPLE)
                : lifting ? Component.literal(String.format("LIFTING  %.1f bar", vessel.pressure())).withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.literal(String.format("SHUT  %.1f bar", vessel.pressure())).withStyle(ChatFormatting.WHITE);
        player.sendSystemMessage(Component.empty().append(tagLabel()).append(state)
                .append(Component.literal(String.format("  set %.0f bar  (sneak-right-click to change)", setPressure)).withStyle(ChatFormatting.DARK_GRAY)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("set_pressure", setPressure);
        output.putBoolean("lifting", lifting);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        setPressure = input.getDoubleOr("set_pressure", 10);
        lifting = input.getBooleanOr("lifting", false);
    }
}
