package com.misterblusky9.pocket.moon;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import org.joml.Vector3dc;
import dev.ryanhcode.sable.api.physics.object.box.BoxPhysicsObject;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

// A sublevel-typed face for the moon. It owns no blocks: the real body stays the
// BoxPhysicsObject, and this reports that body's identity so Sable and Simulated can
// address the moon through their own ServerSubLevel-typed APIs.
//
// Anchors handed to this object are plot-space, exactly as they are for a real sublevel.
// The plot centre is the body origin, and both the pose rotation point and the reported
// centre of mass sit there, which is what makes the two coordinate systems agree.
public final class MoonSubLevel extends ServerSubLevel {
    private final Vector3d plotCenter = new Vector3d();

    public MoonSubLevel(final ServerLevel level, final int plotX, final int plotZ, final Pose3d pose) {
        super(level, plotX, plotZ, pose);
    }

    public Vector3d plotCenter() {
        return new Vector3d(plotCenter);
    }

    // Sable's own plot centre, so our arithmetic cannot disagree with plot.contains().
    public void centerOnPlot() {
        final BlockPos center = getPlot().getCenterBlock();
        setPlotCenter(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D);
    }

    public Vector3d plotAnchor(final double localX, final double localY, final double localZ) {
        return new Vector3d(plotCenter.x + localX, plotCenter.y + localY, plotCenter.z + localZ);
    }

    // The exact check Sable runs inside addConstraint.
    public boolean acceptsPlotAnchor(final Vector3d anchor) {
        return anchor != null && getPlot().contains(anchor);
    }

    public void setPlotCenter(final double x, final double y, final double z) {
        this.plotCenter.set(x, y, z);
        // The rotation point stays at the body origin: the moon's Rapier body is a box
        // in world space, so its local frame is centred on itself, not on a plot.
        this.logicalPose().rotationPoint().set(0.0D, 0.0D, 0.0D);
    }

    // Plot space addresses the moon for lookups; physics wants the body's own frame.
    public Vector3d toBodyLocal(final Vector3dc plotSpace) {
        return new Vector3d(
                plotSpace.x() - plotCenter.x,
                plotSpace.y() - plotCenter.y,
                plotSpace.z() - plotCenter.z
        );
    }

    public BoxPhysicsObject box() {
        return MoonPhysicsTarget.body(getLevel());
    }

    // Pull the box's pose into the sublevel pose so transformPosition works for callers
    // that only know they are holding a ServerSubLevel.
    public void syncFromBody() {
        final BoxPhysicsObject box = box();
        if (box == null) return;

        updateLastPose();
        final Pose3d pose = logicalPose();
        pose.position().set(box.getPose().position());
        pose.orientation().set(box.getPose().orientation());
        pose.rotationPoint().set(0.0D, 0.0D, 0.0D);
        updateBoundingBox();
    }

    public RigidBodyHandle bodyHandle() {
        final BoxPhysicsObject box = box();
        if (box == null) return null;
        return RigidBodyHandle.of(getLevel(), box);
    }

    @Override
    public int getRuntimeId() {
        final BoxPhysicsObject box = box();
        return box == null ? NULL_RUNTIME_ID : box.getRuntimeId();
    }

    @Override
    public MassData getMassTracker() {
        final BoxPhysicsObject box = box();
        return box == null ? super.getMassTracker() : box.getMassTracker();
    }

    @Override
    public boolean isRemoved() {
        if (super.isRemoved()) return true;
        final BoxPhysicsObject box = box();
        return box == null || box.isRemoved();
    }

    @Override
    public void tick() {
        syncFromBody();
        super.tick();
    }

    @Override
    public void updateBoundingBox() {
        final BoxPhysicsObject box = box();
        if (box == null) return;

        // The box reports its own world bounds; the plot has none to derive from.
        box.getBoundingBox(this.globalBounds);
    }

    // Everything below is block-derived bookkeeping a blockless sublevel must not run.

    @Override
    public void buildMassTracker() {}

    @Override
    public void updateMergedMassData(final float partialPhysicsTick) {}

    @Override
    public void prePhysicsTickBegin() {}

    @Override
    public void applyQueuedForces(
            final SubLevelPhysicsSystem physicsSystem,
            final RigidBodyHandle handle,
            final double timeStep
    ) {}

    @Override
    public void prePhysicsTick(
            final SubLevelPhysicsSystem physicsSystem,
            final RigidBodyHandle handle,
            final double timeStep
    ) {}

    @Override
    public void onPlotBoundsChanged() {}
}
