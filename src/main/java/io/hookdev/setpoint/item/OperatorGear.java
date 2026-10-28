package io.hookdev.setpoint.item;

import java.util.Map;

import io.hookdev.setpoint.Setpoint;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * What an operator wears on the plant: a white hard hat, a yellow bump cap, a coverall (top and trousers, blue unless
 * you dye it) and safety boots. As strong as diamond armour and repaired with Hookonium. Every piece has its own look
 * (an equipment asset in assets/setpoint/equipment), so they all share the same numbers here.
 */
public final class OperatorGear {
    private OperatorGear() {}

    /** The colour of an undyed coverall (and its default in the item's tint): workwear blue. */
    public static final int COVERALL_BLUE = 0x2F5FA8;
    public static final TagKey<Item> REPAIRS = TagKey.create(Registries.ITEM, Setpoint.id("repairs_operator_gear"));

    public static final ArmorMaterial HARD_HAT = material("operator_helmet", SoundEvents.ARMOR_EQUIP_IRON);
    public static final ArmorMaterial BUMP_CAP = material("bump_cap", SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final ArmorMaterial COVERALL = material("coverall", SoundEvents.ARMOR_EQUIP_LEATHER);
    public static final ArmorMaterial WORK_BOOTS = material("work_boots", SoundEvents.ARMOR_EQUIP_LEATHER);

    /** Diamond's numbers: durability, protection per piece, toughness. */
    private static ArmorMaterial material(String asset, Holder<SoundEvent> sound) {
        ResourceKey<EquipmentAsset> key = ResourceKey.create(EquipmentAssets.ROOT_ID, Setpoint.id(asset));
        Map<ArmorType, Integer> defense = Map.of(ArmorType.BOOTS, 3, ArmorType.LEGGINGS, 6, ArmorType.CHESTPLATE, 8, ArmorType.HELMET, 3, ArmorType.BODY, 11);
        return new ArmorMaterial(33, defense, 10, sound, 2.0f, 0.0f, REPAIRS, key);
    }
}
