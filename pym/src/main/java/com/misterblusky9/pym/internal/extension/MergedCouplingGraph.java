package com.misterblusky9.pym.internal.extension;

import com.misterblusky9.pym.api.spi.ScaleCoupling;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class MergedCouplingGraph implements ScaleCoupling.Graph {
    private final List<ScaleCoupling.Graph> graphs;
    private final Set<UUID> members;
    private final Map<UUID, Set<UUID>> components;

    private MergedCouplingGraph(
            final List<ScaleCoupling.Graph> graphs,
            final Set<UUID> members,
            final Map<UUID, Set<UUID>> components
    ) {
        this.graphs = graphs;
        this.members = members;
        this.components = components;
    }

    static ScaleCoupling.Graph of(final List<ScaleCoupling.Graph> graphs) {
        if (graphs.isEmpty()) return ScaleCoupling.Graph.EMPTY;
        if (graphs.size() == 1) return graphs.get(0);

        final Map<UUID, UUID> parent = new HashMap<>();
        for (final ScaleCoupling.Graph graph : graphs) {
            final Set<UUID> seen = new HashSet<>();
            for (final UUID member : graph.members()) {
                if (member == null || !seen.add(member)) continue;
                parent.putIfAbsent(member, member);
                for (final UUID linked : graph.component(member)) {
                    if (linked == null) continue;
                    seen.add(linked);
                    parent.putIfAbsent(linked, linked);
                    union(parent, member, linked);
                }
            }
        }

        final Map<UUID, Set<UUID>> byRoot = new HashMap<>();
        for (final UUID member : parent.keySet()) {
            byRoot.computeIfAbsent(find(parent, member), root -> new LinkedHashSet<>()).add(member);
        }
        final Map<UUID, Set<UUID>> components = new HashMap<>();
        for (final Set<UUID> component : byRoot.values()) {
            final Set<UUID> frozen = Collections.unmodifiableSet(component);
            for (final UUID member : component) components.put(member, frozen);
        }

        return new MergedCouplingGraph(
                List.copyOf(graphs), Collections.unmodifiableSet(parent.keySet()), components);
    }

    @Override
    public Set<UUID> members() {
        return this.members;
    }

    @Override
    public Set<UUID> component(final UUID origin) {
        if (origin == null) return Set.of();
        final Set<UUID> component = this.components.get(origin);
        return component == null ? Set.of(origin) : component;
    }

    @Override
    public boolean complete(final UUID origin) {
        for (final UUID member : this.component(origin)) {
            for (final ScaleCoupling.Graph graph : this.graphs) {
                if (graph.contains(member) && !graph.complete(member)) return false;
            }
        }
        return true;
    }

    private static UUID find(final Map<UUID, UUID> parent, final UUID id) {
        UUID root = id;
        while (!root.equals(parent.get(root))) root = parent.get(root);
        UUID current = id;
        while (!current.equals(root)) {
            final UUID next = parent.get(current);
            parent.put(current, root);
            current = next;
        }
        return root;
    }

    private static void union(final Map<UUID, UUID> parent, final UUID a, final UUID b) {
        final UUID rootA = find(parent, a);
        final UUID rootB = find(parent, b);
        if (!rootA.equals(rootB)) parent.put(rootB, rootA);
    }
}
