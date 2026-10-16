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
            List<ScaleCoupling.Graph> graphs,
            Set<UUID> members,
            Map<UUID, Set<UUID>> components
    ) {
        this.graphs = graphs;
        this.members = members;
        this.components = components;
    }

    static ScaleCoupling.Graph of(List<ScaleCoupling.Graph> graphs) {
        if (graphs.isEmpty()) return ScaleCoupling.Graph.EMPTY;
        if (graphs.size() == 1) return graphs.get(0);

        Map<UUID, UUID> parent = new HashMap<>();
        for (ScaleCoupling.Graph graph : graphs) {
            Set<UUID> seen = new HashSet<>();
            for (UUID member : graph.members()) {
                if (member == null || !seen.add(member)) continue;
                parent.putIfAbsent(member, member);
                for (UUID linked : graph.component(member)) {
                    if (linked == null) continue;
                    seen.add(linked);
                    parent.putIfAbsent(linked, linked);
                    union(parent, member, linked);
                }
            }
        }

        Map<UUID, Set<UUID>> byRoot = new HashMap<>();
        for (UUID member : parent.keySet()) {
            byRoot.computeIfAbsent(find(parent, member), root -> new LinkedHashSet<>()).add(member);
        }
        Map<UUID, Set<UUID>> components = new HashMap<>();
        for (Set<UUID> component : byRoot.values()) {
            Set<UUID> frozen = Collections.unmodifiableSet(component);
            for (UUID member : component) components.put(member, frozen);
        }

        return new MergedCouplingGraph(
                List.copyOf(graphs), Collections.unmodifiableSet(parent.keySet()), components);
    }

    @Override
    public Set<UUID> members() {
        return this.members;
    }

    @Override
    public Set<UUID> component(UUID origin) {
        if (origin == null) return Set.of();
        Set<UUID> component = this.components.get(origin);
        return component == null ? Set.of(origin) : component;
    }

    @Override
    public boolean complete(UUID origin) {
        for (UUID member : this.component(origin)) {
            for (ScaleCoupling.Graph graph : this.graphs) {
                if (graph.contains(member) && !graph.complete(member)) return false;
            }
        }
        return true;
    }

    private static UUID find(Map<UUID, UUID> parent, UUID id) {
        UUID root = id;
        while (!root.equals(parent.get(root))) root = parent.get(root);
        UUID current = id;
        while (!current.equals(root)) {
            UUID next = parent.get(current);
            parent.put(current, root);
            current = next;
        }
        return root;
    }

    private static void union(Map<UUID, UUID> parent, UUID a, UUID b) {
        UUID rootA = find(parent, a);
        UUID rootB = find(parent, b);
        if (!rootA.equals(rootB)) parent.put(rootB, rootA);
    }
}
