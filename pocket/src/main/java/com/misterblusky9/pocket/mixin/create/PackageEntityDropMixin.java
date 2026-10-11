package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.simibubi.create.content.logistics.box.PackageEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PackageEntity.class, remap = false)
public abstract class PackageEntityDropMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true, remap = false)
    private void pocket$releaseCraftFromBrokenPackage(
            final ServerLevel level,
            final DamageSource source,
            final CallbackInfo ci
    ) {
        final PackageEntity self = (PackageEntity) (Object) this;
        final ItemStack box = self.box;
        if (box == null
                || box.isEmpty()
                || !(box.getItem() instanceof PocketCaseItem)
                || !PocketCaseItem.isFilled(box)) {
            return;
        }

        ci.cancel();
        final UUID recoveryToken = PocketCaseItem.token(box);
        if (PocketCaseItem.breakOpen(level, self)) return;
        if (!PocketCaseItem.dropRecovery(level, self)) return;

        PocketTrace.logger().warn(
                "[PocketTransfer] broken package restore failed token={} recoveryDropped=true backendValid=false",
                recoveryToken);
    }
}
