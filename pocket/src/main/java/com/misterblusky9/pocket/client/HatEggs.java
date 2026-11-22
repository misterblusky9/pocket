package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.block.HatStandBlockEntity.NameEgg;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3d;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class HatEggs {
    private static final float HEAD_HEIGHT = 8.0F;
    private static final float PINHEAD_SCALE = 0.5F;
    private static final double NECK_OVER_EYE = 1.40625D / 1.62D;
    private static final float SPIN_TICKS = 40.0F;

    private HatEggs() {}

    public static NameEgg worn(LivingEntity entity) {
        ItemStack headwear = HatRendering.headwear(entity);
        return HatStandBlock.isHeadwear(headwear) ? NameEgg.of(headwear) : NameEgg.NONE;
    }

    public static void pinhead(ModelPart head) {
        head.xScale *= PINHEAD_SCALE;
        head.yScale *= PINHEAD_SCALE;
        head.zScale *= PINHEAD_SCALE;
    }

    public static float headScale(NameEgg egg) {
        return egg == NameEgg.PINHEAD ? PINHEAD_SCALE : 1.0F;
    }

    public static double eyeDrop(LivingEntity entity, double eyeHeight) {
        if (worn(entity) != NameEgg.PINHEAD) return 0.0D;
        return (1.0D - PINHEAD_SCALE) * eyeHeight * (1.0D - NECK_OVER_EYE);
    }

    public static void spin(PoseStack pose, int mode, ClientSubLevel mirror, Vector3d head, float partialTick) {
        BoundingBox3ic bounds = mirror.getPlot().getBoundingBox();
        ClientLevel level = Minecraft.getInstance().level;
        if (bounds == null || level == null) return;
        float angle = (level.getGameTime() % (long) SPIN_TICKS + partialTick) / SPIN_TICKS * Mth.TWO_PI;
        float cx = (float) ((bounds.minX() + bounds.maxX() + 1) / 2.0 - head.x);
        float cy = (float) ((bounds.minY() + bounds.maxY() + 1) / 2.0 - head.y);
        float cz = (float) ((bounds.minZ() + bounds.maxZ() + 1) / 2.0 - head.z);
        pose.translate(cx, cy, cz);
        pose.mulPose((mode == 0 ? Axis.XP : mode == 1 ? Axis.YP : Axis.ZP).rotation(angle));
        pose.translate(-cx, -cy, -cz);
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
