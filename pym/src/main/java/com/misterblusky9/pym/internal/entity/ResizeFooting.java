package com.misterblusky9.pym.internal.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ResizeFooting {
    private static final double CONTACT = 1.0E-3D;
    private static final double HOLD = 0.5D;
    private static final double EPSILON = 1.0E-7D;

    public static void keep(Entity entity, AABB before, boolean wasOnGround) {
        if (!wasOnGround || entity.noPhysics || entity.isPassenger() || entity.hasPose(Pose.SLEEPING)) return;
        if (entity instanceof Player) return;
        if (!entity.isControlledByLocalInstance()) return;

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

        if (!Double.isFinite(bestDistance) || bestDistance <= EPSILON * EPSILON) return;
        if (!entity.level().noCollision(entity, now.move(bestX, 0.0D, bestZ))) return;
        entity.setPos(entity.getX() + bestX, entity.getY(), entity.getZ() + bestZ);
    }

    private static double clamp(double value, double min, double max) {
        return min > max ? (min + max) * 0.5D : Math.max(min, Math.min(max, value));
    }

    private ResizeFooting() {}
}
