package com.misterblusky9.pocket.compat.accessories;

import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.compat.CompressorBeltRender;
import com.misterblusky9.pocket.compat.WornHats;
import com.misterblusky9.pocket.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.client.DefaultAccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class AccessoriesRenderers {
    public static void register(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            AccessoriesRendererRegistry.registerRenderer(ModItems.SELF_RESIZE_DEVICE.get(), Compressor::new);
            for (Item item : BuiltInRegistries.ITEM) {
                if (HatStandBlock.isHeadwear(item.getDefaultInstance()) && !AccessoriesRendererRegistry.hasRenderer(item)) {
                    AccessoriesRendererRegistry.registerRenderer(item, Headwear::new);
                }
            }
        });
    }

    private static final class Compressor implements AccessoryRenderer {
        @Override
        public <M extends LivingEntity> void render(
                ItemStack stack, SlotReference reference, PoseStack pose, EntityModel<M> model,
                MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount,
                float partialTicks, float ageInTicks, float netHeadYaw, float headPitch
        ) {
            if (model instanceof HumanoidModel<?> humanoid) {
                CompressorBeltRender.render(stack, reference.entity(), humanoid, pose, buffers, light);
            }
        }
    }

    private static final class Headwear implements AccessoryRenderer {
        @Override
        public <M extends LivingEntity> void render(
                ItemStack stack, SlotReference reference, PoseStack pose, EntityModel<M> model,
                MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount,
                float partialTicks, float ageInTicks, float netHeadYaw, float headPitch
        ) {
            if (WornHats.isCustomHat(stack)) return;
            DefaultAccessoryRenderer.INSTANCE.render(stack, reference, pose, model, buffers, light,
                    limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
        }

        @Override
        public boolean shouldRenderInFirstPerson(HumanoidArm arm, ItemStack stack, SlotReference reference) {
            return DefaultAccessoryRenderer.INSTANCE.shouldRenderInFirstPerson(arm, stack, reference);
        }
    }

    private AccessoriesRenderers() {}
}
