package com.misterblusky9.pocket.build;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.function.Predicate;

public final class HatSeats {
    public static void follow(SubLevel part) {
        for (Entity vehicle : vehicles(part))
            for (Entity passenger : vehicle.getPassengers()) vehicle.positionRider(passenger);
    }

    public static void unseat(SubLevel part) {
        for (Entity vehicle : vehicles(part)) vehicle.ejectPassengers();
    }

    public static void unseat(SubLevel part, Predicate<Entity> rider) {
        for (Entity vehicle : vehicles(part))
            for (Entity passenger : List.copyOf(vehicle.getPassengers()))
                if (rider.test(passenger)) passenger.stopRiding();
    }

    private static List<Entity> vehicles(SubLevel part) {
        BoundingBox3ic bounds = part.getPlot().getBoundingBox();
        if (bounds == BoundingBox3i.EMPTY) return List.of();
        AABB box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1);
        return part.getLevel().getEntities((Entity) null, box, Entity::isVehicle);
    }

    private HatSeats() {}
}
