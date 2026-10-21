package io.hookdev.setpoint.block.entity;

import io.hookdev.setpoint.SetpointContent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Endless FE for testing: pushes energy into every neighbour that accepts it. */
public class CreativeGeneratorBlockEntity extends BlockEntity implements NamedEquipment {
    private final EquipmentName name = new EquipmentName("G");

    public static final int OUTPUT_PER_SIDE = 1_000;

    public CreativeGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(SetpointContent.CREATIVE_GENERATOR_ENTITY.get(), pos, state);
    }

    @Override
    public String tag() { return name.tag(worldPosition); }

    @Override
    public boolean rename(String newName) {
        if (!name.rename(newName)) return false;
        setChanged();
        return true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        name.save(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        name.load(input);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CreativeGeneratorBlockEntity generator) {
        for (Direction direction : Direction.values()) {
            BlockPos target = pos.relative(direction);
            if (!level.isLoaded(target)) continue;
            EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, target, direction.getOpposite());
            if (handler == null) continue;
            try (Transaction transaction = Transaction.openRoot()) {
                handler.insert(OUTPUT_PER_SIDE, transaction);
                transaction.commit();
            }
        }
    }
}
