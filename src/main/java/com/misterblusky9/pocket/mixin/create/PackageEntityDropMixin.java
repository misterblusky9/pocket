package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.simibubi.create.content.logistics.box.PackageEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

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
        if (box == null || box.isEmpty() || !(box.getItem() instanceof PocketCaseItem)) return;

        ci.cancel();
        final ItemStack recovery = box.copy();
        recovery.setCount(1);

        final UUID recoveryToken = PocketCaseItem.token(recovery);
        final boolean restored = PocketCaseItem.deployFromBrokenPackage(
                level, box, new Vec3(self.getX(), self.getY(), self.getZ()));

        box.remove(DataComponents.CUSTOM_DATA);
        if (!restored) {
            Containers.dropItemStack(level, self.getX(), self.getY(), self.getZ(), recovery);
            PocketTrace.logger().warn(
                    "[PocketTransfer] broken package restore failed token={} recoveryDropped=true backendValid=false",
                    recoveryToken);
        }
    }
}
