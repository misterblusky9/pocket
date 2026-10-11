package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pym.api.client.EntityRenderScale;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageVisual;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PackageVisual.class, remap = false)
public abstract class PackageVisualScaleMixin extends AbstractEntityVisual<PackageEntity> {
    @Shadow
    @Final
    public TransformedInstance instance;

    private PackageVisualScaleMixin() {
        super(null, null, 0.0F);
    }

    @Inject(method = "animate", at = @At("TAIL"), remap = false)
    private void pocket$scale(final float partialTick, final CallbackInfo ci) {
        EntityRenderScale.apply(entity, partialTick, Vec3.atLowerCornerOf(renderOrigin()), instance.pose);
    }
}
