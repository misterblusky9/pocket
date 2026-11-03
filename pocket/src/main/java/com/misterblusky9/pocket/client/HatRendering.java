package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.compat.curios.CuriosHats;
import com.misterblusky9.pocket.item.ModDataComponents;
import com.misterblusky9.pym.api.client.Portrait;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.world.level.block.AbstractSkullBlock;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class HatRendering {
    private static final boolean CURIOS = ModList.get().isLoaded("curios");
    private static final float PLOT_BLOCK = 1.0F / 16.0F;
    private static final float HEADWEAR_SIZE = 8.2F;
    public static final float SKIN_FLAT = 8.1F / 9.0F;

    public static ItemStack headwear(LivingEntity entity) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (!CURIOS || CuriosHats.isCustomHat(head)) return head;
        ItemStack cosmetic = CuriosHats.find(entity);
        return cosmetic == null ? head : cosmetic;
    }

    public static @Nullable ClientSubLevel worn(LivingEntity entity) {
        ItemStack head = headwear(entity);
        return HatStandBlock.isHeadwear(head) ? BuildMirrors.get(head) : null;
    }

    public static Vector3d head(ClientSubLevel mirror) {
        BlockPos centre = mirror.getPlot().getCenterBlock();
        return new Vector3d(centre.getX(), centre.getY(), centre.getZ());
    }

    private static Vector3d centre(ClientSubLevel mirror) {
        Vector3d head = head(mirror);
        BoundingBox3ic bounds = mirror.getPlot().getBoundingBox();
        return bounds == null ? head : head.setComponent(1, (bounds.minY() + bounds.maxY() + 1) / 2.0);
    }

    public static int layerMode(LivingEntity entity) {
        ItemStack head = headwear(entity);
        return HatStandBlock.isHeadwear(head) ? head.getOrDefault(ModDataComponents.SKIN_LAYER.get(), 0) : 0;
    }

    public static int headwearMode(LivingEntity entity) {
        ItemStack head = headwear(entity);
        return worn(entity) == null ? 0
                : head.getOrDefault(ModDataComponents.HEADWEAR_LAYER.get(), 0);
    }

    public static float headwearFlat(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof AbstractSkullBlock
                ? HEADWEAR_SIZE / 9.5F : HEADWEAR_SIZE / 10.0F;
    }

    public static void flatten(PoseStack pose, ModelPart head, float scale) {
        PoseStack local = new PoseStack();
        head.translateAndRotate(local);
        Vector3f centre = local.last().pose().transformPosition(0.0F, -0.25F, 0.0F, new Vector3f());
        pose.translate(centre.x, centre.y, centre.z);
        pose.scale(scale, scale, scale);
        pose.translate(-centre.x, -centre.y, -centre.z);
    }

    public static boolean renderItem(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose,
                                     MultiBufferSource buffers, int light, int overlay) {
        if (!HatStandBlock.isHeadwear(stack)) return false;
        ClientSubLevel mirror = BuildMirrors.get(stack);
        if (mirror == null) return BuildMirrors.pending(stack);
        pose.pushPose();
        ClientHooks.handleCameraTransforms(pose,
                Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(Items.PLAYER_HEAD),
                context, leftHand);
        pose.translate(0.0F, -0.25F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.scale(PLOT_BLOCK, PLOT_BLOCK, PLOT_BLOCK);
        Portrait.draw(mirror, centre(mirror), pose, buffers, light, overlay);
        pose.popPose();
        return true;
    }

    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof LivingEntityRenderer<?, ?> renderer) addLayer(renderer);
        }
        for (EntityType<?> type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof LivingEntityRenderer<?, ?> renderer) addLayer(renderer);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLayer(LivingEntityRenderer renderer) {
        if (renderer.getModel() instanceof HeadedModel) renderer.addLayer(new HatLayer(renderer));
    }

    private HatRendering() {}
}
