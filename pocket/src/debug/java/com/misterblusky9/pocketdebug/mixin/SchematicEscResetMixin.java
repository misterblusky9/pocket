package com.misterblusky9.pocketdebug.mixin;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.schematics.SchematicInstances;
import com.simibubi.create.content.schematics.client.SchematicHandler;
import com.simibubi.create.content.schematics.packet.SchematicSyncPacket;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// esc on a deployed hologram -> fresh, undeployed schematic (as SchematicItem.create)
@Mixin(KeyboardHandler.class)
public abstract class SchematicEscResetMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void pocketdebug$escResetSchematic(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (key != GLFW.GLFW_KEY_ESCAPE || action != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().getWindow() || mc.screen != null || mc.player == null) return;

        SchematicHandler handler = CreateClient.SCHEMATIC_HANDLER;
        if (!handler.isActive() || !handler.isDeployed()) return;
        ItemStack stack = handler.getActiveSchematicItem();
        if (stack == null) return;

        stack.set(AllDataComponents.SCHEMATIC_DEPLOYED, false);
        stack.set(AllDataComponents.SCHEMATIC_ANCHOR, BlockPos.ZERO);
        stack.set(AllDataComponents.SCHEMATIC_ROTATION, Rotation.NONE);
        stack.set(AllDataComponents.SCHEMATIC_MIRROR, Mirror.NONE);
        SchematicInstances.clearHash(stack);
        CatnipServices.NETWORK.sendToServer(new SchematicSyncPacket(
                mc.player.getInventory().selected, false, BlockPos.ZERO, Rotation.NONE, Mirror.NONE));
        ((SchematicHandlerAccessor) handler).pocketdebug$setActive(false);
        ci.cancel();
    }
}
