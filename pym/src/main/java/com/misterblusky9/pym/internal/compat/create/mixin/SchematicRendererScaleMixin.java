package com.misterblusky9.pym.internal.compat.create.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.compat.create.SchematicGrid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.SchematicRenderer;
import dev.ryanhcode.sable.neoforge.mixinterface.compatibility.create.schematics.SchematicLevelExtension;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.catnip.render.ShadedBlockSbbBuilder;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = SchematicRenderer.class, remap = false)
public abstract class SchematicRendererScaleMixin {
    @Shadow @Final protected SchematicLevel schematic;
    @Shadow @Final private BlockPos anchor;

    @Inject(
            method = "drawLayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;enableCaching()V"),
            remap = false,
            require = 1
    )
    private void pym$holdScaled(
            RenderType layer,
            CallbackInfoReturnable<SuperByteBuffer> cir,
            @Share("scaled") LocalRef<List<SchematicLevelExtension.SchematicSubLevel>> scaled
    ) {
        List<SchematicLevelExtension.SchematicSubLevel> held = new ArrayList<>();
        ((SchematicLevelExtension) this.schematic).sable$getSubLevels().removeIf(subLevel ->
                !ScaleBounds.same(SchematicGrid.relative(subLevel.uuid()), ScaleBounds.FULL) && held.add(subLevel));
        scaled.set(held);
    }

    @Inject(
            method = "drawLayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;clearCache()V"
            ),
            remap = false,
            require = 1
    )
    private void pym$drawScaled(
            RenderType layer,
            CallbackInfoReturnable<SuperByteBuffer> cir,
            @Local BlockRenderDispatcher dispatcher,
            @Local ModelBlockRenderer renderer,
            @Local RandomSource random,
            @Local PoseStack poseStack,
            @Local BlockPos.MutableBlockPos mutableBlockPos,
            @Local ShadedBlockSbbBuilder sbbBuilder,
            @Share("scaled") LocalRef<List<SchematicLevelExtension.SchematicSubLevel>> scaled
    ) {
        for (SchematicLevelExtension.SchematicSubLevel subLevel : scaled.get()) {
            SchematicLevel renderWorld = subLevel.level();
            BoundingBox bounds = renderWorld.getBounds();
            float scale = (float) SchematicGrid.relative(subLevel.uuid());
            renderWorld.renderMode = true;
            poseStack.pushPose();
            poseStack.translate(subLevel.position().x, subLevel.position().y, subLevel.position().z);
            poseStack.mulPose(new Quaternionf(subLevel.orientation()));
            poseStack.scale(scale, scale, scale);

            for (BlockPos localPos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
                BlockPos pos = mutableBlockPos.setWithOffset(localPos, this.anchor);
                BlockState state = renderWorld.getBlockState(pos);
                if (state.getRenderShape() != RenderShape.MODEL) continue;
                BakedModel model = dispatcher.getBlockModel(state);
                BlockEntity blockEntity = renderWorld.getBlockEntity(localPos);
                ModelData modelData = blockEntity != null ? blockEntity.getModelData() : ModelData.EMPTY;
                modelData = model.getModelData(renderWorld, pos, state, modelData);
                long seed = state.getSeed(pos);
                random.setSeed(seed);
                if (!model.getRenderTypes(state, random, modelData).contains(layer)) continue;
                poseStack.pushPose();
                poseStack.translate(localPos.getX(), localPos.getY(), localPos.getZ());
                renderer.tesselateBlock(renderWorld, model, state, pos, poseStack, sbbBuilder, true,
                        random, seed, OverlayTexture.NO_OVERLAY, modelData, layer);
                poseStack.popPose();
            }

            poseStack.popPose();
            renderWorld.renderMode = false;
        }
    }

    @Inject(
            method = "drawLayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;clearCache()V",
                    shift = At.Shift.AFTER
            ),
            remap = false,
            require = 1
    )
    private void pym$releaseScaled(
            RenderType layer,
            CallbackInfoReturnable<SuperByteBuffer> cir,
            @Share("scaled") LocalRef<List<SchematicLevelExtension.SchematicSubLevel>> scaled
    ) {
        ((SchematicLevelExtension) this.schematic).sable$getSubLevels().addAll(scaled.get());
    }
}
