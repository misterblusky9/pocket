package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.CompressionGunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.zapper.ShootableGadgetRenderHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class CompressionGunRenderHandler extends ShootableGadgetRenderHandler {
    public static final CompressionGunRenderHandler INSTANCE = new CompressionGunRenderHandler();

    private CompressionGunRenderHandler() {}

    @Override
    protected void playSound(final InteractionHand hand, final Vec3 position) {
    }

    @Override
    protected boolean appliesTo(final ItemStack stack) {
        return stack.getItem() instanceof CompressionGunItem;
    }

    @Override
    protected void transformTool(final PoseStack ms, final float flip, final float equipProgress,
                                 final float recoil, final float pt) {
        ms.translate(flip * -0.1F, 0.0F, 0.14F);
        ms.scale(0.75F, 0.75F, 0.75F);
        ms.mulPose(com.mojang.math.Axis.XP.rotationDegrees(recoil * 80.0F));
    }

    @Override
    protected void transformHand(final PoseStack ms, final float flip, final float equipProgress,
                                 final float recoil, final float pt) {
        ms.translate(flip * -0.09F, -0.275F, -0.25F);
        ms.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(flip * -10.0F));
    }
}
