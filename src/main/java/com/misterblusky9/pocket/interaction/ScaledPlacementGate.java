package com.misterblusky9.pocket.interaction;

import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public final class ScaledPlacementGate {
    // (sqrt(3) - 1) / 2 -- reach of a rotated unit cube past its axis-aligned footprint, in LOCAL units
    private static final double ROTATION_PAD = 0.36602540380000004D;

    // sqrt(3) / 2 -- circumradius of a full-size cube, in WORLD units
    private static final double BROADPHASE_PAD = 0.8660254037844386D;

    // squared MTV depth tolerated between two blocks of scale 1
    private static final double CONTACT_TOLERANCE_SQ = 0.05D;

    // degenerate pose backstop; a correctly scaled search is ~2^3
    private static final double MAX_SEARCH_VOLUME = 4096.0D;

    private static final ThreadLocal<LevelReusedVectors> SINK =
            ThreadLocal.withInitial(LevelReusedVectors::new);

    // null -- no scale in play, defer to Sable
    public static Boolean evaluate(final BlockPlaceContext context) {
        final Level level = context.getLevel();
        final BlockPos clicked = context.getClickedPos();
        final SubLevel containing = Sable.HELPER.getContaining(level, clicked);

        final double selfScale = scaleOf(containing);

        final BoundingBox3d worldBox = new BoundingBox3d(clicked);
        final Vector3d selfCenter = new Vector3d(
                clicked.getX() + 0.5D, clicked.getY() + 0.5D, clicked.getZ() + 0.5D);
        final Quaterniond selfOrientation = new Quaterniond();

        if (containing != null) {
            containing.logicalPose().transformPosition(selfCenter);
            selfOrientation.set(containing.logicalPose().orientation());
            worldBox.transform(containing.logicalPose(), worldBox);
        }

        boolean scaled = containing != null && ScaleState.isScaled(containing);

        final BoundingBox3d broad = worldBox.expand(BROADPHASE_PAD, new BoundingBox3d());
        final List<SubLevel> others = new ArrayList<>();
        for (final SubLevel other : Sable.HELPER.getAllIntersecting(level, broad)) {
            if (other == containing) continue;
            others.add(other);
            scaled |= ScaleState.isScaled(other);
        }

        if (!scaled) return null;

        final LevelReusedVectors sink = SINK.get();
        final OrientedBoundingBox3d selfBox = new OrientedBoundingBox3d(
                selfCenter, new Vector3d(selfScale), selfOrientation, sink);

        for (final SubLevel other : others) {
            if (blocked(context, level, clicked, containing, other, worldBox, selfBox, selfScale, sink)) {
                return Boolean.FALSE;
            }
        }
        return blocked(context, level, clicked, containing, null, worldBox, selfBox, selfScale, sink)
                ? Boolean.FALSE
                : Boolean.TRUE;
    }

    private static boolean blocked(
            final BlockPlaceContext context,
            final Level level,
            final BlockPos clicked,
            final SubLevel containing,
            final SubLevel other,
            final BoundingBox3d worldBox,
            final OrientedBoundingBox3d selfBox,
            final double selfScale,
            final LevelReusedVectors sink
    ) {
        final double otherScale = scaleOf(other);

        // pad in the frame we iterate, so the search stays ~2^3 candidates at any scale
        final BoundingBox3d search = other == null
                ? new BoundingBox3d(worldBox)
                : worldBox.transformInverse(other.logicalPose(), new BoundingBox3d());
        search.expand(ROTATION_PAD);
        if (!finite(search) || volume(search) > MAX_SEARCH_VOLUME) return false;

        final double contact = CONTACT_TOLERANCE_SQ * sq(Math.min(selfScale, otherScale));

        final Vector3d center = new Vector3d();
        final Quaterniond orientation = new Quaterniond();

        for (final BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(search.minX()), Mth.floor(search.minY()), Mth.floor(search.minZ()),
                Mth.floor(search.maxX()), Mth.floor(search.maxY()), Mth.floor(search.maxZ()))) {

            if (other == containing && pos.equals(clicked)) continue;
            if (level.getBlockState(pos).canBeReplaced(context)) continue;

            center.set(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            orientation.identity();
            if (other != null) {
                other.logicalPose().transformPosition(center);
                orientation.set(other.logicalPose().orientation());
            }

            final OrientedBoundingBox3d otherBox = new OrientedBoundingBox3d(
                    center, new Vector3d(otherScale), orientation, sink);

            if (OrientedBoundingBox3d.sat(otherBox, selfBox).lengthSquared() > contact) return true;
        }

        return false;
    }

    private static double scaleOf(final SubLevel subLevel) {
        if (subLevel == null) return 1.0D;
        final double scale = ScaleState.getScale(subLevel);
        return Double.isFinite(scale) && scale > 1.0E-7D ? scale : 1.0D;
    }

    private static double sq(final double value) {
        return value * value;
    }

    private static double volume(final BoundingBox3d box) {
        return (box.maxX() - box.minX()) * (box.maxY() - box.minY()) * (box.maxZ() - box.minZ());
    }

    private static boolean finite(final BoundingBox3d box) {
        return Double.isFinite(box.minX()) && Double.isFinite(box.minY()) && Double.isFinite(box.minZ())
                && Double.isFinite(box.maxX()) && Double.isFinite(box.maxY()) && Double.isFinite(box.maxZ());
    }

    private ScaledPlacementGate() {}
}
