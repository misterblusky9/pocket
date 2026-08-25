package com.misterblusky9.pocket.mixin.client;

import dev.ryanhcode.sable.Sable;
import net.createmod.catnip.outliner.AABBOutline;
import net.minecraft.core.Position;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = AABBOutline.class, remap = false)
public abstract class OutlineFaceCullMixin {
    @Shadow
    protected AABB bb;

    @Redirect(
            method = "renderBox",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/AABB;contains(Lnet/minecraft/world/phys/Vec3;)Z"
            ),
            remap = false
    )
    private boolean pocket$dontCullFacesOfASubLevelOutline(final AABB box, final Vec3 camera) {
        if (this.bb != null
                && Sable.HELPER.getContainingClient((Position) this.bb.getCenter()) != null) {
            return true;
        }
        return box.contains(camera);
    }
}
