package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldContact;
import com.misterblusky9.pocket.compat.simulated.WeldGeometry;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.network.CrossScaleWeldPayload;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.items.merging_glue.MergingGlueItemHandler;
import dev.simulated_team.simulated.util.SimColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = PocketSized.MOD_ID, value = Dist.CLIENT)
public final class HotGlueGunClient {
    private static BlockPos firstPos;
    private static Direction firstFacing;
    private static Vec3 firstHit;
    private static InteractionHand firstHand;
    private static int rotationTurns;
    private static PreviewShape originShape;
    private static PreviewShape previewShape;

    private record PreviewShape(
            UUID subLevel,
            BlockPos pos,
            Direction facing,
            double cell,
            List<WeldContactPatch.Edge> faces,
            List<WeldContactPatch.Edge> outline
    ) {}

    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || !event.getItemStack().is(ModItems.GLUE_GUN.get())) return;

        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || event.getEntity() != player) return;

        if (player.isShiftKeyDown() && firstPos != null) {
            discardSelection();
            return;
        }

        final BlockHitResult hit = event.getHitVec();
        if (hit == null || hit.getType() == HitResult.Type.MISS
                || event.getLevel().getBlockState(hit.getBlockPos()).isAir()) {
            return;
        }

        if ((firstPos == null || firstHand != event.getHand())
                && Sable.HELPER.getContaining(event.getLevel(), hit.getBlockPos()) == null) {
            return;
        }

        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        if (firstPos == null || firstHand != event.getHand()) {
            begin(hit, event.getHand());
            player.swing(event.getHand());
            return;
        }

        final CrossScaleWelds.Weld weld = resolve(event.getLevel(), hit);
        if (weld == null) {
            player.displayClientMessage(Component.literal(CrossScaleWelds.Refusal.CANNOT_WELD.message()), true);
            return;
        }

        final CrossScaleWelds.Refusal refusal = weld.check();
        if (!refusal.allowed()) {
            if (refusal.message() != null) player.displayClientMessage(Component.literal(refusal.message()), true);
            return;
        }

        final Vec3 bigHit = weld.startedSmall(firstPos) ? hitFraction(hit) : firstHit;
        PacketDistributor.sendToServer(new CrossScaleWeldPayload(
                weld.smallPos(),
                weld.bigPos(),
                weld.smallFacing(),
                weld.bigFacing(),
                event.getHand(),
                bigHit.x,
                bigHit.y,
                bigHit.z,
                placementMode(weld).ordinal(),
                rotationTurns));

        player.swing(event.getHand());
        clear();
    }

    @SubscribeEvent
    public static void onFrame(final RenderFrameEvent.Pre event) {
        if (firstPos == null) {
            while (PocketKeys.WELD_ROTATE.consumeClick()) {}
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            clear();
            return;
        }
        if (minecraft.isPaused()) return;
        if (firstHand == null || !player.getItemInHand(firstHand).is(ModItems.GLUE_GUN.get())) {
            discardSelection();
            return;
        }

        final SubLevel first = Sable.HELPER.getContaining(minecraft.level, firstPos);
        if ((first != null && first.isRemoved())
                || (first == null && minecraft.level.getBlockState(firstPos).isAir())) {
            discardSelection();
            return;
        }

        while (PocketKeys.WELD_ROTATE.consumeClick()) {
            rotationTurns = (rotationTurns + 1) & 3;
        }

        final Vector3d firstAnchor = WeldGeometry.faceCentre(firstPos, firstFacing);
        final PreviewShape origin = originShape(minecraft, first);
        WeldContactPatch.showFaces(
                "pocket_weld_origin",
                firstPos,
                firstFacing,
                firstAnchor,
                WeldContact.identity(firstFacing),
                origin.faces(),
                SimColors.SUCCESS_LIME);
        final float originLineWidth = CrossScaleWeldSeams.lineWidth(first);
        WeldContactPatch.show(
                "pocket_weld_origin_outline",
                firstPos,
                firstFacing,
                firstAnchor,
                WeldContact.identity(firstFacing),
                origin.outline(),
                SimColors.SUCCESS_LIME,
                originLineWidth);
        if (!(minecraft.hitResult instanceof final BlockHitResult hit)
                || hit.getType() == HitResult.Type.MISS) return;

        final CrossScaleWelds.Weld weld = resolve(minecraft.level, hit);
        if (weld == null) return;
        if (!weld.worldAnchored() && (weld.small() == weld.big()
                || java.util.Objects.equals(weld.small().getUniqueId(), weld.big().getUniqueId()))) return;

        renderPreview(minecraft, weld);
    }

    private static void begin(final BlockHitResult hit, final InteractionHand hand) {
        firstPos = hit.getBlockPos().immutable();
        firstFacing = hit.getDirection();
        firstHit = hitFraction(hit);
        firstHand = hand;
        rotationTurns = 0;
        originShape = null;
        previewShape = null;
    }

    private static CrossScaleWelds.Weld resolve(final net.minecraft.world.level.Level level, final BlockHitResult hit) {
        return CrossScaleWelds.Weld.resolve(
                level,
                firstPos,
                firstFacing,
                firstHit,
                hit.getBlockPos(),
                hit.getDirection(),
                hitFraction(hit),
                mode());
    }

    private static void renderPreview(final Minecraft minecraft, final CrossScaleWelds.Weld weld) {
        final int color = weld.check().allowed() ? SimColors.SUCCESS_LIME : SimColors.NUH_UH_RED;
        final float lineWidth = (float) (PocketSized.clampScale(weld.smallScale()) / 16.0D);
        final boolean sourceIsSmall = weld.startedSmall(firstPos);
        final Quaterniond orientation = CrossScaleWelds.weldOrientation(weld, rotationTurns);
        final double cell = sourceIsSmall ? weld.bigSpan() : 1.0D / weld.bigSpan();
        final WeldContact.Projection projection = WeldContact.projection(
                sourceIsSmall ? weld.smallFacing() : weld.bigFacing(),
                sourceIsSmall ? new Quaterniond(orientation).invert() : new Quaterniond(orientation),
                cell);

        final SubLevel source = sourceIsSmall ? weld.small() : weld.big();
        final BlockPos sourcePos = sourceIsSmall ? weld.smallPos() : weld.bigPos();
        final Direction sourceFacing = sourceIsSmall ? weld.smallFacing() : weld.bigFacing();
        final PreviewShape shape = shape(minecraft, source, sourcePos, sourceFacing, cell);

        final Vector3d sourceAnchor = sourceIsSmall ? weld.smallAnchor() : weld.bigAnchor();
        final BlockPos targetPos = sourceIsSmall ? weld.bigPos() : weld.smallPos();
        final Direction targetFacing = sourceIsSmall ? weld.bigFacing() : weld.smallFacing();
        final Vector3d targetAnchor = sourceIsSmall ? weld.bigAnchor() : weld.smallAnchor();
        final Direction.Axis sourceU = WeldContact.uAxis(sourceFacing);
        final Direction.Axis sourceV = WeldContact.vAxis(sourceFacing);
        final Direction.Axis targetU = WeldContact.uAxis(targetFacing);
        final Direction.Axis targetV = WeldContact.vAxis(targetFacing);
        final double[] offset = WeldContact.project(
                sourcePos.get(sourceU) + 0.5D - WeldContact.axisOf(sourceAnchor, sourceU),
                sourcePos.get(sourceV) + 0.5D - WeldContact.axisOf(sourceAnchor, sourceV),
                projection,
                targetU,
                targetV);
        final Vector3d previewAnchor = WeldGeometry.inPlane(
                targetFacing,
                WeldGeometry.facePlane(targetPos, targetFacing),
                WeldContact.axisOf(targetAnchor, targetU) + offset[0],
                WeldContact.axisOf(targetAnchor, targetV) + offset[1]);
        WeldContactPatch.showFaces(
                "pocket_weld_preview_fill",
                targetPos,
                targetFacing,
                previewAnchor,
                projection,
                shape.faces(),
                color);
        WeldContactPatch.show(
                "pocket_weld_preview",
                targetPos,
                targetFacing,
                previewAnchor,
                projection,
                shape.outline(),
                color,
                lineWidth);
    }

    private static PreviewShape originShape(final Minecraft minecraft, final SubLevel source) {
        if (originShape != null
                && java.util.Objects.equals(originShape.subLevel(), subLevelId(source))
                && originShape.pos().equals(firstPos)
                && originShape.facing() == firstFacing) {
            return originShape;
        }

        final Set<Long> traced = WeldContact.faceCells(
                minecraft.level, source, firstPos, firstFacing, WeldContact.RADIUS);
        final Set<Long> cells = traced.isEmpty() ? Set.of(WeldContact.originCell()) : traced;
        originShape = new PreviewShape(
                subLevelId(source),
                firstPos,
                firstFacing,
                1.0D,
                WeldContactPatch.rectangles(cells),
                WeldContactPatch.silhouette(cells, 0.0D));
        return originShape;
    }

    private static PreviewShape shape(
            final Minecraft minecraft,
            final SubLevel source,
            final BlockPos pos,
            final Direction facing,
            final double cell
    ) {
        if (previewShape != null
                && java.util.Objects.equals(previewShape.subLevel(), subLevelId(source))
                && previewShape.pos().equals(pos)
                && previewShape.facing() == facing
                && Math.abs(previewShape.cell() - cell) < 1.0E-9D) {
            return previewShape;
        }

        final Set<Long> traced = WeldContact.faceCells(
                minecraft.level, source, pos, facing, WeldContact.reachOnTarget(cell));
        final Set<Long> cells = traced.isEmpty() ? Set.of(WeldContact.originCell()) : traced;
        previewShape = new PreviewShape(
                subLevelId(source),
                pos,
                facing,
                cell,
                WeldContactPatch.rectangles(cells),
                WeldContactPatch.silhouette(cells, 0.0D));
        return previewShape;
    }

    private static UUID subLevelId(final SubLevel subLevel) {
        return subLevel == null ? null : subLevel.getUniqueId();
    }

    private static Vec3 hitFraction(final BlockHitResult hit) {
        final BlockPos pos = hit.getBlockPos();
        final Vec3 location = hit.getLocation();
        return new Vec3(location.x - pos.getX(), location.y - pos.getY(), location.z - pos.getZ());
    }

    private static WeldGeometry.SnapMode placementMode(final CrossScaleWelds.Weld weld) {
        if (weld.divisor() > 1 && !weld.startedSmall(firstPos)) return WeldGeometry.SnapMode.GRID;
        return mode();
    }

    private static WeldGeometry.SnapMode mode() {
        if (Screen.hasAltDown()) return WeldGeometry.SnapMode.FREE;
        if (Screen.hasControlDown()) return WeldGeometry.SnapMode.MAGNET;
        return WeldGeometry.SnapMode.SMART;
    }

    private static void discardSelection() {
        if (firstPos != null) {
            MergingGlueItemHandler.sendMessage("connection_terminated", SimColors.DISCARDABLE_ORANGE);
        }
        clear();
    }

    public static void clear() {
        firstPos = null;
        firstFacing = null;
        firstHit = null;
        firstHand = null;
        rotationTurns = 0;
        originShape = null;
        previewShape = null;
    }

    private HotGlueGunClient() {}
}
