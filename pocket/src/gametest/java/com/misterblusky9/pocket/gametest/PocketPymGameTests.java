package com.misterblusky9.pocket.gametest;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ResizeResult;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(PocketSized.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PocketPymGameTests {
    private static final String EMPTY = "empty8";
    private static final double TOLERANCE = 1.0E-6D;

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void ordinaryCraftShrinksThroughPym(final GameTestHelper helper) {
        final ServerSubLevel subLevel = assembleCube(helper, Blocks.IRON_BLOCK);
        final ResizeResult result = Pym.resize().request(subLevel).scaleTo(0.5D).submit();
        helper.assertTrue(result.accepted(), "Pocket/Pym resize was refused: " + result);
        near(helper, result.scale(), 0.5D, "accepted scale");

        helper.succeedWhen(() -> {
            helper.assertTrue(Pym.scale().isAt(subLevel, 0.5D),
                    "ordinary Pocket craft did not settle at 1/2x; at " + Pym.scale().of(subLevel));
            near(helper, subLevel.logicalPose().scale().x(), 0.5D, "pose scale");
        });
    }

    private static ServerSubLevel assembleCube(final GameTestHelper helper, final Block fill) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }

        final List<BlockPos> blocks = new ArrayList<>();
        for (int x = 3; x <= 4; x++) {
            for (int y = 2; y <= 3; y++) {
                for (int z = 3; z <= 4; z++) {
                    final BlockPos relative = new BlockPos(x, y, z);
                    helper.setBlock(relative, fill);
                    blocks.add(helper.absolutePos(relative));
                }
            }
        }

        final BlockPos min = helper.absolutePos(new BlockPos(3, 2, 3));
        final BlockPos max = helper.absolutePos(new BlockPos(4, 3, 4));
        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
                helper.getLevel(), min, blocks, new BoundingBox3i(min, max));
        helper.assertTrue(subLevel != null && !subLevel.isRemoved(), "Sable did not assemble the Pocket test craft");
        return subLevel;
    }

    private static void near(final GameTestHelper helper, final double actual, final double expected, final String what) {
        helper.assertTrue(Math.abs(actual - expected) <= TOLERANCE,
                what + ": expected " + expected + " but was " + actual);
    }

    private PocketPymGameTests() {}
}
