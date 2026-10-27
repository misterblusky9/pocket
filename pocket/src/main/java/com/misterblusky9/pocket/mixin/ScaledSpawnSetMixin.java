package com.misterblusky9.pocket.mixin;

import com.misterblusky9.pocket.entity.ScaledRespawn;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerPlayer.class, priority = 1100)
public abstract class ScaledSpawnSetMixin {
    @Inject(method = "setRespawnPosition", at = @At("HEAD"), cancellable = true)
    private void pocket$storeRelativeSpawnScale(ResourceKey<Level> level, BlockPos pos,
                                                  float angle, boolean forced, boolean notify,
                                                  CallbackInfo ci) {
        if (!ScaledRespawn.settingSpawn((ServerPlayer) (Object) this, level, pos)) ci.cancel();
    }
}
