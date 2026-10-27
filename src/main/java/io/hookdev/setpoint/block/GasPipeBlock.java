package io.hookdev.setpoint.block;

import io.hookdev.setpoint.gas.GasPortBlock;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A gas pipe: steel with a yellow band, the colour gas lines get on a real plant. It joins other gas pipes and the gas
 * connections of machines, never a liquid pipe or a tank. Like a liquid pipe it holds nothing itself: gas goes
 * straight from the vessel at one end to the vessel at the other, from high pressure to low. The pipe wrench cuts it.
 */
public class GasPipeBlock extends FluidPipeBlock {
    public GasPipeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Does a gas pipe join this neighbour, which lies {@code direction} from it? */
    static boolean joins(BlockState neighbour, Direction direction) {
        if (neighbour.getBlock() instanceof GasPipeBlock) return !neighbour.getValue(CUT.get(direction.getOpposite()));
        if (neighbour.getBlock() instanceof io.hookdev.setpoint.gas.GasLine line) return line.joinsGas(neighbour, direction.getOpposite());
        return neighbour.getBlock() instanceof GasPortBlock port && port.gasPort(neighbour, direction.getOpposite());
    }
}
