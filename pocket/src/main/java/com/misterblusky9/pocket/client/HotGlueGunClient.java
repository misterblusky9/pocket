package com.misterblusky9.pocket.client;

import net.neoforged.neoforge.event.level.LevelEvent;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldContact;
import com.misterblusky9.pocket.compat.simulated.WeldGeometry;
import com.misterblusky9.pocket.item.HeldInteractionPriority;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.network.CrossScaleWeldPayload;
import com.misterblusky9.pocket.network.HotGluePunchPayload;
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
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.HashSet;
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
    private static PreviewShape previewSource;
    private static PreviewKey previewKey;
    private static boolean swallowPunch;

    private record PreviewShape(
            UUID subLevel,
            BlockPos pos,
            Direction facing,
            Set<Long> cells,
            List<WeldContactPatch.Edge> faces,
            List<WeldContactPatch.Edge> outline
    ) {}

    private record PreviewKey(
            UUID source,
            UUID target,
            BlockPos targetPos,
            Direction targetFacing,
            Vector3d anchor,
            Vector3d axisU,
            Vector3d axisV,
            double cell
    ) {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || !event.getItemStack().is(ModItems.GLUE_GUN.get())) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || event.getEntity() != player) return;

        if (player.isShiftKeyDown() && firstPos != null) {
            discardSelection();
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
            return;
        }

        BlockHitResult hit = event.getHitVec();
        if (hit == null || hit.getType() == HitResult.Type.MISS
                || event.getLevel().getBlockState(hit.getBlockPos()).isAir()) {
            return;
        }
        if (knifeCuts(player, event.getHand(), hit)) return;
        if (firstPos == null && !player.isShiftKeyDown()
                && event.getLevel().getBlockState(hit.getBlockPos()).is(HeldInteractionPriority.TAKES_HELD_ITEMS)) return;

        if ((firstPos == null || firstHand != event.getHand())
                && Sable.HELPER.getContaining(event.getLevel(), hit.getBlockPos()) == null) {
            return;
        }

        BlockHitResult selectedHit = HotGlueFacePicker.select(event.getLevel(), player, hit);

        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);

        if (firstPos == null || firstHand != event.getHand()) {
            begin(selectedHit, event.getHand());
            return;
        }

        CrossScaleWelds.Weld weld = resolve(event.getLevel(), selectedHit);
        if (weld == null) {
            player.displayClientMessage(CrossScaleWelds.Refusal.CANNOT_WELD.component(), true);
            return;
        }

        CrossScaleWelds.Refusal refusal = weld.check();
        if (!refusal.allowed()) {
            Component message = refusal.component();
            if (message != null) player.displayClientMessage(message, true);
            return;
        }

        Vec3 bigHit = weld.startedSmall(firstPos) ? hitFraction(selectedHit) : firstHit;
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
        HotGlueGunRenderHandler.INSTANCE.shoot(event.getHand(), player.position());

        clear();
    }

    @SubscribeEvent(receiveCanceled = true)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide()
                || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !event.getItemStack().is(ModItems.GLUE_GUN.get())) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (event.getEntity() != minecraft.player || placing() || swallowPunch) return;

        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() == HitResult.Type.MISS
                || !hit.getBlockPos().equals(event.getPos())
                || CrossScaleWelds.weldNear(event.getLevel(), hit.getLocation()) == null) return;

        Vec3 fraction = hitFraction(hit);
        PacketDistributor.sendToServer(new HotGluePunchPayload(hit.getBlockPos(), fraction.x, fraction.y, fraction.z));
    }

    @SubscribeEvent
    public static void onAttackInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        if (placing()) {
            discardSelection();
            swallowPunch = true;
        }
        if (!swallowPunch) return;
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        if (swallowPunch && !Minecraft.getInstance().options.keyAttack.isDown()) swallowPunch = false;

        Minecraft previewClient = Minecraft.getInstance();
        LocalPlayer previewPlayer = previewClient.player;
        if (previewPlayer != null && previewClient.level != null && !previewClient.isPaused()
                && (previewPlayer.getMainHandItem().is(ModItems.GLUE_GUN.get())
                    || previewPlayer.getOffhandItem().is(ModItems.GLUE_GUN.get()))
                && previewClient.hitResult instanceof BlockHitResult hovered
                && hovered.getType() == HitResult.Type.BLOCK
                && (placing() || Sable.HELPER.getContaining(previewClient.level, hovered.getBlockPos()) != null)) {
            HotGlueFacePicker.render(previewClient.level, previewPlayer, hovered);
        }

        if (firstPos == null) {
            while (PocketKeys.WELD_ROTATE.consumeClick()) {}
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            clear();
            return;
        }
        if (minecraft.isPaused()) return;
        if (firstHand == null || !player.getItemInHand(firstHand).is(ModItems.GLUE_GUN.get())) {
            discardSelection();
            return;
        }

        SubLevel first = Sable.HELPER.getContaining(minecraft.level, firstPos);
        if ((first != null && first.isRemoved())
                || (first == null && minecraft.level.getBlockState(firstPos).isAir())) {
            discardSelection();
            return;
        }

        while (PocketKeys.WELD_ROTATE.consumeClick()) {
            rotationTurns = (rotationTurns + 1) & 3;
        }

        Vector3d firstAnchor = WeldGeometry.faceCentre(firstPos, firstFacing);
        PreviewShape origin = originShape(minecraft, first);
        WeldContactPatch.showFaces(
                "pocket_weld_origin",
                firstPos,
                firstFacing,
                firstAnchor,
                WeldContact.identity(firstFacing),
                origin.faces(),
                SimColors.SUCCESS_LIME);
        WeldContactPatch.show(
                "pocket_weld_origin_outline",
                firstPos,
                firstFacing,
                firstAnchor,
                WeldContact.identity(firstFacing),
                origin.outline(),
                SimColors.SUCCESS_LIME,
                WeldContactPatch.LINE_WIDTH);
        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() == HitResult.Type.MISS) return;
        if (knifeCuts(player, firstHand, hit)) return;

        CrossScaleWelds.Weld weld = resolve(minecraft.level, HotGlueFacePicker.select(minecraft.level, player, hit));
        if (weld == null) return;
        if (!weld.worldAnchored() && (weld.small() == weld.big()
                || java.util.Objects.equals(weld.small().getUniqueId(), weld.big().getUniqueId()))) return;

        renderPreview(minecraft, weld);
    }

    private static boolean knifeCuts(LocalPlayer player, InteractionHand gunHand, BlockHitResult hit) {
        InteractionHand other = gunHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        return player.getItemInHand(other).is(ModItems.POCKET_KNIFE.get())
                && CrossScaleWelds.weldNear(player.level(), hit.getLocation()) != null;
    }

    private static void begin(BlockHitResult hit, InteractionHand hand) {
        firstPos = hit.getBlockPos().immutable();
        firstFacing = hit.getDirection();
        firstHit = hitFraction(hit);
        firstHand = hand;
        rotationTurns = 0;
        originShape = null;
        previewShape = null;
        previewSource = null;
        previewKey = null;
    }

    private static CrossScaleWelds.Weld resolve(net.minecraft.world.level.Level level, BlockHitResult hit) {
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

    private static void renderPreview(Minecraft minecraft, CrossScaleWelds.Weld weld) {
        int color = weld.check().allowed() ? SimColors.SUCCESS_LIME : SimColors.NUH_UH_RED;
        boolean sourceIsSmall = weld.startedSmall(firstPos);
        Quaterniond orientation = CrossScaleWelds.weldOrientation(weld, rotationTurns);
        double cell = sourceIsSmall
                ? ScaleBounds.clampValid(weld.smallScale()) / ScaleBounds.clampValid(weld.bigScale())
                : ScaleBounds.clampValid(weld.bigScale()) / ScaleBounds.clampValid(weld.smallScale());
        WeldContact.Projection projection = WeldContact.projection(
                sourceIsSmall ? weld.smallFacing() : weld.bigFacing(),
                sourceIsSmall ? new Quaterniond(orientation).invert() : new Quaterniond(orientation),
                cell);

        SubLevel source = sourceIsSmall ? weld.small() : weld.big();
        SubLevel target = sourceIsSmall ? weld.big() : weld.small();
        BlockPos sourcePos = sourceIsSmall ? weld.smallPos() : weld.bigPos();
        Direction sourceFacing = sourceIsSmall ? weld.smallFacing() : weld.bigFacing();

        Vector3d sourceAnchor = sourceIsSmall ? weld.smallAnchor() : weld.bigAnchor();
        BlockPos targetPos = sourceIsSmall ? weld.bigPos() : weld.smallPos();
        Direction targetFacing = sourceIsSmall ? weld.bigFacing() : weld.smallFacing();
        Vector3d targetAnchor = sourceIsSmall ? weld.bigAnchor() : weld.smallAnchor();
        Direction.Axis sourceU = WeldContact.uAxis(sourceFacing);
        Direction.Axis sourceV = WeldContact.vAxis(sourceFacing);
        Direction.Axis targetU = WeldContact.uAxis(targetFacing);
        Direction.Axis targetV = WeldContact.vAxis(targetFacing);
        double[] offset = WeldContact.project(
                sourcePos.get(sourceU) + 0.5D - WeldContact.axisOf(sourceAnchor, sourceU),
                sourcePos.get(sourceV) + 0.5D - WeldContact.axisOf(sourceAnchor, sourceV),
                projection,
                targetU,
                targetV);
        Vector3d previewAnchor = WeldGeometry.inPlane(
                targetFacing,
                WeldGeometry.facePlane(targetPos, targetFacing),
                WeldContact.axisOf(targetAnchor, targetU) + offset[0],
                WeldContact.axisOf(targetAnchor, targetV) + offset[1]);
        PreviewShape shape = previewShape(
                originShape(minecraft, source), target, targetPos, targetFacing, previewAnchor, projection);
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
                WeldContactPatch.lineWidth(target, source));
    }

    private static PreviewShape originShape(Minecraft minecraft, SubLevel source) {
        if (originShape != null
                && java.util.Objects.equals(originShape.subLevel(), subLevelId(source))
                && originShape.pos().equals(firstPos)
                && originShape.facing() == firstFacing) {
            return originShape;
        }

        Set<Long> traced = WeldContact.faceCells(
                minecraft.level, source, firstPos, firstFacing, WeldContact.RADIUS);
        originShape = shapeOf(subLevelId(source), firstPos, firstFacing,
                traced.isEmpty() ? Set.of(WeldContact.originCell()) : traced);
        return originShape;
    }

    private static PreviewShape previewShape(
            PreviewShape traced,
            SubLevel target,
            BlockPos targetPos,
            Direction targetFacing,
            Vector3d anchor,
            WeldContact.Projection projection
    ) {
        if (target == null) return traced;

        PreviewKey key = new PreviewKey(traced.subLevel(), subLevelId(target), targetPos, targetFacing,
                new Vector3d(anchor), new Vector3d(projection.axisU()), new Vector3d(projection.axisV()),
                projection.cell());
        if (previewSource == traced && key.equals(previewKey)) return previewShape;

        Direction.Axis targetU = WeldContact.uAxis(targetFacing);
        Direction.Axis targetV = WeldContact.vAxis(targetFacing);
        double anchorU = WeldContact.axisOf(anchor, targetU);
        double anchorV = WeldContact.axisOf(anchor, targetV);
        double plane = WeldGeometry.facePlane(targetPos, targetFacing);
        Set<Long> inside = new HashSet<>();
        for (long cell : traced.cells()) {
            double[] centre = WeldContact.project(
                    WeldContact.unpackU(cell), WeldContact.unpackV(cell), projection, targetU, targetV);
            Vector3d point = WeldGeometry.inPlane(
                    targetFacing, plane, anchorU + centre[0], anchorV + centre[1]);
            if (target.getPlot().contains(point.x, point.z)) inside.add(cell);
        }

        previewSource = traced;
        previewKey = key;
        previewShape = inside.size() == traced.cells().size()
                ? traced
                : shapeOf(traced.subLevel(), traced.pos(), traced.facing(),
                        inside.isEmpty() ? Set.of(WeldContact.originCell()) : inside);
        return previewShape;
    }

    private static PreviewShape shapeOf(
            UUID subLevel,
            BlockPos pos,
            Direction facing,
            Set<Long> cells
    ) {
        return new PreviewShape(
                subLevel,
                pos,
                facing,
                cells,
                WeldContactPatch.rectangles(cells),
                WeldContactPatch.silhouette(cells, 0.0D));
    }

    private static UUID subLevelId(SubLevel subLevel) {
        return subLevel == null ? null : subLevel.getUniqueId();
    }

    private static Vec3 hitFraction(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Vec3 location = hit.getLocation();
        return new Vec3(location.x - pos.getX(), location.y - pos.getY(), location.z - pos.getZ());
    }

    private static WeldGeometry.SnapMode placementMode(CrossScaleWelds.Weld weld) {
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

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    public static boolean placing() {
        return firstPos != null;
    }

    public static void clear() {
        firstPos = null;
        firstFacing = null;
        firstHit = null;
        firstHand = null;
        rotationTurns = 0;
        originShape = null;
        previewShape = null;
        previewSource = null;
        previewKey = null;
    }

    private HotGlueGunClient() {}
}
