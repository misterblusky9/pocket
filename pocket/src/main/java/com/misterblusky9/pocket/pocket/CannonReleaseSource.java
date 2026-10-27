package com.misterblusky9.pocket.pocket;

import com.misterblusky9.pym.api.Pym;

import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleCommandSource;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pocket.scale.ScaleLimits;
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
    private final double pocketedScale;
    private boolean released;
    private boolean finished;
    private String jam = "";

    private CannonReleaseSource(
            ServerLevel level,
            ServerSubLevel subLevel,
            Vector3dc muzzle,
            double bloomDistance,
            long timeoutTick,
            long expiresAt,
            Vector3d anchorLocal,
            CannonExpansionMode mode,
            double pocketedScale
    ) {
        this.level = level;
        this.subLevel = subLevel;
        this.muzzle = new Vector3d(muzzle);
        this.bloomDistanceSquared = bloomDistance * bloomDistance;
        this.timeoutTick = timeoutTick;
        this.expiresAt = expiresAt;
        this.anchorLocal = anchorLocal;
        this.mode = mode;
        this.pocketedScale = pocketedScale;
    }

    public static void arm(
            ServerLevel level,
            ServerSubLevel subLevel,
            Vec3 muzzle,
            double bloomDistance,
            int timeoutTicks
    ) {
        arm(level, subLevel, muzzle, bloomDistance, timeoutTicks, CannonExpansionMode.IMMEDIATE);
    }

    public static void arm(
            ServerLevel level,
            ServerSubLevel subLevel,
            Vec3 muzzle,
            double bloomDistance,
            int timeoutTicks,
            CannonExpansionMode mode
    ) {
        if (mode == CannonExpansionMode.NONE) return;
        long now = level.getGameTime();
        long timeoutTick = now + Math.max(1, timeoutTicks);
        long expiresAt = timeoutTick + 20L * 30L;

        CannonReleaseSource source = new CannonReleaseSource(
                level,
                subLevel,
                new Vector3d(muzzle.x, muzzle.y, muzzle.z),
                bloomDistance,
                timeoutTick,
                expiresAt,
                new Vector3d(subLevel.logicalPose().rotationPoint()),
                mode,
                Pym.scale().settled(subLevel)
        );
        Pym.resize().drive(subLevel, source, expiresAt);
    }

    @Override
    public Vector3d anchorLocalPoint() { return new Vector3d(this.anchorLocal); }

    @Override
    public ScaleBounds bounds() { return ScaleLimits.CANNON_RELEASE; }

    @Override
    public double commandedScale() {
        if (this.released) return CompressionStage.NORMAL.scale();

        if (this.level.getGameTime() >= this.timeoutTick || readyToUnfold()) {
            this.released = true;
            return CompressionStage.NORMAL.scale();
        }

        return this.pocketedScale;
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
        BoundingBox3dc box = this.subLevel.boundingBox();
        if (box == null) return false;

        AABB probe = new AABB(
                box.minX() - CONTACT_PROBE, box.minY() - CONTACT_PROBE, box.minZ() - CONTACT_PROBE,
                box.maxX() + CONTACT_PROBE, box.maxY() + CONTACT_PROBE, box.maxZ() + CONTACT_PROBE);

        return this.level.getBlockCollisions(null, probe).iterator().hasNext();
    }

    @Override
    public boolean tryConsumeTransition(
            ServerSubLevel subLevel,
            CompressionStage from,
            CompressionStage to
    ) {
        return true;
    }

    @Override public void setJamMessage(String message) { this.jam = message == null ? "" : message; }
    @Override public void clearJamMessage() { this.jam = ""; }

    @Override
    public boolean isRemoved() {
        if (this.finished) return true;
        long now = this.level.getGameTime();
        if (this.subLevel.isRemoved() || now > this.expiresAt
                || Pym.resize().driversSuspended(this.subLevel.getUniqueId(), now)
                || this.released && ScaleBounds.same(Pym.scale().settled(this.subLevel), CompressionStage.NORMAL.scale())) {
            this.finished = true;
        }
        return this.finished;
    }
}
