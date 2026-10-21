package io.hookdev.setpoint;

import com.mojang.serialization.Codec;

import io.hookdev.setpoint.advancement.PlantEventTrigger;
import io.hookdev.setpoint.block.BoilerBlock;
import io.hookdev.setpoint.block.CheckValveBlock;
import io.hookdev.setpoint.block.ConsoleBlock;
import io.hookdev.setpoint.block.ControlValveBlock;
import io.hookdev.setpoint.block.ControllerBlock;
import io.hookdev.setpoint.block.CreativeGeneratorBlock;
import io.hookdev.setpoint.block.DrainValveBlock;
import io.hookdev.setpoint.block.DrumBlock;
import io.hookdev.setpoint.block.FlowTransmitterBlock;
import io.hookdev.setpoint.block.FluidPipeBlock;
import io.hookdev.setpoint.block.GeneratorBlock;
import io.hookdev.setpoint.block.HandValveBlock;
import io.hookdev.setpoint.block.IbcBlock;
import io.hookdev.setpoint.block.LevelTransmitterBlock;
import io.hookdev.setpoint.block.PalletBlock;
import io.hookdev.setpoint.block.PalletPartBlock;
import io.hookdev.setpoint.block.PidBoardBlock;
import io.hookdev.setpoint.block.PressureTransmitterBlock;
import io.hookdev.setpoint.block.PumpBlock;
import io.hookdev.setpoint.block.SafetyValveBlock;
import io.hookdev.setpoint.block.SteamTurbineBlock;
import io.hookdev.setpoint.block.TankBlock;
import io.hookdev.setpoint.block.TankPartBlock;
import io.hookdev.setpoint.block.WaterIntakeBlock;
import io.hookdev.setpoint.block.entity.BiogasGeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.BoilerBlockEntity;
import io.hookdev.setpoint.block.entity.ConsoleBlockEntity;
import io.hookdev.setpoint.block.entity.ControlValveBlockEntity;
import io.hookdev.setpoint.block.entity.ControllerBlockEntity;
import io.hookdev.setpoint.block.entity.CreativeGeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.DrainValveBlockEntity;
import io.hookdev.setpoint.block.entity.FlowTransmitterBlockEntity;
import io.hookdev.setpoint.block.entity.LevelTransmitterBlockEntity;
import io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity;
import io.hookdev.setpoint.block.entity.PalletBlockEntity;
import io.hookdev.setpoint.block.entity.PidBoardBlockEntity;
import io.hookdev.setpoint.block.entity.PortableTankBlockEntity;
import io.hookdev.setpoint.block.entity.PressureTransmitterBlockEntity;
import io.hookdev.setpoint.block.entity.PumpBlockEntity;
import io.hookdev.setpoint.block.entity.SafetyValveBlockEntity;
import io.hookdev.setpoint.block.entity.SteamTurbineBlockEntity;
import io.hookdev.setpoint.block.entity.StirlingEngineBlockEntity;
import io.hookdev.setpoint.block.entity.TankBlockEntity;
import io.hookdev.setpoint.block.entity.TankPartBlockEntity;
import io.hookdev.setpoint.block.entity.WaterIntakeBlockEntity;
import io.hookdev.setpoint.entity.PalletTruckEntity;
import io.hookdev.setpoint.item.BlankPidItem;
import io.hookdev.setpoint.item.FieldCommunicatorItem;
import io.hookdev.setpoint.item.HandbookItem;
import io.hookdev.setpoint.item.PalletTruckItem;
import io.hookdev.setpoint.item.PidItem;
import io.hookdev.setpoint.item.PipeWrenchItem;
import io.hookdev.setpoint.menu.BoilerMenu;
import io.hookdev.setpoint.menu.ConsoleMenu;
import io.hookdev.setpoint.menu.FaceplateMenu;
import io.hookdev.setpoint.menu.GeneratorMenu;
import io.hookdev.setpoint.menu.TankMenu;
import io.hookdev.setpoint.pid.PidDrawing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Every block, item, block entity type and creative tab in Setpoint. */
public final class SetpointContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Setpoint.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Setpoint.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Setpoint.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Setpoint.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Setpoint.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Setpoint.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Setpoint.MODID);
    public static final DeferredRegister<net.minecraft.advancements.triggers.CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Setpoint.MODID);
    public static final DeferredHolder<net.minecraft.advancements.triggers.CriterionTrigger<?>, PlantEventTrigger> PLANT_EVENT = TRIGGERS.register("plant_event", PlantEventTrigger::new);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Setpoint.MODID);
    /** The drawing on a P&ID. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PidDrawing>> PID_DRAWING = COMPONENTS.register("pid_drawing",
            () -> DataComponentType.<PidDrawing>builder().persistent(PidDrawing.CODEC).networkSynchronized(PidDrawing.STREAM_CODEC).build());
    /** The liquid inside a drum or IBC item, so it keeps its contents when you carry it away. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> FLUID_CONTENTS = COMPONENTS.register("fluid_contents",
            () -> DataComponentType.<SimpleFluidContent>builder().persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC).build());
    /** The first corner picked with a blank P&ID. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> PID_CORNER = COMPONENTS.register("pid_corner",
            () -> DataComponentType.<BlockPos>builder().persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build());
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Setpoint.MODID);

    /** Whether a player already got their handbook (kept through death). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> HANDBOOK_GIVEN = ATTACHMENTS.register("handbook_given",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("given")).copyOnDeath().build());

    /** Crackling vapour bubbles collapsing in the impeller (sound files listed in sounds.json). */
    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_CAVITATE = SOUNDS.register("block.centrifugal_pump.cavitate",
            SoundEvent::createVariableRangeEvent);
    /** Quiet running sounds: a motor hum, a turbine whine that rises with speed, a crackling fire. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_RUN = SOUNDS.register("block.centrifugal_pump.run", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> TURBINE_RUN = SOUNDS.register("block.steam_turbine.run", SoundEvent::createVariableRangeEvent);
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_CRACKLE = SOUNDS.register("block.firebox.crackle", SoundEvent::createVariableRangeEvent);
    /** A soft two-tone chime for new alarms in the control room. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CONSOLE_ALARM = SOUNDS.register("block.dcs_console.alarm",
            SoundEvent::createVariableRangeEvent);
    /** The pallet truck's horn. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PALLET_TRUCK_HORN = SOUNDS.register("entity.pallet_truck.horn", SoundEvent::createVariableRangeEvent);
    /** The hiss of a safety valve blowing off steam. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SAFETY_VALVE_LIFT = SOUNDS.register("block.safety_valve.lift",
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
    public static final DeferredBlock<FlowTransmitterBlock> FLOW_TRANSMITTER = BLOCKS.registerBlock("flow_transmitter", FlowTransmitterBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<PressureTransmitterBlock> PRESSURE_TRANSMITTER = BLOCKS.registerBlock("pressure_transmitter", PressureTransmitterBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<DrumBlock> DRUM = BLOCKS.registerBlock("drum", DrumBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLUE).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<IbcBlock> IBC = BLOCKS.registerBlock("ibc", IbcBlock::new,
            p -> p.mapColor(MapColor.SNOW).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<PalletBlock> PALLET = BLOCKS.registerBlock("pallet", PalletBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.5f, 3.0f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<PalletPartBlock> PALLET_PART = BLOCKS.registerBlock("pallet_part", PalletPartBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.5f, 3.0f).sound(SoundType.WOOD).noOcclusion().noLootTable());
    public static final DeferredBlock<TankPartBlock> TANK_PART = BLOCKS.registerBlock("tank_part", TankPartBlock::new,
            p -> p.mapColor(MapColor.SNOW).strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion().noLootTable());
    public static final DeferredBlock<PidBoardBlock> PID_BOARD = BLOCKS.registerBlock("pid_board", PidBoardBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLUE).strength(1.5f, 6.0f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> state.getValue(PidBoardBlock.LIT) ? PidBoardBlock.LIGHT : 0));
    public static final DeferredBlock<CheckValveBlock> CHECK_VALVE = BLOCKS.registerBlock("check_valve", CheckValveBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<DrainValveBlock> DRAIN_VALVE = BLOCKS.registerBlock("drain_valve", DrainValveBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<SafetyValveBlock> SAFETY_VALVE = BLOCKS.registerBlock("safety_valve", SafetyValveBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion());
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

    public static final DeferredBlock<HandValveBlock> HAND_VALVE = BLOCKS.registerBlock("hand_valve", HandValveBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<WaterIntakeBlock> WATER_INTAKE = BLOCKS.registerBlock("water_intake", WaterIntakeBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL));
    public static final DeferredBlock<ConsoleBlock> CONSOLE = BLOCKS.registerBlock("dcs_console", ConsoleBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).lightLevel(state -> 4));

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
    public static final DeferredItem<BlockItem> FLOW_TRANSMITTER_ITEM = ITEMS.registerSimpleBlockItem(FLOW_TRANSMITTER);
    public static final DeferredItem<BlockItem> PRESSURE_TRANSMITTER_ITEM = ITEMS.registerSimpleBlockItem(PRESSURE_TRANSMITTER);
    public static final DeferredItem<BlockItem> SAFETY_VALVE_ITEM = ITEMS.registerSimpleBlockItem(SAFETY_VALVE);
    public static final DeferredItem<BlockItem> DRAIN_VALVE_ITEM = ITEMS.registerSimpleBlockItem(DRAIN_VALVE);
    public static final DeferredItem<BlockItem> CHECK_VALVE_ITEM = ITEMS.registerSimpleBlockItem(CHECK_VALVE);
    public static final DeferredItem<BlockItem> PID_BOARD_ITEM = ITEMS.registerSimpleBlockItem(PID_BOARD);
    public static final DeferredItem<BlockItem> DRUM_ITEM = ITEMS.registerSimpleBlockItem(DRUM);
    public static final DeferredItem<BlockItem> IBC_ITEM = ITEMS.registerSimpleBlockItem(IBC);
    public static final DeferredItem<BlockItem> PALLET_ITEM = ITEMS.registerSimpleBlockItem(PALLET);
    public static final DeferredItem<BlockItem> CONTROL_VALVE_ITEM = ITEMS.registerSimpleBlockItem(CONTROL_VALVE);
    public static final DeferredItem<BlockItem> CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(CONTROLLER);
    public static final DeferredItem<BlockItem> HAND_VALVE_ITEM = ITEMS.registerSimpleBlockItem(HAND_VALVE);
    public static final DeferredItem<BlockItem> WATER_INTAKE_ITEM = ITEMS.registerSimpleBlockItem(WATER_INTAKE);
    public static final DeferredItem<PipeWrenchItem> PIPE_WRENCH = ITEMS.registerItem("pipe_wrench", PipeWrenchItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<BlockItem> CONSOLE_ITEM = ITEMS.registerSimpleBlockItem(CONSOLE);
    public static final DeferredItem<BlockItem> HOOKONIUM_ORE_ITEM = ITEMS.registerSimpleBlockItem(HOOKONIUM_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_HOOKONIUM_ORE_ITEM = ITEMS.registerSimpleBlockItem(DEEPSLATE_HOOKONIUM_ORE);
    public static final DeferredItem<BlockItem> HOOKONIUM_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(HOOKONIUM_BLOCK, p -> p.rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> RAW_HOOKONIUM_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(RAW_HOOKONIUM_BLOCK);
    public static final DeferredItem<Item> RAW_HOOKONIUM = ITEMS.registerSimpleItem("raw_hookonium");
    public static final DeferredItem<Item> HOOKONIUM_INGOT = ITEMS.registerSimpleItem("hookonium_ingot", p -> p.rarity(Rarity.RARE));
    public static final DeferredItem<BlankPidItem> BLANK_PID = ITEMS.registerItem("blank_pid", BlankPidItem::new);
    public static final DeferredItem<PidItem> PID = ITEMS.registerItem("pid", PidItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<PalletTruckItem> PALLET_TRUCK = ITEMS.registerItem("pallet_truck", PalletTruckItem::new, p -> p.stacksTo(1));
    /** Holds the pallet truck's model, for its renderer; never placed. */
    public static final DeferredBlock<Block> PALLET_TRUCK_MODEL = BLOCKS.registerBlock("pallet_truck", Block::new,
            p -> p.noCollision().noOcclusion().noLootTable());
    public static final DeferredHolder<EntityType<?>, EntityType<PalletTruckEntity>> PALLET_TRUCK_ENTITY = ENTITIES.register("pallet_truck",
            id -> EntityType.Builder.<PalletTruckEntity>of(PalletTruckEntity::new, MobCategory.MISC).sized(1.4f, 1.0f)
                    .clientTrackingRange(10).updateInterval(1).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final DeferredItem<HandbookItem> HANDBOOK = ITEMS.registerItem("handbook", HandbookItem::new, p -> p.stacksTo(1));
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
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlowTransmitterBlockEntity>> FLOW_TRANSMITTER_ENTITY = BLOCK_ENTITIES.register("flow_transmitter",
            () -> new BlockEntityType<>(FlowTransmitterBlockEntity::new, FLOW_TRANSMITTER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PressureTransmitterBlockEntity>> PRESSURE_TRANSMITTER_ENTITY = BLOCK_ENTITIES.register("pressure_transmitter",
            () -> new BlockEntityType<>(PressureTransmitterBlockEntity::new, PRESSURE_TRANSMITTER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortableTankBlockEntity>> PORTABLE_TANK_ENTITY = BLOCK_ENTITIES.register("portable_tank",
            () -> new BlockEntityType<>(PortableTankBlockEntity::new, DRUM.get(), IBC.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PalletBlockEntity>> PALLET_ENTITY = BLOCK_ENTITIES.register("pallet",
            () -> new BlockEntityType<>(PalletBlockEntity::new, PALLET.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MultiblockPartBlockEntity>> PALLET_PART_ENTITY = BLOCK_ENTITIES.register("pallet_part",
            () -> new BlockEntityType<>((pos, state) -> new MultiblockPartBlockEntity(SetpointContent.PALLET_PART_ENTITY.get(), pos, state), PALLET_PART.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TankPartBlockEntity>> TANK_PART_ENTITY = BLOCK_ENTITIES.register("tank_part",
            () -> new BlockEntityType<>(TankPartBlockEntity::new, TANK_PART.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PidBoardBlockEntity>> PID_BOARD_ENTITY = BLOCK_ENTITIES.register("pid_board",
            () -> new BlockEntityType<>(PidBoardBlockEntity::new, PID_BOARD.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DrainValveBlockEntity>> DRAIN_VALVE_ENTITY = BLOCK_ENTITIES.register("drain_valve",
            () -> new BlockEntityType<>(DrainValveBlockEntity::new, DRAIN_VALVE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SafetyValveBlockEntity>> SAFETY_VALVE_ENTITY = BLOCK_ENTITIES.register("safety_valve",
            () -> new BlockEntityType<>(SafetyValveBlockEntity::new, SAFETY_VALVE.get()));
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
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterIntakeBlockEntity>> WATER_INTAKE_ENTITY = BLOCK_ENTITIES.register("water_intake",
            () -> new BlockEntityType<>(WaterIntakeBlockEntity::new, WATER_INTAKE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConsoleBlockEntity>> CONSOLE_ENTITY = BLOCK_ENTITIES.register("dcs_console",
            () -> new BlockEntityType<>(ConsoleBlockEntity::new, CONSOLE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamTurbineBlockEntity>> STEAM_TURBINE_ENTITY = BLOCK_ENTITIES.register("steam_turbine",
            () -> new BlockEntityType<>(SteamTurbineBlockEntity::new, STEAM_TURBINE.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<FaceplateMenu>> FACEPLATE = MENUS.register("faceplate",
            () -> IMenuTypeExtension.create(FaceplateMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<GeneratorMenu>> GENERATOR_MENU = MENUS.register("generator",
            () -> IMenuTypeExtension.create(GeneratorMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<TankMenu>> TANK_MENU = MENUS.register("tank",
            () -> IMenuTypeExtension.create(TankMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ConsoleMenu>> CONSOLE_MENU = MENUS.register("dcs_console",
            () -> IMenuTypeExtension.create(ConsoleMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<BoilerMenu>> BOILER_MENU = MENUS.register("boiler",
            () -> IMenuTypeExtension.create(BoilerMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.setpoint"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> PUMP_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(HANDBOOK.get());
                output.accept(BLANK_PID.get());
                output.accept(PID_BOARD_ITEM.get());
                output.accept(PIPE_WRENCH.get());
                output.accept(PIPE_ITEM.get());
                output.accept(HAND_VALVE_ITEM.get());
                output.accept(CHECK_VALVE_ITEM.get());
                output.accept(DRAIN_VALVE_ITEM.get());
                output.accept(WATER_INTAKE_ITEM.get());
                output.accept(TANK_ITEM.get());
                output.accept(DRUM_ITEM.get());
                output.accept(IBC_ITEM.get());
                output.accept(PALLET_ITEM.get());
                output.accept(PALLET_TRUCK.get());
                output.accept(PUMP_ITEM.get());
                output.accept(STIRLING_ENGINE_ITEM.get());
                output.accept(BIOGAS_GENERATOR_ITEM.get());
                output.accept(BOILER_ITEM.get());
                output.accept(STEAM_TURBINE_ITEM.get());
                output.accept(SAFETY_VALVE_ITEM.get());
                output.accept(LEVEL_TRANSMITTER_ITEM.get());
                output.accept(FLOW_TRANSMITTER_ITEM.get());
                output.accept(PRESSURE_TRANSMITTER_ITEM.get());
                output.accept(CONTROL_VALVE_ITEM.get());
                output.accept(CONTROLLER_ITEM.get());
                output.accept(CONSOLE_ITEM.get());
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
        ENTITIES.register(modEventBus);
        TABS.register(modEventBus);
        SOUNDS.register(modEventBus);
        MENUS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        TRIGGERS.register(modEventBus);
    }
}
