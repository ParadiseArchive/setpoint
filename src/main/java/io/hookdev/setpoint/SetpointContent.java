package io.hookdev.setpoint;

import io.hookdev.setpoint.block.BoilerBlock;
import io.hookdev.setpoint.block.ControlValveBlock;
import io.hookdev.setpoint.block.ControllerBlock;
import io.hookdev.setpoint.block.CreativeGeneratorBlock;
import io.hookdev.setpoint.block.FluidPipeBlock;
import io.hookdev.setpoint.block.GeneratorBlock;
import io.hookdev.setpoint.block.LevelTransmitterBlock;
import io.hookdev.setpoint.block.PumpBlock;
import io.hookdev.setpoint.block.SteamTurbineBlock;
import io.hookdev.setpoint.block.TankBlock;
import io.hookdev.setpoint.block.entity.BiogasGeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.BoilerBlockEntity;
import io.hookdev.setpoint.block.entity.ControlValveBlockEntity;
import io.hookdev.setpoint.block.entity.ControllerBlockEntity;
import io.hookdev.setpoint.block.entity.CreativeGeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.LevelTransmitterBlockEntity;
import io.hookdev.setpoint.block.entity.PumpBlockEntity;
import io.hookdev.setpoint.block.entity.SteamTurbineBlockEntity;
import io.hookdev.setpoint.block.entity.StirlingEngineBlockEntity;
import io.hookdev.setpoint.block.entity.TankBlockEntity;
import io.hookdev.setpoint.item.FieldCommunicatorItem;
import io.hookdev.setpoint.menu.BoilerMenu;
import io.hookdev.setpoint.menu.FaceplateMenu;
import io.hookdev.setpoint.menu.GeneratorMenu;
import io.hookdev.setpoint.menu.TankMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
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
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Setpoint.MODID);
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

    public static final DeferredBlock<LevelTransmitterBlock> LEVEL_TRANSMITTER = BLOCKS.registerBlock("level_transmitter", LevelTransmitterBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<ControlValveBlock> CONTROL_VALVE = BLOCKS.registerBlock("control_valve", ControlValveBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<ControllerBlock> CONTROLLER = BLOCKS.registerBlock("controller", ControllerBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL));

    public static final DeferredBlock<GeneratorBlock> STIRLING_ENGINE = BLOCKS.registerBlock("stirling_engine",
            p -> new GeneratorBlock(p, StirlingEngineBlockEntity::new, () -> SetpointContent.STIRLING_ENGINE_ENTITY.get()),
            p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<GeneratorBlock> BIOGAS_GENERATOR = BLOCKS.registerBlock("biogas_generator",
            p -> new GeneratorBlock(p, BiogasGeneratorBlockEntity::new, () -> SetpointContent.BIOGAS_GENERATOR_ENTITY.get()),
            p -> p.mapColor(MapColor.COLOR_GREEN).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<BoilerBlock> BOILER = BLOCKS.registerBlock("boiler", BoilerBlock::new,
            p -> p.mapColor(MapColor.COLOR_RED).strength(3.5f, 6.0f).sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(BoilerBlock.LIT) ? 10 : 0));
    public static final DeferredBlock<SteamTurbineBlock> STEAM_TURBINE = BLOCKS.registerBlock("steam_turbine", SteamTurbineBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL));

    // Hookonium: endgame ore, as rare as diamond, mined with a diamond pickaxe
    public static final DeferredBlock<DropExperienceBlock> HOOKONIUM_ORE = BLOCKS.registerBlock("hookonium_ore",
            p -> new DropExperienceBlock(UniformInt.of(3, 7), p),
            p -> p.mapColor(MapColor.STONE).requiresCorrectToolForDrops().strength(3.0f, 3.0f));
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_HOOKONIUM_ORE = BLOCKS.registerBlock("deepslate_hookonium_ore",
            p -> new DropExperienceBlock(UniformInt.of(3, 7), p),
            p -> p.mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops().strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE));
    public static final DeferredBlock<Block> HOOKONIUM_BLOCK = BLOCKS.registerSimpleBlock("hookonium_block",
            p -> p.mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops().strength(5.0f, 6.0f).sound(SoundType.METAL));
    public static final DeferredBlock<Block> RAW_HOOKONIUM_BLOCK = BLOCKS.registerSimpleBlock("raw_hookonium_block",
            p -> p.mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops().strength(5.0f, 6.0f));

    public static final DeferredItem<BlockItem> PIPE_ITEM = ITEMS.registerSimpleBlockItem(PIPE);
    public static final DeferredItem<BlockItem> TANK_ITEM = ITEMS.registerSimpleBlockItem(TANK);
    public static final DeferredItem<BlockItem> PUMP_ITEM = ITEMS.registerSimpleBlockItem(PUMP);
    public static final DeferredItem<BlockItem> CREATIVE_GENERATOR_ITEM = ITEMS.registerSimpleBlockItem(CREATIVE_GENERATOR);
    public static final DeferredItem<BlockItem> STIRLING_ENGINE_ITEM = ITEMS.registerSimpleBlockItem(STIRLING_ENGINE);
    public static final DeferredItem<BlockItem> BIOGAS_GENERATOR_ITEM = ITEMS.registerSimpleBlockItem(BIOGAS_GENERATOR);
    public static final DeferredItem<BlockItem> BOILER_ITEM = ITEMS.registerSimpleBlockItem(BOILER);
    public static final DeferredItem<BlockItem> STEAM_TURBINE_ITEM = ITEMS.registerSimpleBlockItem(STEAM_TURBINE);
    public static final DeferredItem<BlockItem> LEVEL_TRANSMITTER_ITEM = ITEMS.registerSimpleBlockItem(LEVEL_TRANSMITTER);
    public static final DeferredItem<BlockItem> CONTROL_VALVE_ITEM = ITEMS.registerSimpleBlockItem(CONTROL_VALVE);
    public static final DeferredItem<BlockItem> CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(CONTROLLER);
    public static final DeferredItem<BlockItem> HOOKONIUM_ORE_ITEM = ITEMS.registerSimpleBlockItem(HOOKONIUM_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_HOOKONIUM_ORE_ITEM = ITEMS.registerSimpleBlockItem(DEEPSLATE_HOOKONIUM_ORE);
    public static final DeferredItem<BlockItem> HOOKONIUM_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(HOOKONIUM_BLOCK, p -> p.rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> RAW_HOOKONIUM_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(RAW_HOOKONIUM_BLOCK);
    public static final DeferredItem<Item> RAW_HOOKONIUM = ITEMS.registerSimpleItem("raw_hookonium");
    public static final DeferredItem<Item> HOOKONIUM_INGOT = ITEMS.registerSimpleItem("hookonium_ingot", p -> p.rarity(Rarity.RARE));
    public static final DeferredItem<FieldCommunicatorItem> FIELD_COMMUNICATOR = ITEMS.registerItem("field_communicator", FieldCommunicatorItem::new,
            p -> p.stacksTo(1));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TankBlockEntity>> TANK_ENTITY = BLOCK_ENTITIES.register("tank",
            () -> new BlockEntityType<>(TankBlockEntity::new, TANK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpBlockEntity>> PUMP_ENTITY = BLOCK_ENTITIES.register("centrifugal_pump",
            () -> new BlockEntityType<>(PumpBlockEntity::new, PUMP.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreativeGeneratorBlockEntity>> CREATIVE_GENERATOR_ENTITY = BLOCK_ENTITIES.register("creative_generator",
            () -> new BlockEntityType<>(CreativeGeneratorBlockEntity::new, CREATIVE_GENERATOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LevelTransmitterBlockEntity>> LEVEL_TRANSMITTER_ENTITY = BLOCK_ENTITIES.register("level_transmitter",
            () -> new BlockEntityType<>(LevelTransmitterBlockEntity::new, LEVEL_TRANSMITTER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControlValveBlockEntity>> CONTROL_VALVE_ENTITY = BLOCK_ENTITIES.register("control_valve",
            () -> new BlockEntityType<>(ControlValveBlockEntity::new, CONTROL_VALVE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControllerBlockEntity>> CONTROLLER_ENTITY = BLOCK_ENTITIES.register("controller",
            () -> new BlockEntityType<>(ControllerBlockEntity::new, CONTROLLER.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StirlingEngineBlockEntity>> STIRLING_ENGINE_ENTITY = BLOCK_ENTITIES.register("stirling_engine",
            () -> new BlockEntityType<>(StirlingEngineBlockEntity::new, STIRLING_ENGINE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BiogasGeneratorBlockEntity>> BIOGAS_GENERATOR_ENTITY = BLOCK_ENTITIES.register("biogas_generator",
            () -> new BlockEntityType<>(BiogasGeneratorBlockEntity::new, BIOGAS_GENERATOR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoilerBlockEntity>> BOILER_ENTITY = BLOCK_ENTITIES.register("boiler",
            () -> new BlockEntityType<>(BoilerBlockEntity::new, BOILER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamTurbineBlockEntity>> STEAM_TURBINE_ENTITY = BLOCK_ENTITIES.register("steam_turbine",
            () -> new BlockEntityType<>(SteamTurbineBlockEntity::new, STEAM_TURBINE.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<FaceplateMenu>> FACEPLATE = MENUS.register("faceplate",
            () -> IMenuTypeExtension.create(FaceplateMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<GeneratorMenu>> GENERATOR_MENU = MENUS.register("generator",
            () -> IMenuTypeExtension.create(GeneratorMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<TankMenu>> TANK_MENU = MENUS.register("tank",
            () -> IMenuTypeExtension.create(TankMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<BoilerMenu>> BOILER_MENU = MENUS.register("boiler",
            () -> IMenuTypeExtension.create(BoilerMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.setpoint"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> PUMP_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(PIPE_ITEM.get());
                output.accept(TANK_ITEM.get());
                output.accept(PUMP_ITEM.get());
                output.accept(STIRLING_ENGINE_ITEM.get());
                output.accept(BIOGAS_GENERATOR_ITEM.get());
                output.accept(BOILER_ITEM.get());
                output.accept(STEAM_TURBINE_ITEM.get());
                output.accept(LEVEL_TRANSMITTER_ITEM.get());
                output.accept(CONTROL_VALVE_ITEM.get());
                output.accept(CONTROLLER_ITEM.get());
                output.accept(FIELD_COMMUNICATOR.get());
                output.accept(CREATIVE_GENERATOR_ITEM.get());
                output.accept(HOOKONIUM_ORE_ITEM.get());
                output.accept(DEEPSLATE_HOOKONIUM_ORE_ITEM.get());
                output.accept(RAW_HOOKONIUM.get());
                output.accept(HOOKONIUM_INGOT.get());
                output.accept(RAW_HOOKONIUM_BLOCK_ITEM.get());
                output.accept(HOOKONIUM_BLOCK_ITEM.get());
            })
            .build());

    private SetpointContent() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        TABS.register(modEventBus);
        SOUNDS.register(modEventBus);
        MENUS.register(modEventBus);
    }
}
