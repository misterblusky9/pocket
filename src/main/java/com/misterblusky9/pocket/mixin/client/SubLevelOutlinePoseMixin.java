package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.SubLevelOutlineScale;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.util.SublevelRenderOffsetHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SublevelRenderOffsetHelper.class, remap = false)
public abstract class SubLevelOutlinePoseMixin {
    @Inject(method = "posePlotToProjected", at = @At("HEAD"), remap = false, require = 1)
    private static void pocket$noteSubLevelOutlineScale(
            final SubLevel subLevel,
            final PoseStack poseStack,
            final CallbackInfo ci
    ) {
        if (subLevel instanceof final ClientSubLevel clientSubLevel) {
            SubLevelOutlineScale.notePush(clientSubLevel.renderPose().scale().x());
        }
    }
}
