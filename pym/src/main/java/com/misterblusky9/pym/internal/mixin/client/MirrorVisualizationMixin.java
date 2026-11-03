package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.api.client.Mirrors;
import dev.ryanhcode.sable.Sable;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage", remap = false)
public abstract class MirrorVisualizationMixin {
    @Inject(method = "willAccept(Lnet/minecraft/world/level/block/entity/BlockEntity;)Z", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void pym$portraitDrawsMirrors(BlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        if (blockEntity.getLevel() != null && Mirrors.isMirror(Sable.HELPER.getContaining(blockEntity)))
            cir.setReturnValue(false);
    }
}
