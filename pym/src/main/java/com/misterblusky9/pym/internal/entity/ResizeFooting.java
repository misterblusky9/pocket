package com.misterblusky9.pym.internal.entity;

import com.misterblusky9.pym.internal.collision.SubLevelIndexedCollision;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ResizeFooting {
    private static final double CONTACT = 1.0E-3D;
    private static final double HOLD = 0.5D;
    private static final double EPSILON = 1.0E-7D;
    private static final int STEPS = 16;

    public static void keep(Entity entity, AABB before, boolean wasOnGround) {
        if (!wasOnGround || entity.noPhysics || entity.isPassenger() || entity.hasPose(Pose.SLEEPING)) return;
        if (entity instanceof Player player ? !player.isLocalPlayer() : !entity.isControlledByLocalInstance()) return;

        AABB now = entity.getBoundingBox();
        if (now.getXsize() >= before.getXsize() - EPSILON && now.getZsize() >= before.getZsize() - EPSILON) return;

        double centreX = (now.minX + now.maxX) * 0.5D;
        double centreZ = (now.minZ + now.maxZ) * 0.5D;
        double halfX = now.getXsize() * 0.5D;
        double halfZ = now.getZsize() * 0.5D;

        double bestX = 0.0D;
        double bestZ = 0.0D;
        double bestDistance = Double.POSITIVE_INFINITY;
        AABB contact = new AABB(before.minX, before.minY - CONTACT, before.minZ,
                before.maxX, before.minY, before.maxZ);
        for (VoxelShape shape : entity.level().getBlockCollisions(entity, contact)) {
            for (AABB box : shape.toAabbs()) {
                if (Math.abs(box.maxY - before.minY) > CONTACT) continue;
                double minX = Math.max(box.minX, before.minX);
                double maxX = Math.min(box.maxX, before.maxX);
                double minZ = Math.max(box.minZ, before.minZ);
                double maxZ = Math.min(box.maxZ, before.maxZ);
                if (maxX <= minX || maxZ <= minZ) continue;

                double holdX = Math.min(halfX, (maxX - minX) * 0.5D) * HOLD;
                double holdZ = Math.min(halfZ, (maxZ - minZ) * 0.5D) * HOLD;
                double dx = clamp(centreX, minX + holdX - halfX, maxX - holdX + halfX) - centreX;
                double dz = clamp(centreZ, minZ + holdZ - halfZ, maxZ - holdZ + halfZ) - centreZ;
                double distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestX = dx;
                    bestZ = dz;
                }
            }
        }

        if (!Double.isFinite(bestDistance)) {
            keepOnSubLevel(entity, before, now);
            return;
        }
        if (bestDistance <= EPSILON * EPSILON) return;
        if (!entity.level().noCollision(entity, now.move(bestX, 0.0D, bestZ))) return;
        entity.setPos(entity.getX() + bestX, entity.getY(), entity.getZ() + bestZ);
    }

    private static void keepOnSubLevel(Entity entity, AABB before, AABB now) {
        double depth = Math.max(EPSILON, entity.maxUpStep());
        if (!supported(entity, before, depth) || supported(entity, now, depth)) return;

        double loX = before.minX - now.minX, hiX = before.maxX - now.maxX;
        double loZ = before.minZ - now.minZ, hiZ = before.maxZ - now.maxZ;
        double hold = Math.min(now.getXsize(), now.getZsize()) * 0.5D * HOLD;
        double best = Double.POSITIVE_INFINITY, bestX = 0.0D, bestZ = 0.0D;
        for (int i = 0; i < 8; i++) {
            double ax = i % 4 == 2 ? 0.0D : Math.cos(i * Math.PI / 4.0D);
            double az = i % 4 == 0 ? 0.0D : Math.sin(i * Math.PI / 4.0D);
            double limit = Math.min(ax > 0.0D ? hiX / ax : ax < 0.0D ? loX / ax : Double.POSITIVE_INFINITY,
                    az > 0.0D ? hiZ / az : az < 0.0D ? loZ / az : Double.POSITIVE_INFINITY);
            if (!(limit > EPSILON) || !Double.isFinite(limit) || limit >= best) continue;
            double lower = 0.0D, upper = -1.0D;
            for (int k = 1; k <= STEPS; k++) {
                double t = limit * k / STEPS;
                if (supported(entity, now.move(t * ax, 0.0D, t * az), depth)) {
                    upper = t;
                    break;
                }
                lower = t;
            }
            if (upper < 0.0D) continue;
            for (int k = 0; k < 10; k++) {
                double mid = (lower + upper) * 0.5D;
                if (supported(entity, now.move(mid * ax, 0.0D, mid * az), depth)) upper = mid;
                else lower = mid;
            }
            double t = Math.min(limit, upper + hold);
            if (t < best) {
                best = t;
                bestX = t * ax;
                bestZ = t * az;
            }
        }
        if (!Double.isFinite(best)) return;

        AABB moved = now.move(bestX, 0.0D, bestZ);
        if (!entity.level().noCollision(entity, moved) || blocked(entity, moved) && !blocked(entity, now)) return;
        entity.setPos(entity.getX() + bestX, entity.getY(), entity.getZ() + bestZ);
    }

    private static boolean supported(Entity entity, AABB box, double depth) {
        AABB below = new AABB(box.minX, box.minY - depth, box.minZ, box.maxX, box.minY, box.maxZ);
        return SubLevelIndexedCollision.probing(entity,
                () -> CanFallAtleastHelper.canFallAtleastWithSubLevels(entity.level(), below) != null);
    }

    private static boolean blocked(Entity entity, AABB box) {
        AABB inside = box.setMinY(box.minY + Math.max(EPSILON, box.getYsize() * 1.0E-4D)).deflate(EPSILON);
        return SubLevelIndexedCollision.probing(entity,
                () -> CanFallAtleastHelper.canFallAtleastWithSubLevels(entity.level(), inside) != null);
    }

    private static double clamp(double value, double min, double max) {
        return min > max ? (min + max) * 0.5D : Math.max(min, Math.min(max, value));
    }

    private ResizeFooting() {}
}
