package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.PocketSized;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScaleState {
    private static final int CLIENT_HISTORY_LIMIT = 16;
    private static final Map<UUID, ServerState> SERVER = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> SERVER_IDS_VIEW =
            java.util.Collections.unmodifiableSet(SERVER.keySet());
    private static final Map<UUID, Double> CLIENT_CURRENT = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> CLIENT_CURRENT_VIEW =
            java.util.Collections.unmodifiableMap(CLIENT_CURRENT);
    private static final Map<UUID, Double> CLIENT_TARGET = new ConcurrentHashMap<>();
    private static final Map<UUID, ClientScaleHistory> CLIENT_HISTORY = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> CLIENT_KNOWN = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, BoundsKey> LAST_SERVER_BOUNDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> CLIENT_SNAP_INTERPOLATION = new ConcurrentHashMap<>();

    public static ServerState serverState(final ServerSubLevel subLevel) {
        return SERVER.computeIfAbsent(subLevel.getUniqueId(), ignored -> {
            final double initial = PocketSized.clampScale(subLevel.logicalPose().scale().x());
            final CompressionStage stage = CompressionStage.nearest(initial);
            return new ServerState(initial, stage, stage, null);
        });
    }

    public static ServerState restoreServerState(
            final ServerSubLevel subLevel,
            final double current,
            final CompressionStage stableStage,
            final CompressionStage requestedStage,
            final CompressionStage transitionStage
    ) {
        final ServerState state = new ServerState(
                PocketSized.clampScale(current),
                stableStage == null ? CompressionStage.nearest(current) : stableStage,
                requestedStage == null ? CompressionStage.nearest(current) : requestedStage,
                transitionStage
        );
        SERVER.put(subLevel.getUniqueId(), state);
        return state;
    }

    public static ServerState restoreServerState(
            final ServerSubLevel subLevel,
            final double current,
            final double oldTarget
    ) {
        return restoreServerState(
                subLevel,
                current,
                CompressionStage.nearest(current),
                CompressionStage.nearest(oldTarget),
                null
        );
    }

    public static boolean hasServerState(final UUID id) {
        return id != null && SERVER.containsKey(id);
    }

    public static boolean isSettled(final UUID id) {
        if (id == null) return true;
        final ServerState state = SERVER.get(id);
        return state == null || state.transitionStage() == null;
    }

    public static java.util.Set<UUID> trackedIds() {
        return SERVER_IDS_VIEW;
    }

    public static void clearServerState(final UUID id) {
        if (id != null) SERVER.remove(id);
    }

    public static double getScale(final SubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return 1.0D;
        if (subLevel instanceof final ClientSubLevel client) return getClientScale(client);
        if (subLevel instanceof final ServerSubLevel server) return getServerScale(server);
        return subLevel.logicalPose().scale().x();
    }

    public static CompressionStage getStage(final SubLevel subLevel) {
        if (subLevel instanceof final ServerSubLevel server) {
            final ServerState state = SERVER.get(server.getUniqueId());
            if (state != null && state.transitionStage == null) return state.stableStage;
        }
        return CompressionStage.nearest(getScale(subLevel));
    }

    public static double getServerScale(final ServerSubLevel subLevel) {
        if (subLevel == null) return 1.0D;
        final ServerState state = SERVER.get(subLevel.getUniqueId());
        return state == null ? subLevel.logicalPose().scale().x() : state.currentScale;
    }

    public static double getClientScale(final ClientSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return 1.0D;

        final UUID id = subLevel.getUniqueId();
        final ClientScaleHistory history = CLIENT_HISTORY.get(id);
        if (history == null) return getClientScale(id);

        final ClientSubLevelContainer container = ClientSubLevelContainer.getContainer(subLevel.getLevel());
        final double scale = container == null || container.getInterpolation().isStopped()
                ? history.latest()
                : history.sample(container.getInterpolation().getTickPointer());

        putOrRemove(CLIENT_CURRENT, id, scale);
        return scale;
    }

    public static double getClientScale(final UUID id) {
        return id == null ? 1.0D : CLIENT_CURRENT.getOrDefault(id, 1.0D);
    }

    public static Map<UUID, Double> clientScaledView() {
        return CLIENT_CURRENT_VIEW;
    }

    public static double getClientTarget(final UUID id) {
        return id == null ? 1.0D : CLIENT_TARGET.getOrDefault(id, 1.0D);
    }

    public static void acceptClientSnapshot(
            final UUID id,
            final int interpolationTick,
            final double current,
            final double target,
            final boolean snapInterpolation
    ) {
        if (id == null) return;

        final double clampedCurrent = PocketSized.clampScale(current);
        final double clampedTarget = PocketSized.clampScale(target);
        final boolean first = CLIENT_KNOWN.add(id);

        final double previous = getClientScale(id);
        CLIENT_HISTORY.computeIfAbsent(id, ignored -> new ClientScaleHistory())
                .accept(interpolationTick, clampedCurrent, previous, snapInterpolation);
        putOrRemove(CLIENT_TARGET, id, clampedTarget);

        if (first || snapInterpolation) {
            putOrRemove(CLIENT_CURRENT, id, clampedCurrent);
        }
        if (snapInterpolation) CLIENT_SNAP_INTERPOLATION.put(id, Boolean.TRUE);
    }

    public static boolean hasClientSnapshot(final UUID id) {
        return id != null && CLIENT_KNOWN.contains(id);
    }

    public static void forgetClientSnapshot(final UUID id) {
        if (id == null) return;
        CLIENT_KNOWN.remove(id);
        CLIENT_CURRENT.remove(id);
        CLIENT_TARGET.remove(id);
        CLIENT_HISTORY.remove(id);
        CLIENT_SNAP_INTERPOLATION.remove(id);
    }

    public static void clearClientSnapshots() {
        CLIENT_KNOWN.clear();
        CLIENT_CURRENT.clear();
        CLIENT_TARGET.clear();
        CLIENT_HISTORY.clear();
        CLIENT_SNAP_INTERPOLATION.clear();
    }

    public static boolean consumeClientInterpolationSnap(final UUID id) {
        return id != null && CLIENT_SNAP_INTERPOLATION.remove(id) != null;
    }

    private static void putOrRemove(final Map<UUID, Double> map, final UUID id, final double value) {
        if (Math.abs(value - 1.0D) <= PocketSized.EPSILON) map.remove(id);
        else map.put(id, value);
    }

    public static boolean isScaled(final SubLevel subLevel) {
        return Math.abs(getScale(subLevel) - 1.0D) > PocketSized.EPSILON;
    }

    public static boolean serverBoundsChanged(final ServerSubLevel subLevel) {
        final UUID id = subLevel.getUniqueId();
        if (id == null) return false;
        final BoundsKey before = LAST_SERVER_BOUNDS.get(id);
        final var bounds = subLevel.getPlot().getBoundingBox();
        if (before != null && before.matches(bounds)) return false;
        LAST_SERVER_BOUNDS.put(id, BoundsKey.of(bounds));
        return true;
    }

    public static void captureServerBounds(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        LAST_SERVER_BOUNDS.put(subLevel.getUniqueId(), BoundsKey.of(subLevel.getPlot().getBoundingBox()));
    }

    public static void clearServerBounds(final UUID id) {
        if (id != null) LAST_SERVER_BOUNDS.remove(id);
    }

    private record ClientScaleSample(int tick, double scale) {}

    private static final class ClientScaleHistory {
        private final ArrayDeque<ClientScaleSample> samples = new ArrayDeque<>();
        private double latest = 1.0D;

        private synchronized void accept(
                final int tick,
                final double scale,
                final double previousScale,
                final boolean snap
        ) {
            if (snap) {
                this.samples.clear();
                this.samples.addLast(new ClientScaleSample(tick, scale));
                this.latest = scale;
                return;
            }

            if (this.samples.isEmpty()) {
                this.samples.addLast(new ClientScaleSample(tick - 1, previousScale));
                this.samples.addLast(new ClientScaleSample(tick, scale));
                this.latest = scale;
                return;
            }

            final ClientScaleSample last = this.samples.getLast();
            if (tick < last.tick()) return;

            if (tick == last.tick()) {
                this.samples.removeLast();
            } else if (tick > last.tick() + 1) {
                this.samples.clear();
                this.samples.addLast(new ClientScaleSample(tick - 1, last.scale()));
            }

            this.samples.addLast(new ClientScaleSample(tick, scale));
            this.latest = scale;

            while (this.samples.size() > CLIENT_HISTORY_LIMIT) {
                this.samples.removeFirst();
            }
        }

        private synchronized double latest() {
            return this.latest;
        }

        private synchronized double sample(final double tick) {
            if (this.samples.isEmpty()) return this.latest;

            final var iterator = this.samples.iterator();
            ClientScaleSample before = iterator.next();
            if (tick <= before.tick()) return before.scale();

            while (iterator.hasNext()) {
                final ClientScaleSample after = iterator.next();
                if (tick <= after.tick()) {
                    final double span = after.tick() - before.tick();
                    if (span <= 0.0D) return after.scale();
                    final double alpha = (tick - before.tick()) / span;
                    return PocketSized.clampScale(before.scale() + (after.scale() - before.scale()) * alpha);
                }
                before = after;
            }

            return this.latest;
        }
    }

    public static final class ServerState {
        private double currentScale;
        private CompressionStage stableStage;
        private CompressionStage requestedStage;
        private CompressionStage transitionStage;

        private double transitionFrom;
        private int transitionTicks;
        private double transitionSpeedFactor = 1.0D;

        private boolean persistenceDirty;

        private ServerState(
                final double currentScale,
                final CompressionStage stableStage,
                final CompressionStage requestedStage,
                final CompressionStage transitionStage
        ) {
            this.currentScale = PocketSized.clampScale(currentScale);
            this.stableStage = stableStage;
            this.requestedStage = requestedStage;
            this.transitionStage = transitionStage;
            this.transitionFrom = this.currentScale;
        }

        public double currentScale() { return this.currentScale; }
        public CompressionStage stableStage() { return this.stableStage; }
        public CompressionStage requestedStage() { return this.requestedStage; }
        public CompressionStage transitionStage() { return this.transitionStage; }
        public double transitionFrom() { return this.transitionFrom; }
        public int transitionTicks() { return this.transitionTicks; }
        public double transitionSpeedFactor() { return this.transitionSpeedFactor; }
        public boolean needsPersistence() { return this.persistenceDirty; }
        public void tickTransition() { this.transitionTicks++; }

        public void beginTransition(final CompressionStage stage, final double fromScale) {
            beginTransition(stage, fromScale, 1.0D);
        }

        public void beginTransition(
                final CompressionStage stage,
                final double fromScale,
                final double speedFactor
        ) {
            this.transitionStage = stage;
            this.transitionFrom = PocketSized.clampScale(fromScale);
            this.transitionTicks = 0;
            this.transitionSpeedFactor = speedFactor;
            this.persistenceDirty = true;
        }

        public void currentScale(final double value) { this.currentScale = PocketSized.clampScale(value); }
        public void stableStage(final CompressionStage value) {
            if (this.stableStage == value) return;
            this.stableStage = value;
            this.persistenceDirty = true;
        }
        public void requestedStage(final CompressionStage value) {
            if (this.requestedStage == value) return;
            this.requestedStage = value;
            this.persistenceDirty = true;
        }
        public void transitionStage(final CompressionStage value) {
            if (this.transitionStage == value) return;
            if (value == null) this.transitionSpeedFactor = 1.0D;
            this.transitionStage = value;
            this.persistenceDirty = true;
        }
        public void markPersisted() { this.persistenceDirty = false; }
    }

    private ScaleState() {}
}
