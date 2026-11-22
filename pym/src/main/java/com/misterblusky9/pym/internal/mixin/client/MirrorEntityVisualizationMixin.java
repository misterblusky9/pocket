package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.api.client.Mirrors;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.storage.EntityStorage", remap = false)
public abstract class MirrorEntityVisualizationMixin {
    @Inject(method = "willAccept(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void pym$portraitDrawsMirrorEntities(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Mirrors.holds(entity)) cir.setReturnValue(false);
    }
}
