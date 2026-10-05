package com.misterblusky9.pym.internal.extension;

import com.misterblusky9.pym.api.spi.ScaleCoupling;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MergedCouplingGraphTest {
    private static final UUID A = id(1);
    private static final UUID B = id(2);
    private static final UUID C = id(3);
    private static final UUID D = id(4);
    private static final UUID E = id(5);

    public static void main(final String[] args) {
        oneGraphPassesThrough();
        componentsJoinAcrossProviders();
        incompleteInAnyProviderBlocksTheWholeComponent();
        System.out.println("MergedCouplingGraphTest: PASS");
    }

    private static void oneGraphPassesThrough() {
        final ScaleCoupling.Graph only = graph(Set.of(), Set.of(A, B));
        check(MergedCouplingGraph.of(List.of(only)) == only, "a single provider's graph is used as is");
        check(MergedCouplingGraph.of(List.of()) == ScaleCoupling.Graph.EMPTY, "no providers means no coupling");
    }

    private static void componentsJoinAcrossProviders() {
        final ScaleCoupling.Graph welds = graph(Set.of(), Set.of(A, B), Set.of(D, E));
        final ScaleCoupling.Graph chains = graph(Set.of(), Set.of(B, C));
        final ScaleCoupling.Graph merged = MergedCouplingGraph.of(List.of(welds, chains));

        check(merged.component(A).equals(Set.of(A, B, C)), "A-B in one provider and B-C in another couple A to C");
        check(merged.component(C).equals(Set.of(A, B, C)), "the merged component is the same from every member");
        check(merged.component(D).equals(Set.of(D, E)), "unrelated components stay apart");
        check(merged.members().equals(Set.of(A, B, C, D, E)), "members are the union of every provider");
        check(merged.component(id(99)).equals(Set.of(id(99))), "an uncoupled sublevel is its own component");
        check(merged.complete(A) && merged.complete(D), "complete when every provider says so");
    }

    private static void incompleteInAnyProviderBlocksTheWholeComponent() {
        final ScaleCoupling.Graph welds = graph(Set.of(), Set.of(A, B));
        final ScaleCoupling.Graph chains = graph(Set.of(C), Set.of(B, C), Set.of(D, E));
        final ScaleCoupling.Graph merged = MergedCouplingGraph.of(List.of(welds, chains));

        check(!merged.complete(A), "an unloaded member reached through another provider blocks the component");
        check(merged.complete(D), "other components are not affected");
    }

    @SafeVarargs
    private static ScaleCoupling.Graph graph(final Set<UUID> incomplete, final Set<UUID>... components) {
        final Map<UUID, Set<UUID>> byMember = new HashMap<>();
        final Set<UUID> members = new HashSet<>();
        for (final Set<UUID> component : components) {
            members.addAll(component);
            for (final UUID member : component) byMember.put(member, component);
        }
        return new ScaleCoupling.Graph() {
            @Override public Set<UUID> members() { return members; }
            @Override public Set<UUID> component(final UUID origin) {
                return byMember.getOrDefault(origin, Set.of(origin));
            }
            @Override public boolean complete(final UUID origin) {
                for (final UUID member : this.component(origin)) {
                    if (incomplete.contains(member)) return false;
                }
                return true;
            }
        };
    }

    private static UUID id(final int value) {
        return new UUID(0L, value);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) throw new AssertionError(message);
    }

    private MergedCouplingGraphTest() {}
}
