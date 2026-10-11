package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.client.PocketSizedClient;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.item.PocketContainer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PackageRenderer.class, remap = false)
public abstract class PackageRendererOutlineMixin {
    @Unique
    private static final ResourceLocation OPAQUE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void pocket$outlineBox(
            final PackageEntity entity,
            final float yaw,
            final float partialTick,
            final PoseStack ms,
            final MultiBufferSource buffer,
            final int light,
            final CallbackInfo ci
    ) {
        if (!(buffer instanceof OutlineBufferSource)) return;
        final boolean silhouette = seeThroughCase(entity.box);
        if (!silhouette && !VisualizationManager.supportsVisualization(entity.level())) return;
        final PartialModel model = boxModel(entity.box);
        if (model == null) return;

        CachedBuffers.partial(model, Blocks.AIR.defaultBlockState())
                .translate(-0.5D, 0.0D, -0.5D)
                .rotateCentered(-AngleHelper.rad(yaw + 90.0F), Direction.UP)
                .nudge(entity.getId())
                .renderInto(ms, buffer.getBuffer(RenderType.outline(silhouette ? OPAQUE : InventoryMenu.BLOCK_ATLAS)));
    }

    @Unique
    private static boolean seeThroughCase(final ItemStack box) {
        return box.getItem() instanceof PocketCaseItem && PocketCaseItem.isFilled(box) && PocketContainer.of(box).translucent();
    }

    @Unique
    private static PartialModel boxModel(final ItemStack box) {
        if (box.getItem() instanceof PocketCaseItem && PocketCaseItem.isFilled(box)) {
            return PocketSizedClient.boxModelFor(box);
        }
        final ItemStack shown = box.isEmpty() || !PackageItem.isPackage(box) ? AllBlocks.CARDBOARD_BLOCK.asStack() : box;
        return AllPartialModels.PACKAGES.get(BuiltInRegistries.ITEM.getKey(shown.getItem()));
    }
}
