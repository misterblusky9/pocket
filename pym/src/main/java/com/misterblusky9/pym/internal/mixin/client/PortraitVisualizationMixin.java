package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.internal.client.PortraitScope;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl", remap = false)
public abstract class PortraitVisualizationMixin {
    @Inject(method = "supportsVisualization", at = @At("HEAD"), cancellable = true, remap = false)
    private static void pym$portraitDrawsBlockEntities(LevelAccessor level, CallbackInfoReturnable<Boolean> cir) {
        if (PortraitScope.active()) cir.setReturnValue(false);
    }
}
