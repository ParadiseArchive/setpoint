package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.advancement.PlantEventTrigger;
import io.hookdev.setpoint.block.SteamTurbineBlock;
import io.hookdev.setpoint.physics.TurbineModel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * Steam turbine with a generator on its shaft. Place it against a boiler: it takes the steam,
 * spins up and pushes FE into its other neighbours. It only takes steam while its buffer has room,
 * so an idle plant lets the boiler build up steam and stop firing.
 */
public class SteamTurbineBlockEntity extends BlockEntity implements NamedEquipment {
    private final EquipmentName name = new EquipmentName("ST");

    public static final int ENERGY_CAPACITY = 20_000;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, 0, EnergyPush.PER_SIDE * 6) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };

    private double speed;
    private int steamUsed;
    private int output;

    public SteamTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.STEAM_TURBINE_ENTITY.get(), pos, state);
    }

    public SimpleEnergyHandler energy() { return energy; }
    public double speed() { return speed; }
    public int output() { return output; }
    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }


    public static void serverTick(Level level, BlockPos pos, BlockState state, SteamTurbineBlockEntity turbine) {
        turbine.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        int room = ENERGY_CAPACITY - energy.getAmountAsInt();
        // Only take as much steam as the buffer can turn into power.
        int wanted = (int) Math.min(TurbineModel.MAX_STEAM, Math.floor(room / TurbineModel.FE_PER_MB));
        steamUsed = 0;
        for (Direction direction : Direction.values()) {
            if (steamUsed >= wanted) break;
            if (level.getBlockEntity(pos.relative(direction)) instanceof BoilerBlockEntity boiler) {
                // the inlet swallows less steam at low pressure
                steamUsed += boiler.takeSteam(Math.min(wanted - steamUsed, TurbineModel.inletFlow(boiler.pressure())));
            }
        }
        speed = TurbineModel.nextSpeed(speed, steamUsed);
        output = TurbineModel.power(speed, steamUsed);
        if (output > 0) energy.set(Math.min(ENERGY_CAPACITY, energy.getAmountAsInt() + output));
        EnergyPush.push(level, pos, energy);

        boolean running = speed > 100;
        if (speed > TurbineModel.RATED_SPEED * 0.97 && level.getGameTime() % 100 == 0) PlantEventTrigger.near(level, pos, PlantEventTrigger.TURBINE_RATED);
        if (state.getValue(SteamTurbineBlock.RUNNING) != running) {
            level.setBlock(pos, state.setValue(SteamTurbineBlock.RUNNING, running), Block.UPDATE_CLIENTS);
        }
        if (running && (level.getGameTime() + pos.hashCode()) % 60 == 0) {
            // the whine rises with the rotor speed
            level.playSound(null, pos, SetpointContent.TURBINE_RUN.get(), SoundSource.BLOCKS, 0.18f,
                    (float) (0.6 + 1.0 * speed / TurbineModel.RATED_SPEED));
        }
        if (steamUsed > 0 && level.getGameTime() % 8 == 0) {
            // exhaust steam
            level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 1, 0.12, 0.02, 0.12, 0.02);
        }
    }

    public void report(Player player) {
        MutableComponent line = Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA);
        if (speed <= 100) {
            line.append(Component.literal("STOPPED").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(steamUsed > 0 ? "  (spinning up)" : "  (no steam: place it against a boiler)").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            line.append(Component.literal("RUNNING").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(String.format("  %d rpm  %d FE/t  steam %d mB/t", Math.round(speed), output, steamUsed)).withStyle(ChatFormatting.WHITE));
        }
        line.append(Component.literal(String.format("  %,d / %,d FE", energy.getAmountAsInt(), ENERGY_CAPACITY)).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(line);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        output.putDouble("speed", speed);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        speed = input.getDoubleOr("speed", 0);
        name.load(input);
    }
}
