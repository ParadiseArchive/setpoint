package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.EquipmentTag;
import io.hookdev.setpoint.block.TankBlock;
import io.hookdev.setpoint.menu.TankMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * A 16 bucket atmospheric tank. Its level shows in the sight glass through the LEVEL block state.
 * The screen has a bucket slot: a full bucket is emptied into the tank, an empty one is filled from it,
 * and the result lands in the output slot.
 */
public class TankBlockEntity extends BlockEntity implements MenuProvider {
    public static final int CAPACITY = 16_000;
    public static final int BUCKET = 1_000;
    public static final int SLOT_IN = 0, SLOT_OUT = 1;

    // Values shown on the screen (synced as shorts)
    public static final int DATA_AMOUNT = 0, DATA_FLUID = 1, DATA_COUNT = 2;

    private final FluidStacksResourceHandler fluid = new FluidStacksResourceHandler(1, CAPACITY) {
        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            onFluidChanged();
        }
    };

    private final ItemStacksResourceHandler buckets = new ItemStacksResourceHandler(2) {
        @Override
        public boolean isValid(int index, ItemResource resource) {
            if (index != SLOT_IN || resource.isEmpty()) return false;
            ItemStack stack = resource.toStack();
            return stack.is(Items.BUCKET) || !FluidUtil.getFirstStackContained(stack).isEmpty();
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_AMOUNT -> amount();
                case DATA_FLUID -> amount() == 0 ? -1 : BuiltInRegistries.FLUID.getId(resource().getFluid());
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

    public TankBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.TANK_ENTITY.get(), pos, state);
    }

    public FluidStacksResourceHandler fluid() {
        return fluid;
    }

    public ItemStacksResourceHandler buckets() {
        return buckets;
    }

    public ContainerData data() {
        return data;
    }

    public FluidResource resource() {
        return fluid.getResource(0);
    }

    public int amount() {
        return fluid.getAmountAsInt(0);
    }

    public double fillFraction() {
        return amount() / (double) CAPACITY;
    }

    /** Height of the liquid surface in the world, in blocks. */
    public double surfaceY() {
        return getBlockPos().getY() + fillFraction();
    }

    public boolean canAccept(FluidResource incoming) {
        return amount() < CAPACITY && (amount() == 0 || resource().equals(incoming));
    }

    private void setFluid(FluidResource resource, int amount) {
        fluid.set(0, amount == 0 ? FluidResource.EMPTY : resource, amount);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TankBlockEntity tank) {
        tank.processBuckets();
    }

    /** Empty a full bucket into the tank, or fill an empty one from it. */
    private void processBuckets() {
        int count = buckets.getAmountAsInt(SLOT_IN);
        if (count == 0) return;
        ItemStack in = buckets.getResource(SLOT_IN).toStack();
        FluidStack contained = FluidUtil.getFirstStackContained(in);
        if (!contained.isEmpty()) {
            FluidResource incoming = FluidResource.of(contained.getFluid());
            if (!canAccept(incoming) || amount() + contained.getAmount() > CAPACITY) return;
            if (!output(new ItemStack(Items.BUCKET))) return;
            setFluid(incoming, amount() + contained.getAmount());
            takeInput(count);
        } else if (in.is(Items.BUCKET) && amount() >= BUCKET) {
            Item filled = resource().getFluid().getBucket();
            if (filled == Items.AIR || !output(new ItemStack(filled))) return;
            setFluid(resource(), amount() - BUCKET);
            takeInput(count);
        }
    }

    private void takeInput(int count) {
        buckets.set(SLOT_IN, count == 1 ? ItemResource.EMPTY : buckets.getResource(SLOT_IN), count - 1);
    }

    /** Puts one item in the output slot if it fits. */
    private boolean output(ItemStack stack) {
        int have = buckets.getAmountAsInt(SLOT_OUT);
        ItemResource current = buckets.getResource(SLOT_OUT);
        if (have == 0) {
            buckets.set(SLOT_OUT, ItemResource.of(stack), 1);
            return true;
        }
        if (current.is(stack.getItem()) && have < stack.getMaxStackSize()) {
            buckets.set(SLOT_OUT, current, have + 1);
            return true;
        }
        return false;
    }

    private void onFluidChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            int shown = TankBlock.levelFor(amount(), CAPACITY);
            if (state.getValue(TankBlock.LEVEL) != shown) {
                level.setBlock(worldPosition, state.setValue(TankBlock.LEVEL, shown), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Extra data the client screen needs once, when it opens. */
    public void writeOpenData(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(EquipmentTag.of("T", worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new TankMenu(containerId, inventory, this);
    }

    /** Drop the buckets when the block is broken (the liquid is lost, like a broken cauldron). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) return;
        for (int slot = 0; slot < buckets.size(); slot++) {
            ItemStack stack = buckets.getResource(slot).toStack(buckets.getAmountAsInt(slot));
            if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        fluid.serialize(output);
        buckets.serialize(output.child("buckets"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fluid.deserialize(input);
        buckets.deserialize(input.childOrEmpty("buckets"));
    }
}
