package com.misterblusky9.pocket.compat.photomancy.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.misterblusky9.pocket.compat.photomancy.PhotomancyCompat;
import dev.rew1nd.sableschematicapi.network.BlueprintToolServerActions;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlueprintToolServerActions.class, remap = false)
public abstract class BlueprintToolLookedHatMixin {
    @ModifyReturnValue(method = "lookedSubLevel", at = @At("RETURN"))
    private static ServerSubLevel pocket$skipHats(ServerSubLevel target) {
        return target != null && PhotomancyCompat.isHat(target) ? null : target;
    }
}
