package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.compression.EntityCompressionTargeting;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.joml.Vector3d;

import java.util.Optional;
import java.util.function.Supplier;

public final class PlayerSpawnScale {
    private record Use(Player player, SubLevel surface, BlockHitResult hit) {}

    private static final ThreadLocal<Use> USE = new ThreadLocal<>();

    public static <T> T using(Player player, SubLevel surface, BlockHitResult hit, Supplier<T> use) {
        Use outer = USE.get();
        USE.set(player == null ? null : new Use(player, surface, hit));
        try {
            return use.get();
        } finally {
            USE.set(outer);
        }
    }

    public static void restoring(Runnable restore) {
        using(null, null, null, () -> {
            restore.run();
            return null;
        });
    }

    public static void onJoin(EntityJoinLevelEvent event) {
        Use use = USE.get();
        if (use == null || event.getLevel().isClientSide() || event.loadedFromDisk()) return;

        Entity entity = event.getEntity();
        if (!follows(entity)) return;

        SubLevel surface = use.surface() != null ? use.surface() : Sable.HELPER.getContaining(entity);
        double grid = gridScale(surface);
        double scale = spawnScale(use.player(), grid);
        double goal = scale / EntityCompressionTargeting.containerScale(entity);
        if (!ScaleBounds.isValid(goal)) return;
        if (!ScaleBounds.same(goal, Pym.entities().target(entity))) Pym.entities().setScale(entity, goal, 0);

        if (ScaleBounds.same(scale, grid) || entity.isPassenger() || !entity.getPassengers().isEmpty() || !snapsToClick(entity)) return;
        entity.refreshDimensions();
        if (use.hit() != null) placeAtHit(entity, use.hit());
        else placeAlongLook(entity, use.player(), surface);
    }

    public static void inherit(Entity replacement, Entity original) {
        if (replacement == null || original == null || !Pym.entities().supports(replacement)) return;
        double scale = Pym.entities().target(original);
        if (ScaleBounds.isValid(scale) && !ScaleBounds.same(scale, Pym.entities().target(replacement))) {
            Pym.entities().setScale(replacement, scale, 0);
        }
    }

    public static AABB scaledBox(Player player, Level level, BlockPos clicked, AABB box) {
        if (player == null) return box;
        double grid = gridScale(Sable.HELPER.getContaining(level, clicked));
        double factor = spawnScale(player, grid) / grid;
        if (!ScaleBounds.isValid(factor) || ScaleBounds.same(factor, 1.0D)) return box;
        double x = (box.minX + box.maxX) * 0.5D;
        double z = (box.minZ + box.maxZ) * 0.5D;
        double halfX = box.getXsize() * 0.5D * factor;
        double halfZ = box.getZsize() * 0.5D * factor;
        return new AABB(x - halfX, box.minY, z - halfZ, x + halfX, box.minY + box.getYsize() * factor, z + halfZ);
    }

    public static AABB placementBox(UseOnContext context, AABB box) {
        BlockHitResult hit = new BlockHitResult(
                context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        AABB scaled = scaledBox(context.getPlayer(), context.getLevel(), hit.getBlockPos(), box);
        if (scaled == box) return box;
        Vec3 at = clickPoint(hit, scaled.getXsize(), scaled.getYsize());
        double halfX = scaled.getXsize() * 0.5D;
        double halfZ = scaled.getZsize() * 0.5D;
        return new AABB(at.x - halfX, at.y, at.z - halfZ, at.x + halfX, at.y + scaled.getYsize(), at.z + halfZ);
    }

    private static double spawnScale(Player player, double grid) {
        return ScaleBounds.same(grid, 1.0D) ? Pym.entities().scaleOf(player) : grid;
    }

    private static double gridScale(SubLevel surface) {
        if (surface == null || surface.isRemoved()) return 1.0D;
        double scale = Pym.scale().of(surface);
        return ScaleBounds.isValid(scale) ? scale : 1.0D;
    }

    private static void placeAtHit(Entity entity, BlockHitResult hit) {
        Vec3 at = clickPoint(hit, entity.getBbWidth(), entity.getBbHeight());
        entity.setPos(at.x, at.y, at.z);
    }

    private static Vec3 clickPoint(BlockHitResult hit, double width, double height) {
        Direction face = hit.getDirection();
        double out = switch (face) {
            case UP -> 0.0D;
            case DOWN -> height;
            default -> width * 0.5D;
        };
        return hit.getLocation().add(Vec3.atLowerCornerOf(face.getNormal()).scale(out));
    }

    private static void placeAlongLook(Entity entity, Player player, SubLevel surface) {
        BlockPos cell = entity.blockPosition();
        if (Math.abs(entity.getX() - (cell.getX() + 0.5D)) > 1.0E-6D
                || Math.abs(entity.getZ() - (cell.getZ() + 0.5D)) > 1.0E-6D) return;

        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange() + 1.0D));
        if (surface != null) {
            eye = toLocal(surface, eye);
            end = toLocal(surface, end);
        }
        Optional<Vec3> entry = new AABB(cell).clip(eye, end);
        if (entry.isEmpty()) return;

        double inset = Math.min(0.5D, entity.getBbWidth() * 0.5D);
        entity.setPos(
                Mth.clamp(entry.get().x, cell.getX() + inset, cell.getX() + 1.0D - inset),
                entry.get().y,
                Mth.clamp(entry.get().z, cell.getZ() + inset, cell.getZ() + 1.0D - inset));
    }

    private static Vec3 toLocal(SubLevel subLevel, Vec3 world) {
        Vector3d local = subLevel.logicalPose().transformPositionInverse(new Vector3d(world.x, world.y, world.z));
        return new Vec3(local.x, local.y, local.z);
    }

    private static boolean snapsToClick(Entity entity) {
        return !(entity instanceof HangingEntity || entity instanceof AbstractMinecart || entity instanceof EndCrystal);
    }

    private static boolean follows(Entity entity) {
        return !(entity instanceof Player
                || entity instanceof Projectile
                || entity instanceof AbstractContraptionEntity
                || entity instanceof SeatEntity)
                && Pym.entities().supports(entity);
    }

    private PlayerSpawnScale() {}
}
