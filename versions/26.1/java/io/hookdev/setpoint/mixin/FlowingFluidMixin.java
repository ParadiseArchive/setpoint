package io.hookdev.setpoint.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.hookdev.setpoint.Setpoint;

import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Minecraft 26.1 lets flowing water and lava wash away every block that is not solid, so pipes, cables, valves,
 * transmitters and multiblock parts would float off. 26.3 only washes away blocks tagged for it. This makes 26.1
 * do the same for Setpoint's blocks: liquids flow round them, as on 26.3.
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
    @Inject(method = "canHoldAnyFluid", at = @At("HEAD"), cancellable = true)
    private static void setpoint$flowRoundSetpointBlocks(BlockState state, CallbackInfoReturnable<Boolean> result) {
        if (!(state.getBlock() instanceof LiquidBlockContainer)
                && state.getBlock().builtInRegistryHolder().key().identifier().getNamespace().equals(Setpoint.MODID)) {
            result.setReturnValue(false);
        }
    }
}
