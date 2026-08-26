package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.item.CompressionGunItem;
import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

public final class ScaleNetwork {
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("8");
        registrar.playToClient(
                ScaleSyncPayload.TYPE,
                ScaleSyncPayload.STREAM_CODEC,
                (payload, context) -> ScaleState.acceptClientSnapshot(
                        payload.subLevelId(),
                        payload.currentScale(),
                        payload.targetScale(),
                        payload.snapInterpolation()
                )
        );
        registrar.playToClient(
                CompressionSyncPayload.TYPE,
                CompressionSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.misterblusky9.pocket.client.CompressionClientHooks.accept(payload)
                )
        );
        registrar.playToClient(
                CompressionBeamPayload.TYPE,
                CompressionBeamPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.misterblusky9.pocket.client.CompressionClientHooks.acceptBeam(payload)
                )
        );
        registrar.playToClient(
                SelfCompressionEffectPayload.TYPE,
                SelfCompressionEffectPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.misterblusky9.pocket.client.CompressionClientHooks.acceptSelfEffect(payload)
                )
        );
        registrar.playToClient(
                CompressionGunOpenMenuPayload.TYPE,
                CompressionGunOpenMenuPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.misterblusky9.pocket.client.CompressionGunScreenHooks.open(payload.hand())
                )
        );
        registrar.playToServer(
                CompressionGunSettingsPayload.TYPE,
                CompressionGunSettingsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    final ItemStack stack = context.player().getItemInHand(payload.hand());
                    if (!(stack.getItem() instanceof CompressionGunItem)) return;

                    CompressionGunItem.setTargetingMode(stack, payload.targetingMode());
                    CompressionGunItem.setGrowing(stack, payload.growing());
                })
        );
        registrar.playToServer(
                CannonExpansionPayload.TYPE,
                CannonExpansionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    final ItemStack cannon = context.player().getItemInHand(payload.hand());

                    if (cannon.getItem() instanceof com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem) {
                        com.misterblusky9.pocket.pocket.CannonExpansionMode.set(cannon, payload.mode());
                    }
                })
        );
        registrar.playToServer(
                ScaleRequestPayload.TYPE,
                ScaleRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof final net.minecraft.server.level.ServerPlayer player) {
                        answerScaleRequest(player, payload.subLevelId());
                    }
                })
        );
        registrar.playToClient(
                TweezerLocksPayload.TYPE,
                TweezerLocksPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.misterblusky9.pocket.client.TweezerDrag.acceptLocks(payload.locked()))
        );
        registrar.playToClient(
                TweezerGripsPayload.TYPE,
                TweezerGripsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.misterblusky9.pocket.client.TweezerDrag.acceptGrips(payload.grips()))
        );
        registrar.playToServer(
                TweezerGrabPayload.TYPE,
                TweezerGrabPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof final ServerPlayer player)) return;

                    final ServerSubLevelContainer container =
                            ServerSubLevelContainer.getContainer(player.serverLevel());
                    if (container == null) return;
                    if (!(container.getSubLevel(payload.subLevelId()) instanceof final ServerSubLevel craft)) {
                        return;
                    }

                    com.misterblusky9.pocket.tweezers.TweezerSessions.grab(
                            player,
                            craft,
                            new org.joml.Vector3d(payload.anchorX(), payload.anchorY(), payload.anchorZ()),
                            new org.joml.Vector3d(payload.goalX(), payload.goalY(), payload.goalZ()),
                            new org.joml.Quaterniond(
                                    payload.qx(), payload.qy(), payload.qz(), payload.qw()));
                })
        );
        registrar.playToServer(
                TweezerDragPayload.TYPE,
                TweezerDragPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.misterblusky9.pocket.tweezers.TweezerSessions.steer(
                                context.player(),
                                new org.joml.Vector3d(payload.goalX(), payload.goalY(), payload.goalZ()),
                                new org.joml.Quaterniond(
                                        payload.qx(), payload.qy(), payload.qz(), payload.qw())))
        );
        registrar.playToServer(
                TweezerCommandPayload.TYPE,
                TweezerCommandPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof final ServerPlayer player)) return;

                    switch (payload.command()) {
                        case STOP -> com.misterblusky9.pocket.tweezers.TweezerSessions.stop(player);

                        case LOCK -> com.misterblusky9.pocket.tweezers.TweezerSessions.lock(
                                player, payload.subLevelId());
                    }
                })
        );
        registrar.playToServer(
                ShrinkRayStagePayload.TYPE,
                ShrinkRayStagePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    final ItemStack stack = context.player().getItemInHand(payload.hand());
                    if (stack.getItem() instanceof CreativeShrinkRayItem) {
                        CreativeShrinkRayItem.setSelectedStage(stack, payload.stage());
                    }
                })
        );
        registrar.playToServer(
                CreativeShrinkRayTargetingPayload.TYPE,
                CreativeShrinkRayTargetingPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    final ItemStack stack = context.player().getItemInHand(payload.hand());
                    if (stack.getItem() instanceof CreativeShrinkRayItem) {
                        CreativeShrinkRayItem.setTargetingMode(stack, payload.targetingMode());
                    }
                })
        );
    }

    private static void answerScaleRequest(final net.minecraft.server.level.ServerPlayer player, final UUID id) {
        if (player == null || id == null) return;

        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(player.serverLevel());
        if (container == null) return;
        if (!(container.getSubLevel(id) instanceof final ServerSubLevel subLevel) || subLevel.isRemoved()) return;

        final double current = ScaleState.getServerScale(subLevel);
        final double target;
        if (ScaleState.hasServerState(id)) {
            final ScaleState.ServerState state = ScaleState.serverState(subLevel);
            target = state.transitionStage() == null
                    ? state.stableStage().scale()
                    : state.transitionStage().scale();
        } else {
            target = current;
        }

        PacketDistributor.sendToPlayer(player, new ScaleSyncPayload(id, current, target, true));
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
        PacketDistributor.sendToPlayersInDimension(
                subLevel.getLevel(),
                new ScaleSyncPayload(subLevel.getUniqueId(), current, target, snapInterpolation)
        );
    }

    private ScaleNetwork() {}
}
