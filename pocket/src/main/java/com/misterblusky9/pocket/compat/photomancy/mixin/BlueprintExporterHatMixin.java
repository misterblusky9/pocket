package com.misterblusky9.pocket.compat.photomancy.mixin;

import com.misterblusky9.pocket.compat.photomancy.PhotomancyCompat;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = SableBlueprintExporter.class, remap = false)
public abstract class BlueprintExporterHatMixin {
    @ModifyVariable(
            method = "export(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/companion/math/BoundingBox3d;Ljava/lang/Iterable;)Ldev/rew1nd/sableschematicapi/blueprint/SableBlueprint;",
            at = @At("HEAD"),
            argsOnly = true)
    private static Iterable<? extends SubLevel> pocket$skipHats(Iterable<? extends SubLevel> candidates) {
        List<SubLevel> kept = new ArrayList<>();
        for (SubLevel subLevel : candidates) {
            if (!PhotomancyCompat.isHat(subLevel)) kept.add(subLevel);
        }
        return kept;
    }
}
