package com.misterblusky9.pocket.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

public final class MagnifyingGlassArmPose {
    public static final EnumProxy<HumanoidModel.ArmPose> POINTING = new EnumProxy<>(
            HumanoidModel.ArmPose.class, false, (IArmPoseTransformer) (model, entity, arm) -> {
                boolean right = arm == HumanoidArm.RIGHT;
                ModelPart part = right ? model.rightArm : model.leftArm;
                // Mirror the spyglass's head-relative arm pose rather than a horizontal aiming-gun pose.
                part.yRot = model.head.yRot + (right ? -0.2617994F : 0.2617994F);
                part.xRot = net.minecraft.util.Mth.clamp(model.head.xRot - 1.9198622F
                        - (entity.isCrouching() ? 0.2617994F : 0.0F), -2.4F, 3.3F);
            });

    private MagnifyingGlassArmPose() {}
}
