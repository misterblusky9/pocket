package com.misterblusky9.pocket.gametest;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.pocket.PocketedSubLevelEvents;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ResizeResult;
import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(PocketSized.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PocketCaseDeployGameTests {
    @GameTest(template = "empty8", timeoutTicks = 200)
    public static void deployOntoScaledCraftLandsInWorld(final GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }

        final ServerLevel level = helper.getLevel();
        final ServerSubLevel cargo = assembleCube(helper, new BlockPos(1, 2, 1));
        final ServerSubLevel surface = assembleCube(helper, new BlockPos(5, 2, 5));
        resize(helper, cargo, PocketCaseItem.POCKETABLE_SCALE);
        resize(helper, surface, 0.5D);

        final ServerPlayer player = player(helper);
        player.setShiftKeyDown(true);

        final ServerSubLevel[] restored = new ServerSubLevel[1];
        helper.startSequence()
                .thenWaitUntil(() -> resting(helper, level, surface))
                .thenExecute(() -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.EMPTY_BOX.get()));
                    final BlockHitResult cargoHit = topHit(level, cargo);
                    PocketedSubLevelEvents.onRightClickBlock(new PlayerInteractEvent.RightClickBlock(
                            player, InteractionHand.MAIN_HAND, cargoHit.getBlockPos(), cargoHit));
                    final ItemStack filled = player.getItemInHand(InteractionHand.MAIN_HAND);
                    helper.assertTrue(PocketCaseItem.isFilled(filled), "the cargo craft was not pocketed: " + filled);
                    helper.assertTrue(cargo.isRemoved(), "the cargo craft is still in the world");

                    final BlockHitResult surfaceHit = topHit(level, surface);
                    final Vec3 worldClick = surface.logicalPose().transformPosition(surfaceHit.getLocation());
                    final Set<UUID> before = new HashSet<>();
                    for (final ServerSubLevel existing : container(helper).getAllSubLevels()) {
                        before.add(existing.getUniqueId());
                    }
                    final InteractionResult result = filled.useOn(
                            new UseOnContext(player, InteractionHand.MAIN_HAND, surfaceHit));
                    helper.assertTrue(result.consumesAction(), "deploy onto the scaled craft was refused: " + result);
                    helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(ModItems.EMPTY_BOX.get()),
                            "the case was not emptied: " + player.getItemInHand(InteractionHand.MAIN_HAND));

                    restored[0] = restoredSince(helper, before);
                    final Vector3d at = new Vector3d(restored[0].logicalPose().position());
                    final double distance = at.distance(worldClick.x, worldClick.y, worldClick.z);
                    helper.assertTrue(distance < 2.0D,
                            "the restored craft landed " + distance + " blocks from the click, at " + at
                                    + " (click " + worldClick + ")");
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(!restored[0].isRemoved(), "the restored craft was removed");
                    helper.assertTrue(container(helper).getSubLevel(restored[0].getUniqueId()) == restored[0],
                            "the restored craft is no longer registered");
                })
                .thenSucceed();
    }

    private static void resting(final GameTestHelper helper, final ServerLevel level, final ServerSubLevel surface) {
        final double top = surface.logicalPose().transformPosition(topHit(level, surface).getLocation()).y;
        final double boxTop = surface.boundingBox().maxY();
        helper.assertTrue(Math.abs(top - boxTop) < 1.0E-3D,
                "the surface craft is still moving: top " + top + " box " + boxTop);
    }

    private static ServerSubLevel assembleCube(final GameTestHelper helper, final BlockPos corner) {
        final List<BlockPos> blocks = new ArrayList<>();
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    final BlockPos relative = corner.offset(x, y, z);
                    helper.setBlock(relative, Blocks.IRON_BLOCK);
                    blocks.add(helper.absolutePos(relative));
                }
            }
        }

        final BlockPos min = helper.absolutePos(corner);
        final BlockPos max = helper.absolutePos(corner.offset(1, 1, 1));
        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
                helper.getLevel(), min, blocks, new BoundingBox3i(min, max));
        helper.assertTrue(subLevel != null && !subLevel.isRemoved(), "Sable did not assemble the test craft");
        return subLevel;
    }

    private static void resize(final GameTestHelper helper, final ServerSubLevel subLevel, final double scale) {
        final ResizeResult result = Pym.resize().request(subLevel).scaleTo(scale).immediate().submit();
        helper.assertTrue(result.accepted(), "resize to " + scale + " was refused: " + result);
    }

    private static BlockHitResult topHit(final ServerLevel level, final ServerSubLevel subLevel) {
        final BoundingBox3ic box = subLevel.getPlot().getBoundingBox();
        for (int y = box.maxY(); y >= box.minY(); y--) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    final BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).is(Blocks.IRON_BLOCK)) continue;
                    return new BlockHitResult(
                            Vec3.atCenterOf(pos).add(0.0D, 0.5D, 0.0D), Direction.UP, pos, false);
                }
            }
        }
        throw new AssertionError("no block found in the plot of " + subLevel.getUniqueId());
    }

    private static ServerSubLevel restoredSince(final GameTestHelper helper, final Set<UUID> before) {
        for (final ServerSubLevel candidate : container(helper).getAllSubLevels()) {
            if (!candidate.isRemoved() && !before.contains(candidate.getUniqueId())) return candidate;
        }
        throw new AssertionError("no restored craft exists");
    }

    private static ServerSubLevelContainer container(final GameTestHelper helper) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        helper.assertTrue(container != null, "no Sable container");
        return container;
    }

    private static ServerPlayer player(final GameTestHelper helper) {
        final ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "pocket-test"));
        final Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 3)));
        player.moveTo(at.x, at.y, at.z, -45.0F, 30.0F);
        return player;
    }

    private PocketCaseDeployGameTests() {}
}
