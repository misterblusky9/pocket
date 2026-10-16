package com.misterblusky9.pym.internal.scale;

import com.misterblusky9.pym.api.ResizeResult;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.ScaleDriver;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScaleDrivers {
    public static final int SUSPEND_TICKS = 60;
    private static final long DEFAULT_LEASE = 8L;

    private static final Map<UUID, Lease> EXTERNAL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SUSPENDED = new ConcurrentHashMap<>();

    public static void drive(ServerSubLevel subLevel, ScaleDriver driver, long until) {
        if (subLevel == null || driver == null || subLevel.getUniqueId() == null) return;
        EXTERNAL.put(subLevel.getUniqueId(), new Lease(driver, until));
    }

    public static void drive(ServerSubLevel subLevel, ScaleDriver driver) {
        if (subLevel == null) return;
        drive(subLevel, driver, subLevel.getLevel().getGameTime() + DEFAULT_LEASE);
    }

    public static void release(UUID id) {
        if (id != null) EXTERNAL.remove(id);
    }

    public static boolean suspend(ServerSubLevel subLevel, long now) {
        if (subLevel == null || subLevel.getUniqueId() == null) return false;
        boolean fresh = !isSuspended(subLevel.getUniqueId(), now);
        SUSPENDED.put(subLevel.getUniqueId(), now + SUSPEND_TICKS);
        return fresh;
    }

    public static void sustain(UUID id, long now) {
        if (id != null) SUSPENDED.put(id, now + SUSPEND_TICKS);
    }

    public static boolean isSuspended(UUID id, long now) {
        Long until = id == null ? null : SUSPENDED.get(id);
        if (until == null) return false;
        if (now <= until) return true;
        SUSPENDED.remove(id, until);
        return false;
    }

    public static void forget(UUID id) {
        if (id == null) return;
        EXTERNAL.remove(id);
        SUSPENDED.remove(id);
    }

    static void tick(ServerSubLevel subLevel, long now) {
        ScaleRecord record = ScaleState.recordOf(subLevel.getUniqueId());
        if (record != null && record.moving()) return;

        ScaleDriver driver = choose(subLevel, now);
        if (driver == null) return;

        double from = record == null ? ScaleState.getServerScale(subLevel) : record.target();
        double wanted = driver.commandedScale();
        if (!Double.isFinite(wanted)) return;
        if (ScaleBounds.same(wanted, from)) {
            driver.clearJamMessage();
            return;
        }

        double to = driver.stepwiseTransitions() ? driver.nextStage(from, wanted) : wanted;
        Resizer.Request request = new Resizer.Request(
                subLevel, to, driver.anchorLocalPoint(), driver.bounds(), driver.propagatesJoints(),
                ScalePhysicsMode.TRACKING, driver.transitionTicks(), driver);

        ResizePlan plan = Resizer.plan(request);
        if (plan.refusal() != null) {
            driver.setJamMessage(message(plan.refusal()));
            return;
        }
        if (!driver.tryBeginTransition(subLevel, from, to)) {
            String refused = driver.transitionRefusedMessage();
            if (refused != null) driver.setJamMessage(refused);
            return;
        }
        driver.clearJamMessage();
        Resizer.commit(plan, request);
    }

    private static ScaleDriver choose(ServerSubLevel subLevel, long now) {
        UUID id = subLevel.getUniqueId();
        boolean suspended = isSuspended(id, now);

        Lease lease = EXTERNAL.get(id);
        if (lease != null) {
            if (lease.driver.isRemoved() || now > lease.until) {
                EXTERNAL.remove(id, lease);
            } else {
                return suspended && lease.driver.yieldsToManualOverride() ? null : lease.driver;
            }
        }

        ScaleDriver best = null;
        for (BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (!(actor instanceof ScaleDriver driver) || driver.isRemoved()) continue;
            if (suspended && driver.yieldsToManualOverride()) continue;
            if (!Double.isFinite(driver.commandedScale())) continue;
            if (best == null || driver.commandedScale() < best.commandedScale() - ScaleBounds.EPSILON) best = driver;
        }
        return best;
    }

    private static String message(ResizeResult refusal) {
        return refusal.message() == null ? refusal.status().name() : refusal.message();
    }

    private record Lease(ScaleDriver driver, long until) {}

    private ScaleDrivers() {}
}
