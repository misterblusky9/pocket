package com.misterblusky9.pocket.mixin.simulated;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.misterblusky9.pocket.client.BuildMirrors;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueClientHandler", remap = false)
public abstract class HoneyGlueWearOutlinesMixin {
    @ModifyExpressionValue(
            method = "renderHoneyGlue",
            at = @At(value = "INVOKE",
                    target = "Ldev/simulated_team/simulated/content/entities/honey_glue/HoneyGlueClientHandler;getHoneyGlue(Lnet/minecraft/world/entity/player/Player;)Ljava/util/List;"),
            remap = false
    )
    private List<HoneyGlueEntity> pocket$hideWornGlueOutlines(List<HoneyGlueEntity> visible) {
        return visible.stream().filter(glue -> !BuildMirrors.wornAt(glue.blockMin)
                && !BuildMirrors.wornAt(glue.blockMax)).toList();
    }
}
