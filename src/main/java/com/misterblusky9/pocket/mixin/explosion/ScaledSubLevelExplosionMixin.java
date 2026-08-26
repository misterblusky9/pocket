package com.misterblusky9.pocket.mixin.explosion;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pocket.explosion.BlastSuppression;
import com.misterblusky9.pocket.explosion.ScaledExplosionRelay;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(value = Explosion.class, priority = 1500)
public abstract class ScaledSubLevelExplosionMixin {
    @Shadow @Final private Level level;

    @Shadow @Final private float radius;

    @Shadow @Final @Mutable private double x;

    @Shadow @Final @Mutable private double y;

    @Shadow @Final @Mutable private double z;

    @Shadow public abstract void explode();

    @Unique private boolean pocket$mirroring;

    @WrapMethod(method = "explode")
    private void pocket$blastScaledSubLevelsInTheirOwnFrame(final Operation<Void> original) {
        if (this.pocket$mirroring || this.level.isClientSide) {
            original.call();
            return;
        }

        BlastSuppression.enter();
        try {
            original.call();
        } finally {
            BlastSuppression.exit();
        }

        final List<SubLevel> targets =
                ScaledExplosionRelay.mirrorTargets(this.level, this.x, this.y, this.z, this.radius);
        if (targets.isEmpty()) return;

        final double worldX = this.x;
        final double worldY = this.y;
        final double worldZ = this.z;

        this.pocket$mirroring = true;
        try {
            for (final SubLevel subLevel : targets) {
                final Vec3 center =
                        ScaledExplosionRelay.plotCenter(this.level, subLevel, worldX, worldY, worldZ);
                if (center == null) continue;

                this.x = center.x;
                this.y = center.y;
                this.z = center.z;
                this.explode();
            }
        } finally {
            this.x = worldX;
            this.y = worldY;
            this.z = worldZ;
            this.pocket$mirroring = false;
        }
    }
}
