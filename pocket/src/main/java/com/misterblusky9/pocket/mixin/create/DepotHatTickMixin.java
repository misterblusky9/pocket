package com.misterblusky9.pocket.mixin.create;

import com.misterblusky9.pocket.build.BuildHostBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.build.CarriedBuilds;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = DepotBehaviour.class, remap = false)
public abstract class DepotHatTickMixin extends BlockEntityBehaviour {
    private DepotHatTickMixin(SmartBlockEntity be) {
        super(be);
    }

    @Inject(method = "tick()V", at = @At("HEAD"), remap = false)
    private void pocket$keepHat(CallbackInfo ci) {
        if (!(getWorld() instanceof ServerLevel level)) return;
        ItemStack stack = ((DepotBehaviour) (Object) this).getHeldItemStack();
        UUID token = stack.getItem() instanceof PocketCaseItem
                ? PocketCaseItem.token(stack) : BuildHostBlockEntity.token(stack);
        if (token == null) return;
        BlockPos pos = getPos();
        CarriedBuilds.keep(level, token, pos, !(stack.getItem() instanceof PocketCaseItem), hat -> BuildSites.hold(hat,
                new Vector3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), new Quaterniond(), stack));
    }
}
