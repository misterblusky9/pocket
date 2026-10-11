package com.misterblusky9.pocket.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pocket.scale.PlayerSpawnScale;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class PlayerSpawnScaleReleaseMixin {
    @WrapMethod(method = "releaseUsingItem")
    private void pocket$scaleReleaseSpawns(final Operation<Void> original) {
        if (!((Object) this instanceof final ServerPlayer player)) {
            original.call();
            return;
        }
        PlayerSpawnScale.using(player, null, null, () -> original.call());
    }
}
