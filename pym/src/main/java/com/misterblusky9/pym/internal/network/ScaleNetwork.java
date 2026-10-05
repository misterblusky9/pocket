package com.misterblusky9.pym.internal.network;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.client.DebugOverlay;
import com.misterblusky9.pym.internal.debug.PymTrace;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

public final class ScaleNetwork {
    private static final String PROTOCOL = "4";

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToClient(
                ScaleSyncPayload.TYPE,
                ScaleSyncPayload.STREAM_CODEC,
                (payload, context) -> ScaleState.acceptClientSnapshot(
                        payload.subLevelId(),
                        payload.interpolationTick(),
                        payload.currentScale(),
                        payload.targetScale(),
                        payload.snapInterpolation()
                )
        );
        registrar.playToClient(
                DebugPayload.TYPE,
                DebugPayload.STREAM_CODEC,
                (payload, context) -> {
                    DebugOverlay.setEnabled(payload.on());
                    PymTrace.setEnabled(payload.on());
                }
        );
        registrar.playToClient(
                EntityScaleSyncPayload.TYPE,
                EntityScaleSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    final var level = context.player().level();
                    final var entity = level.getEntity(payload.entityId());
                    if (entity != null) {
                        com.misterblusky9.pym.internal.entity.EntityScaleTracker.acceptClientSnapshot(
                                entity, payload.currentScale(), payload.targetScale(), payload.remainingTicks());
                    }
                })
        );
        registrar.playToServer(
                ScaleRequestPayload.TYPE,
                ScaleRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof final ServerPlayer player) {
                        answerScaleRequest(player, payload.subLevelId());
                    }
                })
        );
    }

    private static void answerScaleRequest(final ServerPlayer player, final UUID id) {
        if (player == null || id == null) return;

        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(player.serverLevel());
        if (container == null) return;
        if (!(container.getSubLevel(id) instanceof final ServerSubLevel subLevel) || subLevel.isRemoved()) return;

        final double current = ScaleState.getServerScale(subLevel);
        final double target = ScaleState.getTargetScale(subLevel);

        PacketDistributor.sendToPlayer(player, new ScaleSyncPayload(
                id,
                container.trackingSystem().getInterpolationTick(),
                current,
                target,
                true
        ));
    }

    public static void sendTrackingScale(
            final ServerPlayer player,
            final ServerSubLevel subLevel,
            final int interpolationTick
    ) {
        if (player == null || subLevel == null || subLevel.isRemoved()) return;

        final double current = ScaleState.getServerScale(subLevel);
        if (!ScaleBounds.isValid(current)) return;

        final double target = ScaleState.getTargetScale(subLevel);

        player.connection.send(new ClientboundCustomPayloadPacket(new ScaleSyncPayload(
                subLevel.getUniqueId(),
                interpolationTick,
                current,
                target,
                true
        )));
    }

    public static void sendScale(final ServerSubLevel subLevel, final double current, final double target) {
        sendScale(subLevel, current, target, false);
    }

    public static void sendScale(
            final ServerSubLevel subLevel,
            final double current,
            final double target,
            final boolean snapInterpolation
    ) {
        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(subLevel.getLevel());
        final int interpolationTick = container == null
                ? (int) subLevel.getLevel().getGameTime()
                : container.trackingSystem().getInterpolationTick();

        PacketDistributor.sendToPlayersInDimension(
                subLevel.getLevel(),
                new ScaleSyncPayload(
                        subLevel.getUniqueId(),
                        interpolationTick,
                        current,
                        target,
                        snapInterpolation
                )
        );
    }

    private ScaleNetwork() {}
}
