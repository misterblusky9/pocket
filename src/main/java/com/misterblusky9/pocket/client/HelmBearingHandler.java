package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.HelmBearingBlockEntity;
import com.misterblusky9.pocket.create.HelmBearingContraption;
import com.misterblusky9.pocket.mixin.create.ControlledContraptionEntityControllerPosAccessor;
import com.misterblusky9.pocket.network.HelmBearingUpdatePayload;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.index.SimClickInteractions;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction;
import dev.simulated_team.simulated.util.hold_interaction.HoldInteractionManager;
import net.createmod.catnip.data.Couple;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Optional;

public final class HelmBearingHandler extends BlockHoldInteraction {
    public static final HelmBearingHandler INSTANCE = new HelmBearingHandler();

    private HelmBearingBlockEntity blockEntity;
    private boolean updated;
    private float rawAngle;
    private float effectiveAngle;
    private boolean wasShiftKeyDown;
    private int angleSgn = 1;
    private float angleLimit;

    private HelmBearingHandler() {
    }

    public static void register() {
        SimClickInteractions.register(INSTANCE);
    }


    @Override
    public Result onUse(final int modifiers, final int action, final KeyMapping rightKey) {
        final Result result = super.onUse(modifiers, action, rightKey);
        if (result.cancelled() || action == GLFW.GLFW_RELEASE) {
            return result;
        }
        if (isActive()) {
            return new Result(true);
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || player.isSpectator()) {
            return result;
        }

        final ControlledContraptionEntity controlled = findTargetHelm(player);
        if (controlled == null) {
            return result;
        }

        final BlockPos controllerPos =
                ((ControlledContraptionEntityControllerPosAccessor) controlled).pocket$getControllerPos();
        if (controllerPos == null
                || !(minecraft.level.getBlockEntity(controllerPos) instanceof HelmBearingBlockEntity)) {
            return result;
        }

        startHold(minecraft.level, player, controllerPos);
        return isActive() ? new Result(true) : result;
    }

