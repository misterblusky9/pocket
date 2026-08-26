package com.misterblusky9.pocket.mixin.explosion;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.misterblusky9.pocket.explosion.BlastSuppression;
import com.misterblusky9.pocket.explosion.ScaledExplosionRelay;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = ActiveSableCompanion.class, remap = false)
public abstract class SableIntersectionFilterMixin {
    @ModifyReturnValue(
            method = "getAllIntersecting(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;)Ljava/lang/Iterable;",
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private Iterable<SubLevel> pocket$hideScaledSubLevelsFromAliasedBlast(final Iterable<SubLevel> intersecting) {
        if (intersecting == null || !BlastSuppression.active()) return intersecting;

        boolean scaled = false;
        for (final SubLevel subLevel : intersecting) {
            if (ScaledExplosionRelay.isScaled(subLevel)) {
                scaled = true;
                break;
            }
        }
        if (!scaled) return intersecting;

        final List<SubLevel> unscaled = new ArrayList<>();
        for (final SubLevel subLevel : intersecting) {
            if (!ScaledExplosionRelay.isScaled(subLevel)) unscaled.add(subLevel);
        }
        return unscaled;
    }
}
