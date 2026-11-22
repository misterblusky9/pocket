package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.compat.WornHats;
import com.misterblusky9.pocket.item.ModDataComponents;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pym.api.client.Portrait;
import dev.ryanhcode.sable.companion.math.Pose3d;
import java.util.UUID;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.armor.CardboardArmorHandler;
import net.minecraft.world.entity.player.Player;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
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
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.minecraft.world.level.block.AbstractSkullBlock;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class HatRendering {
    private static final float PLOT_BLOCK = 1.0F / 16.0F;
    private static final float HEADWEAR_SIZE = 8.2F;
    private static final double HEADWEAR_REACH = 4.0;
    private static final float CAMERA_PULL = 0.9995F;
    private static final float GUI_PULL = 0.1F;
    private static final float SKULL_CAP_CAMERA_PULL = 0.99975F;
    private static final float SKULL_CAP_GUI_PULL = 0.05F;
    private static final float SKULL_CAP_SIZE = 8.0F;
    private static final float SKULL_CAP_SKIN_FLAT = 8.02F / 9.0F;
    public static final float SKIN_FLAT = 8.1F / 9.0F;
    public static final ResourceLocation KEYGEN_GLINT =
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "textures/misc/keygen_glint_item.png");

    public static ItemStack headwear(LivingEntity entity) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (WornHats.isCustomHat(head)) return head;
        ItemStack cosmetic = WornHats.find(entity);
        return cosmetic == null ? head : cosmetic;
    }

    public static @Nullable ClientSubLevel worn(LivingEntity entity) {
        ItemStack head = headwear(entity);
        return HatStandBlock.isHeadwear(head) ? BuildMirrors.get(head) : null;
    }

    public static boolean hatDisguised(Player player) {
        if (!CardboardArmorHandler.testForStealth(player) || !WornHats.isCustomHat(headwear(player))) return false;
        ClientSubLevel mirror = worn(player);
        return mirror != null && mirror.getPlot().getBoundingBox() != null;
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

    private static Vector3d framedWithHeadwear(ClientSubLevel mirror, Vector3d head) {
        BoundingBox3ic bounds = mirror.getPlot().getBoundingBox();
        if (bounds == null) return new Vector3d(head);
        double bottom = Math.min(bounds.minY(), head.y - HEADWEAR_REACH);
        double top = Math.max(bounds.maxY() + 1, head.y + HEADWEAR_REACH);
        return new Vector3d(head.x, (bottom + top) / 2.0, head.z);
    }

    public static int layerMode(LivingEntity entity) {
        ItemStack head = headwear(entity);
        if (!HatStandBlock.isHeadwear(head)) return 0;
        return skinLayer(head, head.getOrDefault(ModDataComponents.SKIN_LAYER.get(), 0));
    }

    public static int skinLayer(ItemStack headwear, int layer) {
        return headwear.is(ModItems.SKULL_CAP.get()) ? 2 : layer;
    }

    public static int headwearMode(LivingEntity entity) {
        ItemStack head = headwear(entity);
        if (head.is(ModItems.SKULL_CAP.get())) return HatStandBlockEntity.headwearLayer(head);
        return worn(entity) == null ? 0 : HatStandBlockEntity.headwearLayer(head);
    }

    public static float skinFlat(LivingEntity entity) {
        return headwear(entity).is(ModItems.SKULL_CAP.get()) ? SKULL_CAP_SKIN_FLAT : SKIN_FLAT;
    }

    public static void pullSkullCap(PoseStack pose, ItemStack headwear) {
        if (!headwear.is(ModItems.SKULL_CAP.get())) return;
        if (RenderSystem.getProjectionMatrix().m33() == 0.0F) pose.last().pose().scaleLocal(SKULL_CAP_CAMERA_PULL);
        else pose.last().pose().translateLocal(0.0F, 0.0F, SKULL_CAP_GUI_PULL);
    }

    public static float headwearFlat(ItemStack stack) {
        if (stack.is(ModItems.SKULL_CAP.get())) return SKULL_CAP_SIZE / 10.0F;
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

    public static void pullToCamera(PoseStack pose) {
        if (RenderSystem.getProjectionMatrix().m33() == 0.0F) pose.last().pose().scaleLocal(CAMERA_PULL);
        else pose.last().pose().translateLocal(0.0F, 0.0F, GUI_PULL);
    }

    public static void drawHat(@Nullable UUID token, ClientSubLevel mirror, Vector3d head, HatStandBlockEntity.NameEgg egg,
                               boolean foil, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                               float partialTick) {
        Portrait.begin(egg == HatStandBlockEntity.NameEgg.RAINBOW ? KEYGEN_GLINT
                : foil ? ItemRenderer.ENCHANTED_GLINT_ITEM : null);
        try {
            drawParts(token, mirror, head, egg, pose, buffers, light, overlay, partialTick);
        } finally {
            Portrait.end(buffers);
        }
    }

    private static void drawParts(@Nullable UUID token, ClientSubLevel mirror, Vector3d head, HatStandBlockEntity.NameEgg egg,
                                  PoseStack pose, MultiBufferSource buffers, int light, int overlay, float partialTick) {
        if (egg == HatStandBlockEntity.NameEgg.MELONS) {
            FacadeSkin.show(mirror);
            if (token != null) for (BuildMirrors.Rider rider : BuildMirrors.riders(token)) FacadeSkin.show(rider.mirror());
        }
        boolean rootDrawn = Portrait.draw(mirror, head, pose, buffers, light, overlay,
                entity -> token != null && HatNativeContraptionRender.drawnByPreview(token, entity));
        if (rootDrawn && token != null)
            HatNativeContraptionRender.draw(token, mirror, head, pose, buffers, light);
        if (rootDrawn && HatWool.changes(egg))
            HatWool.draw(egg, mirror, head, pose, buffers, light, overlay, partialTick);
        if (token == null) return;
        for (BuildMirrors.Rider rider : BuildMirrors.riders(token)) {
            Pose3d relative = rider.at();
            pose.pushPose();
            pose.translate(relative.position().x() - head.x, relative.position().y() - head.y,
                    relative.position().z() - head.z);
            pose.mulPose(new Quaternionf(relative.orientation()));
            pose.scale((float) relative.scale().x(), (float) relative.scale().y(), (float) relative.scale().z());
            boolean childDrawn = Portrait.draw(rider.mirror(), relative.rotationPoint(), pose, buffers, light, overlay,
                    entity -> HatNativeContraptionRender.drawnByPreview(token, entity));
            if (childDrawn)
                HatNativeContraptionRender.draw(token, rider.mirror(), relative.rotationPoint(), pose, buffers, light);
            if (childDrawn && HatWool.changes(egg))
                HatWool.draw(egg, rider.mirror(), relative.rotationPoint(), pose, buffers, light, overlay, partialTick);
            pose.popPose();
        }
    }

    public static boolean renderItem(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose,
                                     MultiBufferSource buffers, int light, int overlay) {
        if (!HatStandBlock.isHeadwear(stack)) return false;
        if (context != ItemDisplayContext.HEAD && !stack.getOrDefault(ModDataComponents.CUSTOM_SPRITE.get(), true))
            return false;
        ClientSubLevel mirror = BuildMirrors.get(stack);
        if (mirror == null) mirror = BuildMirrors.interim(stack);
        if (mirror == null) return BuildMirrors.pending(stack);
        int layer = HatStandBlockEntity.headwearLayer(stack);
        boolean headwear = layer != HatStandBlockEntity.LAYER_REMOVED;
        Vector3d head = BuildSites.head(mirror, BuildSites.offsetVector(stack), stack);
        Vector3d frame = headwear ? framedWithHeadwear(mirror, BuildSites.head(mirror, BuildSites.offsetVector(stack)))
                : centre(mirror);
        pose.pushPose();
        HatSlotFit.apply(pose, context, leftHand, mirror, head, frame,
                HatStandBlockEntity.NameEgg.of(stack) == HatStandBlockEntity.NameEgg.UPSIDE_DOWN,
                headwear ? (float) (1.0 / BuildSites.scale(stack)) : 0.0F);
        ClientHooks.handleCameraTransforms(pose,
                Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(Items.PLAYER_HEAD),
                context, leftHand);
        pose.translate(0.0F, -0.25F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.scale(PLOT_BLOCK, PLOT_BLOCK, PLOT_BLOCK);
        HatStandBlockEntity.NameEgg egg = HatStandBlockEntity.NameEgg.of(stack);
        if (egg == HatStandBlockEntity.NameEgg.UPSIDE_DOWN) {
            pose.translate(head.x - frame.x, head.y - frame.y, head.z - frame.z);
            pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
            pose.translate(frame.x - head.x, frame.y - head.y, frame.z - head.z);
        }
        if (headwear) {
            pose.pushPose();
            float standScale = (float) (1.0 / BuildSites.scale(stack));
            pose.translate(head.x - frame.x, head.y - frame.y, head.z - frame.z);
            pose.scale(standScale, standScale, standScale);
            HatStandRenderer.renderHeadwearAtHeadCentre(BuildHostBlockEntity.plain(stack), layer == 1, pose,
                    buffers, light);
            pose.popPose();
        }
        pose.pushPose();
        pullToCamera(pose);
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        if (context == ItemDisplayContext.GUI && egg == HatStandBlockEntity.NameEgg.SPIN)
            HatEggs.spin(pose, HatStandBlockEntity.spinMode(stack), mirror, frame, partialTick);
        drawHat(BuildHostBlockEntity.token(stack), mirror, frame, egg, stack.hasFoil(), pose, buffers, light, overlay,
                partialTick);
        pose.popPose();
        pose.popPose();
        return true;
    }

    public static boolean renderOnDepot(ItemStack stack, int angle, PoseStack pose, MultiBufferSource buffers,
                                        int light, int overlay) {
        if (!HatStandBlock.isHeadwear(stack)) return false;
        if (!stack.getOrDefault(ModDataComponents.CUSTOM_SPRITE.get(), true)) return false;
        ClientSubLevel mirror = BuildMirrors.get(stack);
        if (mirror == null) mirror = BuildMirrors.interim(stack);
        if (mirror == null) return BuildMirrors.pending(stack);
        Vector3d head = BuildSites.head(mirror, BuildSites.offsetVector(stack), stack);
        BoundingBox3ic bounds = mirror.getPlot().getBoundingBox();
        double bottom = bounds == null ? head.y : bounds.minY();
        double top = bounds == null ? head.y : bounds.maxY() + 1;
        float scale = (float) BuildSites.scale(stack);
        HatStandBlockEntity.NameEgg egg = HatStandBlockEntity.NameEgg.of(stack);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.scale(scale, scale, scale);
        pose.translate(0.0, (top - bottom) / 2.0, 0.0);
        if (egg == HatStandBlockEntity.NameEgg.UPSIDE_DOWN) pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.translate(0.0, head.y - (top + bottom) / 2.0, 0.0);
        pullToCamera(pose);
        drawHat(BuildHostBlockEntity.token(stack), mirror, head, egg, stack.hasFoil(), pose, buffers, light, overlay,
                Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
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
