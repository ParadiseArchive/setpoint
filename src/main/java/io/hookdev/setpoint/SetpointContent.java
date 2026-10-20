package io.hookdev.setpoint;

import io.hookdev.setpoint.block.CreativeGeneratorBlock;
import io.hookdev.setpoint.block.FluidPipeBlock;
import io.hookdev.setpoint.block.PumpBlock;
import io.hookdev.setpoint.block.TankBlock;
import io.hookdev.setpoint.block.entity.CreativeGeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.PumpBlockEntity;
import io.hookdev.setpoint.block.entity.TankBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every block, item, block entity type and creative tab in Setpoint. */
public final class SetpointContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Setpoint.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Setpoint.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Setpoint.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Setpoint.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Setpoint.MODID);

    /** Crackling vapour bubbles collapsing in the impeller (sound files listed in sounds.json). */
    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_CAVITATE = SOUNDS.register("block.centrifugal_pump.cavitate",
            SoundEvent::createVariableRangeEvent);

    public static final DeferredBlock<FluidPipeBlock> PIPE = BLOCKS.registerBlock("pipe", FluidPipeBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<TankBlock> TANK = BLOCKS.registerBlock("tank", TankBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL));
    public static final DeferredBlock<PumpBlock> PUMP = BLOCKS.registerBlock("centrifugal_pump", PumpBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL));
    public static final DeferredBlock<CreativeGeneratorBlock> CREATIVE_GENERATOR = BLOCKS.registerBlock("creative_generator", CreativeGeneratorBlock::new,
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(2.0f, 6.0f).sound(SoundType.METAL));

    public static final DeferredItem<BlockItem> PIPE_ITEM = ITEMS.registerSimpleBlockItem(PIPE);
    public static final DeferredItem<BlockItem> TANK_ITEM = ITEMS.registerSimpleBlockItem(TANK);
    public static final DeferredItem<BlockItem> PUMP_ITEM = ITEMS.registerSimpleBlockItem(PUMP);
    public static final DeferredItem<BlockItem> CREATIVE_GENERATOR_ITEM = ITEMS.registerSimpleBlockItem(CREATIVE_GENERATOR);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TankBlockEntity>> TANK_ENTITY = BLOCK_ENTITIES.register("tank",
            () -> new BlockEntityType<>(TankBlockEntity::new, TANK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpBlockEntity>> PUMP_ENTITY = BLOCK_ENTITIES.register("centrifugal_pump",
            () -> new BlockEntityType<>(PumpBlockEntity::new, PUMP.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativeGeneratorBlockEntity>> CREATIVE_GENERATOR_ENTITY = BLOCK_ENTITIES.register("creative_generator",
            () -> new BlockEntityType<>(CreativeGeneratorBlockEntity::new, CREATIVE_GENERATOR.get()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.setpoint"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> PUMP_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(PIPE_ITEM.get());
                output.accept(TANK_ITEM.get());
                output.accept(PUMP_ITEM.get());
                output.accept(CREATIVE_GENERATOR_ITEM.get());
            })
            .build());

    private SetpointContent() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        TABS.register(modEventBus);
        SOUNDS.register(modEventBus);
    }
}
