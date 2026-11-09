package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.block.HatStandBlockEntity.NameEgg;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class HatEggs {
    private static final float HEAD_HEIGHT = 8.0F;

    private HatEggs() {}

    public static NameEgg worn(LivingEntity entity) {
        ItemStack headwear = HatRendering.headwear(entity);
        return HatStandBlock.isHeadwear(headwear) ? NameEgg.of(headwear) : NameEgg.NONE;
    }

    public static void flipInPlace(ModelPart head) {
        Vector3f lift = new Quaternionf().rotationZYX(head.zRot, head.yRot, head.xRot)
                .transform(new Vector3f(0.0F, -HEAD_HEIGHT, 0.0F));
        head.x += lift.x;
        head.y += lift.y;
        head.z += lift.z;
        head.zRot += Mth.PI;
        head.yRot = -head.yRot;
        head.xRot = -head.xRot;
    }
}
