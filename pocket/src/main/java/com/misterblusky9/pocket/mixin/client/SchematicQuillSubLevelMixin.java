package com.misterblusky9.pocket.mixin.client;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.schematics.client.SchematicAndQuillHandler;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SchematicAndQuillHandler.class, remap = false)
public abstract class SchematicQuillSubLevelMixin {
    @Shadow public BlockPos firstPos;
    @Shadow public BlockPos secondPos;

    @Inject(method = "onMouseInput", at = @At("HEAD"), cancellable = true)
    private void pocket$selectSubLevel(
            int button,
            boolean pressed,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (button != 1 || !pressed) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null
                || !player.isShiftKeyDown()
                || !AllItems.SCHEMATIC_AND_QUILL.isIn(player.getMainHandItem())) return;

        SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container == null) return;

        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getLookAngle().scale(75));

        SubLevel closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (SubLevel subLevel : container.getAllSubLevels()) {
            BoundingBox3dc bounds = subLevel.boundingBox();
            if (bounds == null) continue;

            AABB box = new AABB(
                    bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ()
            );

            var intersection = box.clip(from, to);
            if (intersection.isEmpty() && !box.contains(from)) continue;

            double distance = box.contains(from)
                    ? 0
                    : intersection.get().distanceToSqr(from);

            if (distance >= closestDistance) continue;

            closest = subLevel;
            closestDistance = distance;
        }

        if (closest == null) return;

        BoundingBox3ic plot = closest.getPlot().getBoundingBox();
        firstPos = new BlockPos(plot.minX(), plot.minY(), plot.minZ());
        secondPos = new BlockPos(plot.maxX(), plot.maxY(), plot.maxZ());

        cir.setReturnValue(true);
    }
}
