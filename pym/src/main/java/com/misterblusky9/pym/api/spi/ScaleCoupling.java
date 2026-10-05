package com.misterblusky9.pym.api.spi;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.Set;
import java.util.UUID;

public interface ScaleCoupling {
    interface Graph {
        Graph EMPTY = new Graph() {
            @Override public Set<UUID> members() { return Set.of(); }
            @Override public Set<UUID> component(final UUID origin) { return origin == null ? Set.of() : Set.of(origin); }
            @Override public boolean complete(final UUID origin) { return true; }
        };

        Set<UUID> members();

        Set<UUID> component(UUID origin);

        boolean complete(UUID origin);

        default boolean isEmpty() { return members().isEmpty(); }

        default boolean contains(final UUID id) { return id != null && members().contains(id); }
    }

    Graph graph(SubLevelContainer container);

    @Nullable
    default Vector3d worldAnchor(final SubLevelContainer container, final UUID origin) { return null; }
}
