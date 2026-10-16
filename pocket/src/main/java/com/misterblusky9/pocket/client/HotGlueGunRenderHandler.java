package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.HotGlueGunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.zapper.ShootableGadgetRenderHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class HotGlueGunRenderHandler extends ShootableGadgetRenderHandler {
    public static final HotGlueGunRenderHandler INSTANCE = new HotGlueGunRenderHandler();

    private HotGlueGunRenderHandler() {}

    @Override
    protected void playSound(InteractionHand hand, Vec3 position) {
    }

    @Override
    protected boolean appliesTo(ItemStack stack) {
        return stack.getItem() instanceof HotGlueGunItem;
    }

    @Override
    protected void transformTool(PoseStack ms, float flip, float equipProgress,
                                 float recoil, float pt) {
        ms.translate(flip * -0.1F, 0.1F, -0.4F);
        ms.mulPose(Axis.YP.rotationDegrees(flip * 5.0F));
    }

    @Override
    protected void transformHand(PoseStack ms, float flip, float equipProgress,
                                 float recoil, float pt) {
    }
}
