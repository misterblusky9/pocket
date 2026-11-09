package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.block.HatPlateSlot;
import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.block.TallHatStandBlock;
import com.misterblusky9.pocket.block.ModBlockEntities;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pym.api.client.Portrait;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.mojang.math.Axis;
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
import net.minecraft.world.level.block.state.BlockState;
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
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;

public final class HatStandRenderer implements BlockEntityRenderer<HatStandBlockEntity> {
    private static final ResourceLocation HEAD =
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "textures/block/hatters_stand_head.png");
    public static final ResourceLocation HEROBRINE =
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "textures/block/herobrine.png");
    private static final float[] HAT_INFLATION = {0.25F, 0.05F, -1.0F, 0.01F};
    private static final int SKULL_CAP_SKIN_LAYER = 3;
    static final float HEAD_SCALE = 1.005F;
    private static final float KEYGEN_SHELL = 1.004F;
    private static SkullModel[] heads;
    private static ModelPart helmet;
    private static ModelPart helmetHat;
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
        float rise = stand.getBlockState().getBlock() instanceof TallHatStandBlock ? TallHatStandBlock.RISE : 0.0F;
        HatStandBlock block = (HatStandBlock) stand.getBlockState().getBlock();
        Vec3 pivot = stand.pivot();
        pose.pushPose();
        pose.translate(pivot.x, pivot.y, pivot.z);
        pose.mulPose(new Quaternionf(stand.orientation(partialTick)
                .mul(new Quaterniond(block.orientation(stand.getBlockState())).invert())));
        pose.translate(-pivot.x, -pivot.y, -pivot.z);
        HatStandBlockEntity.NameEgg egg = stand.nameEgg();
        if (egg == HatStandBlockEntity.NameEgg.UPSIDE_DOWN) flip(pose, block, stand.getBlockState());
        boolean skullCap = stand.getActivator().is(ModItems.SKULL_CAP.get());
        int skinLayer = HatRendering.skinLayer(stand.getActivator(), stand.getSkinLayer());
        if (skullCap && skinLayer == HatStandBlockEntity.LAYER_FLAT) skinLayer = SKULL_CAP_SKIN_LAYER;
        float headScale = skullCap ? 1.0F : HEAD_SCALE;
        RenderType headType = DummyHeadHaunting.haunts(stand) || egg == HatStandBlockEntity.NameEgg.HEROBRINE
                ? RenderType.entityTranslucent(HEROBRINE) : headType(stand.getSkinProfile());
        boolean keygen = egg == HatStandBlockEntity.NameEgg.RAINBOW && !DummyHeadHaunting.haunts(stand);
        renderHead(headType, keygen, skinLayer, yaw, pose, buffers, light, rise, headScale);
        if (!stand.getActivator().isEmpty() && stand.getHeadwearLayer() != HatStandBlockEntity.LAYER_REMOVED)
            renderHeadwear(BuildHostBlockEntity.plain(stand.getActivator()), egg, stand.getHeadwearLayer() == 1,
                    yaw, pose, buffers, light, rise);
        pose.popPose();
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
        ClientSubLevel mirror = token == null || stand.getActivator().isEmpty() ? null
                : Portrait.drawing() ? BuildMirrors.get(token) : BuildMirrors.standing(token);
        if (mirror == null || !(stand.getBlockState().getBlock() instanceof HatStandBlock block)) return;
        Vec3 anchor = stand.headCentre(partialTick);
        Vector3d head = BuildSites.head(mirror, BuildMirrors.easedOffset(token, stand.getOffset(), partialTick), stand.getActivator());
        float scale = (float) BuildSites.scale(stand.getActivator());
        pose.pushPose();
        pose.translate(anchor.x, anchor.y, anchor.z);
        pose.mulPose(new Quaternionf(stand.orientation(partialTick)));
        if (stand.nameEgg() == HatStandBlockEntity.NameEgg.UPSIDE_DOWN) pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.scale(scale, scale, scale);
        HatRendering.pullToCamera(pose);
        HatRendering.drawHat(token, mirror, head, stand.nameEgg(), stand.getActivator().hasFoil(), pose, buffers, light, overlay, partialTick);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(HatStandBlockEntity stand) {
        return new AABB(stand.getBlockPos()).inflate(8.0);
    }

    private static void renderHeadwear(ItemStack stack, HatStandBlockEntity.NameEgg egg, boolean flat, float yaw, PoseStack pose,
                                       MultiBufferSource buffers, int light, float rise) {
        pose.pushPose();
        pose.translate(0.5, 5.0 / 16.0 + rise, 0.5);
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        drawHeadwear(stack, egg, flat, pose, buffers, light);
        pose.popPose();
    }

    public static void renderHeadwearAtHeadCentre(ItemStack stack, boolean flat, PoseStack pose,
                                                  MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0.0F, -0.25F, 0.0F);
        pose.scale(-1.0F, -1.0F, 1.0F);
        drawHeadwear(stack, HatStandBlockEntity.NameEgg.of(stack), flat, pose, buffers, light);
        pose.popPose();
    }

    private static void drawHeadwear(ItemStack stack, HatStandBlockEntity.NameEgg egg, boolean flat, PoseStack pose,
                                     MultiBufferSource buffers, int light) {
        Minecraft minecraft = Minecraft.getInstance();
        pose.pushPose();
        HatRendering.pullSkullCap(pose, stack);
        if (flat) {
            float scale = HatRendering.headwearFlat(stack);
            pose.translate(0.0F, -0.25F, 0.0F);
            pose.scale(scale, scale, scale);
            pose.translate(0.0F, 0.25F, 0.0F);
        }
        if (stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.HEAD) {
            if (helmet == null) {
                ModelPart armorModel = minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR);
                helmet = armorModel.getChild("head");
                helmetHat = armorModel.getChild("hat");
            }
            int dye = stack.is(ItemTags.DYEABLE) ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, -6265536)) : -1;
            for (ArmorMaterial.Layer layer : armor.getMaterial().value().layers()) {
                ResourceLocation texture = ClientHooks.getArmorTexture(minecraft.player, stack, layer, false, EquipmentSlot.HEAD);
                VertexConsumer consumer = buffers.getBuffer(RenderType.armorCutoutNoCull(texture));
                helmet.render(pose, consumer, light, OverlayTexture.NO_OVERLAY, layer.dyeable() ? dye : -1);
                helmetHat.render(pose, consumer, light, OverlayTexture.NO_OVERLAY, layer.dyeable() ? dye : -1);
            }
            RenderType glintType = egg == HatStandBlockEntity.NameEgg.RAINBOW ? PocketRenderTypes.KEYGEN_ARMOR_GLINT
                    : stack.hasFoil() ? RenderType.armorEntityGlint() : null;
            if (glintType != null) {
                VertexConsumer glint = buffers.getBuffer(glintType);
                helmet.render(pose, glint, light, OverlayTexture.NO_OVERLAY);
                helmetHat.render(pose, glint, light, OverlayTexture.NO_OVERLAY);
            }
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
        renderHead(profile, layer, yaw, pose, buffers, light, 0.0F);
    }

    public static void renderHead(@Nullable ResolvableProfile profile, int layer, float yaw, PoseStack pose,
                                  MultiBufferSource buffers, int light, float rise) {
        renderHead(headType(profile), HatStandBlockEntity.NameEgg.of(profile) == HatStandBlockEntity.NameEgg.RAINBOW,
                layer, yaw, pose, buffers, light, rise, HEAD_SCALE);
    }

    private static void flip(PoseStack pose, HatStandBlock block, BlockState state) {
        Vec3 centre = block.anchor(state);
        Quaterniond resting = block.orientation(state);
        pose.translate(centre.x, centre.y, centre.z);
        pose.mulPose(new Quaternionf(new Quaterniond(resting).rotateZ(Math.PI).mul(new Quaterniond(resting).invert())));
        pose.translate(-centre.x, -centre.y, -centre.z);
    }

    private static RenderType headType(@Nullable ResolvableProfile profile) {
        HatStandBlockEntity.NameEgg egg = HatStandBlockEntity.NameEgg.of(profile);
        if (egg == HatStandBlockEntity.NameEgg.HEROBRINE) return RenderType.entityTranslucent(HEROBRINE);
        return profile == null || egg == HatStandBlockEntity.NameEgg.UPSIDE_DOWN || egg == HatStandBlockEntity.NameEgg.RAINBOW
                ? RenderType.entityTranslucent(HEAD)
                : SkullBlockRenderer.getRenderType(SkullBlock.Types.PLAYER, profile);
    }

    private static void renderHead(RenderType type, boolean keygen, int layer, float yaw, PoseStack pose,
                                   MultiBufferSource buffers, int light, float rise, float scale) {
        pose.pushPose();
        pose.translate(0.0, 5.0 / 16.0 + rise, 0.0);
        pose.translate(0.5, 0.0, 0.5);
        pose.scale(scale, scale, scale);
        pose.translate(-0.5, 0.0, -0.5);
        SkullBlockRenderer.renderSkull(null, yaw, 0.0F, pose, buffers, light, head(layer), type);
        if (keygen) {
            if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(type);
            pose.translate(0.5, 0.25, 0.5);
            pose.scale(KEYGEN_SHELL, KEYGEN_SHELL, KEYGEN_SHELL);
            pose.translate(-0.5, -0.25, -0.5);
            SkullBlockRenderer.renderSkull(null, yaw, 0.0F, pose, buffers, light, head(HatStandBlockEntity.LAYER_REMOVED),
                    PocketRenderTypes.KEYGEN_HEAD_GLINT);
        }
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
