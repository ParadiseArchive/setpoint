package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The drawers of a filing cabinet: 27 slots, like a barrel. Broken, it drops what it holds. */
public class FilingCabinetBlockEntity extends RandomizableContainerBlockEntity {
    public static final int SIZE = 27;
    private static final Component DEFAULT_NAME = Component.translatable("container.setpoint.filing_cabinet");
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public FilingCabinetBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.FILING_CABINET_ENTITY.get(), pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) ContainerHelper.saveAllItems(output, this.items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) ContainerHelper.loadAllItems(input, this.items);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    /** A drawer slides out. */
    @Override
    public void startOpen(ContainerUser user) {
        if (level != null && !remove && !user.getLivingEntity().isSpectator()) {
            level.playSound(null, worldPosition, SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 0.6f, 0.7f);
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        if (level != null && !remove && !user.getLivingEntity().isSpectator()) {
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.3f, 1.4f);
        }
    }
}
