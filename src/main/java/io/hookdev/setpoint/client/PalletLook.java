package io.hookdev.setpoint.client;

import java.util.List;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.PalletBlock;
import io.hookdev.setpoint.block.entity.PortableTankBlockEntity;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

/**
 * The colours of what is on a pallet, for its model's tints. Drums: tint 0 to 3 is drum 1 to 4, each in its dye or the
 * drum's own blue. An IBC: tint 0 is its liquid, tint 1 its tote (white, or black, which hides the liquid).
 */
public final class PalletLook {
    private PalletLook() {}

    public static int colour(List<ItemStack> load, int index) {
        if (PalletBlock.hasIbc(load)) {
            ItemStack ibc = load.get(0);
            DyedItemColor dyed = ibc.get(DataComponents.DYED_COLOR);
            boolean black = dyed != null && (dyed.rgb() & 0xFFFFFF) == (DyeColor.BLACK.getTextureDiffuseColor() & 0xFFFFFF);
            if (index == 1) return black ? PortableTankBlockEntity.IBC_BLACK : PortableTankBlockEntity.IBC_WHITE;
            if (black) return PortableTankBlockEntity.IBC_BLACK;
            SimpleFluidContent contents = ibc.get(SetpointContent.FLUID_CONTENTS.get());
            return FluidColours.of(contents == null || contents.isEmpty() ? Fluids.WATER : contents.copy().getFluid());
        }
        if (index >= load.size()) return PortableTankBlockEntity.DRUM_BLUE;
        DyedItemColor dyed = load.get(index).get(DataComponents.DYED_COLOR);
        return dyed == null ? PortableTankBlockEntity.DRUM_BLUE : 0xFF000000 | dyed.rgb();
    }

    /** Fills a pallet model's tint layers for this load. */
    public static void tints(IntList tints, List<ItemStack> load) {
        tints.clear();
        for (int i = 0; i < 4; i++) tints.add(colour(load, i));
    }
}
