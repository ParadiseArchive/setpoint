package io.hookdev.setpoint.block.entity;

import java.util.Optional;

import io.hookdev.setpoint.block.GeneratorBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A generator that turns items into FE: one input slot (hoppers can fill it), an energy buffer,
 * and it pushes energy into every neighbour that accepts it. It only burns when the buffer has room,
 * so fuel is never wasted.
 */
public abstract class GeneratorBlockEntity extends BlockEntity {
    public static final int ENERGY_CAPACITY = 20_000;
    public static final int PUSH_PER_SIDE = 1_000;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(ENERGY_CAPACITY, 0, PUSH_PER_SIDE * 6) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };

    private final ItemStacksResourceHandler input = new ItemStacksResourceHandler(1) {
        @Override
        public boolean isValid(int index, ItemResource resource) {
            return !resource.isEmpty() && accepts(resource.toStack());
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };

    private int runTicksLeft;
    private boolean running;

    protected GeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** FE produced per tick while running. */
    public abstract int output();

    /** Whether this item can go in. */
    public abstract boolean accepts(ItemStack stack);

    /** How many ticks one item keeps the generator running. */
    protected abstract int runTicks(ServerLevel level, ItemStack stack);

    /** What the player should put in, for messages ("coal", "organic waste"). */
    protected abstract String fuelName();

    public SimpleEnergyHandler energy() { return energy; }
    public ItemStacksResourceHandler input() { return input; }
    public boolean running() { return running; }

    /** Loot context for resolving item values (burn time, compost chance) as if used at this block. */
    protected LootContext lootContext(ServerLevel level) {
        return new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.BLOCK_STATE, getBlockState())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .create(LootContextParamSets.BLOCK_INTERACT))
                .create(Optional.empty());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity generator) {
        generator.tick((ServerLevel) level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        int stored = energy.getAmountAsInt();
        boolean room = stored + output() <= ENERGY_CAPACITY;

        if (runTicksLeft <= 0 && room) {
            ItemResource resource = input.getResource(0);
            if (!resource.isEmpty()) {
                int ticks = runTicks(level, resource.toStack());
                try (Transaction transaction = Transaction.openRoot()) {
                    if (input.extract(0, resource, 1, transaction) == 1) {
                        transaction.commit();
                        runTicksLeft = ticks;
                    }
                }
            }
        }

        running = runTicksLeft > 0 && room;
        if (running) {
            runTicksLeft--;
            energy.set(stored + output());
        }
        pushEnergy(level, pos);

        if (state.getValue(GeneratorBlock.LIT) != running) {
            level.setBlock(pos, state.setValue(GeneratorBlock.LIT, running), Block.UPDATE_CLIENTS);
        }
        if (running) effects(level, pos);
    }

    /** Small visual or sound cues while running. */
    protected void effects(ServerLevel level, BlockPos pos) {}

    private void pushEnergy(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            int available = energy.getAmountAsInt();
            if (available <= 0) return;
            BlockPos target = pos.relative(direction);
            if (!level.isLoaded(target)) continue;
            EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, target, direction.getOpposite());
            if (handler == null || handler == energy) continue;
            try (Transaction transaction = Transaction.openRoot()) {
                int sent = handler.insert(Math.min(available, PUSH_PER_SIDE), transaction);
                if (sent > 0) {
                    transaction.commit();
                    energy.set(available - sent);
                }
            }
        }
    }

    /** Puts as much of the held stack in as fits. Returns how many went in. */
    public int insertFromHand(ItemStack stack) {
        if (stack.isEmpty() || !accepts(stack)) return 0;
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = input.insert(0, ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    /** Plain-language status for the player. */
    public void describe(Player player) {
        int count = input.getAmountAsInt(0);
        String fuel = count == 0 ? "empty" : count + " x " + input.getResource(0).toStack().getHoverName().getString();
        Component status;
        if (running) {
            status = Component.literal("Running, " + output() + " FE/t").withStyle(ChatFormatting.AQUA);
        } else if (count == 0 && runTicksLeft <= 0) {
            status = Component.literal("Out of fuel: add " + fuelName()).withStyle(ChatFormatting.GOLD);
        } else {
            status = Component.literal("Waiting: buffer full, nothing is using power").withStyle(ChatFormatting.GRAY);
        }
        player.sendSystemMessage(status);
        player.sendSystemMessage(Component.literal(String.format("Fuel: %s   Buffer: %,d / %,d FE", fuel, energy.getAmountAsInt(), ENERGY_CAPACITY))
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        input.serialize(output);
        output.putInt("run_ticks", runTicksLeft);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        this.input.deserialize(input);
        runTicksLeft = input.getIntOr("run_ticks", 0);
    }
}
