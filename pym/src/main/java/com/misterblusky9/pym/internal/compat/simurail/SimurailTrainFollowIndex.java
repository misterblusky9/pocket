package com.misterblusky9.pym.internal.compat.simurail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class SimurailTrainFollowIndex {
    private static final long FRESH_TICKS = 4;
    private final Map<Object, Observation> axles = new WeakHashMap<>();

    private record Observation(UUID carriage, UUID track, Object dimension, long tick) {}

    private static final class Group {
        private UUID track;
        private boolean initialized;
        private boolean ambiguous;
        private int axles;

        void add(UUID id) {
            axles++;
            if (!initialized) {
                track = id;
                initialized = true;
            } else if (!java.util.Objects.equals(track, id)) {
                ambiguous = true;
            }
            if (id == null) ambiguous = true;
        }

        boolean follows(UUID id) {
            return axles >= 2 && !ambiguous && id.equals(track);
        }
    }

    public synchronized void record(
            Object axle, UUID carriage, UUID track,
            Object dimension, long tick
    ) {
        if (axle == null || carriage == null || dimension == null) return;
        axles.put(axle, new Observation(carriage, track, dimension, tick));
    }

    public synchronized UUID trackOfCarriage(UUID carriage, Object dimension, long tick) {
        if (carriage == null || dimension == null) return null;
        Group group = new Group();
        for (Observation observation : axles.values()) {
            if (!carriage.equals(observation.carriage) || !dimension.equals(observation.dimension)) continue;
            long age = tick - observation.tick;
            if (age < 0 || age > FRESH_TICKS) continue;
            group.add(observation.track);
        }
        return group.axles >= 2 && !group.ambiguous ? group.track : null;
    }

    public synchronized Set<UUID> followers(
            UUID track, Object dimension, long tick
    ) {
        if (track == null || dimension == null) return Set.of();
        Map<UUID, Group> groups = new HashMap<>();
        for (Observation observation : axles.values()) {
            if (!dimension.equals(observation.dimension)) continue;
            long age = tick - observation.tick;
            if (age < 0 || age > FRESH_TICKS) continue;
            groups.computeIfAbsent(observation.carriage, unused -> new Group())
                    .add(observation.track);
        }
        Set<UUID> followers = new HashSet<>();
        groups.forEach((carriage, group) -> {
            if (group.follows(track)) followers.add(carriage);
        });
        return followers;
    }
}
