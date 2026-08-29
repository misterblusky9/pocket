package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.client.MoonPunchClient;
import com.misterblusky9.pocket.client.TweezerDrag;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleState;
import com.simibubi.create.AllItems;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerTweezerMixin {
    private static final double QUILL_SCALE_TOLERANCE = 0.0015D;

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void pocket$tweezerButtons(
            final long window, final int button, final int action, final int modifiers, final CallbackInfo ci
    ) {
        if (action != GLFW.GLFW_PRESS) return;

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && pocket$selectTinySubLevelWithQuill()) {
            ci.cancel();
            return;
        }

        if (TweezerDrag.acceptingInput()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                TweezerDrag.toggleDrag();
                ci.cancel();
                return;
            }

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                TweezerDrag.punch();
                ci.cancel();
            }
            return;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && MoonPunchClient.tryPunch()) {
            ci.cancel();
        }
    }

    private static boolean pocket$selectTinySubLevelWithQuill() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) return false;
        if (minecraft.screen != null || !minecraft.player.isShiftKeyDown()) return false;
        if (!AllItems.SCHEMATIC_AND_QUILL.isIn(minecraft.player.getMainHandItem())) return false;

        if (!(minecraft.hitResult instanceof final BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        SubLevel subLevel = Sable.HELPER.getContaining(minecraft.level, hit.getBlockPos());
        if (subLevel == null) {
            subLevel = Sable.HELPER.getContaining(minecraft.level, hit.getLocation());
        }
        if (subLevel == null || subLevel.getPlot() == null) return false;

        final double scale = ScaleState.getScale(subLevel);
        if (Math.abs(scale - PocketSized.MIN_SCALE) > QUILL_SCALE_TOLERANCE
                || ScaleState.getStage(subLevel) != CompressionStage.SIXTEENTH) {
            return false;
        }

        final var bounds = subLevel.getPlot().getBoundingBox();
        if (bounds == null) return false;

        final BlockPos first = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        final BlockPos second = new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ());

        CreateClient.SCHEMATIC_AND_QUILL_HANDLER.firstPos = first;
        CreateClient.SCHEMATIC_AND_QUILL_HANDLER.secondPos = second;

        CreateLang.translate(
                        "schematicAndQuill.dimensions",
                        bounds.maxX() - bounds.minX() + 1,
                        bounds.maxY() - bounds.minY() + 1,
                        bounds.maxZ() - bounds.minZ() + 1
                )
                .sendStatus(minecraft.player);

        return true;
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void pocket$turnHeldCraftInstead(final double sensitivity, final CallbackInfo ci) {
        if (this.accumulatedDX == 0.0D && this.accumulatedDY == 0.0D) return;
        if (!TweezerDrag.rotate(this.accumulatedDX, this.accumulatedDY)) return;

        this.accumulatedDX = 0.0D;
        this.accumulatedDY = 0.0D;
        ci.cancel();
    }
}
