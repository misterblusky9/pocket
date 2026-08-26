package com.misterblusky9.pocket.mixin.createbigcannons;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.misterblusky9.pocket.interaction.ScaledSurfaceNormal;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
        targets = "rbasamoyai.createbigcannons.compat.sable.SableCannonProjectileCompat$NormalTransformer",
        remap = false
)
public abstract class CannonSurfaceNormalMixin {
    @ModifyReturnValue(
            method = "apply(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private Vec3 pocket$unitSurfaceNormal(final Vec3 normal) {
        if (normal == null) return null;
        if (!ScaledSurfaceNormal.needsUnit(normal.x, normal.y, normal.z)) return normal;

        final double[] unit = ScaledSurfaceNormal.unit(normal.x, normal.y, normal.z);
        return new Vec3(unit[0], unit[1], unit[2]);
    }
}
