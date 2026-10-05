package com.misterblusky9.pym.internal.scale;

import com.misterblusky9.pym.api.ScaleBounds;
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
    private static final Map<UUID, ScaleRecord> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> CLIENT_CURRENT = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> CLIENT_CURRENT_VIEW =
            java.util.Collections.unmodifiableMap(CLIENT_CURRENT);
    private static final Map<UUID, Double> CLIENT_TARGET = new ConcurrentHashMap<>();
    private static final Map<UUID, ClientScaleHistory> CLIENT_HISTORY = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> CLIENT_KNOWN = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, BoundsKey> LAST_SERVER_BOUNDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> CLIENT_SNAP_INTERPOLATION = new ConcurrentHashMap<>();

    public static ScaleRecord record(final ServerSubLevel subLevel) {
        return SERVER.computeIfAbsent(subLevel.getUniqueId(), ignored ->
                new ScaleRecord(ScaleBounds.clampValid(subLevel.logicalPose().scale().x())));
    }

    public static ScaleRecord recordOf(final UUID id) {
        return id == null ? null : SERVER.get(id);
    }

    public static ScaleRecord settle(final ServerSubLevel subLevel, final double scale) {
        final ScaleRecord record = record(subLevel);
        record.settle(ScaleBounds.clampValid(scale));
        return record;
    }

    public static void forget(final UUID id) {
        if (id == null) return;
        SERVER.remove(id);
        LAST_SERVER_BOUNDS.remove(id);
    }

    public static boolean hasServerState(final UUID id) {
        return id != null && SERVER.containsKey(id);
    }

    public static boolean isSettled(final UUID id) {
        final ScaleRecord record = recordOf(id);
        return record == null || !record.moving();
    }

    public static boolean isSettled(final SubLevel subLevel) {
        if (subLevel == null) return true;
        if (subLevel instanceof final ServerSubLevel server) return isSettled(server.getUniqueId());
        if (subLevel instanceof final ClientSubLevel client) {
            final UUID id = client.getUniqueId();
            return !hasClientSnapshot(id)
                    || ScaleBounds.same(getClientScale(client), getClientTarget(id));
        }
        return true;
    }

    public static boolean isAt(final SubLevel subLevel, final double scale) {
        return isSettled(subLevel) && ScaleBounds.same(getSettledScale(subLevel), scale);
    }

    public static double getScale(final SubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return 1.0D;
        if (subLevel instanceof final ClientSubLevel client) return getClientScale(client);
        if (subLevel instanceof final ServerSubLevel server) return getServerScale(server);
        return subLevel.logicalPose().scale().x();
    }

    public static double getSettledScale(final SubLevel subLevel) {
        if (subLevel instanceof final ServerSubLevel server) {
            final ScaleRecord record = recordOf(server.getUniqueId());
            return record == null ? getServerScale(server) : record.settled();
        }
        if (subLevel instanceof final ClientSubLevel client && hasClientSnapshot(client.getUniqueId())) {
            return getClientTarget(client.getUniqueId());
        }
        return getScale(subLevel);
    }

    public static double getTargetScale(final ServerSubLevel subLevel) {
        if (subLevel == null) return 1.0D;
        final ScaleRecord record = recordOf(subLevel.getUniqueId());
        return record == null ? getServerScale(subLevel) : record.target();
    }

    public static double getServerScale(final ServerSubLevel subLevel) {
        if (subLevel == null) return 1.0D;
        final ScaleRecord record = recordOf(subLevel.getUniqueId());
        return record == null ? subLevel.logicalPose().scale().x() : record.current();
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

        final double clampedCurrent = ScaleBounds.clampValid(current);
        final double clampedTarget = ScaleBounds.clampValid(target);
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
        if (ScaleBounds.same(value, 1.0D)) map.remove(id);
        else map.put(id, value);
    }

    public static boolean isScaled(final SubLevel subLevel) {
        return !ScaleBounds.same(getScale(subLevel), 1.0D);
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

    public static void clearServerBounds(final UUID id) {
        if (id != null) LAST_SERVER_BOUNDS.remove(id);
    }

    public static void captureServerBounds(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        LAST_SERVER_BOUNDS.put(subLevel.getUniqueId(), BoundsKey.of(subLevel.getPlot().getBoundingBox()));
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
                    return ScaleBounds.clampValid(before.scale() + (after.scale() - before.scale()) * alpha);
                }
                before = after;
            }

            return this.latest;
        }
    }

    private ScaleState() {}
}
