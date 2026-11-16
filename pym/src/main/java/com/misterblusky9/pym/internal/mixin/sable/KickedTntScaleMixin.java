package com.misterblusky9.pym.internal.mixin.sable;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.entity.EntityScaleTracker;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntitySubLevelUtil.class, remap = false)
public abstract class KickedTntScaleMixin {
    @Inject(method = "kickEntity", at = @At("HEAD"), remap = false)
    private static void pym$carryPlotScale(SubLevel subLevel, Entity entity, CallbackInfo ci) {
        if (!(entity instanceof PrimedTnt) || entity.level().isClientSide()) return;
        if (subLevel == null || subLevel.isRemoved()) return;
        double scale = subLevel.logicalPose().scale().x();
        if (!ScaleBounds.isValid(scale) || ScaleBounds.same(scale, 1.0D)) return;
        EntityScaleTracker.multiply(entity, scale, 0);
    }
}
