package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.compression.EntityCompressionTargeting;
import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.misterblusky9.pocket.network.ShrinkRayScalePayload;
import com.misterblusky9.pocket.scale.ScaleState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import com.misterblusky9.pocket.PocketSized;

public final class ShrinkRayPick {
    public static boolean tryPick() {
        return pick(aimedScale());
    }

    public static boolean pick(final double scale) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !CreativeShrinkRayItem.permits(player, scale)) return false;

        final ItemStack ray = player.getMainHandItem();
        CreativeShrinkRayItem.setSelectedScale(ray, scale);
        PacketDistributor.sendToServer(new ShrinkRayScalePayload(InteractionHand.MAIN_HAND, scale));
        ShrinkRayControls.onPicked(CreativeShrinkRayItem.selectedScale(ray, player));
        return true;
    }

    public static boolean aiming() {
        return PocketSized.isValidScale(aimedScale());
    }

    private static double aimedScale() {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) return Double.NaN;
        if (!(player.getMainHandItem().getItem() instanceof CreativeShrinkRayItem)) return Double.NaN;

        final EntityCompressionTargeting.Target entity =
                EntityCompressionTargeting.find(player, CreativeShrinkRayItem.RANGE);
        if (entity != null) return ScaleReadout.value(entity.entity());

        final CompressionAim.Aim aim = CompressionAim.of(player, CreativeShrinkRayItem.RANGE);
        return aim == null ? Double.NaN : ScaleState.getSettledScale(aim.subLevel());
    }

    private ShrinkRayPick() {}
}
