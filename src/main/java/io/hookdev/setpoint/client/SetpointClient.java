package io.hookdev.setpoint.client;

import io.hookdev.setpoint.entity.HornVehicle;
import net.minecraft.world.entity.Entity;
import java.util.HashMap;
import java.util.Map;
import io.hookdev.setpoint.network.ForkliftLift;
import io.hookdev.setpoint.entity.ForkliftEntity;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import io.hookdev.setpoint.network.TruckLower;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.PalletBlockEntity;
import io.hookdev.setpoint.block.entity.PortableTankBlockEntity;
import io.hookdev.setpoint.block.entity.ShowsFluid;
import io.hookdev.setpoint.block.entity.TankBlockEntity;
import io.hookdev.setpoint.entity.PalletTruckEntity;
import io.hookdev.setpoint.menu.ConsoleMenu;
import io.hookdev.setpoint.network.ConsoleSnapshot;
import io.hookdev.setpoint.network.TruckHorn;
import io.hookdev.setpoint.pid.PidCapture;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

/** Client-only setup. Never loaded on a dedicated server. */
@Mod(value = Setpoint.MODID, dist = Dist.CLIENT)
public class SetpointClient {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Setpoint.id("setpoint"));
    /** H: the horn of the pallet truck or forklift you are driving, for as long as you hold it. */
    public static final KeyMapping HORN = new KeyMapping("key.setpoint.horn", InputConstants.KEY_H, CATEGORY);
    /** Left CTRL: set the pallet down from the pallet truck you are driving. */
    public static final KeyMapping LOWER = new KeyMapping("key.setpoint.lower", InputConstants.KEY_LCONTROL, CATEGORY);
    /** The arrow keys: the forks of the forklift you drive, up and down. */
    public static final KeyMapping FORKS_UP = new KeyMapping("key.setpoint.forks_up", InputConstants.KEY_UP, CATEGORY);
    public static final KeyMapping FORKS_DOWN = new KeyMapping("key.setpoint.forks_down", InputConstants.KEY_DOWN, CATEGORY);
    /** What the arrow keys and the horn key last told the server. */
    private static int forksSent;
    private static boolean hornSent;
    /** The horns sounding now, by vehicle. */
    private static final Map<Integer, HornSound> HORNS = new HashMap<>();

    public SetpointClient(IEventBus modEventBus) {
        modEventBus.addListener(SetpointClient::registerScreens);
        modEventBus.addListener(SetpointClient::registerBlockTints);
        modEventBus.addListener(SetpointClient::registerRenderers);
        // gases look like a soft cloud in their own colour (in JEI and the like)
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterFluidModelsEvent event) -> {
            var cloud = new net.minecraft.client.resources.model.sprite.Material(Setpoint.id("block/gas"));
            for (var gas : io.hookdev.setpoint.gas.Gases.ALL) {
                event.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(cloud, cloud, null,
                        net.neoforged.neoforge.client.fluid.FluidTintSources.constant(gas.colour())), gas.fluid());
            }
        });
        modEventBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.registerCategory(CATEGORY);
            event.register(HORN);
            event.register(LOWER);
            event.register(FORKS_UP);
            event.register(FORKS_DOWN);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            horn();
            while (LOWER.consumeClick()) sendIfDriving(new TruckLower());
            forks();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> PidBoardTextures.clear());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> BoardLook.tick());
        modEventBus.addListener(SetpointClient::registerPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(SetpointClient::tooltip);
        NeoForge.EVENT_BUS.addListener(SetpointClient::showPidSelection);
    }

    /** While a blank P&ID has its first corner, outline the box from that corner to the block you look at. */
    private static void showPidSelection(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.level.getGameTime() % 4 != 0) return;
        ItemStack held = mc.player.getMainHandItem().is(SetpointContent.BLANK_PID.get()) ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        BlockPos corner = held.is(SetpointContent.BLANK_PID.get()) ? held.get(SetpointContent.PID_CORNER.get()) : null;
        if (corner == null) return;
        BlockPos target = mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : corner;
        double x0 = Math.min(corner.getX(), target.getX()), y0 = Math.min(corner.getY(), target.getY()), z0 = Math.min(corner.getZ(), target.getZ());
        double x1 = Math.max(corner.getX(), target.getX()) + 1, y1 = y0 + PidCapture.HEIGHT, z1 = Math.max(corner.getZ(), target.getZ()) + 1;
        DustParticleOptions dust = new DustParticleOptions(0x2DE2E6, 0.7f);
        double[][] ends = {{x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}};
        for (double[] e : ends) {
            double length = Math.abs(e[3] - e[0]) + Math.abs(e[4] - e[1]) + Math.abs(e[5] - e[2]);
            int steps = (int) Math.min(64, Math.max(1, length * 2));
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                mc.level.addParticle(dust, e[0] + (e[3] - e[0]) * t, e[1] + (e[4] - e[1]) * t, e[2] + (e[5] - e[2]) * t, 0, 0, 0);
            }
        }
    }

    /** Every Setpoint item explains itself in plain words; Shift shows the details. */
    private static void tooltip(ItemTooltipEvent event) {
        var id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (!id.getNamespace().equals(Setpoint.MODID)) return;
        String key = "tooltip.setpoint." + id.getPath();
        var lines = event.getToolTip();
        SimpleFluidContent contents = event.getItemStack().get(SetpointContent.FLUID_CONTENTS.get());
        if (contents != null && !contents.isEmpty()) { // a drum or IBC that was carried away full
            FluidStack stack = contents.copy();
            lines.add(Component.literal(String.format("%s  %.2f B", stack.getHoverName().getString(), stack.getAmount() / 1000.0)).withStyle(ChatFormatting.AQUA));
        }
        if (!Language.getInstance().has(key)) return;
        for (String line : I18n.get(key).split("\n")) lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        if (!Language.getInstance().has(key + ".more")) return;
        if (Minecraft.getInstance().hasShiftDown()) {
            for (String line : I18n.get(key + ".more").split("\n")) lines.add(Component.literal(line).withStyle(ChatFormatting.DARK_AQUA));
        } else {
            lines.add(Component.literal("Hold Shift for details").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ConsoleSnapshot.TYPE, (snapshot, context) -> {
            if (context.player().containerMenu instanceof ConsoleMenu menu && menu.containerId == snapshot.containerId()) menu.receive(snapshot);
        });
    }

    /** Holding H sounds the horn: the server hears when that changes. Every vehicle whose horn is on gets its sound. */
    private static void horn() {
        while (HORN.consumeClick()) { } // only whether it is held counts
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            hornSent = false;
            HORNS.clear();
            return;
        }
        boolean held = HORN.isDown() && driving(mc);
        if (held != hornSent) {
            hornSent = held;
            ClientPacketDistributor.sendToServer(new TruckHorn(held));
        }
        HORNS.values().removeIf(HornSound::isStopped);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof HornVehicle vehicle && vehicle.hornOn() && !HORNS.containsKey(entity.getId())) {
                HornSound sound = entity instanceof PalletTruckEntity truck ? new HornSound(truck) : new HornSound((ForkliftEntity) entity);
                HORNS.put(entity.getId(), sound);
                mc.getSoundManager().play(sound);
            }
        }
    }

    /** You drive a forklift, or hold a pallet truck's tiller. */
    private static boolean driving(Minecraft mc) {
        if (mc.player.getVehicle() instanceof ForkliftEntity) return true;
        for (PalletTruckEntity truck : mc.level.getEntitiesOfClass(PalletTruckEntity.class, mc.player.getBoundingBox().inflate(PalletTruckEntity.REACH))) {
            if (truck.driver() == mc.player) return true;
        }
        return false;
    }

    /** Holding an arrow key moves the forks: the server hears when that changes. */
    private static void forks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof ForkliftEntity)) {
            forksSent = 0;
            return;
        }
        int direction = (FORKS_UP.isDown() ? 1 : 0) - (FORKS_DOWN.isDown() ? 1 : 0);
        if (direction != forksSent) {
            forksSent = direction;
            ClientPacketDistributor.sendToServer(new ForkliftLift(direction));
        }
    }

    /** The pallet truck's CTRL key only does something while you hold its tiller. */
    private static void sendIfDriving(CustomPacketPayload message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.player.getVehicle() instanceof ForkliftEntity) return;
        for (PalletTruckEntity truck : mc.level.getEntitiesOfClass(PalletTruckEntity.class, mc.player.getBoundingBox().inflate(PalletTruckEntity.REACH))) {
            if (truck.driver() == mc.player) {
                ClientPacketDistributor.sendToServer(message);
                return;
            }
        }
    }

    /** P&ID boards draw their drawing over the whole panel; the pallet truck and the forklift are drawn from their models. */
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SetpointContent.PALLET_TRUCK_ENTITY.get(), PalletTruckRenderer::new);
        event.registerEntityRenderer(SetpointContent.FORKLIFT_ENTITY.get(), ForkliftRenderer::new);
        event.registerBlockEntityRenderer(SetpointContent.PID_BOARD_ENTITY.get(), PidBoardRenderer::new);
        event.registerBlockEntityRenderer(SetpointContent.FLOATING_ROOF_TANK_ENTITY.get(), FloatingRoofTankRenderer::new);
    }

    /** The liquid in a tank's sight glass takes the colour of the fluid in that block. */
    private static void registerBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return FluidColours.WATER;
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return level.getBlockEntity(pos) instanceof ShowsFluid tank ? FluidColours.of(tank.shownFluid()) : FluidColours.WATER;
            }
        }), SetpointContent.TANK.get());
        BlockTintSource body = new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return state.is(SetpointContent.IBC.get()) ? PortableTankBlockEntity.IBC_WHITE : PortableTankBlockEntity.DRUM_BLUE;
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return level.getBlockEntity(pos) instanceof PortableTankBlockEntity tank ? tank.bodyColour() : color(state);
            }
        };
        BlockTintSource liquid = new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return FluidColours.WATER;
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                if (!(level.getBlockEntity(pos) instanceof PortableTankBlockEntity tank)) return FluidColours.WATER;
                return tank.colour() == DyeColor.BLACK ? PortableTankBlockEntity.IBC_BLACK : FluidColours.of(tank.shownFluid()); // a black tote keeps it hidden
            }
        };
        event.register(List.of(body), SetpointContent.DRUM.get());
        // what is on a pallet: each drum in its own colour (tint 0 to 3 is drum 1 to 4), or an IBC (its liquid, its tote)
        List<BlockTintSource> drums = new ArrayList<>();
        for (int i = 0; i < PalletBlockEntity.MAX_DRUMS; i++) {
            int index = i;
            drums.add(new BlockTintSource() {
                @Override
                public int color(BlockState state) {
                    return PortableTankBlockEntity.DRUM_BLUE;
                }

                @Override
                public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                    return level.getBlockEntity(pos) instanceof PalletBlockEntity pallet ? PalletLook.colour(pallet.drums(), index) : PortableTankBlockEntity.DRUM_BLUE;
                }
            });
        }
        event.register(drums, SetpointContent.PALLET.get());
        event.register(List.of(liquid, body), SetpointContent.IBC.get());
        // a gas cylinder's shoulder in the colour of its gas
        event.register(List.of(new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return FluidColours.UNKNOWN;
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return level.getBlockEntity(pos) instanceof ShowsFluid cylinder ? FluidColours.of(cylinder.shownFluid()) : FluidColours.UNKNOWN;
            }
        }), SetpointContent.GAS_CYLINDER.get());
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SetpointContent.FACEPLATE.get(), FaceplateScreen::new);
        event.register(SetpointContent.GENERATOR_MENU.get(), GeneratorScreen::new);
        event.register(SetpointContent.TANK_MENU.get(), TankScreen::new);
        event.register(SetpointContent.BOILER_MENU.get(), BoilerScreen::new);
        event.register(SetpointContent.CONSOLE_MENU.get(), ConsoleScreen::new);
    }
}
