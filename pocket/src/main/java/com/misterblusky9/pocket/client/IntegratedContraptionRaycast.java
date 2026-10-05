package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.create.InteractiveContraption;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

public final class IntegratedContraptionRaycast {
    public record Ray(Vec3 origin, Vec3 target, float partialTick) {
    }

    public record Hit(
            AbstractContraptionEntity entity,
            InteractiveContraption contraption,
            Vec3 localOrigin,
            Vec3 localTarget,
            Vec3 localHit,
            Vec3 worldHit,
            double distanceSqr,
            float partialTick
    ) {
    }

    public static Ray capture(final LocalPlayer player) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (player == null || minecraft.level == null || player.level() != minecraft.level) {
            return null;
        }

        final Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return null;
        }

        final float partialTick = camera.getPartialTickTime();
        if (!Float.isFinite(partialTick)) {
            return null;
        }
        final Vec3 origin = Sable.HELPER.getEyePositionInterpolated(player, partialTick);
        double reach = player.blockInteractionRange();

        if (!Double.isFinite(reach) || reach <= 0.0D || !finite(origin)) {
            return null;
        }

        if (minecraft.hitResult != null && minecraft.hitResult.getLocation() != null) {
            final double hitDistanceSqr = Sable.HELPER.distanceSquaredWithSubLevels(
                    minecraft.level,
                    origin,
                    minecraft.hitResult.getLocation()
            );
            if (Double.isFinite(hitDistanceSqr) && hitDistanceSqr >= 0.0D) {
                reach = Math.min(reach, Math.sqrt(hitDistanceSqr));
            }
        }

        final Vector3f look = camera.getLookVector();
        final double lookLengthSqr = look.x * look.x + look.y * look.y + look.z * look.z;
        if (!Double.isFinite(lookLengthSqr) || lookLengthSqr <= 1.0E-12D) {
            return null;
        }

        final double scale = reach / Math.sqrt(lookLengthSqr);
        final Vec3 target = origin.add(look.x * scale, look.y * scale, look.z * scale);
        if (!finite(target)) {
            return null;
        }

        return new Ray(origin, target, partialTick);
    }

    public static Optional<Hit> rayTrace(
            final AbstractContraptionEntity entity,
            final InteractiveContraption contraption,
            final Ray ray
    ) {
        if (entity == null || entity.isRemoved() || contraption == null || ray == null) {
            return Optional.empty();
        }

        final AABB bounds = contraption.getInteractionBounds();
        if (bounds == null) {
            return Optional.empty();
        }

        final Vec3 localOrigin = toLocal(entity, ray.origin(), ray.partialTick());
        final Vec3 localTarget = toLocal(entity, ray.target(), ray.partialTick());
        if (localOrigin == null || localTarget == null || !finite(localOrigin) || !finite(localTarget)) {
            return Optional.empty();
        }

        final Vec3 localHit;
        if (bounds.contains(localOrigin)) {
            localHit = localOrigin;
        } else {
            final Optional<Vec3> clipped = bounds.clip(localOrigin, localTarget);
            if (clipped.isEmpty()) {
                return Optional.empty();
            }
            localHit = clipped.get();
        }

        final Vec3 worldHit = toWorld(entity, localHit, ray.partialTick());
        if (worldHit == null || !finite(worldHit)) {
            return Optional.empty();
        }

        final double distanceSqr = worldHit.distanceToSqr(ray.origin());
        if (!Double.isFinite(distanceSqr)) {
            return Optional.empty();
        }

        return Optional.of(new Hit(
                entity,
                contraption,
                localOrigin,
                localTarget,
                localHit,
                worldHit,
                distanceSqr,
                ray.partialTick()
        ));
    }

    public static Hit pick(
            final LocalPlayer player,
            final Predicate<InteractiveContraption> filter
    ) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }

        final Ray ray = capture(player);
        if (ray == null) {
            return null;
        }

        final var loadedContraptions = ContraptionHandler.loadedContraptions.get(minecraft.level);
        if (loadedContraptions == null || loadedContraptions.isEmpty()) {
            return null;
        }
        final Collection<WeakReference<AbstractContraptionEntity>> contraptions = loadedContraptions.values();

        Hit best = null;
        for (final WeakReference<AbstractContraptionEntity> ref : contraptions) {
            final AbstractContraptionEntity entity = ref.get();
            if (entity == null
                    || entity.isRemoved()
                    || !(entity.getContraption() instanceof final InteractiveContraption contraption)
                    || !filter.test(contraption)) {
                continue;
            }

            final Optional<Hit> hit = rayTrace(entity, contraption, ray);
            if (hit.isEmpty()) {
                continue;
            }

            if (best == null || hit.get().distanceSqr() < best.distanceSqr()) {
                best = hit.get();
            }
        }

        return best;
    }

    public static Vec3 toWorld(
            final AbstractContraptionEntity entity,
            final Vec3 localPoint,
            final float partialTick
    ) {
        if (entity == null || entity.isRemoved() || localPoint == null) {
            return null;
        }

        final Vec3 plotPoint = entity.toGlobalVector(localPoint, partialTick);
        final SubLevel containing = Sable.HELPER.getContaining(entity);
        if (containing == null) {
            return plotPoint;
        }
        if (containing.isRemoved()) {
            return null;
        }

        final Vector3d world = new Vector3d(plotPoint.x, plotPoint.y, plotPoint.z);
        if (containing instanceof final ClientSubLevel clientSubLevel) {
            clientSubLevel.renderPose(partialTick).transformPosition(world);
        } else {
            containing.logicalPose().transformPosition(world);
        }
        return new Vec3(world.x, world.y, world.z);
    }

    private static Vec3 toLocal(
            final AbstractContraptionEntity entity,
            final Vec3 worldPoint,
            final float partialTick
    ) {
        final SubLevel containing = Sable.HELPER.getContaining(entity);
        if (containing == null) {
            return entity.toLocalVector(worldPoint, partialTick);
        }
        if (containing.isRemoved()) {
            return null;
        }

        final Vector3d plotPoint = new Vector3d(worldPoint.x, worldPoint.y, worldPoint.z);
        if (containing instanceof final ClientSubLevel clientSubLevel) {
            clientSubLevel.renderPose(partialTick).transformPositionInverse(plotPoint);
        } else {
            containing.logicalPose().transformPositionInverse(plotPoint);
        }

        return entity.toLocalVector(new Vec3(plotPoint.x, plotPoint.y, plotPoint.z), partialTick);
    }

    private static boolean finite(final Vec3 vec) {
        return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }

    private IntegratedContraptionRaycast() {
    }
}
