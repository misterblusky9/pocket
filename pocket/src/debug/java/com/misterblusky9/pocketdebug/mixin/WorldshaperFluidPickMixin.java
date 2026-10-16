package com.misterblusky9.pocketdebug.mixin;

import com.simibubi.create.content.equipment.zapper.ZapperInteractionHandler;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

// pick only - placement raycast in ZapperItem.use stays Fluid.NONE
@Mixin(value = ZapperInteractionHandler.class, remap = false)
public abstract class WorldshaperFluidPickMixin {
    @ModifyArg(
            method = "trySelect",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/ClipContext;<init>(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/entity/Entity;)V"
            ),
            index = 3,
            remap = false
    )
    private static ClipContext.Fluid pocketdebug$pickFluids(ClipContext.Fluid fluid) {
        return ClipContext.Fluid.SOURCE_ONLY;
    }

    @Redirect(
            method = "trySelect",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/utility/BlockHelper;getRequiredItem(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;"
            ),
            remap = false
    )
    private static ItemStack pocketdebug$fluidRequiredItem(BlockState state) {
        if (state.getBlock() instanceof LiquidBlock) return new ItemStack(Items.BUCKET);
        return BlockHelper.getRequiredItem(state);
    }
}
