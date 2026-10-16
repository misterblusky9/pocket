package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.FlexibleLinkScale;
import com.misterblusky9.pocket.item.FlexibleShaftItem;
import com.misterblusky9.pocket.item.ModItems;
import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.index.SimClickInteractions;
import dev.simulated_team.simulated.util.SimColors;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class FlexibleShaftItemHandler implements InteractCallback {
    public static final FlexibleShaftItemHandler INSTANCE = new FlexibleShaftItemHandler();

    public static void register() {
        SimClickInteractions.register(INSTANCE);
    }

    @Override
    public void clientTick(Level level, LocalPlayer player) {
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            BlockPos first = held.is(ModItems.FLEXIBLE_SHAFT.get()) ? FlexibleShaftItem.pending(held, level) : null;
            if (first != null) {
                if (Minecraft.getInstance().hitResult instanceof BlockHitResult hit) {
                    preview(level, first, hit);
                }
                return;
            }
        }
    }

    private static void preview(Level level, BlockPos firstBlock, BlockHitResult hit) {
        BlockPos hitBlock = hit.getBlockPos();
        Vec3 firstPoint = firstBlock.getCenter();
        boolean inRange = FlexibleShaftItem.inRange(level, firstBlock, hit.getLocation());
        boolean valid = FlexibleShaftItem.isValidAttachment(level, hitBlock)
                && !hitBlock.equals(firstBlock) && inRange
                && FlexibleLinkScale.sameScale(level, firstBlock, hitBlock);

        Vec3 target = valid ? hitBlock.getCenter() : hit.getLocation();
        Color color = new Color(valid
                ? SimColors.SUCCESS_LIME
                : inRange ? SimColors.PERCHANCE_ORANGE : SimColors.NUH_UH_RED);

        Outliner.getInstance().chaseAABB("FirstFlexibleShaftPoint", new AABB(firstPoint, firstPoint))
                .colored(color).lineWidth(1 / 3f).disableLineNormals();
        Vec3 globalFirst = Sable.HELPER.projectOutOfSubLevel(level, firstPoint);
        Vec3 globalTarget = Sable.HELPER.projectOutOfSubLevel(level, target);

        if (valid) {
            Outliner.getInstance().chaseAABB("SecondFlexibleShaftPoint", new AABB(target, target))
                    .colored(color).lineWidth(1 / 3f).disableLineNormals();
            double points = Math.floor(globalFirst.distanceTo(globalTarget));
            Vec3 backwards = globalFirst.subtract(globalTarget).normalize();
            for (int i = 0; i < points; i++) {
                Vec3 point = globalTarget.add(backwards.scale(i));
                Outliner.getInstance().chaseAABB("FlexibleShaftPoint" + i, new AABB(point, point))
                        .colored(color).lineWidth(0.125F).disableLineNormals();
            }
        } else if (!inRange) {
            globalTarget = globalTarget.subtract(globalFirst).normalize()
                    .scale(FlexibleShaftItem.MAX_LENGTH - 0.5).add(globalFirst);
            Outliner.getInstance().chaseAABB("SecondFlexibleShaftPoint", new AABB(globalTarget, globalTarget))
                    .colored(color).lineWidth(1 / 3f).disableLineNormals();
        }

        DustParticleOptions dust = new DustParticleOptions(color.asVectorF(), 1.0F);
        for (int i = 0; i < 4; i++) {
            Vec3 vec = globalFirst.lerp(globalTarget, level.random.nextFloat());
            level.addParticle(dust, vec.x, vec.y, vec.z, 0.0, 0.0, 0.0);
        }
    }
}
