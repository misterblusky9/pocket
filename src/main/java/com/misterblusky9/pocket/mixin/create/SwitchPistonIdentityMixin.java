package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.block.SwitchPistonBlock;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MechanicalPistonBlock.class, remap = false)
public abstract class SwitchPistonIdentityMixin {
    @Inject(
            method = "isStickyPiston",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private static void pocket$switchPistonIsSticky(
            final BlockState state,
            final CallbackInfoReturnable<Boolean> cir
    ) {
        if (state.getBlock() instanceof SwitchPistonBlock) {
            cir.setReturnValue(true);
        }
    }
}