    private static ControlledContraptionEntity findTargetHelm(final LocalPlayer player) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }

        final Couple<Vec3> rayInputs = ContraptionHandlerClient.getRayInputs(player);
        final Vec3 origin = rayInputs.getFirst();
        final Vec3 target = rayInputs.getSecond();
        final AABB rayBounds = new AABB(origin, target).inflate(1.0D);
        final Collection<WeakReference<AbstractContraptionEntity>> contraptions =
                ContraptionHandler.loadedContraptions.get(minecraft.level).values();

        double bestDistance = Double.MAX_VALUE;
        ControlledContraptionEntity best = null;

        for (final WeakReference<AbstractContraptionEntity> ref : contraptions) {
            final AbstractContraptionEntity entity = ref.get();
            if (!(entity instanceof final ControlledContraptionEntity controlled)
                    || !(controlled.getContraption() instanceof final HelmBearingContraption helm)
                    || !entity.getBoundingBox().intersects(rayBounds)) {
                continue;
            }

            final AABB bounds = helm.getInteractionBounds();
            if (bounds == null) {
                continue;
            }

            final Vec3 localOrigin = entity.toLocalVector(origin, 1.0F);
            final Vec3 localTarget = entity.toLocalVector(target, 1.0F);
            final Vec3 localHit;
            if (bounds.contains(localOrigin)) {
                localHit = localOrigin;
            } else {
                final Optional<Vec3> clipped = bounds.clip(localOrigin, localTarget);
                if (clipped.isEmpty()) {
                    continue;
                }
                localHit = clipped.get();
            }

            final double distance = entity.toGlobalVector(localHit, 1.0F).distanceTo(origin);
            if (distance >= bestDistance) {
                continue;
            }

            bestDistance = distance;
            best = controlled;
        }

        return best;
    }

    @Override
    public void startHold(final Level level, final Player player, final BlockPos blockPos) {
        if (!(level.getBlockEntity(blockPos) instanceof final HelmBearingBlockEntity be)) {
            return;
        }
        super.startHold(level, player, blockPos);
        blockEntity = be;
        rawAngle = blockEntity.getInteractionAngle(Minecraft.getInstance().getTimer().getGameTimeDeltaTicks());
        angleSgn = (int) blockEntity.directionConvert(1);
        updated = true;
        wasShiftKeyDown = false;
        angleLimit = blockEntity.angleInput.getValue();
    }

    @Override
    public void renderOverlay(final GuiGraphics guiGraphics, final int width, final int height, final boolean hideGui) {
        if (hideGui || blockEntity == null) {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !GogglesItem.isWearingGoggles(mc.player)) {
            return;
        }

        final ResourceLocation tex = Simulated.path("textures/gui/steering_wheel.png");
        final float magicOffset = 0.56F;
        final int x = ((width - 223) / 2) + SimConfigService.INSTANCE.client().blockConfig.steeringWheelXOffset.get();
        final int y = 10 + SimConfigService.INSTANCE.client().blockConfig.steeringWheelYOffset.get();

        guiGraphics.blit(tex, x, y, 0, 0, 223, 31, 256, 256);

        final float offset = wrapDegrees(angleLimit) * magicOffset;
        final int activeWidth = (int) Math.abs(offset);
        final int centerX = x + 111 - 4;
        final float realDegrees = angleSgn * -effectiveAngle;

        if (Math.abs(angleLimit) <= 180) {
            final int leftDeadZoneWidth = (centerX - x) - activeWidth + 4;
            if (leftDeadZoneWidth > 0) {
                guiGraphics.blit(tex, x, y, 0, 32, leftDeadZoneWidth, 31, 256, 256);
            }
            final int rightSideStart = centerX + activeWidth + 8;
            final int rightDeadZoneWidth = x + 223 - rightSideStart;
            if (rightDeadZoneWidth > 0) {
                guiGraphics.blit(tex, rightSideStart, y, rightSideStart - x, 32, rightDeadZoneWidth, 31, 256, 256);
            }
        } else {
            if (realDegrees <= -180) {
                final int rightSideStart = centerX - activeWidth + 4;
                final int rightDeadZoneWidth = x + 223 - rightSideStart;
                if (rightDeadZoneWidth > 0) {
                    guiGraphics.blit(tex, rightSideStart, y, rightSideStart - x, 32, rightDeadZoneWidth, 31, 256, 256);
                }
            }
            if (realDegrees >= 180) {
                final int leftDeadZoneWidth = centerX - x + activeWidth + 4;
                if (leftDeadZoneWidth > 0) {
                    guiGraphics.blit(tex, x, y, 0, 32, leftDeadZoneWidth, 31, 256, 256);
                }
            }
        }

        if (Math.abs(angleLimit) > 180) {
            if (-realDegrees >= 180) {
                guiGraphics.blit(tex, (int) (centerX + offset) + 2, y + 10, 239, 0, 6, 20, 256, 256);
            }
            if (-realDegrees <= -180) {
                guiGraphics.blit(tex, (int) (centerX - offset) + 2, y + 10, 239, 0, 6, 20, 256, 256);
            }
        } else {
            guiGraphics.blit(tex, (int) (centerX + offset) + 2, y + 10, 239, 0, 6, 20, 256, 256);
            guiGraphics.blit(tex, (int) (centerX - offset) + 2, y + 10, 239, 0, 6, 20, 256, 256);
        }

        final float degrees = Math.abs(angleLimit) <= 180
                ? Mth.clamp(realDegrees, -180.0F, 180.0F)
                : wrapDegrees(realDegrees);
        final int markerX = (int) (centerX - degrees * magicOffset) + 1;
        guiGraphics.blit(tex, markerX, y + 11, 224, 0, 9, 18, 256, 256);

        final String text = (int) -realDegrees + "°";
        final int textWidth = mc.font.width(text);
        final int centeredX = markerX + 6 - textWidth / 2;
        for (int xoff = -1; xoff < 2; xoff++) {
            for (int yoff = -1; yoff < 2; yoff++) {
                if (xoff == 0 && yoff == 0) {
                    continue;
                }
                guiGraphics.drawString(mc.font, text, centeredX + xoff, y + yoff, 0x2b2117, false);
            }
        }
        guiGraphics.drawString(mc.font, text, centeredX, y, 0x886539, false);
    }

    public static float wrapDegrees(final float value) {
        float result = value % 360.0F;
        if (result >= 180.0F) {
            result -= 360.0F;
        } else if (result <= -180.0F) {
            result += 360.0F;
        }
        return result;
    }

    @Override
    public void stop() {
        if (blockEntity != null && !blockEntity.isRemoved() && getInteractionPos() != null) {
            blockEntity.held = false;
            PacketDistributor.sendToServer(new HelmBearingUpdatePayload(true, effectiveAngle, getInteractionPos()));
        }
        blockEntity = null;
        super.stop();
    }

    @Override
    public boolean activeOnMouseMove(final double yaw, final double pitch) {
        if (blockEntity == null) {
            return true;
        }
        if (yaw != 0) {
            final float oldAngle = rawAngle;
            rawAngle += (float) (yaw / 10.0D * angleSgn);
            rawAngle = Mth.clamp(rawAngle, -blockEntity.angleInput.getValue(), blockEntity.angleInput.getValue());
            updated |= oldAngle != rawAngle;
        }
        return true;
    }

    @Override
    public boolean activeTick(final Level level, final LocalPlayer player) {
        if (blockEntity == null || blockEntity.isRemoved() || getInteractionPos() == null) {
            return true;
        }

        effectiveAngle = rawAngle;
        if (HoldInteractionManager.unblockedShift()) {
            effectiveAngle = Mth.clamp(
                    Math.round(effectiveAngle / 45.0F) * 45.0F,
                    -blockEntity.angleInput.getValue(),
                    blockEntity.angleInput.getValue()
            );
            if (!wasShiftKeyDown) {
                updated = true;
            }
            wasShiftKeyDown = true;
        } else {
            if (wasShiftKeyDown) {
                updated = true;
            }
            wasShiftKeyDown = false;
        }

        setTargetAngle(effectiveAngle);
        return false;
    }

    @Override
    public boolean isBlockActive(final BlockPos pos) {
        return super.isBlockActive(pos) && !Float.isNaN(rawAngle);
    }

    public void setTargetAngle(final float targetAngle) {
        if (!updated || blockEntity == null || getInteractionPos() == null) {
            return;
        }

        PacketDistributor.sendToServer(new HelmBearingUpdatePayload(false, targetAngle, getInteractionPos()));
        updated = false;
        blockEntity.setTargetAngleToUpdate(targetAngle);
        blockEntity.held = true;
    }

    @Override
    public int getCrouchBlockingTicks() {
        return 6;
    }
}
