package com.misterblusky9.pym.internal;

import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

public final class PlacedSubLevels {
    private static final List<SubLevel> PLACED = new CopyOnWriteArrayList<>();
    private static final List<SubLevel> SOLID = new CopyOnWriteArrayList<>();
    private static final List<SubLevel> INERT = new CopyOnWriteArrayList<>();
    private static BiPredicate<SubLevel, BlockState> pickable = (subLevel, state) -> false;

    public static void add(SubLevel subLevel) {
        if (!PLACED.contains(subLevel)) PLACED.add(subLevel);
    }

    public static void remove(SubLevel subLevel) {
        PLACED.remove(subLevel);
    }

    public static void solid(SubLevel subLevel, boolean solid) {
        if (!solid) SOLID.remove(subLevel);
        else if (!SOLID.contains(subLevel)) SOLID.add(subLevel);
    }

    public static void inert(SubLevel subLevel, boolean inert) {
        if (!inert) INERT.remove(subLevel);
        else if (!INERT.contains(subLevel)) INERT.add(subLevel);
    }

    public static boolean isInert(SubLevel subLevel) {
        return INERT.contains(subLevel);
    }

    public static List<SubLevel> inertIn(Level level) {
        List<SubLevel> found = new ArrayList<>();
        for (SubLevel subLevel : INERT) {
            if (subLevel.getLevel() == level && !subLevel.isRemoved()) found.add(subLevel);
        }
        return found;
    }

    public static void pickable(BiPredicate<SubLevel, BlockState> rule) {
        pickable = pickable.or(rule);
    }

    public static boolean pickable(SubLevel subLevel, BlockState state) {
        return pickable.test(subLevel, state);
    }

    public static List<SubLevel> solidIn(Level level) {
        List<SubLevel> found = new ArrayList<>();
        for (SubLevel subLevel : SOLID) {
            if (subLevel.getLevel() == level && !subLevel.isRemoved()) found.add(subLevel);
        }
        return found;
    }

    public static boolean isPlaced(SubLevel subLevel) {
        return PLACED.contains(subLevel);
    }

    public static boolean isEmpty() {
        return PLACED.isEmpty();
    }

    public static List<SubLevel> in(Level level) {
        List<SubLevel> found = new ArrayList<>();
        for (SubLevel subLevel : PLACED) {
            if (subLevel.getLevel() == level && !subLevel.isRemoved()) found.add(subLevel);
        }
        return found;
    }

    public static List<SubLevel> colliding(Iterable<SubLevel> found, Level level, BoundingBox3dc bounds, Entity entity) {
        List<SubLevel> all = new ArrayList<>();
        for (SubLevel subLevel : found) {
            if (entity == null || !Hosted.wornBy(subLevel, entity)) all.add(subLevel);
        }
        if (!level.isClientSide()) return all;
        for (SubLevel solid : solidIn(level)) {
            if (!all.contains(solid) && solid.boundingBox().intersects(bounds)
                    && (entity == null || !Hosted.wornBy(solid, entity))) all.add(solid);
        }
        return all;
    }

    private PlacedSubLevels() {}
}
