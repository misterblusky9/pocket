package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.block.HelmBearingBlockEntity;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class HelmBearingNetwork {
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("10");
        registrar.playToServer(
                HelmBearingUpdatePayload.TYPE,
                HelmBearingUpdatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().level().getBlockEntity(payload.pos())
                            instanceof final HelmBearingBlockEntity be) {
                        be.setTargetAngleToUpdate(payload.targetAngle());
                        if (payload.shouldStop()) {
                            be.stopHolding();
                        } else {
                            be.startHolding();
                        }
                    }
                })
        );
    }

    private HelmBearingNetwork() {
    }
}
