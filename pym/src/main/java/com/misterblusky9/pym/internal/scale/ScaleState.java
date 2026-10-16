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

    public static ScaleRecord record(ServerSubLevel subLevel) {
        return SERVER.computeIfAbsent(subLevel.getUniqueId(), ignored ->
                new ScaleRecord(ScaleBounds.clampValid(subLevel.logicalPose().scale().x())));
    }

    public static ScaleRecord recordOf(UUID id) {
        return id == null ? null : SERVER.get(id);
    }

    public static ScaleRecord settle(ServerSubLevel subLevel, double scale) {
        ScaleRecord record = record(subLevel);
        record.settle(ScaleBounds.clampValid(scale));
        return record;
    }

    public static void forget(UUID id) {
        if (id == null) return;
        SERVER.remove(id);
        LAST_SERVER_BOUNDS.remove(id);
    }

    public static boolean hasServerState(UUID id) {
        return id != null && SERVER.containsKey(id);
    }

    public static boolean isSettled(UUID id) {
        ScaleRecord record = recordOf(id);
        return record == null || !record.moving();
    }

    public static boolean isSettled(SubLevel subLevel) {
        if (subLevel == null) return true;
        if (subLevel instanceof ServerSubLevel server) return isSettled(server.getUniqueId());
        if (subLevel instanceof ClientSubLevel client) {
            UUID id = client.getUniqueId();
            return !hasClientSnapshot(id)
                    || ScaleBounds.same(getClientScale(client), getClientTarget(id));
        }
        return true;
    }

    public static boolean isAt(SubLevel subLevel, double scale) {
        return isSettled(subLevel) && ScaleBounds.same(getSettledScale(subLevel), scale);
    }

    public static double getScale(SubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return 1.0D;
        if (subLevel instanceof ClientSubLevel client) return getClientScale(client);
        if (subLevel instanceof ServerSubLevel server) return getServerScale(server);
        return subLevel.logicalPose().scale().x();
    }

    public static double getSettledScale(SubLevel subLevel) {
        if (subLevel instanceof ServerSubLevel server) {
            ScaleRecord record = recordOf(server.getUniqueId());
            return record == null ? getServerScale(server) : record.settled();
        }
        if (subLevel instanceof ClientSubLevel client && hasClientSnapshot(client.getUniqueId())) {
            return getClientTarget(client.getUniqueId());
        }
        return getScale(subLevel);
    }

    public static double getTargetScale(ServerSubLevel subLevel) {
        if (subLevel == null) return 1.0D;
        ScaleRecord record = recordOf(subLevel.getUniqueId());
        return record == null ? getServerScale(subLevel) : record.target();
    }

    public static double getServerScale(ServerSubLevel subLevel) {
        if (subLevel == null) return 1.0D;
        ScaleRecord record = recordOf(subLevel.getUniqueId());
        return record == null ? subLevel.logicalPose().scale().x() : record.current();
    }

    public static double getClientScale(ClientSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return 1.0D;

        UUID id = subLevel.getUniqueId();
        ClientScaleHistory history = CLIENT_HISTORY.get(id);
        if (history == null) return getClientScale(id);

        ClientSubLevelContainer container = ClientSubLevelContainer.getContainer(subLevel.getLevel());
        double scale = container == null || container.getInterpolation().isStopped()
                ? history.latest()
                : history.sample(container.getInterpolation().getTickPointer());

        putOrRemove(CLIENT_CURRENT, id, scale);
        return scale;
    }

    public static double getClientScale(UUID id) {
        return id == null ? 1.0D : CLIENT_CURRENT.getOrDefault(id, 1.0D);
    }

    public static Map<UUID, Double> clientScaledView() {
        return CLIENT_CURRENT_VIEW;
    }

    public static double getClientTarget(UUID id) {
        return id == null ? 1.0D : CLIENT_TARGET.getOrDefault(id, 1.0D);
    }

    public static void acceptClientSnapshot(
            UUID id,
            int interpolationTick,
            double current,
            double target,
            boolean snapInterpolation
    ) {
        if (id == null) return;

        double clampedCurrent = ScaleBounds.clampValid(current);
        double clampedTarget = ScaleBounds.clampValid(target);
        boolean first = CLIENT_KNOWN.add(id);

        double previous = getClientScale(id);
        CLIENT_HISTORY.computeIfAbsent(id, ignored -> new ClientScaleHistory())
                .accept(interpolationTick, clampedCurrent, previous, snapInterpolation);
        putOrRemove(CLIENT_TARGET, id, clampedTarget);

        if (first || snapInterpolation) {
            putOrRemove(CLIENT_CURRENT, id, clampedCurrent);
        }
        if (snapInterpolation) CLIENT_SNAP_INTERPOLATION.put(id, Boolean.TRUE);
    }

    public static boolean hasClientSnapshot(UUID id) {
        return id != null && CLIENT_KNOWN.contains(id);
    }

    public static void forgetClientSnapshot(UUID id) {
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

    public static boolean consumeClientInterpolationSnap(UUID id) {
        return id != null && CLIENT_SNAP_INTERPOLATION.remove(id) != null;
    }

    private static void putOrRemove(Map<UUID, Double> map, UUID id, double value) {
        if (ScaleBounds.same(value, 1.0D)) map.remove(id);
        else map.put(id, value);
    }

    public static boolean isScaled(SubLevel subLevel) {
        return !ScaleBounds.same(getScale(subLevel), 1.0D);
    }

    public static boolean serverBoundsChanged(ServerSubLevel subLevel) {
        UUID id = subLevel.getUniqueId();
        if (id == null) return false;
        BoundsKey before = LAST_SERVER_BOUNDS.get(id);
        var bounds = subLevel.getPlot().getBoundingBox();
        if (before != null && before.matches(bounds)) return false;
        LAST_SERVER_BOUNDS.put(id, BoundsKey.of(bounds));
        return true;
    }

    public static void clearServerBounds(UUID id) {
        if (id != null) LAST_SERVER_BOUNDS.remove(id);
    }

    public static void captureServerBounds(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;
        LAST_SERVER_BOUNDS.put(subLevel.getUniqueId(), BoundsKey.of(subLevel.getPlot().getBoundingBox()));
    }

    private record ClientScaleSample(int tick, double scale) {}

    private static final class ClientScaleHistory {
        private final ArrayDeque<ClientScaleSample> samples = new ArrayDeque<>();
        private double latest = 1.0D;

        private synchronized void accept(
                int tick,
                double scale,
                double previousScale,
                boolean snap
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

            ClientScaleSample last = this.samples.getLast();
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

        private synchronized double sample(double tick) {
            if (this.samples.isEmpty()) return this.latest;

            var iterator = this.samples.iterator();
            ClientScaleSample before = iterator.next();
            if (tick <= before.tick()) return before.scale();

            while (iterator.hasNext()) {
                ClientScaleSample after = iterator.next();
                if (tick <= after.tick()) {
                    double span = after.tick() - before.tick();
                    if (span <= 0.0D) return after.scale();
                    double alpha = (tick - before.tick()) / span;
                    return ScaleBounds.clampValid(before.scale() + (after.scale() - before.scale()) * alpha);
                }
                before = after;
            }

            return this.latest;
        }
    }

    private ScaleState() {}
}
