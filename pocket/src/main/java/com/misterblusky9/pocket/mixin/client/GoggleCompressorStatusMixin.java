package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.block.PortableSubspaceCompressorBlockEntity;
import com.misterblusky9.pocket.client.CompressionFieldRenderer;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

@Mixin(value = GoggleOverlayRenderer.class, remap = false)
public abstract class GoggleCompressorStatusMixin {
    @Inject(
            method = "renderOverlay",
            at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 2),
            remap = false
    )
    private static void pocket$appendCompressorStatus(
            GuiGraphics graphics,
            DeltaTracker deltaTracker,
            CallbackInfo ci,
            @Local List<Component> tooltip
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !GogglesItem.isWearingGoggles(mc.player)) return;
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;

        SubLevel sub = Sable.HELPER.getContaining(mc.level, hit.getLocation());
        if (sub == null) sub = Sable.HELPER.getContaining(mc.level, hit.getBlockPos());
        if (sub == null) return;

        if (sub.getLevel().getBlockState(hit.getBlockPos()).is(ModBlocks.PORTABLE_SUBSPACE_COMPRESSOR.get())) {
            appendCompressorHud(tooltip, sub, hit);
        }
    }

    private static void appendCompressorHud(
            List<Component> tooltip,
            SubLevel sub,
            BlockHitResult hit
    ) {
        if (!tooltip.isEmpty()) tooltip.add(CommonComponents.EMPTY);

        CreateLang.builder().add(Component.translatable("pocket.goggles.compressor"))
                .style(ChatFormatting.GOLD).forGoggles(tooltip);

        UUID id = sub.getUniqueId();
        if (id != null && CompressionFieldRenderer.isGripped(id)) {
            if (!CompressionFieldRenderer.isSealed(id)) {
                int percent = Math.max(0, Math.min(100,
                        Math.round(CompressionFieldRenderer.progress(id) * 100.0F)));
                CreateLang.builder().add(Component.translatable("pocket.goggles.acquiring", percent))
                        .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
            } else {
                CreateLang.builder().add(Component.translatable(CompressionFieldRenderer.isGrowing(id) ? "pocket.goggles.growing" : "pocket.goggles.shrinking"))
                        .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
            }
        } else {
            BlockEntity blockEntity = sub.getLevel().getBlockEntity(hit.getBlockPos());
            boolean noPower = blockEntity instanceof PortableSubspaceCompressorBlockEntity compressor
                    && Math.abs(compressor.getSpeed()) < 1.0F;
            CreateLang.builder().add(Component.translatable(noPower ? "pocket.goggles.idle.no_power" : "pocket.goggles.idle"))
                    .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }
    }
}
