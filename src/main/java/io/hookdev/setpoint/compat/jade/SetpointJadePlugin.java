package io.hookdev.setpoint.compat.jade;

import io.hookdev.setpoint.Setpoint;
import io.hookdev.setpoint.block.BoilerBlock;
import io.hookdev.setpoint.block.ControlValveBlock;
import io.hookdev.setpoint.block.ControllerBlock;
import io.hookdev.setpoint.block.GeneratorBlock;
import io.hookdev.setpoint.block.LevelTransmitterBlock;
import io.hookdev.setpoint.block.PumpBlock;
import io.hookdev.setpoint.block.SteamTurbineBlock;
import io.hookdev.setpoint.block.TankBlock;
import io.hookdev.setpoint.block.entity.BoilerBlockEntity;
import io.hookdev.setpoint.block.entity.ControlValveBlockEntity;
import io.hookdev.setpoint.block.entity.ControllerBlockEntity;
import io.hookdev.setpoint.block.entity.GeneratorBlockEntity;
import io.hookdev.setpoint.block.entity.LevelTransmitterBlockEntity;
import io.hookdev.setpoint.block.entity.PumpBlockEntity;
import io.hookdev.setpoint.block.entity.SteamTurbineBlockEntity;
import io.hookdev.setpoint.block.entity.TankBlockEntity;
import io.hookdev.setpoint.console.PlantScanner;
import io.hookdev.setpoint.console.Reading;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade support: look at a Setpoint machine and see its tag, status and main value, the same reading the
 * control room shows. Only loaded when Jade is installed.
 */
@WailaPlugin(Setpoint.MODID)
public class SetpointJadePlugin implements IWailaPlugin {
    private static final Identifier UID = Setpoint.id("status");

    private static final Class<?>[] ENTITIES = {PumpBlockEntity.class, TankBlockEntity.class, ControlValveBlockEntity.class,
            ControllerBlockEntity.class, LevelTransmitterBlockEntity.class, BoilerBlockEntity.class, SteamTurbineBlockEntity.class,
            GeneratorBlockEntity.class, io.hookdev.setpoint.block.entity.FlowTransmitterBlockEntity.class,
            io.hookdev.setpoint.block.entity.PressureTransmitterBlockEntity.class, io.hookdev.setpoint.block.entity.SafetyValveBlockEntity.class,
            io.hookdev.setpoint.block.entity.WaterIntakeBlockEntity.class, io.hookdev.setpoint.block.entity.PortableTankBlockEntity.class,
            io.hookdev.setpoint.block.entity.TankPartBlockEntity.class, io.hookdev.setpoint.block.entity.GasMakerBlockEntity.class,
            io.hookdev.setpoint.block.entity.GasCylinderBlockEntity.class, io.hookdev.setpoint.block.entity.GasSphereBlockEntity.class,
            io.hookdev.setpoint.block.entity.GasSpherePartBlockEntity.class, io.hookdev.setpoint.block.entity.CompressorBlockEntity.class,
            io.hookdev.setpoint.block.entity.FuelCellBlockEntity.class, io.hookdev.setpoint.block.entity.AmmoniaReactorBlockEntity.class,
            io.hookdev.setpoint.block.entity.FertilizerMixerBlockEntity.class, io.hookdev.setpoint.block.entity.PumpjackBlockEntity.class,
            io.hookdev.setpoint.block.entity.DistillationColumnBlockEntity.class, io.hookdev.setpoint.block.entity.DieselGeneratorBlockEntity.class,
            io.hookdev.setpoint.block.entity.FlareBlockEntity.class, io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity.class,
            io.hookdev.setpoint.block.entity.BurnerBlockEntity.class};
    @SuppressWarnings("unchecked")
    private static final Class<? extends Block>[] BLOCKS = new Class[]{PumpBlock.class, TankBlock.class, ControlValveBlock.class,
            ControllerBlock.class, LevelTransmitterBlock.class, BoilerBlock.class, SteamTurbineBlock.class, GeneratorBlock.class,
            io.hookdev.setpoint.block.FlowTransmitterBlock.class, io.hookdev.setpoint.block.PressureTransmitterBlock.class,
            io.hookdev.setpoint.block.SafetyValveBlock.class, io.hookdev.setpoint.block.WaterIntakeBlock.class, io.hookdev.setpoint.block.PortableTankBlock.class,
            io.hookdev.setpoint.block.TankPartBlock.class, io.hookdev.setpoint.block.GasMakerBlock.class, io.hookdev.setpoint.block.GasCylinderBlock.class,
            io.hookdev.setpoint.block.GasSphereBlock.class, io.hookdev.setpoint.block.GasSpherePartBlock.class,
            io.hookdev.setpoint.block.CompressorBlock.class, io.hookdev.setpoint.block.PumpjackBlock.class, io.hookdev.setpoint.block.DistillationColumnBlock.class,
            io.hookdev.setpoint.block.FlareBlock.class, io.hookdev.setpoint.block.FlarePartBlock.class};

    @Override
    public void register(IWailaCommonRegistration registration) {
        for (Class<?> entity : ENTITIES) registration.registerBlockDataProvider(StatusData.INSTANCE, entity);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        for (Class<? extends Block> block : BLOCKS) registration.registerBlockComponent(StatusLine.INSTANCE, block);
    }

    /** Server side: read the machine like the console does. */
    enum StatusData implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            var be = accessor.getBlockEntity() instanceof io.hookdev.setpoint.block.entity.TankPartBlockEntity part ? part.tank()
                    : accessor.getBlockEntity() instanceof io.hookdev.setpoint.block.entity.GasSpherePartBlockEntity part ? part.sphere()
                    : accessor.getBlockEntity() instanceof io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity part && part.base() != null ? accessor.getLevel().getBlockEntity(part.base())
                    : accessor.getBlockEntity();
            Reading r = be == null ? null : PlantScanner.read(be);
            if (r == null) return;
            data.putString("setpoint_tag", r.tag());
            data.putString("setpoint_status", r.status());
            data.putInt("setpoint_severity", r.severity());
            data.putString("setpoint_value", r.value());
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    /** Client side: one line with tag and status, one with the value. */
    enum StatusLine implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            String tag = data.getStringOr("setpoint_tag", "");
            if (tag.isEmpty()) return;
            int severity = data.getIntOr("setpoint_severity", Reading.NORMAL);
            ChatFormatting colour = severity == Reading.ALARM ? ChatFormatting.LIGHT_PURPLE : severity == Reading.ATTENTION ? ChatFormatting.GOLD : ChatFormatting.AQUA;
            tooltip.add(Component.literal(tag + "  ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(data.getStringOr("setpoint_status", "")).withStyle(colour)));
            tooltip.add(Component.literal(data.getStringOr("setpoint_value", "")).withStyle(ChatFormatting.WHITE));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
