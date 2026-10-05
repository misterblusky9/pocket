package com.misterblusky9.pocket.scale;

import com.misterblusky9.pym.api.spi.ResizeFollower;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashSet;
import java.util.Set;

final class CreateSeatResizeFollower implements ResizeFollower {
    private static final double SEARCH_MARGIN = 3.0D;

    @Override
    public Set<LivingEntity> followers(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || !(subLevel.getLevel() instanceof final ServerLevel level)) {
            return Set.of();
        }

        final var bounds = subLevel.boundingBox();
        final AABB search = new AABB(
                bounds.minX() - SEARCH_MARGIN, bounds.minY() - SEARCH_MARGIN, bounds.minZ() - SEARCH_MARGIN,
                bounds.maxX() + SEARCH_MARGIN, bounds.maxY() + SEARCH_MARGIN, bounds.maxZ() + SEARCH_MARGIN);

        final Set<LivingEntity> found = new LinkedHashSet<>();
        for (final LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class,
                search,
                candidate -> candidate != null && candidate.isAlive() && !(candidate instanceof Player))) {
            if (seatSubLevel(entity) == subLevel) found.add(entity);
        }
        return found;
    }

    private static ServerSubLevel seatSubLevel(final LivingEntity passenger) {
        final Entity vehicle = passenger == null ? null : passenger.getVehicle();
        if (vehicle == null || !isCreateSeatRide(passenger, vehicle)) return null;

        Entity current = vehicle;
        while (current != null) {
            SubLevel subLevel = Sable.HELPER.getContaining(current);
            if (subLevel == null) subLevel = Sable.HELPER.getTrackingSubLevel(current);
            if (subLevel instanceof final ServerSubLevel server && !server.isRemoved()) return server;
            current = current.getVehicle();
        }
        return null;
    }

    private static boolean isCreateSeatRide(final LivingEntity passenger, final Entity vehicle) {
        if (vehicle instanceof SeatEntity) return true;
        if (!(vehicle instanceof final AbstractContraptionEntity contraptionEntity)) return false;
        final var contraption = contraptionEntity.getContraption();
        return contraption != null && contraption.getSeatOf(passenger.getUUID()) != null;
    }
}
