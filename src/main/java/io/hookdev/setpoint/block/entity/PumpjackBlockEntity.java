package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.PumpjackBlock;
import io.hookdev.setpoint.gas.Liquids;
import io.hookdev.setpoint.hydraulics.GravityFlow;
import io.hookdev.setpoint.hydraulics.Vessel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * A pumpjack and its well: with power it lifts crude oil into the wellhead, slowly and without end. The wellhead is a
 * vessel like a tank: a pump takes the oil from it, or it runs downhill by itself to a lower tank.
 */
public class PumpjackBlockEntity extends BlockEntity implements Vessel, NamedEquipment, Describable {
    public static final int CRUDE_CAPACITY = 8_000, ENERGY_CAPACITY = 4_000, ENERGY_MAX_INPUT = 400;
    /** A tick of pumping: this much power for this much crude oil. */
    public static final int POWER = 30, CRUDE_PER_TICK = 10;
    /** The crank turns this far a tick while it runs, in degrees (one nod every five seconds). */
    public static final float CRANK_SPEED = 3.6f;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, ENERGY_MAX_INPUT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final FluidStacksResourceHandler crude = new FluidStacksResourceHandler(1, CRUDE_CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.isEmpty() || resource.getFluid() == Liquids.CRUDE_OIL.fluid().get();
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
            if (level != null && !level.isClientSide()) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    };
    private final EquipmentName name = new EquipmentName("PJ");
    private final GravityFlow gravity = new GravityFlow();
    private String status = "IDLE";
    /** Client side: how far the crank has turned, in degrees. */
    private float crank, crankBefore;

    public PumpjackBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.PUMPJACK_ENTITY.get(), pos, state);
    }

    public SimpleEnergyHandler energy() { return energy; }
    /** RUNNING, or why not: NO POWER, FULL. */
    public String status() { return status; }

    public float crank(float partialTicks) {
        return crankBefore + (crank - crankBefore) * partialTicks;
    }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PumpjackBlockEntity pumpjack) {
        ServerLevel server = (ServerLevel) level;
        boolean running = false;
        if (io.hookdev.setpoint.control.Esd.tripped(level, pos)) pumpjack.status = "ESD";
        else if (pumpjack.amount() + CRUDE_PER_TICK > CRUDE_CAPACITY) pumpjack.status = "FULL";
        else if (pumpjack.energy.getAmountAsInt() < POWER) pumpjack.status = "NO POWER";
        else {
            pumpjack.energy.set(pumpjack.energy.getAmountAsInt() - POWER);
            pumpjack.crude.set(0, Liquids.CRUDE_OIL.resource(), pumpjack.amount() + CRUDE_PER_TICK);
            pumpjack.status = "RUNNING";
            running = true;
        }
        pumpjack.gravity.tick(server, pumpjack, java.util.List.of(pos));
        if (state.getValue(PumpjackBlock.RUNNING) != running) level.setBlock(pos, state.setValue(PumpjackBlock.RUNNING, running), Block.UPDATE_CLIENTS);
        if (running && (level.getGameTime() + pos.hashCode()) % 50 == 0) {
            level.playSound(null, pos, SetpointContent.PUMP_RUN.get(), SoundSource.BLOCKS, 0.2f, 0.45f); // the creak of the beam
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, PumpjackBlockEntity pumpjack) {
        pumpjack.crankBefore = pumpjack.crank;
        if (state.getValue(PumpjackBlock.RUNNING)) pumpjack.crank += CRANK_SPEED;
        if (pumpjack.crank >= 360) { pumpjack.crank -= 360; pumpjack.crankBefore -= 360; }
    }

    @Override
    public void describe(Player player) {
        ChatFormatting colour = status.equals("RUNNING") ? ChatFormatting.WHITE : ChatFormatting.GOLD;
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(status + "  ").withStyle(colour))
                .append(Component.literal(String.format("crude oil %.1f / %.0f B", amount() / 1000.0, CRUDE_CAPACITY / 1000.0)).withStyle(ChatFormatting.GRAY)));
    }

    // ---------- the wellhead: a vessel of crude oil ----------
    @Override public FluidStacksResourceHandler fluid() { return crude; }
    @Override public FluidResource resource() { return crude.getResource(0); }
    @Override public int amount() { return crude.getAmountAsInt(0); }
    @Override public double fillFraction() { return amount() / (double) CRUDE_CAPACITY; }
    @Override public boolean canAccept(FluidResource incoming) { return false; }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        crude.serialize(output);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        crude.deserialize(input);
        name.load(input);
    }
}
