package com.misterblusky9.pym.internal.mixin.companion;

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
    private void pym$emptyWhenDisjoint(
            BoundingBox3ic box,
            CallbackInfoReturnable<BoundingBox3i> cir
    ) {
        BoundingBox3i self = (BoundingBox3i) (Object) this;

        int minX = Math.max(self.minX(), box.minX());
        int minY = Math.max(self.minY(), box.minY());
        int minZ = Math.max(self.minZ(), box.minZ());
        int maxX = Math.min(self.maxX(), box.maxX());
        int maxY = Math.min(self.maxY(), box.maxY());
        int maxZ = Math.min(self.maxZ(), box.maxZ());

        if (minX <= maxX && minY <= maxY && minZ <= maxZ) return;

        self.setUnchecked(minX, minY, minZ, minX - 1, minY - 1, minZ - 1);
        cir.setReturnValue(self);
    }
}
