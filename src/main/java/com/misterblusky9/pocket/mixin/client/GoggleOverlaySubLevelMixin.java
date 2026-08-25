package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.block.ModBlocks;
import com.misterblusky9.pocket.block.PortableSubspaceCompressorBlockEntity;
import com.misterblusky9.pocket.client.CompressionFieldRenderer;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleState;
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
public abstract class GoggleOverlaySubLevelMixin {
    @Inject(
            method = "renderOverlay",
            at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 2),
            remap = false
    )
    private static void pocket$appendSubLevelScale(
            final GuiGraphics graphics,
            final DeltaTracker deltaTracker,
            final CallbackInfo ci,
            @Local final List<Component> tooltip
    ) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !GogglesItem.isWearingGoggles(mc.player)) return;
        if (!(mc.hitResult instanceof final BlockHitResult hit)) return;

        SubLevel sub = Sable.HELPER.getContaining(mc.level, hit.getLocation());
        if (sub == null) sub = Sable.HELPER.getContaining(mc.level, hit.getBlockPos());
        if (sub == null) return;

        final boolean lookingAtCompressor = sub.getLevel().getBlockState(hit.getBlockPos())
                .is(ModBlocks.PORTABLE_SUBSPACE_COMPRESSOR.get());

        if (lookingAtCompressor) {
            appendCompressorHud(tooltip, sub, hit);
            return;
        }

        if (!ScaleState.isScaled(sub)) return;

        if (!tooltip.isEmpty()) tooltip.add(CommonComponents.EMPTY);

        CreateLang.builder().text(" Pocket Sized").style(ChatFormatting.GOLD).forGoggles(tooltip);
        CreateLang.builder().text("Current scale: " + CompressionStage.nearest(ScaleState.getScale(sub)).label())
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
    }

    private static void appendCompressorHud(
            final List<Component> tooltip,
            final SubLevel sub,
            final BlockHitResult hit
    ) {
        if (!tooltip.isEmpty()) tooltip.add(CommonComponents.EMPTY);

        CreateLang.builder().text(" Portable Subspace Compressor")
                .style(ChatFormatting.GOLD).forGoggles(tooltip);

        final UUID id = sub.getUniqueId();
        if (id != null && CompressionFieldRenderer.isGripped(id)) {
            if (!CompressionFieldRenderer.isSealed(id)) {
                final int percent = Math.max(0, Math.min(100,
                        Math.round(CompressionFieldRenderer.progress(id) * 100.0F)));
                CreateLang.builder().text("Acquiring hull: " + percent + "%")
                        .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
            } else {
                CreateLang.builder().text(CompressionFieldRenderer.isGrowing(id) ? "Growing..." : "Shrinking...")
                        .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
            }
        } else {
            final BlockEntity blockEntity = sub.getLevel().getBlockEntity(hit.getBlockPos());
            final boolean noPower = blockEntity instanceof PortableSubspaceCompressorBlockEntity compressor
                    && Math.abs(compressor.getSpeed()) < 1.0F;
            CreateLang.builder().text(noPower ? "Idle — no rotational power" : "Idle")
                    .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }

        CreateLang.builder().text("Current scale: " + CompressionStage.nearest(ScaleState.getScale(sub)).label())
                .style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
    }
}
