package io.hookdev.setpoint.block.entity;

import java.util.Locale;

import io.hookdev.setpoint.block.EquipmentTag;
import io.hookdev.setpoint.control.TagDirectory;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A device with a tag (LT-101, LV-101, LIC-101) that the DCS can find. */
public abstract class InstrumentBlockEntity extends BlockEntity {
    public static final int MAX_TAG_LENGTH = 16;

    private final String defaultPrefix;
    private String tag = "";

    protected InstrumentBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, String defaultPrefix) {
        super(type, pos, state);
        this.defaultPrefix = defaultPrefix;
    }

    public String tag() {
        return tag;
    }

    /** The number part of the tag (101 for LIC-101), used to give a whole loop matching tags. */
    public String loopNumber() {
        int dash = tag.lastIndexOf('-');
        return dash >= 0 && dash < tag.length() - 1 ? tag.substring(dash + 1) : tag;
    }

    public String defaultPrefix() {
        return defaultPrefix;
    }

    public void setTag(String newTag) {
        String clean = newTag.trim().toUpperCase(Locale.ROOT).replace(' ', '-');
        if (clean.length() > MAX_TAG_LENGTH) clean = clean.substring(0, MAX_TAG_LENGTH);
        if (clean.isEmpty() || clean.equals(tag)) return;
        if (level != null && !level.isClientSide()) TagDirectory.remove(level, tag, worldPosition);
        tag = clean;
        if (level != null && !level.isClientSide()) TagDirectory.put(level, tag, worldPosition);
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (tag.isEmpty()) tag = EquipmentTag.of(defaultPrefix, worldPosition);
        if (level != null && !level.isClientSide()) TagDirectory.put(level, tag, worldPosition);
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) TagDirectory.remove(level, tag, worldPosition);
        super.setRemoved();
    }

    /** One or two chat lines with the live values, for right-clicking or the field communicator. */
    public abstract void describe(Player player);

    protected Component tagLabel() {
        return Component.literal(tag + "  ").withStyle(ChatFormatting.AQUA);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("tag", tag);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tag = input.getStringOr("tag", "");
    }
}
