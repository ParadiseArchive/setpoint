package io.hookdev.setpoint.block.entity;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

/** The drums on a pallet, as their items: each keeps its liquid and its colour. Players get them, to draw them. */
public class PalletBlockEntity extends BlockEntity {
    public static final int MAX_DRUMS = 4;

    private final List<ItemStack> drums = new ArrayList<>();

    public PalletBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.PALLET_ENTITY.get(), pos, state);
    }

    public List<ItemStack> drums() { return drums; }

    public void add(ItemStack drum) {
        if (drums.size() >= MAX_DRUMS) return;
        drums.add(drum);
        changed();
    }

    /** Everything off at once (onto a pallet truck): the block can then go without dropping the drums. */
    public List<ItemStack> takeAll() {
        List<ItemStack> all = new ArrayList<>(drums);
        drums.clear();
        changed();
        return all;
    }

    public ItemStack takeLast() {
        if (drums.isEmpty()) return ItemStack.EMPTY;
        ItemStack drum = drums.remove(drums.size() - 1);
        changed();
        return drum;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** The colour of drum number {@code index}: its dye, or the drum's own blue. */
    public int colour(int index) {
        if (index >= drums.size()) return PortableTankBlockEntity.DRUM_BLUE;
        DyedItemColor dyed = drums.get(index).get(DataComponents.DYED_COLOR);
        return dyed == null ? PortableTankBlockEntity.DRUM_BLUE : 0xFF000000 | dyed.rgb();
    }

    /** An empty hand: what is on the pallet. */
    public void describe(Player player) {
        MutableComponent line = Component.literal("Pallet  ").withStyle(ChatFormatting.AQUA);
        if (drums.isEmpty()) line.append(Component.literal("empty: right-click it with a drum").withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < drums.size(); i++) {
            SimpleFluidContent contents = drums.get(i).get(SetpointContent.FLUID_CONTENTS.get());
            FluidStack fluid = contents == null ? FluidStack.EMPTY : contents.copy();
            line.append(Component.literal((i > 0 ? ",  " : "") + (fluid.isEmpty() ? "empty drum"
                    : String.format("%s %.1f B", fluid.getHoverName().getString(), fluid.getAmount() / 1000.0))).withStyle(ChatFormatting.WHITE));
        }
        player.sendSystemMessage(line);
    }

    /** Broken: the drums drop with it. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) for (ItemStack drum : drums) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), drum);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!drums.isEmpty()) output.store("drums", ItemStack.CODEC.listOf(), drums);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        drums.clear();
        drums.addAll(input.read("drums", ItemStack.CODEC.listOf()).orElse(List.of()));
    }

    // players get the drums, to draw them in their colours
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        loadAdditional(input);
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); // redraw
    }
}
