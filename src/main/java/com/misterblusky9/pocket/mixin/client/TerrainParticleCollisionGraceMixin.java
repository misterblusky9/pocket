package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.PocketSized;
import dev.ryanhcode.sable.api.particle.ParticleSubLevelKickable;
import dev.ryanhcode.sable.mixinterface.particle.ParticleExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.particle.TerrainParticle;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TerrainParticle.class)
public abstract class TerrainParticleCollisionGraceMixin implements ParticleSubLevelKickable {
    @Override
    public boolean sable$shouldCareAboutIntersectingSubLevels() {
        return !pocket$collisionGraceActive();
    }

    @Override
    public boolean sable$shouldKickFromTracking() {
        return true;
    }

    @Override
    public boolean sable$shouldCollideWithTrackingSubLevel() {
        return !pocket$collisionGraceActive();
    }

    private boolean pocket$collisionGraceActive() {
        final SubLevel tracked = ((ParticleExtension) this).sable$getTrackingSubLevel();
        if (!(tracked instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) return false;

        final Vector3dc scale = subLevel.renderPose().scale();
        if (Math.abs(scale.x() - scale.y()) > PocketSized.EPSILON
                || Math.abs(scale.x() - scale.z()) > PocketSized.EPSILON) return false;

        final double uniform = scale.x();
        if (!Double.isFinite(uniform) || uniform <= 0.0D
                || uniform >= 1.0D - PocketSized.EPSILON) return false;

        final int graceTicks = uniform <= 0.125D ? 3 : uniform <= 0.25D ? 2 : 1;
        return ((ParticleMotionAccessor) this).pocket$getAge() < graceTicks;
    }
}
