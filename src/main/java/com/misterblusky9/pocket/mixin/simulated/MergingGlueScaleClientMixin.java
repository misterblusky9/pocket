package com.misterblusky9.pocket.mixin.simulated;

import com.misterblusky9.pocket.compat.simulated.MergingGlueScaleGate;
import com.simibubi.create.AllSpecialTextures;
import dev.simulated_team.simulated.index.SimTags;
import dev.simulated_team.simulated.util.SimColors;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.simulated_team.simulated.content.items.merging_glue.MergingGlueItemHandler", remap = false)
public abstract class MergingGlueScaleClientMixin {
    @Shadow public BlockPos firstPos;

    @Inject(method = "onItemUseBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void pocket$requireMatchingScale(
            final Level level,
            final Player player,
            final ItemStack itemStack,
            final InteractionHand hand,
            final CallbackInfoReturnable<Boolean> cir
    ) {
        if (this.firstPos == null || itemStack.isEmpty() || !itemStack.is(SimTags.Items.MERGING_GLUE)) return;
        final HitResult result = Minecraft.getInstance().hitResult;
        if (!(result instanceof final BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) return;
        final MergingGlueScaleGate.Refusal refusal = MergingGlueScaleGate.check(
                level, this.firstPos, hit.getBlockPos());
        if (refusal == MergingGlueScaleGate.Refusal.NONE) return;
        player.displayClientMessage(Component.literal(refusal.message()), true);
        cir.setReturnValue(true);
    }
    @Inject(method = "clientTick", at = @At("TAIL"), remap = false)
    private void pocket$showScaleRefusal(
            final Level level,
            final LocalPlayer player,
            final CallbackInfo ci
    ) {
        if (this.firstPos == null) return;
        final HitResult result = Minecraft.getInstance().hitResult;
        if (!(result instanceof final BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) return;
        if (MergingGlueScaleGate.check(level, this.firstPos, hit.getBlockPos())
                == MergingGlueScaleGate.Refusal.NONE) return;

        final Direction normal = hit.getDirection();
        final AABB hitAABB = new AABB(hit.getBlockPos())
                .contract(-normal.getStepX(), -normal.getStepY(), -normal.getStepZ())
                .inflate(-0.1D);
        Outliner.getInstance()
                .showAABB(this.firstPos + " Merging Glue Selection", hitAABB)
                .colored(SimColors.NUH_UH_RED)
                .withFaceTexture(AllSpecialTextures.GLUE)
                .lineWidth(1.0F / 16.0F);
    }


}
