package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.compression.EntityCompressionTargeting;
import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.simibubi.create.AllSpecialTextures;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ShrinkRayHoverOutline {
    private static final String SLOT = "pocketShrinkRayHover";
    public static final int ENTITY_COLOUR = 0xBFBFBF;
    private static int hoveredEntity = -1;

    public static boolean outlines(final Entity entity) {
        return entity != null && entity.getId() == hoveredEntity;
    }

    public static void tick() {
        hoveredEntity = -1;
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        final ItemStack ray = player.getMainHandItem().getItem() instanceof CreativeShrinkRayItem
                ? player.getMainHandItem()
                : player.getOffhandItem();
        if (!(ray.getItem() instanceof CreativeShrinkRayItem)) return;
        final CreativeShrinkRayItem.TargetingMode targeting = CreativeShrinkRayItem.targetingMode(ray);

        if (targeting.allowsEntities()) {
            final EntityCompressionTargeting.Target entity =
                    EntityCompressionTargeting.find(player, CreativeShrinkRayItem.RANGE, true);
            if (entity != null) {
                hoveredEntity = entity.entity().getId();
                return;
            }
        }

        if (!targeting.allowsContraptions()) return;
        final Vec3 start = player.getEyePosition();
        final Vec3 end = start.add(player.getLookAngle().scale(CreativeShrinkRayItem.RANGE));
        final BlockHitResult hit = player.level().clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit == null || hit.getType() == HitResult.Type.MISS) return;
        final SubLevel subLevel = Sable.HELPER.getContaining(player.level(), hit.getBlockPos());
        if (subLevel == null || subLevel.isRemoved()) return;

        Outliner.getInstance().showCluster(SLOT, List.of(hit.getBlockPos()))
                .colored(0xbfbfbf)
                .disableLineNormals()
                .lineWidth(1 / 32f)
                .withFaceTexture(AllSpecialTextures.CHECKERED);
    }

    private ShrinkRayHoverOutline() {}
}
