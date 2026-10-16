package com.misterblusky9.pocket.client;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.client.ScaleReadout;
import com.misterblusky9.pym.api.Pym;

import com.misterblusky9.pocket.compression.EntityCompressionTargeting;
import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.misterblusky9.pocket.item.ScaleSelectingItem;
import com.misterblusky9.pocket.item.SelfResizeDeviceItem;
import com.misterblusky9.pocket.network.ShrinkRayScalePayload;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ShrinkRayPick {
    public static boolean tryPick() {
        return pick(aimedScale());
    }

    public static boolean pick(double scale) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ScaleSelectingItem tool)
                || !tool.permitsSelection(held, player, scale)) return false;

        if (ScaleBounds.same(CreativeShrinkRayItem.selectedScale(held), scale)) return true;
        tool.select(held, scale);
        PacketDistributor.sendToServer(new ShrinkRayScalePayload(InteractionHand.MAIN_HAND, scale));
        AllSoundEvents.CONFIRM.playAt(player.level(), player.position(), 1.0F, 1.0F, false);
        return true;
    }

    public static boolean aiming() {
        return ScaleBounds.isValid(aimedScale());
    }

    private static double aimedScale() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) return Double.NaN;
        ItemStack held = player.getMainHandItem();
        CreativeShrinkRayItem.TargetingMode targeting;
        if (held.getItem() instanceof CreativeShrinkRayItem) {
            targeting = CreativeShrinkRayItem.targetingMode(held);
        } else if (held.getItem() instanceof SelfResizeDeviceItem) {
            targeting = CreativeShrinkRayItem.TargetingMode.BOTH;
        } else {
            return Double.NaN;
        }

        if (targeting.allowsEntities()) {
            EntityCompressionTargeting.Target entity =
                    EntityCompressionTargeting.find(player, CreativeShrinkRayItem.RANGE, true);
            if (entity != null) {
                return ScaleReadout.value(entity.entity()) * EntityCompressionTargeting.containerScale(entity.entity());
            }
        }

        if (!targeting.allowsContraptions()) return Double.NaN;
        CompressionAim.Aim aim = CompressionAim.of(player, CreativeShrinkRayItem.RANGE);
        return aim == null ? Double.NaN : Pym.scale().settled(aim.subLevel());
    }

    private ShrinkRayPick() {}
}
