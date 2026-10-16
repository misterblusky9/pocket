package com.misterblusky9.pym.internal.compat.simulated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public final class AssemblyGlueIndex {
    private final ServerLevel level;
    private final Map<Class<?>, List<? extends Entity>> byType = new HashMap<>(4);

    public AssemblyGlueIndex(ServerLevel level) {
        this.level = level;
    }

    public ServerLevel level() {
        return this.level;
    }

    public <T extends Entity> List<T> query(Class<T> type, AABB box) {
        List<T> hits = new ArrayList<>();
        for (T entity : this.all(type)) {
            if (entity.getBoundingBox().intersects(box)) hits.add(entity);
        }
        return hits;
    }

    @SuppressWarnings("unchecked")
    private <T extends Entity> List<T> all(Class<T> type) {
        return (List<T>) this.byType.computeIfAbsent(type, key ->
                this.level.getEntities(EntityTypeTest.forClass(type), entity -> !entity.isSpectator()));
    }

    public interface Owner {
        @Nullable
        AssemblyGlueIndex pym$glueIndex(ServerLevel level);
    }
}
