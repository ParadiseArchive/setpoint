package io.hookdev.setpoint.block.entity;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.IbcBlock;
import io.hookdev.setpoint.block.PortableTankBlock;
import io.hookdev.setpoint.block.TankBlock;
import io.hookdev.setpoint.hydraulics.GravityFlow;
import io.hookdev.setpoint.hydraulics.Vessel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * What is inside a drum or an IBC. A vessel like a tank, so pumps, gravity lines, controllers and transmitters all
 * work with it. When it is broken the liquid goes along in the item (the setpoint:fluid_contents component).
 */
public class PortableTankBlockEntity extends BlockEntity implements Vessel, NamedEquipment, ShowsFluid {
    private final FluidStacksResourceHandler fluid;
    private final EquipmentName name;
    private final GravityFlow gravity = new GravityFlow();
    /** Client side: the liquid you see through the tote, for its colour. */
    private Fluid shownFluid = Fluids.EMPTY;

    public PortableTankBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.PORTABLE_TANK_ENTITY.get(), pos, state);
        PortableTankBlock block = (PortableTankBlock) state.getBlock();
        this.name = new EquipmentName(block.prefix());
        this.fluid = new FluidStacksResourceHandler(1, block.capacity()) {
            @Override
            protected void onContentsChanged(int index, FluidStack previousContents) {
                onFluidChanged(previousContents.getFluid());
            }
        };
    }

    public int capacity() { return ((PortableTankBlock) getBlockState().getBlock()).capacity(); }

    // ---------- vessel ----------
    @Override public FluidStacksResourceHandler fluid() { return fluid; }
    @Override public FluidResource resource() { return fluid.getResource(0); }
    @Override public int amount() { return fluid.getAmountAsInt(0); }
    @Override public double fillFraction() { return amount() / (double) capacity(); }

    @Override
    public boolean canAccept(FluidResource incoming) {
        return amount() < capacity() && (amount() == 0 || resource().equals(incoming));
    }

    @Override public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    @Override public Fluid shownFluid() { return shownFluid; }

    /** The base block and its parts. */
    @Override
    public List<BlockPos> positions() {
        List<BlockPos> all = new ArrayList<>();
        all.add(worldPosition);
        all.addAll(((PortableTankBlock) getBlockState().getBlock()).parts(worldPosition, getBlockState()));
        return all;
    }

    /** The liquid stands up to one and a half blocks in a drum, two in an IBC. */
    @Override
    public double surfaceY() {
        return worldPosition.getY() + fillFraction() * ((PortableTankBlock) getBlockState().getBlock()).liquidHeight();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PortableTankBlockEntity tank) {
        tank.gravity.tick((ServerLevel) level, tank, tank.positions()); // it drains downhill through the pipes on any of its blocks
    }

    private void onFluidChanged(Fluid before) {
        setChanged();
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (state.getBlock() instanceof IbcBlock) {
            int shown = TankBlock.levelFor(amount(), capacity());
            if (state.getValue(IbcBlock.LEVEL) != shown) level.setBlock(worldPosition, state = state.setValue(IbcBlock.LEVEL, shown), Block.UPDATE_CLIENTS);
        }
        if (!before.isSame(resource().getFluid())) level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
    }

    /** Right-click with an empty hand: what is inside. */
    public void describe(Player player) {
        Component what = amount() == 0 ? Component.literal("EMPTY").withStyle(ChatFormatting.GRAY)
                : Component.literal(String.format("%s  %.2f / %.0f B", resource().getFluidType().getDescription().getString(), amount() / 1000.0, capacity() / 1000.0))
                        .withStyle(ChatFormatting.WHITE);
        player.sendSystemMessage(Component.literal(tag() + "  ").withStyle(ChatFormatting.AQUA).append(what));
    }

    // ---------- the liquid goes along in the item ----------
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (amount() > 0) components.set(SetpointContent.FLUID_CONTENTS.get(), SimpleFluidContent.copyOf(resource().toStack(amount())));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        SimpleFluidContent contents = components.get(SetpointContent.FLUID_CONTENTS.get());
        if (contents != null && !contents.isEmpty()) {
            FluidStack stack = contents.copy();
            fluid.set(0, FluidResource.of(stack), Math.min(capacity(), stack.getAmount()));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        fluid.serialize(output);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fluid.deserialize(input);
        name.load(input);
    }

    // ---------- the client sees which liquid it is ----------
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("shown_fluid", BuiltInRegistries.FLUID.getKey(resource().getFluid()).toString());
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
}
