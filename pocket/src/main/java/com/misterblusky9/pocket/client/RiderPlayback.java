package com.misterblusky9.pocket.client;

import dev.ryanhcode.sable.companion.math.Pose3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class RiderPlayback {
    private static final double DELAY = 2.0D;
    private static final double RESYNC = 4.0D;
    private static final double EASE = 0.02D;
    private static final int LIMIT = 16;

    private record Snapshot(long tick, Pose3d pose) {}

    private final ArrayDeque<Snapshot> snapshots = new ArrayDeque<>();
    private double offset;
    private boolean synced;

    public void push(long tick, double clock, Pose3d pose) {
        double gap = tick - clock;
        if (!synced || Math.abs(gap - offset) > RESYNC) {
            offset = gap;
            synced = true;
        } else {
            offset += (gap - offset) * EASE;
        }
        if (!snapshots.isEmpty() && tick <= snapshots.peekLast().tick()) return;
        snapshots.addLast(new Snapshot(tick, pose));
        while (snapshots.size() > LIMIT) snapshots.removeFirst();
    }

    public Pose3d at(double clock, Pose3d fallback) {
        if (snapshots.isEmpty()) return new Pose3d(fallback);
        double time = clock + offset - DELAY;
        Iterator<Snapshot> iterator = snapshots.iterator();
        Snapshot before = iterator.next();
        if (time <= before.tick()) return new Pose3d(before.pose());
        while (iterator.hasNext()) {
            Snapshot after = iterator.next();
            if (time < after.tick()) return blend(before, after, time);
            before = after;
        }
        return new Pose3d(before.pose());
    }

    private static Pose3d blend(Snapshot before, Snapshot after, double time) {
        double amount = (time - before.tick()) / (after.tick() - before.tick());
        Pose3d pose = new Pose3d(after.pose());
        pose.position().set(new Vector3d(before.pose().position()).lerp(after.pose().position(), amount));
        pose.orientation().set(new Quaterniond(before.pose().orientation()).slerp(after.pose().orientation(), amount));
        return pose;
    }
}
