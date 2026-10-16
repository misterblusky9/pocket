package com.misterblusky9.pocket.compression;

import com.misterblusky9.pocket.mixin.LevelEntityGetterAccessor;
import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SubLevelInclusiveLevelEntityGetter;
import com.misterblusky9.pym.api.ScaleBounds;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class EntityCompressionTargeting {
    public record Target(Entity entity, Vec3 hitPos) {}

    private record Query(double range, boolean decorations) {}

    private static Level cachedLevel;
    private static Player cachedPlayer;
    private static long cachedTick = Long.MIN_VALUE;
    private static final Map<Query, Target> cachedTargets = new HashMap<>();

    private EntityCompressionTargeting() {}

    public static @Nullable Target find(Player player, double range) {
        return find(player, range, false);
    }

    public static @Nullable Target find(Player player, double range, boolean decorations) {
        if (player == null || range <= 0.0D || !Pym.entities().available()) return null;

        Level level = player.level();
        if (!level.isClientSide) return findUncached(player, range, decorations);

        long tick = level.getGameTime();
        if (cachedLevel != level || cachedPlayer != player || cachedTick != tick) {
            cachedTargets.clear();
            cachedLevel = level;
            cachedPlayer = player;
            cachedTick = tick;
        }

        Query query = new Query(range, decorations);
        if (cachedTargets.containsKey(query)) return cachedTargets.get(query);

        Target result = findUncached(player, range, decorations);
        cachedTargets.put(query, result);
        return result;
    }

    private static @Nullable Target findUncached(Player player, double range, boolean decorations) {
        Level level = player.level();
        Vec3 start = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(range));

        BlockHitResult blockHit = level.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Search search = new Search(blockHit == null || blockHit.getType() == HitResult.Type.MISS
                ? range * range
                : start.distanceToSqr(worldPoint(level, blockHit)));

        Predicate<Entity> eligible = entity -> entity != player
                && entity.isAlive()
                && !entity.isSpectator()
                && entity instanceof LivingEntity
                && (decorations || !(entity instanceof ArmorStand));

        AABB worldSearch = new AABB(start, end).inflate(1.0D);
        LevelEntityGetter<Entity> getter = ((LevelEntityGetterAccessor) level).pocket$getEntities();
        query(getter, worldSearch, candidate -> {
            if (!eligible.test(candidate) || Sable.HELPER.getContaining(candidate) != null) return;
            candidate.getBoundingBox().inflate(0.3D).clip(start, end)
                    .ifPresent(point -> search.offer(candidate, point, start.distanceToSqr(point)));
        });

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return search.result();
        for (SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            BoundingBox3dc bounds = subLevel.boundingBox();
            if (bounds == null) continue;
            AABB box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()).inflate(2.0D);
            Vec3 entry = box.contains(start) ? start : box.clip(start, end).orElse(null);
            if (entry == null) continue;
            Vec3 exit = box.contains(end) ? end : box.clip(end, start).orElse(end);

            Vec3 localStart = toLocal(subLevel, start);
            Vec3 localEnd = toLocal(subLevel, end);
            Vec3 localEntry = toLocal(subLevel, entry);
            Vec3 localExit = toLocal(subLevel, exit);
            if (!finite(localStart) || !finite(localEnd) || !finite(localEntry) || !finite(localExit)) continue;

            query(getter, new AABB(localEntry, localExit).inflate(1.0D), candidate -> {
                if (!eligible.test(candidate) || Sable.HELPER.getContaining(candidate) != subLevel) return;
                candidate.getBoundingBox().inflate(0.3D).clip(localStart, localEnd).ifPresent(point -> {
                    Vec3 world = toWorld(subLevel, point);
                    search.offer(candidate, world, start.distanceToSqr(world));
                });
            });
        }

        return search.result();
    }

    private static void query(LevelEntityGetter<Entity> getter, AABB box, Consumer<Entity> visitor) {
        if (getter instanceof SubLevelInclusiveLevelEntityGetter<Entity> sable) {
            sable.getIgnoringSubLevels(box, visitor);
        } else {
            getter.get(box, visitor);
        }
    }

    public static double scale(Entity entity) {
        return entity == null ? 1.0D : Pym.entities().scaleOf(entity) * containerScale(entity);
    }

    public static double containerScale(Entity entity) {
        if (!(entity instanceof ArmorStand || entity instanceof HangingEntity || entity instanceof Display)) return 1.0D;
        SubLevel subLevel = Sable.HELPER.getContaining(entity);
        if (subLevel == null || subLevel.isRemoved()) return 1.0D;
        double scale = Pym.scale().of(subLevel);
        return ScaleBounds.isValid(scale) ? scale : 1.0D;
    }

    private static Vec3 worldPoint(Level level, BlockHitResult hit) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, hit.getBlockPos());
        return subLevel == null ? hit.getLocation() : toWorld(subLevel, hit.getLocation());
    }

    private static Vec3 toLocal(SubLevel subLevel, Vec3 world) {
        Vector3d local = subLevel.logicalPose().transformPositionInverse(new Vector3d(world.x, world.y, world.z));
        return new Vec3(local.x, local.y, local.z);
    }

    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private static Vec3 toWorld(SubLevel subLevel, Vec3 local) {
        Vector3d world = subLevel.logicalPose().transformPosition(new Vector3d(local.x, local.y, local.z));
        return new Vec3(world.x, world.y, world.z);
    }


    private static final class Search {
        private double nearestDistanceSq;
        private Entity nearest;
        private Vec3 nearestPoint;

        private Search(double limitSq) {
            this.nearestDistanceSq = limitSq;
        }

        private Target result() {
            return this.nearest == null ? null : new Target(this.nearest, this.nearestPoint);
        }

        private void offer(Entity entity, Vec3 point, double distanceSq) {
            if (distanceSq >= this.nearestDistanceSq) return;
            this.nearestDistanceSq = distanceSq;
            this.nearest = entity;
            this.nearestPoint = point;
        }
    }
}
