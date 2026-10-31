package com.misterblusky9.pocket.client;

import net.neoforged.neoforge.event.level.LevelEvent;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldContact;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.compat.simulated.SlimeWeldMerge;
import com.misterblusky9.pocket.item.ModItems;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
@EventBusSubscriber(modid = PocketSized.MOD_ID, value = Dist.CLIENT)
public final class CrossScaleWeldSeams {
    private static final long RETRACE_INTERVAL = 5L;

    private static final Map<UUID, Cache> CELLS = new HashMap<>();
    private static long hoverStamp = Long.MIN_VALUE;
    private static UUID hovered;

    private record Cache(long stamp, boolean onSmall, Set<Long> cells, List<WeldContactPatch.Edge> outline) {}

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null || minecraft.isPaused()) return;

        List<WeldRecord> welds = CrossScaleWelds.clientWelds();
        if (welds.isEmpty() || !revealing(player)) return;

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        CELLS.keySet().removeIf(id -> welds.stream().noneMatch(w -> w.weldId().equals(id)));

        boolean slimeMode = holdingSlimeball(player);
        UUID doomed = holdingKnife(player) || punchCuts(player) || slimeMode
                ? hoveredWeld(minecraft, level) : null;

        for (WeldRecord record : welds) {
            if (slimeMode && !SlimeWeldMerge.eligible(level, record)) continue;
            SubLevel small = container.getSubLevel(record.smallSubLevel());
            SubLevel big = record.worldAnchored() ? null : container.getSubLevel(record.bigSubLevel());
            if (small == null || small.isRemoved()
                    || (!record.worldAnchored() && (big == null || big.isRemoved()))) continue;
            boolean drawOnSmall = scaleOf(small) <= scaleOf(big);
            SubLevel drawnOn = drawOnSmall ? small : big;
            SubLevel other = drawOnSmall ? big : small;
            Cache cache = cached(level, record, drawOnSmall, drawnOn, other);
            if (cache.cells().isEmpty()) continue;

            int color = record.weldId().equals(doomed)
                    ? SimColors.NUH_UH_RED
                    : SimColors.SUCCESS_LIME;

            WeldContactPatch.show(
                    "pocket_weld_" + record.weldId(),
                    record.posFor(drawOnSmall),
                    record.facingFor(drawOnSmall),
                    record.anchorFor(drawOnSmall),
                    WeldContact.identity(record.facingFor(drawOnSmall)),
                    cache.outline(),
                    color,
                    WeldContactPatch.lineWidth(drawnOn, other));
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    public static void clear() {
        CELLS.clear();
        hoverStamp = Long.MIN_VALUE;
        hovered = null;
    }

    private static double scaleOf(SubLevel subLevel) {
        return subLevel == null ? ScaleBounds.FULL : ScaleBounds.clampValid(Pym.scale().of(subLevel));
    }

    private static Cache cached(
            Level level,
            WeldRecord record,
            boolean onSmall,
            SubLevel source,
            SubLevel target
    ) {
        long now = level.getGameTime();
        Cache hit = CELLS.get(record.weldId());
        if (hit != null && hit.onSmall() == onSmall && now - hit.stamp() < RETRACE_INTERVAL) return hit;

        Set<Long> cells = WeldContact.cells(
                level,
                source,
                record.posFor(onSmall),
                record.facingFor(onSmall),
                target,
                record.posFor(!onSmall),
                record.facingFor(!onSmall),
                record.anchorFor(!onSmall),
                WeldContact.projectionFor(record, onSmall));
        Cache cache = new Cache(
                now,
                onSmall,
                cells,
                cells.isEmpty()
                        ? List.of()
                        : WeldContactPatch.silhouette(cells, WeldContactPatch.SEAM_OOZE));
        CELLS.put(record.weldId(), cache);
        return cache;
    }

    private static UUID hoveredWeld(Minecraft minecraft, Level level) {
        long now = level.getGameTime();
        if (hoverStamp == now) return hovered;
        hoverStamp = now;

        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() == HitResult.Type.MISS) {
            hovered = null;
            return null;
        }
        WeldRecord record = CrossScaleWelds.weldNear(level, hit.getLocation());
        hovered = record == null ? null : record.weldId();
        return hovered;
    }

    public static boolean revealing(LocalPlayer player) {
        if (player == null) return false;
        return reveals(player.getMainHandItem()) || reveals(player.getOffhandItem());
    }

    private static boolean holdingKnife(LocalPlayer player) {
        return isKnife(player.getMainHandItem()) || isKnife(player.getOffhandItem());
    }

    private static boolean punchCuts(LocalPlayer player) {
        return player.getMainHandItem().is(ModItems.GLUE_GUN.get()) && !HotGlueGunClient.placing();
    }

    private static boolean holdingSlimeball(LocalPlayer player) {
        return player.getMainHandItem().is(Items.SLIME_BALL)
                || player.getOffhandItem().is(Items.SLIME_BALL);
    }

    private static boolean isKnife(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.POCKET_KNIFE.get());
    }

    private static boolean reveals(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && (stack.is(ModItems.GLUE_GUN.get()) || stack.is(ModItems.POCKET_KNIFE.get())
                    || stack.is(Items.SLIME_BALL));
    }

    private CrossScaleWeldSeams() {}
}
