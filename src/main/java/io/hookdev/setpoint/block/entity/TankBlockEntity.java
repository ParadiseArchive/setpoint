package io.hookdev.setpoint.block.entity;

import java.util.ArrayList;
import java.util.List;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.hydraulics.GravityFlow;
import io.hookdev.setpoint.hydraulics.Vessel;
import io.hookdev.setpoint.block.TankBlock;
import io.hookdev.setpoint.menu.TankMenu;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * A 16 bucket atmospheric tank. Its level shows in the sight glass through the LEVEL block state.
 * The screen has a bucket slot: a full bucket is emptied into the tank, an empty one is filled from it,
 * and the result lands in the output slot.
 * <p>
 * Tanks stacked on top of each other form one tall tank (a column): liquid settles to the bottom, fills
 * from the bottom up and is drawn from the top down. As a vessel (for pumps, controllers, comparators and
 * other mods) every tank in a column speaks for the whole column; the screen shows the whole column too.
 */
public class TankBlockEntity extends BlockEntity implements MenuProvider, Vessel, NamedEquipment, ShowsFluid {
    private final EquipmentName name = new EquipmentName("T");
    private final GravityFlow gravity = new GravityFlow();

    public static final int CAPACITY = 16_000;
    public static final int BUCKET = 1_000;
    public static final int SLOT_IN = 0, SLOT_OUT = 1;
    /** Tallest column that counts as one tank. */
    public static final int MAX_COLUMN = 24;
    /** How fast liquid settles down into the tank below, in mB per tick. */
    public static final int SETTLE_PER_TICK = 2_000;

    // Values shown on the screen (synced as shorts): the column's amount in 10 mB steps, its fluid, its height
    public static final int DATA_AMOUNT = 0, DATA_FLUID = 1, DATA_TANKS = 2, DATA_COUNT = 3;

