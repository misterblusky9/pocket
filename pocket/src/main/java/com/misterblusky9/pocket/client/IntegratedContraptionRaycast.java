package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.create.ContraptionSightline;
import com.misterblusky9.pocket.create.InteractiveContraption;
import com.misterblusky9.pocket.mixin.create.ControlledContraptionEntityControllerPosAccessor;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

    public static Ray capture(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player == null || minecraft.level == null || player.level() != minecraft.level) {
            return null;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return null;
        }

        float partialTick = camera.getPartialTickTime();
        if (!Float.isFinite(partialTick)) {
            return null;
        }
        Vec3 origin = Sable.HELPER.getEyePositionInterpolated(player, partialTick);
        double reach = player.blockInteractionRange();

        if (!Double.isFinite(reach) || reach <= 0.0D || !finite(origin)) {
            return null;
        }

        if (minecraft.hitResult != null && minecraft.hitResult.getLocation() != null) {
            double hitDistanceSqr = Sable.HELPER.distanceSquaredWithSubLevels(
                    minecraft.level,
                    origin,
                    minecraft.hitResult.getLocation()
            );
            if (Double.isFinite(hitDistanceSqr) && hitDistanceSqr >= 0.0D) {
                reach = Math.min(reach, Math.sqrt(hitDistanceSqr));
            }
        }

        Vector3f look = camera.getLookVector();
        double lookLengthSqr = look.x * look.x + look.y * look.y + look.z * look.z;
        if (!Double.isFinite(lookLengthSqr) || lookLengthSqr <= 1.0E-12D) {
            return null;
        }

        double scale = reach / Math.sqrt(lookLengthSqr);
        Vec3 target = origin.add(look.x * scale, look.y * scale, look.z * scale);
        if (!finite(target)) {
            return null;
        }

        return new Ray(origin, target, partialTick);
    }

    public static Optional<Hit> rayTrace(
            AbstractContraptionEntity entity,
            InteractiveContraption contraption,
            Ray ray
    ) {
        if (entity == null || entity.isRemoved() || contraption == null || ray == null) {
            return Optional.empty();
        }
        AABB bounds = contraption.getInteractionBounds();
        if (bounds == null) {
            return Optional.empty();
        }

        Vec3 localOrigin = toLocal(entity, ray.origin(), ray.partialTick());
        Vec3 localTarget = toLocal(entity, ray.target(), ray.partialTick());
        if (localOrigin == null || localTarget == null || !finite(localOrigin) || !finite(localTarget)) {
            return Optional.empty();
        }
        if (aimsAtOwnController(entity, localOrigin, ray.partialTick())) {
            return Optional.empty();
        }

        Vec3 localHit;
        if (bounds.contains(localOrigin)) {
            localHit = localOrigin;
        } else {
            Optional<Vec3> clipped = bounds.clip(localOrigin, localTarget);
            if (clipped.isEmpty()) {
                return Optional.empty();
            }
            localHit = clipped.get();
        }

        Vec3 worldHit = toWorld(entity, localHit, ray.partialTick());
        if (worldHit == null || !finite(worldHit)) {
            return Optional.empty();
        }

        double distanceSqr = worldHit.distanceToSqr(ray.origin());
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
            LocalPlayer player,
            Predicate<InteractiveContraption> filter
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }

        Ray ray = capture(player);
        if (ray == null) {
            return null;
        }

        var loadedContraptions = ContraptionHandler.loadedContraptions.get(minecraft.level);
        if (loadedContraptions == null || loadedContraptions.isEmpty()) {
            return null;
        }
        Collection<WeakReference<AbstractContraptionEntity>> contraptions = loadedContraptions.values();

        Hit best = null;
        for (WeakReference<AbstractContraptionEntity> ref : contraptions) {
            AbstractContraptionEntity entity = ref.get();
            if (entity == null
                    || entity.isRemoved()
                    || !(entity.getContraption() instanceof InteractiveContraption contraption)
                    || !filter.test(contraption)) {
                continue;
            }

            Optional<Hit> hit = rayTrace(entity, contraption, ray);
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
            AbstractContraptionEntity entity,
            Vec3 localPoint,
            float partialTick
    ) {
        if (entity == null || entity.isRemoved() || localPoint == null) {
            return null;
        }

        Vec3 plotPoint = entity.toGlobalVector(localPoint, partialTick);
        SubLevel containing = Sable.HELPER.getContaining(entity);
        if (containing == null) {
            return plotPoint;
        }
        if (containing.isRemoved()) {
            return null;
        }

        Vector3d world = new Vector3d(plotPoint.x, plotPoint.y, plotPoint.z);
        if (containing instanceof ClientSubLevel clientSubLevel) {
            clientSubLevel.renderPose(partialTick).transformPosition(world);
        } else {
            containing.logicalPose().transformPosition(world);
        }
        return new Vec3(world.x, world.y, world.z);
    }

    private static Vec3 toLocal(
            AbstractContraptionEntity entity,
            Vec3 worldPoint,
            float partialTick
    ) {
        SubLevel containing = Sable.HELPER.getContaining(entity);
        if (containing == null) {
            return entity.toLocalVector(worldPoint, partialTick);
        }
        if (containing.isRemoved()) {
            return null;
        }

        Vector3d plotPoint = new Vector3d(worldPoint.x, worldPoint.y, worldPoint.z);
        if (containing instanceof ClientSubLevel clientSubLevel) {
            clientSubLevel.renderPose(partialTick).transformPositionInverse(plotPoint);
        } else {
            containing.logicalPose().transformPositionInverse(plotPoint);
        }

        return entity.toLocalVector(new Vec3(plotPoint.x, plotPoint.y, plotPoint.z), partialTick);
    }

    private static boolean aimsAtOwnController(
            AbstractContraptionEntity entity,
            Vec3 localOrigin,
            float partialTick
    ) {
        if (!(entity instanceof ControlledContraptionEntity controlled)
                || !(Minecraft.getInstance().hitResult instanceof BlockHitResult blockHit)
                || blockHit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos controllerPos =
                ((ControlledContraptionEntityControllerPosAccessor) controlled).pocket$getControllerPos();
        if (!blockHit.getBlockPos().equals(controllerPos)) return false;

        Vec3 localController = toLocal(entity, blockHit.getLocation(), partialTick);
        return localController == null || !finite(localController)
                || !ContraptionSightline.between(entity.getContraption(), localOrigin, localController);
    }

    private static boolean finite(Vec3 vec) {
        return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }

    private IntegratedContraptionRaycast() {
    }
}
