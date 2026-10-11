package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.compression.EntityCompressionTargeting;
import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.misterblusky9.pocket.network.ShrinkRayBeamColourPayload;
import com.simibubi.create.AllSpecialTextures;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ShrinkRayHoverOutline {
    private static final String SLOT = "pocketShrinkRayHover";
    private static final int INERT_ENTITY_COLOUR = 0xBFBFBF;
    private static final Map<UUID, CompressionGlow> compressionGlows = new HashMap<>();
    private static Level clientLevel;
    private static int hoveredEntity = -1;
    private static boolean revealCases;

    public static boolean outlines(final Entity entity) {
        return entity != null && (compressionColour(entity) != CaseSealAim.NO_COLOUR
                || entity.getId() == hoveredEntity || caseColour(entity) != CaseSealAim.NO_COLOUR);
    }

    public static int entityColour(final Entity entity) {
        final int compression = compressionColour(entity);
        if (compression != CaseSealAim.NO_COLOUR) return compression;
        return entity.getId() == hoveredEntity ? INERT_ENTITY_COLOUR : caseColour(entity);
    }

    public static void compressionGlow(final UUID entityId, final boolean growing, final int ticks) {
        final Level level = Minecraft.getInstance().level;
        if (level == null || entityId == null) return;
        if (clientLevel != level) {
            compressionGlows.clear();
            clientLevel = level;
        }
        if (ticks <= 0) {
            compressionGlows.remove(entityId);
            return;
        }
        compressionGlows.put(entityId, new CompressionGlow(
                growing ? ShrinkRayBeamColourPayload.GROW_COLOUR : ShrinkRayBeamColourPayload.SHRINK_COLOUR,
                level.getGameTime() + ticks
        ));
    }

    private static int compressionColour(final Entity entity) {
        if (entity == null || entity.level() != clientLevel) return CaseSealAim.NO_COLOUR;
        final CompressionGlow glow = compressionGlows.get(entity.getUUID());
        return glow == null || entity.level().getGameTime() >= glow.until()
                ? CaseSealAim.NO_COLOUR : glow.colour();
    }

    private record CompressionGlow(int colour, long until) {}

    private static int caseColour(final Entity entity) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (!revealCases || player == null) return CaseSealAim.NO_COLOUR;
        final CaseSealAim aim = CaseSealAim.of(player, entity);
        return aim == null ? CaseSealAim.NO_COLOUR : aim.colour();
    }

    public static void tick() {
        hoveredEntity = -1;
        final Level level = Minecraft.getInstance().level;
        if (clientLevel != level) {
            compressionGlows.clear();
            clientLevel = level;
        }
        if (level != null) {
            compressionGlows.values().removeIf(glow -> level.getGameTime() >= glow.until());
        }
        final LocalPlayer player = Minecraft.getInstance().player;
        revealCases = player != null && CaseSealAim.holdingTool(player);
        if (player == null) return;

        final ItemStack ray = player.getMainHandItem().getItem() instanceof CreativeShrinkRayItem
                ? player.getMainHandItem()
                : player.getOffhandItem();
        if (!(ray.getItem() instanceof CreativeShrinkRayItem)) return;
        final CreativeShrinkRayItem.TargetingMode targeting = CreativeShrinkRayItem.targetingMode(ray);

        if (targeting.allowsEntities()) {
            final EntityCompressionTargeting.Target entity =
                    EntityCompressionTargeting.find(player, CreativeShrinkRayItem.RANGE, true);
            if (entity != null) {
                hoveredEntity = entity.entity().getId();
                return;
            }
        }

        if (!targeting.allowsContraptions()) return;
        final Vec3 start = player.getEyePosition();
        final Vec3 end = start.add(player.getLookAngle().scale(CreativeShrinkRayItem.RANGE));
        final BlockHitResult hit = player.level().clip(new ClipContext(
                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit == null || hit.getType() == HitResult.Type.MISS) return;
        final SubLevel subLevel = Sable.HELPER.getContaining(player.level(), hit.getBlockPos());
        if (subLevel == null || subLevel.isRemoved()) return;

        Outliner.getInstance().showCluster(SLOT, List.of(hit.getBlockPos()))
                .colored(0xbfbfbf)
                .disableLineNormals()
                .lineWidth(1 / 32f)
                .withFaceTexture(AllSpecialTextures.CHECKERED);
    }

    private ShrinkRayHoverOutline() {}
}
