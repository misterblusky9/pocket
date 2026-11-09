package com.misterblusky9.pym.internal;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlacedSubLevels {
    private static final List<SubLevel> PLACED = new CopyOnWriteArrayList<>();

    public static void add(SubLevel subLevel) {
        if (!PLACED.contains(subLevel)) PLACED.add(subLevel);
    }

    public static void remove(SubLevel subLevel) {
        PLACED.remove(subLevel);
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

    private PlacedSubLevels() {}
}
