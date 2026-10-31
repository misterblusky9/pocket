package com.misterblusky9.pym.internal.interaction;

import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public final class ScaledPlacementGate {
    private static final double ROTATION_PAD = 0.36602540380000004D;

    private static final double BROADPHASE_PAD = 0.8660254037844386D;

    private static final double CONTACT_TOLERANCE_SQ = 0.05D;

    private static final double MAX_SEARCH_VOLUME = 4096.0D;

    private static final ThreadLocal<LevelReusedVectors> SINK =
            ThreadLocal.withInitial(LevelReusedVectors::new);

    public static Boolean evaluate(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        SubLevel containing = Sable.HELPER.getContaining(level, clicked);

        double selfScale = scaleOf(containing);

        BoundingBox3d worldBox = new BoundingBox3d(clicked);
        Vector3d selfCenter = new Vector3d(
                clicked.getX() + 0.5D, clicked.getY() + 0.5D, clicked.getZ() + 0.5D);
        Quaterniond selfOrientation = new Quaterniond();

        if (containing != null) {
            containing.logicalPose().transformPosition(selfCenter);
            selfOrientation.set(containing.logicalPose().orientation());
            worldBox.transform(containing.logicalPose(), worldBox);
        }

        boolean scaled = containing != null && ScaleState.isScaled(containing);

        BoundingBox3d broad = worldBox.expand(BROADPHASE_PAD, new BoundingBox3d());
        List<SubLevel> others = new ArrayList<>();
        for (SubLevel other : Sable.HELPER.getAllIntersecting(level, broad)) {
            if (other == containing) continue;
            others.add(other);
            scaled |= ScaleState.isScaled(other);
        }

        if (!scaled) return null;

        LevelReusedVectors sink = SINK.get();
        OrientedBoundingBox3d selfBox = new OrientedBoundingBox3d(
                selfCenter, new Vector3d(selfScale), selfOrientation, sink);

        for (SubLevel other : others) {
            if (blocked(context, level, clicked, containing, other, worldBox, selfBox, selfScale, sink)) {
                return Boolean.FALSE;
            }
        }
        return blocked(context, level, clicked, containing, null, worldBox, selfBox, selfScale, sink)
                ? Boolean.FALSE
                : Boolean.TRUE;
    }

    private static boolean blocked(
            BlockPlaceContext context,
            Level level,
            BlockPos clicked,
            SubLevel containing,
            SubLevel other,
            BoundingBox3d worldBox,
            OrientedBoundingBox3d selfBox,
            double selfScale,
            LevelReusedVectors sink
    ) {
        double otherScale = scaleOf(other);

        BoundingBox3d search = other == null
                ? new BoundingBox3d(worldBox)
                : worldBox.transformInverse(other.logicalPose(), new BoundingBox3d());
        search.expand(ROTATION_PAD);
        if (!finite(search) || volume(search) > MAX_SEARCH_VOLUME) return false;

        double contact = CONTACT_TOLERANCE_SQ * sq(Math.min(selfScale, otherScale));

        Vector3d center = new Vector3d();
        Quaterniond orientation = new Quaterniond();

        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(search.minX()), Mth.floor(search.minY()), Mth.floor(search.minZ()),
                Mth.floor(search.maxX()), Mth.floor(search.maxY()), Mth.floor(search.maxZ()))) {

            if (other == containing && pos.equals(clicked)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.canBeReplaced(context)) continue;

            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;

            for (AABB part : shape.toAabbs()) {
                Vec3 middle = part.getCenter();
                center.set(pos.getX() + middle.x, pos.getY() + middle.y, pos.getZ() + middle.z);
                orientation.identity();
                if (other != null) {
                    other.logicalPose().transformPosition(center);
                    orientation.set(other.logicalPose().orientation());
                }

                OrientedBoundingBox3d otherBox = new OrientedBoundingBox3d(
                        center,
                        new Vector3d(part.getXsize(), part.getYsize(), part.getZsize()).mul(otherScale),
                        orientation,
                        sink);

                if (OrientedBoundingBox3d.sat(otherBox, selfBox).lengthSquared() > contact) return true;
            }
        }

        return false;
    }

    private static double scaleOf(SubLevel subLevel) {
        if (subLevel == null) return 1.0D;
        double scale = ScaleState.getScale(subLevel);
        return Double.isFinite(scale) && scale > 1.0E-7D ? scale : 1.0D;
    }

    private static double sq(double value) {
        return value * value;
    }

    private static double volume(BoundingBox3d box) {
        return (box.maxX() - box.minX()) * (box.maxY() - box.minY()) * (box.maxZ() - box.minZ());
    }

    private static boolean finite(BoundingBox3d box) {
        return Double.isFinite(box.minX()) && Double.isFinite(box.minY()) && Double.isFinite(box.minZ())
                && Double.isFinite(box.maxX()) && Double.isFinite(box.maxY()) && Double.isFinite(box.maxZ());
    }

    private ScaledPlacementGate() {}
}
