package io.hookdev.setpoint;

import io.hookdev.setpoint.network.ForkliftLift;
import io.hookdev.setpoint.network.TruckLower;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import io.hookdev.setpoint.block.entity.NamedEquipment;
import io.hookdev.setpoint.gametest.SetpointGameTests;
import io.hookdev.setpoint.network.ConsoleSnapshot;
import io.hookdev.setpoint.network.PidRename;
import io.hookdev.setpoint.network.TruckHorn;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Setpoint: process engineering for Minecraft. Pumps, tanks, valves and control loops
 * that behave like the real thing.
 */
@Mod(Setpoint.MODID)
public class Setpoint {
    public static final String MODID = "setpoint";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Setpoint(IEventBus modEventBus, ModContainer modContainer) {
        SetpointContent.register(modEventBus);
        modContainer.registerConfig(McVersion.WORLD_CONFIG, SetpointConfig.SPEC);
        SetpointGameTests.register(modEventBus);
        modEventBus.addListener(Setpoint::registerCapabilities);
        modEventBus.addListener(Setpoint::registerPayloads);
        NeoForge.EVENT_BUS.addListener(Setpoint::giveHandbook);
        NeoForge.EVENT_BUS.addListener(Setpoint::nameEquipment);
        NeoForge.EVENT_BUS.addListener(io.hookdev.setpoint.showcase.ShowcaseCommand::register);
        LOGGER.info("Setpoint loaded. Holding steady.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    /** New players get the handbook once, unless the config says not to. */
    private static void giveHandbook(PlayerEvent.PlayerLoggedInEvent event) {
        var player = event.getEntity();
        if (player.level().isClientSide() || !SetpointConfig.giveHandbook() || player.getData(SetpointContent.HANDBOOK_GIVEN)) return;
        player.setData(SetpointContent.HANDBOOK_GIVEN, true);
        ItemStack book = new ItemStack(SetpointContent.HANDBOOK.get());
        if (!player.getInventory().add(book)) McVersion.drop(player, book);
    }

    /** A renamed name tag on a pump, tank, boiler or other machine gives it that tag, like P-FEED. Instruments do this themselves. */
    private static void nameEquipment(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        Component name = stack.getCustomName();
        if (!stack.is(Items.NAME_TAG) || name == null) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof NamedEquipment equipment)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide() || !equipment.rename(name.getString())) return;
        event.getEntity().sendSystemMessage(Component.literal("Tag set to " + equipment.tag()).withStyle(ChatFormatting.AQUA));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // The control room screen's live data; the client handler is registered in SetpointClient.
        event.registrar("1")
                .playToClient(ConsoleSnapshot.TYPE, ConsoleSnapshot.CODEC)
                .playToServer(PidRename.TYPE, PidRename.CODEC, PidRename::handle)
                .playToServer(TruckHorn.TYPE, TruckHorn.CODEC, TruckHorn::handle)
                .playToServer(TruckLower.TYPE, TruckLower.CODEC, TruckLower::handle)
                .playToServer(ForkliftLift.TYPE, ForkliftLift.CODEC, ForkliftLift::handle);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Tanks hold fluid for buckets, pumps and other mods' pipes.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SetpointContent.TANK_ENTITY.get(), (tank, side) -> tank.fluid());
        // Drums and IBCs hold fluid for buckets, pumps and other mods' pipes too.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SetpointContent.PORTABLE_TANK_ENTITY.get(), (tank, side) -> tank.fluid());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SetpointContent.TANK_PART_ENTITY.get(), (part, side) -> part.tank() == null ? null : part.tank().fluid());
        // Hoppers can feed the tank's bucket slot and take the result out below.
        event.registerBlockEntity(Capabilities.Item.BLOCK, SetpointContent.TANK_ENTITY.get(), (tank, side) -> tank.buckets());
        // Power cables take FE from generators and hand it straight on to the machines along them.
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, blockEntity, side) -> io.hookdev.setpoint.power.PowerNetwork.handler(level, pos), SetpointContent.POWER_CABLE.get(), SetpointContent.POWER_METER.get(),
                SetpointContent.CABLE_TRAY.get(), SetpointContent.TRANSFORMER.get());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.BATTERY_BANK_ENTITY.get(), (battery, side) -> battery.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.WIND_TURBINE_ENTITY.get(), (turbine, side) -> turbine.energy());
        // Pumps take FE from any side; cables from other mods can power them.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.PUMP_ENTITY.get(), (pump, side) -> pump.energy());
        // Generators: cables can pull their FE, hoppers can feed their fuel slot.
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.STIRLING_ENGINE_ENTITY.get(), (generator, side) -> generator.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.BIOGAS_GENERATOR_ENTITY.get(), (generator, side) -> generator.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SetpointContent.STIRLING_ENGINE_ENTITY.get(), (generator, side) -> generator.input());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SetpointContent.BIOGAS_GENERATOR_ENTITY.get(), (generator, side) -> generator.input());
        // Boilers: pipes and buckets fill the water drum, hoppers feed the firebox. Turbines give FE to cables.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SetpointContent.BOILER_ENTITY.get(), (boiler, side) -> side == net.minecraft.core.Direction.UP ? boiler.steamExport() : boiler.fluid());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SetpointContent.BURNER_ENTITY.get(), (burner, side) -> burner.fluid());
        // The water intake gives river water to other mods' pipes too.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SetpointContent.WATER_INTAKE_ENTITY.get(), (intake, side) -> intake.fluid());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SetpointContent.BOILER_ENTITY.get(), (boiler, side) -> boiler.fuel());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.STEAM_TURBINE_ENTITY.get(), (turbine, side) -> turbine.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.ELECTROLYSER_ENTITY.get(), (maker, side) -> maker.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.NITROGEN_GENERATOR_ENTITY.get(), (maker, side) -> maker.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.COMPRESSOR_ENTITY.get(), (compressor, side) -> compressor.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.FUEL_CELL_ENTITY.get(), (cell, side) -> cell.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.PUMPJACK_ENTITY.get(), (pumpjack, side) -> pumpjack.energy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SetpointContent.PUMPJACK_ENTITY.get(), (pumpjack, side) -> pumpjack.fluid());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SetpointContent.DISTILLATION_COLUMN_ENTITY.get(), (column, side) -> column.fluid());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SetpointContent.DIESEL_GENERATOR_ENTITY.get(), (generator, side) -> generator.fluid());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.DIESEL_GENERATOR_ENTITY.get(), (generator, side) -> generator.energy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SetpointContent.COLUMN_PART_ENTITY.get(), (part, side) -> part.fluid());
        // power can go into any block of the pumpjack: its parts hand it to the wellhead
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.PUMPJACK_PART_ENTITY.get(), (part, side) ->
                part.base() != null && part.getLevel() != null && part.getLevel().getBlockEntity(part.base()) instanceof io.hookdev.setpoint.block.entity.PumpjackBlockEntity pumpjack ? pumpjack.energy() : null);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SetpointContent.FUEL_CELL_ENTITY.get(), (cell, side) -> cell.fluid());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SetpointContent.AMMONIA_REACTOR_ENTITY.get(), (reactor, side) -> reactor.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SetpointContent.FERTILIZER_MIXER_ENTITY.get(), (mixer, side) -> mixer.items());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SetpointContent.ELECTROLYSER_ENTITY.get(), (electrolyser, side) -> electrolyser.fluid());
    }
}
