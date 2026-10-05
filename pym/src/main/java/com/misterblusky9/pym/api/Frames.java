package com.misterblusky9.pym.api;

import com.misterblusky9.pym.internal.physics.ScaleFrame;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class Frames {
    static final Frames INSTANCE = new Frames();

    private Frames() {}

    public Vector3d toWorld(final SubLevel subLevel, final Vector3dc local) {
        final Vector3d result = new Vector3d(local);
        return subLevel == null ? result : subLevel.logicalPose().transformPosition(result);
    }

    public Vec3 toWorld(final SubLevel subLevel, final Vec3 local) {
        return subLevel == null ? local : subLevel.logicalPose().transformPosition(local);
    }

    public Vector3d toLocal(final SubLevel subLevel, final Vector3dc world) {
        final Vector3d result = new Vector3d(world);
        return subLevel == null ? result : subLevel.logicalPose().transformPositionInverse(result);
    }

    public Vec3 toLocal(final SubLevel subLevel, final Vec3 world) {
        return subLevel == null ? world : subLevel.logicalPose().transformPositionInverse(world);
    }

    public Vector3d vectorToWorld(final SubLevel subLevel, final Vector3dc local) {
        final Vector3d result = new Vector3d(local);
        if (subLevel == null) return result;
        result.mul(poseScale(subLevel));
        return subLevel.logicalPose().orientation().transform(result);
    }

    public Vector3d vectorToLocal(final SubLevel subLevel, final Vector3dc world) {
        final Vector3d result = new Vector3d(world);
        if (subLevel == null) return result;
        new Quaterniond(subLevel.logicalPose().orientation()).conjugate().transform(result);
        return result.div(poseScale(subLevel));
    }

    public Vector3d directionToWorld(final SubLevel subLevel, final Vector3dc local) {
        final Vector3d result = new Vector3d(local);
        return subLevel == null ? result : subLevel.logicalPose().orientation().transform(result);
    }

    public Vector3d directionToLocal(final SubLevel subLevel, final Vector3dc world) {
        final Vector3d result = new Vector3d(world);
        return subLevel == null ? result : new Quaterniond(subLevel.logicalPose().orientation()).conjugate().transform(result);
    }

    public double lengthToWorld(final SubLevel subLevel, final double localLength) {
        return subLevel == null ? localLength : localLength * poseScale(subLevel);
    }

    public double lengthToLocal(final SubLevel subLevel, final double worldLength) {
        return subLevel == null ? worldLength : worldLength / poseScale(subLevel);
    }

    public Vector3dc toBodyMetric(final PhysicsPipelineBody body, final Vector3dc plotPoint) {
        return ScaleFrame.toBodyMetric(body, plotPoint);
    }

    private static double poseScale(final SubLevel subLevel) {
        final double scale = subLevel.logicalPose().scale().x();
        return Double.isFinite(scale) && scale > 0.0D ? scale : ScaleBounds.FULL;
    }
}
