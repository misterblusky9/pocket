package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.misterblusky9.pym.internal.compat.simulated.SimulatedGlueLookupContext;
import dev.ryanhcode.sable.util.SubLevelInclusiveLevelEntityGetter;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelInclusiveLevelEntityGetter.class, remap = false)
public abstract class SubLevelInclusiveGlueLookupMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(
            method = "get(Lnet/minecraft/world/level/entity/EntityTypeTest;"
                    + "Lnet/minecraft/world/phys/AABB;"
                    + "Lnet/minecraft/util/AbortableIterationConsumer;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void pym$skipRecursiveSubLevelGlueLookup(
            final EntityTypeTest entityTypeTest,
            final AABB bounds,
            final AbortableIterationConsumer consumer,
            final CallbackInfo ci
    ) {
        if (!SimulatedGlueLookupContext.active()) return;

        ((SubLevelInclusiveLevelEntityGetter) (Object) this)
                .getIgnoringSubLevels(entityTypeTest, bounds, consumer);
        ci.cancel();
    }
}
