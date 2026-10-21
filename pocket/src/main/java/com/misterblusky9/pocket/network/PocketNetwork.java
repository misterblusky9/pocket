package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.item.CompressionGunItem;
import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.misterblusky9.pocket.item.MagnifyingGlassItem;
import com.misterblusky9.pocket.item.ScaleToolModifier;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pocket.item.ScaleSelectingItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PocketNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("20");
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
                EntityCompressionGlowPayload.TYPE,
                EntityCompressionGlowPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.misterblusky9.pocket.client.ShrinkRayHoverOutline.compressionGlow(
                                payload.entityId(), payload.growing(), payload.ticks())
                )
        );
        registrar.playToClient(
                ShrinkRayBeamColourPayload.TYPE,
                ShrinkRayBeamColourPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.misterblusky9.pocket.client.PocketBeamColours.push(payload.colour(), payload.target(), payload.shooter())
                )
        );
        registrar.playToServer(
                MagnifyingGlassPayload.TYPE,
                MagnifyingGlassPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        MagnifyingGlassItem.setRaised(context.player(), payload.zoomed()))
        );
        registrar.playToServer(
                CompressionGunSettingsPayload.TYPE,
                CompressionGunSettingsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    ItemStack stack = context.player().getItemInHand(payload.hand());
                    if (!(stack.getItem() instanceof CompressionGunItem)) return;

                    if (!CompressionGunItem.modeLocked(context.player(), payload.hand())) {
                        CompressionGunItem.setGrowing(stack, payload.growing());
                    }
                })
        );
        registrar.playToClient(
                CrossScaleWeldListPayload.TYPE,
                CrossScaleWeldListPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.misterblusky9.pocket.compat.simulated.CrossScaleWelds
                                .acceptClientWelds(payload.welds()))
        );
        registrar.playToServer(
                CrossScaleWeldPayload.TYPE,
                CrossScaleWeldPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        payload.handle(player);
                    }
                })
        );
        registrar.playToServer(
                HotGluePunchPayload.TYPE,
                HotGluePunchPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        payload.handle(player);
                    }
                })
        );
        registrar.playToServer(
                CannonExpansionPayload.TYPE,
                CannonExpansionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    ItemStack cannon = context.player().getItemInHand(payload.hand());

                    if (cannon.getItem() instanceof com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem) {
                        com.misterblusky9.pocket.pocket.CannonExpansionMode.set(cannon, payload.mode());
                    }
                })
        );
        registrar.playToServer(
                ShrinkRayScalePayload.TYPE,
                ShrinkRayScalePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    ItemStack stack = context.player().getItemInHand(payload.hand());
                    if (stack.getItem() instanceof ScaleSelectingItem tool
                            && tool.permitsSelection(stack, context.player(), payload.scale())) {
                        tool.select(stack, payload.scale());
                    }
                })
        );
        registrar.playBidirectional(
                PersonalLockPayload.TYPE,
                PersonalLockPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.flow().isServerbound()) {
                        if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                            com.misterblusky9.pocket.compression.PersonalLock.set(player, payload.unlocked());
                        }
                    } else {
                        com.misterblusky9.pocket.compression.PersonalLock.setClient(payload.unlocked());
                    }
                })
        );
        registrar.playToServer(
                ShrinkRaySettingsPayload.TYPE,
                ShrinkRaySettingsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    ItemStack stack = context.player().getItemInHand(payload.hand());
                    if (!(stack.getItem() instanceof CreativeShrinkRayItem ray)) return;
                    CreativeShrinkRayItem.TargetingMode mode =
                            CreativeShrinkRayItem.TargetingMode.byId(payload.targetingMode());
                    double scale = payload.scale();
                    if (!ScaleBounds.isValid(scale)
                            || !CreativeShrinkRayItem.limits(mode, context.player()).contains(scale)) return;

                    CreativeShrinkRayItem.setTargetingMode(stack, mode, context.player());
                    ray.select(stack, scale);
                })
        );
        registrar.playToServer(
                ScaleToolModifierPayload.TYPE,
                ScaleToolModifierPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ScaleToolModifier.set(context.player(), payload.targetOnly(), payload.follow())
                )
        );
        registrar.playToServer(
                FacadeAlignmentPayload.TYPE,
                FacadeAlignmentPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        payload.handle(player);
                    }
                })
        );
        registrar.playToClient(
                LiveCaseHeldPayload.TYPE,
                LiveCaseHeldPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.misterblusky9.pocket.item.LiveCaseHolds.accept(payload.held())
                )
        );
        registrar.playToClient(
                FacadeAlignmentSyncPayload.TYPE,
                FacadeAlignmentSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.misterblusky9.pocket.client.CopycatFacadeFrames.acceptAlignment(
                                payload.craft(), payload.offset())
                )
        );
    }

    private PocketNetwork() {}
}
