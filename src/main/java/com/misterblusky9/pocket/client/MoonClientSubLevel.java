package com.misterblusky9.pocket.client;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

// Client-side twin of MoonSubLevel. Like the server shim it owns no blocks and is not
// registered with the container; it exists so Simulated's client code can resolve the
// moon through getContainingClient and send its uuid and plot anchors to the server.
public final class MoonClientSubLevel extends ClientSubLevel {
    private final Vector3d plotCenter = new Vector3d();
    private final Pose3d moonPose = new Pose3d();

    public MoonClientSubLevel(final Level level, final int plotX, final int plotZ, final Pose3d pose) {
        super(level, plotX, plotZ, pose);
        centerOnPlot();
    }

    public void centerOnPlot() {
        final BlockPos center = getPlot().getCenterBlock();
        plotCenter.set(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D);
        logicalPose().rotationPoint().set(0.0D, 0.0D, 0.0D);
        moonPose.rotationPoint().set(0.0D, 0.0D, 0.0D);
    }

    public Vector3d plotCenter() {
        return new Vector3d(plotCenter);
    }

    public Vector3d plotAnchor(final double localX, final double localY, final double localZ) {
        return new Vector3d(plotCenter.x + localX, plotCenter.y + localY, plotCenter.z + localZ);
    }

    public boolean acceptsPlotAnchor(final Vector3d anchor) {
        return anchor != null && getPlot().contains(anchor);
    }

    // Body-local -> plot space, the coordinates Simulated hands back to the server.
    public BlockPos plotBlock(final Vector3d localPoint) {
        final Vector3d anchor = plotAnchor(localPoint.x, localPoint.y, localPoint.z);
        return BlockPos.containing(anchor.x, anchor.y, anchor.z);
    }

    public void sync(final float partialTick) {
        final MoonPhysicsClient.RenderState state = MoonPhysicsClient.renderState(partialTick);
        if (state == null) return;

        updateLastPose();
        final Pose3d pose = logicalPose();
        pose.position().set(state.position().x, state.position().y, state.position().z);
        pose.orientation().set(state.orientation());
        pose.rotationPoint().set(0.0D, 0.0D, 0.0D);
        moonPose.set(pose);
    }

    @Override
    public Pose3dc renderPose() {
        return moonPose;
    }

    @Override
    public Pose3dc renderPose(final float partialTick) {
        sync(partialTick);
        return moonPose;
    }

    @Override
    public void updateBoundingBox() {
        // No plot bounds to derive from; the moon's bounds come from its own state.
    }

    @Override
    public void onPlotBoundsChanged() {}
}
