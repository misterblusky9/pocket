package com.misterblusky9.pocket.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.client.HatRendering;
import com.misterblusky9.pocket.compat.WornHats;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.armor.CardboardArmorHandlerClient;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CardboardArmorHandlerClient.class, remap = false)
public abstract class CardboardArmorHatDisguiseMixin {
    @WrapOperation(
            method = "playerRendersAsBoxWhenSneaking",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/logistics/box/PackageRenderer;renderBox("
                            + "Lnet/minecraft/world/entity/Entity;"
                            + "FLcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
                            + "ILdev/engine_room/flywheel/lib/model/baked/PartialModel;)V"
            ),
            remap = false
    )
    private static void pocket$renderHatInsteadOfPackage(
            Entity entity, float yaw, PoseStack pose, MultiBufferSource buffers, int light,
            PartialModel model, Operation<Void> original
    ) {
        if (entity instanceof Player player) {
            ItemStack hat = HatRendering.headwear(player);
            if (WornHats.isCustomHat(hat)) {
                ClientSubLevel mirror = HatRendering.worn(player);
                BoundingBox3ic bounds = mirror == null ? null : mirror.getPlot().getBoundingBox();
                if (bounds != null) {
                    // Create has already applied the player's own scale and the crouch offset.
                    // Draw the hat's blocks as a ground-level prop, not as headwear.
                    double centreX = (bounds.minX() + bounds.maxX() + 1.0D) * 0.5D;
                    double centreZ = (bounds.minZ() + bounds.maxZ() + 1.0D) * 0.5D;
                    Vector3d base = new Vector3d(centreX, bounds.minY(), centreZ);

                    pose.pushPose();
                    try {
                        pose.mulPose(Axis.YP.rotationDegrees(-yaw - 180.0F));
                        float scale = (float) BuildSites.scale(hat);
                        pose.scale(scale, scale, scale);
                        HatRendering.drawHat(
                                BuildHostBlockEntity.token(hat), mirror, base,
                                HatStandBlockEntity.NameEgg.of(hat), hat.hasFoil(),
                                pose, buffers, light, OverlayTexture.NO_OVERLAY,
                                Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)
                        );
                    } finally {
                        pose.popPose();
                    }
                    return;
                }
            }
        }
        // Preserve the original Create disguise until a valid hat mirror is available.
        original.call(entity, yaw, pose, buffers, light, model);
    }
}
