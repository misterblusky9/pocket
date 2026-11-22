package com.misterblusky9.pym.internal.mixin.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.internal.PlacedSubLevels;
import com.misterblusky9.pym.internal.collision.SubLevelIndexedCollision;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CanFallAtleastHelper.class, remap = false)
public abstract class SubLevelStandupWornMixin {
    @WrapOperation(
            method = "canFallAtleastWithSubLevels",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/ActiveSableCompanion;getAllIntersecting(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;)Ljava/lang/Iterable;"),
            remap = false,
            require = 1
    )
    private static Iterable<SubLevel> pym$solidAndNotWorn(
            ActiveSableCompanion helper, Level level, BoundingBox3dc bounds, Operation<Iterable<SubLevel>> original) {
        return PlacedSubLevels.colliding(original.call(helper, level, bounds), level, bounds,
                SubLevelIndexedCollision.prober());
    }
}
