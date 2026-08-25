package com.misterblusky9.pocket.mixin.companion;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BoundingBox3i.class, remap = false)
public abstract class BoundingBoxIntersectMixin {
    @Inject(
            method = "intersect(Ldev/ryanhcode/sable/companion/math/BoundingBox3ic;)Ldev/ryanhcode/sable/companion/math/BoundingBox3i;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void pocket$emptyWhenDisjoint(
            final BoundingBox3ic box,
            final CallbackInfoReturnable<BoundingBox3i> cir
    ) {
        final BoundingBox3i self = (BoundingBox3i) (Object) this;

        final int minX = Math.max(self.minX(), box.minX());
        final int minY = Math.max(self.minY(), box.minY());
        final int minZ = Math.max(self.minZ(), box.minZ());
        final int maxX = Math.min(self.maxX(), box.maxX());
        final int maxY = Math.min(self.maxY(), box.maxY());
        final int maxZ = Math.min(self.maxZ(), box.maxZ());

        if (minX <= maxX && minY <= maxY && minZ <= maxZ) return;

        self.setUnchecked(minX, minY, minZ, minX - 1, minY - 1, minZ - 1);
        cir.setReturnValue(self);
    }
}
