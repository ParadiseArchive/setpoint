package io.hookdev.setpoint.block.entity;

import java.util.Optional;

import io.hookdev.setpoint.block.GeneratorBlock;
import io.hookdev.setpoint.menu.GeneratorMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
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
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * A generator that turns items into FE: one input slot (hoppers can fill it), an energy buffer,
 * and it pushes energy into every neighbour that accepts it. It only burns when the buffer has room,
 * so fuel is never wasted.
 */
public abstract class GeneratorBlockEntity extends BlockEntity implements MenuProvider, NamedEquipment {
    private final EquipmentName name = new EquipmentName("G");

    public static final int ENERGY_CAPACITY = 20_000;
    public static final int PUSH_PER_SIDE = EnergyPush.PER_SIDE;

    // Values shown on the screen (synced as shorts, all well below 32767)
    public static final int DATA_ENERGY = 0, DATA_RUN_LEFT = 1, DATA_RUN_TOTAL = 2, DATA_RUNNING = 3, DATA_COUNT = 4;

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
    private int runTicksTotal;
    private boolean running;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getAmountAsInt();
                case DATA_RUN_LEFT -> Math.min(Short.MAX_VALUE, runTicksLeft);
                case DATA_RUN_TOTAL -> Math.min(Short.MAX_VALUE, runTicksTotal);
                case DATA_RUNNING -> running ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    protected GeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** FE produced per tick while running. */
    public abstract int output();

    /** Whether this item can go in. */
    public abstract boolean accepts(ItemStack stack);

    /** How many ticks one item keeps the generator running. */
    protected abstract int runTicks(ServerLevel level, ItemStack stack);

    /** What the player should put in, shown on the screen ("coal", "organic waste"). */
    public abstract String fuelName();

    public SimpleEnergyHandler energy() { return energy; }
    public ItemStacksResourceHandler input() { return input; }
    public ContainerData data() { return data; }
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
        boolean room = stored + output() <= ENERGY_CAPACITY && !io.hookdev.setpoint.control.Esd.tripped(level, pos); // an ESD stops it like a full buffer

        if (runTicksLeft <= 0 && room) {
            ItemResource resource = input.getResource(0);
            if (!resource.isEmpty()) {
                int ticks = runTicks(level, resource.toStack());
                try (Transaction transaction = Transaction.openRoot()) {
                    if (input.extract(0, resource, 1, transaction) == 1) {
                        transaction.commit();
                        runTicksLeft = ticks;
                        runTicksTotal = ticks;
                    }
                }
            }
        }

        running = runTicksLeft > 0 && room;
        if (running) {
            runTicksLeft--;
            energy.set(stored + output());
        }
        EnergyPush.push(level, pos, energy);

        if (state.getValue(GeneratorBlock.LIT) != running) {
            level.setBlock(pos, state.setValue(GeneratorBlock.LIT, running), Block.UPDATE_CLIENTS);
        }
        if (running) effects(level, pos);
    }

    /** Small visual or sound cues while running. */
    protected void effects(ServerLevel level, BlockPos pos) {}

    /** Puts as much of the held stack in as fits. Returns how many went in. */
    public int insertFromHand(ItemStack stack) {
        if (stack.isEmpty() || !accepts(stack)) return 0;
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = input.insert(0, ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    /** Extra data the client screen needs once, when it opens. */
    public void writeOpenData(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(fuelName());
        buf.writeVarInt(output());
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GeneratorMenu(containerId, inventory, this);
    }

    @Override
    public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    /** Drop the fuel when the block is broken. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            ItemStack fuel = input.getResource(0).toStack(input.getAmountAsInt(0));
            if (!fuel.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), fuel);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output);
        input.serialize(output);
        output.putInt("run_ticks", runTicksLeft);
        output.putInt("run_ticks_total", runTicksTotal);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input);
        this.input.deserialize(input);
        runTicksLeft = input.getIntOr("run_ticks", 0);
        runTicksTotal = input.getIntOr("run_ticks_total", runTicksLeft);
        name.load(input);
    }
}