    private final FluidStacksResourceHandler fluid = new FluidStacksResourceHandler(1, CAPACITY) {
        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            onFluidChanged(previousContents.getFluid());
        }
    };
    /** Client side: the fluid behind this block's sight glass, for its colour. */
    private Fluid shownFluid = Fluids.EMPTY;

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
                case DATA_AMOUNT -> Math.min(Short.MAX_VALUE, amount() / 10);
                case DATA_FLUID -> amount() == 0 ? -1 : BuiltInRegistries.FLUID.getId(resource().getFluid());
                case DATA_TANKS -> column().size();
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

    /** This one block's liquid. */
    public FluidStacksResourceHandler own() {
        return fluid;
    }

    /** The whole column's liquid: fills from the bottom, drains from the top. */
    @Override
    public ResourceHandler<FluidResource> fluid() {
        return new Column(column());
    }

    public ItemStacksResourceHandler buckets() {
        return buckets;
    }

    public ContainerData data() {
        return data;
    }

    public FluidResource blockResource() {
        return fluid.getResource(0);
    }

    public int blockAmount() {
        return fluid.getAmountAsInt(0);
    }

    // ---------- the column ----------

    /** The tanks of this column, bottom first. */
    public List<TankBlockEntity> column() {
        if (level == null) return List.of(this);
        BlockPos pos = worldPosition;
        for (int i = 0; i < MAX_COLUMN && level.getBlockEntity(pos.below()) instanceof TankBlockEntity; i++) pos = pos.below();
        List<TankBlockEntity> tanks = new ArrayList<>();
        for (int i = 0; i < MAX_COLUMN && level.getBlockEntity(pos) instanceof TankBlockEntity tank; i++) {
            tanks.add(tank);
            pos = pos.above();
        }
        return tanks.isEmpty() ? List.of(this) : tanks;
    }

    public TankBlockEntity bottom() {
        return column().get(0);
    }

    @Override
    public List<BlockPos> positions() {
        return column().stream().map(BlockEntity::getBlockPos).toList();
    }

    @Override
    public FluidResource resource() {
        for (TankBlockEntity tank : column()) if (tank.blockAmount() > 0) return tank.blockResource();
        return FluidResource.EMPTY;
    }

    @Override
    public int amount() {
        return column().stream().mapToInt(TankBlockEntity::blockAmount).sum();
    }

    @Override
    public double fillFraction() {
        List<TankBlockEntity> column = column();
        return column.stream().mapToInt(TankBlockEntity::blockAmount).sum() / (double) (CAPACITY * column.size());
    }

    /** The liquid surface of the whole column, in blocks. */
    @Override
    public double surfaceY() {
        List<TankBlockEntity> column = column();
        return column.get(0).getBlockPos().getY() + column.stream().mapToInt(TankBlockEntity::blockAmount).sum() / (double) CAPACITY;
    }

    @Override
    public boolean canAccept(FluidResource incoming) {
        List<TankBlockEntity> column = column();
        int total = column.stream().mapToInt(TankBlockEntity::blockAmount).sum();
        FluidResource current = resource();
        return total < CAPACITY * column.size() && (current.isEmpty() || current.equals(incoming));
    }

    /** All tanks of a column as one handler. */
    private static final class Column extends CombinedResourceHandler<FluidResource> {
        private final List<TankBlockEntity> tanks;

        Column(List<TankBlockEntity> tanks) {
            super(tanks.stream().map(TankBlockEntity::own).toList());
            this.tanks = tanks;
        }

        /** One liquid per column, filled from the bottom up. */
        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            for (TankBlockEntity tank : tanks) {
                if (tank.blockAmount() > 0 && !tank.blockResource().equals(resource)) return 0;
            }
            return super.insert(resource, amount, transaction);
        }

        /** Drawn from the top down, the way a level falls. */
        @Override
        public int extract(FluidResource resource, int amount, TransactionContext transaction) {
            int extracted = 0;
            for (int i = tanks.size() - 1; i >= 0 && extracted < amount; i--) {
                extracted += tanks.get(i).own().extract(0, resource, amount - extracted, transaction);
            }
            return extracted;
        }
    }

    private void setFluid(FluidResource resource, int amount) {
        fluid.set(0, amount == 0 ? FluidResource.EMPTY : resource, amount);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TankBlockEntity tank) {
        tank.settle(level, pos);
        if (level.getGameTime() % 100 == 0 && level instanceof net.minecraft.server.level.ServerLevel server
                && !(level.getBlockEntity(pos.below()) instanceof TankBlockEntity) && tank.column().size() >= 4) {
            io.hookdev.setpoint.advancement.PlantEventTrigger.near(server, pos, io.hookdev.setpoint.advancement.PlantEventTrigger.TALL_TANK);
        }
        tank.processBuckets();
        // the bottom block lets the whole column run downhill through its pipes
        if (level instanceof net.minecraft.server.level.ServerLevel server && tank.bottom() == tank) tank.gravity.tick(server, tank, tank.positions());
    }

    /** Liquid runs down into the tank below until that one is full. */
    private void settle(Level level, BlockPos pos) {
        int here = blockAmount();
        if (here == 0 || !(level.getBlockEntity(pos.below()) instanceof TankBlockEntity below)) return;
        FluidResource liquid = blockResource();
        int room = CAPACITY - below.blockAmount();
        if (room <= 0 || (below.blockAmount() > 0 && !below.blockResource().equals(liquid))) return;
        int moved = Math.min(SETTLE_PER_TICK, Math.min(room, here));
        below.setFluid(liquid, below.blockAmount() + moved);
        setFluid(liquid, here - moved);
    }

    /** Empty a full bucket into the column, or fill an empty one from it. */
    private void processBuckets() {
        int count = buckets.getAmountAsInt(SLOT_IN);
        if (count == 0) return;
        ItemStack in = buckets.getResource(SLOT_IN).toStack();
        FluidStack contained = FluidUtil.getFirstStackContained(in);
        if (!contained.isEmpty()) {
            if (!canOutput(new ItemStack(Items.BUCKET))) return;
            FluidResource incoming = FluidResource.of(contained.getFluid());
            try (Transaction transaction = Transaction.openRoot()) {
                if (fluid().insert(incoming, contained.getAmount(), transaction) != contained.getAmount()) return;
                transaction.commit();
            }
            output(new ItemStack(Items.BUCKET));
            takeInput(count);
        } else if (in.is(Items.BUCKET) && amount() >= BUCKET) {
            FluidResource liquid = resource();
            Item filled = liquid.getFluid().getBucket();
            if (filled == Items.AIR || !canOutput(new ItemStack(filled))) return;
            try (Transaction transaction = Transaction.openRoot()) {
                if (fluid().extract(liquid, BUCKET, transaction) != BUCKET) return;
                transaction.commit();
            }
            output(new ItemStack(filled));
            takeInput(count);
        }
    }

    private boolean canOutput(ItemStack stack) {
        int have = buckets.getAmountAsInt(SLOT_OUT);
        return have == 0 || (buckets.getResource(SLOT_OUT).is(stack.getItem()) && have < stack.getMaxStackSize());
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

    private void onFluidChanged(Fluid before) {
        setChanged();
        if (level != null && !level.isClientSide()) {
            // a different liquid (or none): the clients redraw the sight glass in its colour
            if (!before.isSame(blockResource().getFluid())) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            BlockState state = getBlockState();
            int shown = TankBlock.levelFor(blockAmount(), CAPACITY);
            if (state.getValue(TankBlock.LEVEL) != shown) {
                level.setBlock(worldPosition, state.setValue(TankBlock.LEVEL, shown), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The fluid behind this block's sight glass, as the client knows it. */
    @Override
    public Fluid shownFluid() {
        return shownFluid;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("shown_fluid", BuiltInRegistries.FLUID.getKey(blockResource().getFluid()).toString());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        readShownFluid(input);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        readShownFluid(input);
    }

    private void readShownFluid(ValueInput input) {
        Identifier id = Identifier.tryParse(input.getStringOr("shown_fluid", "minecraft:empty"));
        shownFluid = id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.getValue(id);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); // redraw
    }

    /** A tall tank is one tank: its tag is the tag of its bottom block. */
    @Override
    public String tag() {
        TankBlockEntity bottom = bottom();
        return bottom.name.tag(bottom.worldPosition);
    }

    @Override
    public boolean rename(String newName) {
        TankBlockEntity bottom = bottom();
        if (!bottom.name.rename(newName)) return false;
        bottom.setChanged();
        return true;
    }

    /** Extra data the client screen needs once, when it opens. */
    public void writeOpenData(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(tag());
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
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fluid.deserialize(input);
        buckets.deserialize(input.childOrEmpty("buckets"));
        name.load(input);
    }
}
