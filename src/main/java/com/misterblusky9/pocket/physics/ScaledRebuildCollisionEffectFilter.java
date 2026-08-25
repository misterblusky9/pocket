package com.misterblusky9.pocket.physics;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class ScaledRebuildCollisionEffectFilter {
    public static final int RECORD_WIDTH = 15;

    private static final Map<BodyKey, Long> REBUILT_AT = new HashMap<>();

    public static synchronized void markRebuilt(
            final long sceneHandle,
            final int bodyId,
            final long gameTime
    ) {
        if (sceneHandle == RapierBridge.NO_SCENE || bodyId == RapierBridge.NO_BODY) return;
        REBUILT_AT.put(new BodyKey(sceneHandle, bodyId), gameTime);
    }

    public static synchronized double[] filter(
            final long sceneHandle,
            final long gameTime,
            final double[] collisions
    ) {
        REBUILT_AT.entrySet().removeIf(entry ->
                entry.getKey().sceneHandle == sceneHandle && entry.getValue() != gameTime);

        if (collisions == null || collisions.length == 0 || collisions.length % RECORD_WIDTH != 0) {
            return collisions;
        }

        int keptLength = 0;
        double[] filtered = null;
        for (int offset = 0; offset < collisions.length; offset += RECORD_WIDTH) {
            final int firstBody = (int) collisions[offset];
            final int secondBody = (int) collisions[offset + 1];
            final boolean rebuildArtifact = rebuiltThisTick(sceneHandle, firstBody, gameTime)
                    || rebuiltThisTick(sceneHandle, secondBody, gameTime);

            if (rebuildArtifact) {
                if (filtered == null) {
                    filtered = new double[collisions.length - RECORD_WIDTH];
                    if (keptLength > 0) {
                        System.arraycopy(collisions, 0, filtered, 0, keptLength);
                    }
                }
                continue;
            }

            if (filtered != null) {
                if (keptLength + RECORD_WIDTH > filtered.length) {
                    filtered = Arrays.copyOf(filtered, keptLength + RECORD_WIDTH);
                }
                System.arraycopy(collisions, offset, filtered, keptLength, RECORD_WIDTH);
            }
            keptLength += RECORD_WIDTH;
        }

        if (filtered == null) return collisions;
        return keptLength == filtered.length ? filtered : Arrays.copyOf(filtered, keptLength);
    }

    public static synchronized void forgetScene(final long sceneHandle) {
        REBUILT_AT.keySet().removeIf(key -> key.sceneHandle == sceneHandle);
    }

    private static boolean rebuiltThisTick(final long sceneHandle, final int bodyId, final long gameTime) {
        return REBUILT_AT.getOrDefault(new BodyKey(sceneHandle, bodyId), Long.MIN_VALUE) == gameTime;
    }

    private record BodyKey(long sceneHandle, int bodyId) {}

    private ScaledRebuildCollisionEffectFilter() {
    }
}
