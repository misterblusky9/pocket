package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.block.ModBlockEntities;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pym.api.client.Portrait;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;

public final class HatStandRenderer implements BlockEntityRenderer<HatStandBlockEntity> {
    private static final ResourceLocation HEAD =
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "textures/block/hatters_stand_head.png");
    private static final float[] HAT_INFLATION = {0.25F, 0.05F, -1.0F};
    private static SkullModel[] heads;
    private static ModelPart helmet;
    private static Map<SkullBlock.Type, SkullModelBase> skulls;

    public HatStandRenderer(BlockEntityRendererProvider.Context context) {}

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.HAT_STAND.get(), HatStandRenderer::new);
    }

    @Override
    public void render(HatStandBlockEntity stand, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (!stand.getBlockState().hasProperty(HatStandBlock.FACING)) return;
        float yaw = stand.getBlockState().getValue(HatStandBlock.FACING).toYRot() - 180.0F;
        renderHead(stand.getSkinProfile(), stand.getSkinLayer(), yaw, pose, buffers, light);
        if (!stand.getActivator().isEmpty() && stand.getHeadwearLayer() != HatStandBlockEntity.LAYER_REMOVED)
            renderHeadwear(BuildHostBlockEntity.plain(stand.getActivator()), stand.getHeadwearLayer() == 1, yaw, pose, buffers, light);
        renderHat(stand, partialTick, pose, buffers, light, overlay);
        if (!stand.getActivator().isEmpty()) {
            pose.pushPose();
            HatPlateSlot.INSTANCE.transform(stand.getLevel(), stand.getBlockPos(), stand.getBlockState(), pose);
            ValueBoxRenderer.renderItemIntoValueBox(BuildHostBlockEntity.plain(stand.getActivator()), pose, buffers, light, overlay);
            pose.popPose();
        }
    }

    private static void renderHat(HatStandBlockEntity stand, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                  int light, int overlay) {
        UUID token = stand.getHat();
        ClientSubLevel mirror = token == null || stand.getActivator().isEmpty() ? null : BuildMirrors.standing(token);
        if (mirror == null || !(stand.getBlockState().getBlock() instanceof HatStandBlock block)) return;
        Vec3 anchor = block.anchor(stand.getBlockState());
        Vector3d head = BuildSites.head(mirror, BuildMirrors.easedOffset(token, partialTick));
        float scale = (float) BuildSites.SCALE;
        pose.pushPose();
        pose.translate(anchor.x, anchor.y, anchor.z);
        pose.mulPose(new Quaternionf(block.orientation(stand.getBlockState())));
        pose.scale(scale, scale, scale);
        Portrait.draw(mirror, head, pose, buffers, light, overlay);
        for (BuildMirrors.Rider rider : BuildMirrors.riders(token)) {
            Pose3d relative = rider.relative();
            pose.pushPose();
            pose.translate(relative.position().x() - head.x, relative.position().y() - head.y,
                    relative.position().z() - head.z);
            pose.mulPose(new Quaternionf(relative.orientation()));
            pose.scale((float) relative.scale().x(), (float) relative.scale().y(), (float) relative.scale().z());
            Portrait.draw(rider.mirror(), relative.rotationPoint(), pose, buffers, light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(HatStandBlockEntity stand) {
        return new AABB(stand.getBlockPos()).inflate(8.0);
    }

    private static void renderHeadwear(ItemStack stack, boolean flat, float yaw, PoseStack pose,
                                       MultiBufferSource buffers, int light) {
        Minecraft minecraft = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(0.5, 5.0 / 16.0, 0.5);
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        if (flat) {
            float scale = HatRendering.headwearFlat(stack);
            pose.translate(0.0F, -0.25F, 0.0F);
            pose.scale(scale, scale, scale);
            pose.translate(0.0F, 0.25F, 0.0F);
        }
        if (stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.HEAD) {
            if (helmet == null) helmet = minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR).getChild("head");
            int dye = stack.is(ItemTags.DYEABLE) ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, -6265536)) : -1;
            for (ArmorMaterial.Layer layer : armor.getMaterial().value().layers()) {
                ResourceLocation texture = ClientHooks.getArmorTexture(minecraft.player, stack, layer, false, EquipmentSlot.HEAD);
                helmet.render(pose, buffers.getBuffer(RenderType.armorCutoutNoCull(texture)), light,
                        OverlayTexture.NO_OVERLAY, layer.dyeable() ? dye : -1);
            }
            if (stack.hasFoil())
                helmet.render(pose, buffers.getBuffer(RenderType.armorEntityGlint()), light, OverlayTexture.NO_OVERLAY);
        } else if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof AbstractSkullBlock skull) {
            if (skulls == null) skulls = SkullBlockRenderer.createSkullRenderers(minecraft.getEntityModels());
            pose.scale(1.1875F, -1.1875F, -1.1875F);
            pose.translate(-0.5, 0.0, -0.5);
            SkullBlockRenderer.renderSkull(null, 180.0F, 0.0F, pose, buffers, light, skulls.get(skull.getType()),
                    SkullBlockRenderer.getRenderType(skull.getType(), stack.get(DataComponents.PROFILE)));
        } else {
            pose.translate(0.0F, -0.25F, 0.0F);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.scale(0.625F, -0.625F, -0.625F);
            minecraft.getItemRenderer().renderStatic(stack, ItemDisplayContext.HEAD, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, minecraft.level, 0);
        }
        pose.popPose();
    }

    public static void renderHead(@Nullable ResolvableProfile profile, int layer, float yaw, PoseStack pose,
                                  MultiBufferSource buffers, int light) {
        RenderType type = profile == null ? RenderType.entityTranslucent(HEAD)
                : SkullBlockRenderer.getRenderType(SkullBlock.Types.PLAYER, profile);
        pose.pushPose();
        pose.translate(0.0, 5.0 / 16.0, 0.0);
        pose.translate(0.5, 0.0, 0.5);
        pose.scale(1.005F, 1.005F, 1.005F);
        pose.translate(-0.5, 0.0, -0.5);
        SkullBlockRenderer.renderSkull(null, yaw, 0.0F, pose, buffers, light, head(layer), type);
        pose.popPose();
    }

    private static SkullModel head(int layer) {
        if (heads == null) {
            heads = new SkullModel[HAT_INFLATION.length];
            for (int i = 0; i < heads.length; i++) {
                MeshDefinition mesh = SkullModel.createHeadModel();
                if (HAT_INFLATION[i] >= 0.0F) mesh.getRoot().getChild("head").addOrReplaceChild("hat",
                        CubeListBuilder.create().texOffs(32, 0)
                                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(HAT_INFLATION[i])),
                        PartPose.ZERO);
                heads[i] = new SkullModel(LayerDefinition.create(mesh, 64, 64).bakeRoot());
            }
        }
        return heads[Math.floorMod(layer, heads.length)];
    }
}
