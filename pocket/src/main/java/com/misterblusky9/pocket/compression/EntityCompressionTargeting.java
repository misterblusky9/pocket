package com.misterblusky9.pocket.compression;

import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.misterblusky9.pym.api.ScaleBounds;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Optional;
import java.util.function.Predicate;

public final class EntityCompressionTargeting {
    public record Target(Entity entity, Vec3 hitPos) {}

    private EntityCompressionTargeting() {}

    public static @Nullable Target find(final Player player, final double range) {
        return find(player, range, false);
    }

    public static @Nullable Target find(final Player player, final double range, final boolean decorations) {
        if (player == null || range <= 0.0D || !Pym.entities().available()) return null;

        final Level level = player.level();
        final Vec3 start = player.getEyePosition(1.0F);
        final Vec3 look = player.getViewVector(1.0F);
        final Vec3 end = start.add(look.scale(range));

        final BlockHitResult blockHit = level.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        final Search search = new Search(blockHit == null || blockHit.getType() == HitResult.Type.MISS
                ? range * range
                : start.distanceToSqr(worldPoint(level, blockHit)));

        final Predicate<Entity> eligible = entity -> entity != player
                && entity.isAlive()
                && !entity.isSpectator()
                && entity instanceof LivingEntity
                && (decorations || !(entity instanceof ArmorStand));

        final AABB worldSearch = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        for (final Entity candidate : level.getEntities(player, worldSearch, eligible)) {
            if (Sable.HELPER.getContaining(candidate) != null) continue;
            final Optional<Vec3> clipped = candidate.getBoundingBox().inflate(0.3D).clip(start, end);
            clipped.ifPresent(point -> search.offer(candidate, point, start.distanceToSqr(point)));
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            for (final SubLevel subLevel : container.getAllSubLevels()) {
                if (subLevel.isRemoved() || !overlaps(subLevel.boundingBox(), worldSearch)) continue;

                final Vec3 localStart = toLocal(subLevel, start);
                final Vec3 localEnd = toLocal(subLevel, end);
                final AABB localSearch = new AABB(localStart, localEnd).inflate(1.0D);
                for (final Entity candidate : level.getEntities(player, localSearch, eligible)) {
                    if (Sable.HELPER.getContaining(candidate) != subLevel) continue;
                    final Optional<Vec3> clipped = candidate.getBoundingBox().inflate(0.3D).clip(localStart, localEnd);
                    if (clipped.isEmpty()) continue;
                    final Vec3 world = toWorld(subLevel, clipped.get());
                    search.offer(candidate, world, start.distanceToSqr(world));
                }
            }
        }

        return search.nearest == null ? null : new Target(search.nearest, search.nearestPoint);
    }

    public static double scale(final Entity entity) {
        return entity == null ? 1.0D : Pym.entities().scaleOf(entity) * containerScale(entity);
    }

    public static double containerScale(final Entity entity) {
        if (!(entity instanceof ArmorStand || entity instanceof HangingEntity || entity instanceof Display)) return 1.0D;
        final SubLevel subLevel = Sable.HELPER.getContaining(entity);
        if (subLevel == null || subLevel.isRemoved()) return 1.0D;
        final double scale = Pym.scale().of(subLevel);
        return ScaleBounds.isValid(scale) ? scale : 1.0D;
    }

    private static Vec3 worldPoint(final Level level, final BlockHitResult hit) {
        final SubLevel subLevel = Sable.HELPER.getContaining(level, hit.getBlockPos());
        return subLevel == null ? hit.getLocation() : toWorld(subLevel, hit.getLocation());
    }

    private static Vec3 toLocal(final SubLevel subLevel, final Vec3 world) {
        final Vector3d local = subLevel.logicalPose().transformPositionInverse(new Vector3d(world.x, world.y, world.z));
        return new Vec3(local.x, local.y, local.z);
    }

    private static Vec3 toWorld(final SubLevel subLevel, final Vec3 local) {
        final Vector3d world = subLevel.logicalPose().transformPosition(new Vector3d(local.x, local.y, local.z));
        return new Vec3(world.x, world.y, world.z);
    }

    private static boolean overlaps(final BoundingBox3dc bounds, final AABB box) {
        return bounds.maxX() >= box.minX && bounds.minX() <= box.maxX
                && bounds.maxY() >= box.minY && bounds.minY() <= box.maxY
                && bounds.maxZ() >= box.minZ && bounds.minZ() <= box.maxZ;
    }

    private static final class Search {
        private double nearestDistanceSq;
        private Entity nearest;
        private Vec3 nearestPoint;

        private Search(final double limitSq) {
            this.nearestDistanceSq = limitSq;
        }

        private void offer(final Entity entity, final Vec3 point, final double distanceSq) {
            if (distanceSq >= this.nearestDistanceSq) return;
            this.nearestDistanceSq = distanceSq;
            this.nearest = entity;
            this.nearestPoint = point;
        }
    }
}
