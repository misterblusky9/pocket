package com.misterblusky9.pym.internal.collision;

import com.misterblusky9.pym.internal.debug.PymTrace;
import com.misterblusky9.pym.internal.entity.EntityScaleTracker;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CollisionLog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long SECOND = 1_000_000_000L;
    private static final long WINDOW = 5L * SECOND;
    private static final long SLOW = 20_000_000L;
    private static final Map<String, String> CHOICES = new ConcurrentHashMap<>();
    private static final Map<String, long[]> QUIET = new ConcurrentHashMap<>();
    private static final Map<String, long[]> WINDOWS = new ConcurrentHashMap<>();
    private static final Map<String, long[]> REBUILDS = new ConcurrentHashMap<>();
    private static final ThreadLocal<long[]> CALL = ThreadLocal.withInitial(() -> new long[4]);

    public static boolean watching(Entity entity) {
        return PymTrace.enabled() && entity instanceof Player;
    }

    public static String id(SubLevel subLevel) {
        UUID id = subLevel.getUniqueId();
        return (id == null ? "?" : id.toString().substring(0, 8))
                + (subLevel.getLevel() != null && subLevel.getLevel().isClientSide() ? "/client" : "/server");
    }

    public static void choice(SubLevel subLevel, Entity entity, double scale, String choice) {
        String key = entity.getUUID() + "|" + id(subLevel);
        if (choice.equals(CHOICES.get(key))) return;
        long skipped = allowed("choice|" + key, SECOND);
        if (skipped < 0L) return;
        CHOICES.put(key, choice);
        LOGGER.info("[Pym LOD] {} on sublevel {} at {}x (relative {}x){}: {}", who(entity), id(subLevel), number(scale),
                number(EntityScaleTracker.factor(entity) / scale), skipped > 0L ? " after " + skipped + " quick changes" : "", choice);
    }

    public static void begin() {
        long[] call = CALL.get();
        call[0] = System.nanoTime();
        call[1] = 0L;
        call[2] = 0L;
        call[3] = 0L;
    }

    public static void candidates(int count) {
        long[] call = CALL.get();
        call[1] += count;
        call[3] = 1L;
    }

    public static void original() {
        long[] call = CALL.get();
        call[2]++;
        call[3] = 1L;
    }

    public static void end(Entity entity) {
        long[] call = CALL.get();
        if (call[3] == 0L || !watching(entity)) return;
        call[3] = 0L;
        long now = System.nanoTime();
        long nanos = now - call[0];
        String key = (entity.level().isClientSide() ? "client|" : "server|") + entity.getUUID();
        long[] window = WINDOWS.computeIfAbsent(key, ignored -> new long[6]);
        String summary = null;
        synchronized (window) {
            if (window[0] == 0L) window[0] = call[0];
            window[1]++;
            window[2] += nanos;
            window[3] = Math.max(window[3], nanos);
            window[4] += call[1];
            if (call[2] > 0L) window[5]++;
            long span = now - window[0];
            if (span >= WINDOW) {
                summary = String.format(Locale.ROOT,
                        "[Pym LOD] %s: %d collision calls near sublevels in %.1f s, %.1f ms total (%.1f ms per second), avg %.3f ms, max %.2f ms, avg %d candidate blocks per call, %d calls used Original | %s",
                        who(entity), window[1], span / 1.0E9, window[2] / 1.0E6, window[2] / 1.0E6 / (span / 1.0E9),
                        window[2] / 1.0E6 / window[1], window[3] / 1.0E6, window[4] / window[1], window[5], choices(entity));
                java.util.Arrays.fill(window, 0L);
            }
        }
        if (summary != null) LOGGER.info(summary);
        if (nanos >= SLOW && allowed("slow|" + key, SECOND) >= 0L) {
            LOGGER.warn("[Pym LOD] slow collision call: {} took {} ms with {} candidate blocks over all substeps{} | {}",
                    who(entity), number(nanos / 1.0E6), call[1], call[2] > 0L ? ", " + call[2] + " sublevel queries via Original" : "",
                    choices(entity));
        }
    }

    public static void built(SubLevel subLevel, int dirty, int sections, String result, long nanos) {
        if (!PymTrace.enabled()) return;
        String id = id(subLevel);
        if (dirty < 0) {
            LOGGER.info("[Pym LOD] built LOD0 for sublevel {}: {} non-air sections, {} in {} ms", id, sections, result, number(nanos / 1.0E6));
            return;
        }
        long now = System.nanoTime();
        long[] rebuilds = REBUILDS.computeIfAbsent(id, ignored -> new long[4]);
        String summary = null;
        synchronized (rebuilds) {
            if (rebuilds[0] == 0L) rebuilds[0] = now;
            rebuilds[1]++;
            rebuilds[2] += dirty;
            rebuilds[3] += nanos;
            if (now - rebuilds[0] >= WINDOW) {
                summary = String.format(Locale.ROOT,
                        "[Pym LOD] sublevel %s: %d LOD0 rebuilds from block edits (%d dirty sections) in %.1f s, %.1f ms total; each rebuild drops LOD1-3 | now %s",
                        id, rebuilds[1], rebuilds[2], (now - rebuilds[0]) / 1.0E9, rebuilds[3] / 1.0E6, result);
                java.util.Arrays.fill(rebuilds, 0L);
            }
        }
        if (summary != null) LOGGER.info(summary);
    }

    public static void detail(String label, int grow, int lod, long cells, int colliders, long nanos) {
        if (!PymTrace.enabled()) return;
        long skipped = allowed("detail|" + label + "|" + grow, SECOND);
        if (skipped < 0L) return;
        LOGGER.info("[Pym LOD] built LOD{} (grow {}) for sublevel {} off-thread: {} colliders from {} cells in {} ms{}",
                lod, grow, label, colliders, cells, number(nanos / 1.0E6), skipped > 0L ? " (" + skipped + " more builds in the last second)" : "");
    }

    private static String choices(Entity entity) {
        StringBuilder line = new StringBuilder();
        String prefix = entity.getUUID() + "|";
        for (Map.Entry<String, String> entry : CHOICES.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            if (!line.isEmpty()) line.append(" ; ");
            line.append(entry.getKey().substring(prefix.length())).append(": ").append(entry.getValue());
        }
        return line.isEmpty() ? "no indexed choice recorded" : line.toString();
    }

    private static long allowed(String key, long interval) {
        long now = System.nanoTime();
        long[] last = QUIET.computeIfAbsent(key, ignored -> new long[]{now - interval, 0L});
        synchronized (last) {
            if (now - last[0] < interval) {
                last[1]++;
                return -1L;
            }
            long skipped = last[1];
            last[0] = now;
            last[1] = 0L;
            return skipped;
        }
    }

    private static String who(Entity entity) {
        return (entity.level().isClientSide() ? "client " : "server ") + entity.getName().getString()
                + " (" + number(EntityScaleTracker.factor(entity)) + "x)";
    }

    private static String number(double value) {
        if (value > 0.0 && value < 1.0 && Math.abs(1.0 / value - Math.rint(1.0 / value)) < 1.0E-6) {
            return "1/" + (long) Math.rint(1.0 / value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private CollisionLog() {}
}
