package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects;

import com.misterblusky9.pym.api.ScaleBounds;
import com.simibubi.create.foundation.blockEntity.CachedRenderBBBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class LinkageRenderScale {
    public static final double VIEW_DISTANCE = 64.0D;
    public static final double ROD_REACH = 20.0D;

    private static double previewStart = Double.NaN;
    private static double previewTarget = Double.NaN;
    private static double pendingCandidate = 1.0D;

    public static double of(@Nullable SubLevel subLevel) {
        double scale = subLevel instanceof ClientSubLevel client ? client.renderPose().scale().x()
                : subLevel != null ? subLevel.logicalPose().scale().x() : 1.0D;
        return ScaleBounds.isValid(scale) ? scale : 1.0D;
    }

    public static double of(BlockEntity blockEntity) {
        return of(Sable.HELPER.getContaining(blockEntity));
    }

    public static double rod(BlockEntity owner) {
        BlockEntity partner = LinkageLengthScale.partner(owner);
        double own = of(owner);
        return partner == null ? own : Math.min(own, of(partner));
    }

    public static double rodRatio(BlockEntity owner) {
        return rod(owner) / of(owner);
    }

    public static boolean shouldRender(BlockEntity blockEntity, Vec3 cameraPos) {
        double local = VIEW_DISTANCE / of(blockEntity) + ROD_REACH;
        return Vec3.atCenterOf(blockEntity.getBlockPos()).closerThan(cameraPos, local);
    }

    public static AABB renderBox(BlockEntity owner) {
        AABB base = owner instanceof CachedRenderBBBlockEntity cached
                ? cached.getRenderBoundingBox() : new AABB(owner.getBlockPos());
        BlockEntity partner = LinkageLengthScale.partner(owner);
        if (partner == null) return base;
        SubLevel ownSubLevel = Sable.HELPER.getContaining(owner);
        Vec3 world = toWorld(Sable.HELPER.getContaining(partner), Vec3.atCenterOf(partner.getBlockPos()));
        Vec3 local = ownSubLevel == null ? world : poseOf(ownSubLevel).transformPositionInverse(world);
        Vec3 center = base.getCenter();
        Vec3 reach = local.subtract(center);
        return base.minmax(AABB.ofSize(center,
                2.0D * Math.abs(reach.x) + 2.0D, 2.0D * Math.abs(reach.y) + 2.0D, 2.0D * Math.abs(reach.z) + 2.0D));
    }

    private static Vec3 toWorld(@Nullable SubLevel subLevel, Vec3 local) {
        return subLevel == null ? local : poseOf(subLevel).transformPosition(local);
    }

    private static Pose3dc poseOf(SubLevel subLevel) {
        return subLevel instanceof ClientSubLevel client ? client.renderPose() : subLevel.logicalPose();
    }

    public static Vec3 virtualEnd(Vec3 start, Vec3 end, double scale) {
        return scale == 1.0D ? end : start.add(end.subtract(start).scale(1.0D / scale));
    }

    public static void resetPreview() {
        previewStart = Double.NaN;
        previewTarget = Double.NaN;
    }

    public static void previewStart(double scale) {
        previewStart = scale;
    }

    public static void previewTarget(double scale) {
        previewTarget = scale;
    }

    public static double previewRod() {
        if (Double.isNaN(previewStart) || Double.isNaN(previewTarget)) return 1.0D;
        return Math.min(previewStart, previewTarget);
    }

    public static double previewPair() {
        if (Double.isNaN(previewStart) || Double.isNaN(previewTarget)) return 1.0D;
        return ScaleBounds.same(previewStart, previewTarget) ? previewStart : 1.0D;
    }

    public static void pendingCandidate(double scale) {
        pendingCandidate = scale;
    }

    public static double takePendingCandidate() {
        double scale = pendingCandidate;
        pendingCandidate = 1.0D;
        return scale;
    }

    private LinkageRenderScale() {}
}
