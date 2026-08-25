package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.network.CompressionBeamPayload;
import com.misterblusky9.pocket.network.CompressionSyncPayload;

public final class CompressionClientHooks {
    private CompressionClientHooks() {}

    public static void acceptBeam(final CompressionBeamPayload payload) {
        if (payload == null) return;
        CompressionBeamRenderer.setFiring(
                payload.playerId(), payload.firing(), payload.growing());
    }

    public static void accept(final CompressionSyncPayload payload) {
        if (payload == null) return;

        if (payload.pulse()) {
            if (payload.sourcePlayerId() != null
                    && CompressionBeamRenderer.surge(payload.sourcePlayerId(), payload.subLevelId())) {
                return;
            }
            CompressionFieldRenderer.pulse(payload.subLevelId(), payload.sourcePlayerId());
            return;
        }

        if (payload.release()) {
            CompressionFieldRenderer.release(payload.subLevelId());

            CompressionBeamRenderer.clearTarget(payload.subLevelId());
            return;
        }

        CompressionBeamRenderer.setTarget(payload.sourcePlayerId(), payload.subLevelId());
        CompressionFieldRenderer.begin(
                payload.subLevelId(), payload.hitLocalPos(), payload.acquireTicks(),
                payload.growing(), payload.cellLimit(), !payload.beam());
    }
}
