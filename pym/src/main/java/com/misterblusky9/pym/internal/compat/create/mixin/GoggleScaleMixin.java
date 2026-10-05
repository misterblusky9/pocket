package com.misterblusky9.pym.internal.compat.create.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.client.ScaleReadout;
import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = GoggleOverlayRenderer.class, remap = false)
public abstract class GoggleScaleMixin {
    @Inject(
            method = "renderOverlay",
            at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 2),
            remap = false
    )
    private static void pym$appendScale(
            final GuiGraphics graphics,
            final DeltaTracker deltaTracker,
            final CallbackInfo ci,
            @Local final List<Component> tooltip
    ) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !GogglesItem.isWearingGoggles(mc.player)) return;
        if (!(mc.hitResult instanceof final BlockHitResult hit)) return;

        SubLevel subLevel = Sable.HELPER.getContaining(mc.level, hit.getLocation());
        if (subLevel == null) subLevel = Sable.HELPER.getContaining(mc.level, hit.getBlockPos());
        if (subLevel == null || !Pym.scale().isScaled(subLevel)) return;

        if (!tooltip.isEmpty()) tooltip.add(CommonComponents.EMPTY);
        CreateLang.builder().text(" Pym").style(ChatFormatting.GOLD).forGoggles(tooltip);
        CreateLang.builder().text("Scale: " + ScaleReadout.of(subLevel)).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
    }
}
