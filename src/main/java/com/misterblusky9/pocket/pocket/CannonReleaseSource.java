package com.misterblusky9.pocket.pocket;

import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleCommandSource;
import com.misterblusky9.pocket.scale.ScaleController;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class CannonReleaseSource implements ScaleCommandSource {
    private static final double CONTACT_PROBE = 0.05D;

    private final ServerLevel level;
    private final ServerSubLevel subLevel;
    private final Vector3d muzzle;
    private final double bloomDistanceSquared;
    private final long timeoutTick;
    private final long expiresAt;
    private final Vector3d anchorLocal;
    private final CannonExpansionMode mode;
    private boolean released;
    private String jam = "";

    private CannonReleaseSource(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final Vector3dc muzzle,
            final double bloomDistance,
            final long timeoutTick,
            final long expiresAt,
            final Vector3d anchorLocal,
            final CannonExpansionMode mode
    ) {
        this.level = level;
        this.subLevel = subLevel;
        this.muzzle = new Vector3d(muzzle);
        this.bloomDistanceSquared = bloomDistance * bloomDistance;
        this.timeoutTick = timeoutTick;
        this.expiresAt = expiresAt;
        this.anchorLocal = anchorLocal;
        this.mode = mode;
    }

    public static void arm(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final Vec3 muzzle,
            final double bloomDistance,
            final int timeoutTicks
    ) {
        arm(level, subLevel, muzzle, bloomDistance, timeoutTicks, CannonExpansionMode.IMMEDIATE);
    }

    public static void arm(
            final ServerLevel level,
            final ServerSubLevel subLevel,
            final Vec3 muzzle,
            final double bloomDistance,
            final int timeoutTicks,
            final CannonExpansionMode mode
    ) {
        if (mode == CannonExpansionMode.NONE) return;
        final long now = level.getGameTime();
        final long timeoutTick = now + Math.max(1, timeoutTicks);
        final long expiresAt = timeoutTick + 20L * 30L;

        final CannonReleaseSource source = new CannonReleaseSource(
                level,
                subLevel,
                new Vector3d(muzzle.x, muzzle.y, muzzle.z),
                bloomDistance,
                timeoutTick,
                expiresAt,
                new Vector3d(subLevel.logicalPose().rotationPoint()),
                mode
        );
        ScaleController.registerExternalCommandUntil(subLevel, source, expiresAt);
    }

    @Override
    public Vector3d anchorLocalPoint() { return new Vector3d(this.anchorLocal); }

    @Override
    public CompressionStage commandedStage() {
        if (this.released) return CompressionStage.NORMAL;

        if (this.level.getGameTime() >= this.timeoutTick || readyToUnfold()) {
            this.released = true;
            return CompressionStage.NORMAL;
        }

        return CompressionStage.SIXTEENTH;
    }

    private boolean readyToUnfold() {
        if (this.subLevel.isRemoved()) return true;
        return this.mode == CannonExpansionMode.IMPACT ? hasLanded() : pastBloomDistance();
    }

    private boolean pastBloomDistance() {
        return this.subLevel.logicalPose().position().distanceSquared(this.muzzle)
                >= this.bloomDistanceSquared;
    }

    private boolean hasLanded() {
        final BoundingBox3dc box = this.subLevel.boundingBox();
        if (box == null) return false;

        final AABB probe = new AABB(
                box.minX() - CONTACT_PROBE, box.minY() - CONTACT_PROBE, box.minZ() - CONTACT_PROBE,
                box.maxX() + CONTACT_PROBE, box.maxY() + CONTACT_PROBE, box.maxZ() + CONTACT_PROBE);

        return this.level.getBlockCollisions(null, probe).iterator().hasNext();
    }

    @Override
    public boolean tryConsumeTransition(
            final ServerSubLevel subLevel,
            final CompressionStage from,
            final CompressionStage to
    ) {
        return true;
    }

    @Override public void setJamMessage(final String message) { this.jam = message == null ? "" : message; }
    @Override public void clearJamMessage() { this.jam = ""; }

    @Override
    public boolean isRemoved() {
        return this.subLevel.isRemoved() || this.level.getGameTime() > this.expiresAt;
    }
}
