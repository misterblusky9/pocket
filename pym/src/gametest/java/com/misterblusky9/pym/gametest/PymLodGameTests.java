package com.misterblusky9.pym.gametest;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.SubLevelShape;
import com.misterblusky9.pym.internal.collision.SubLevelIndexedCollision;
import com.misterblusky9.pym.internal.entity.EntityScaleTracker;
import com.misterblusky9.pym.internal.physics.PlotShapeCache;
import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@GameTestHolder(Pym.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PymLodGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FLOOR = "empty48";
    private static final double CRAFT = 1.0D / 32.0D;
    private static final double[] PLAYERS = {0.5D, 1.0D, 2.0D, 4.0D, 8.0D, 16.0D};

    @GameTest(template = FLOOR, timeoutTicks = 100)
    public static void lodWall(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 40; x++) {
            for (int y = 0; y < 12; y++) {
                boolean small = x >= 6 && x < 8 && y >= 4 && y < 6;
                boolean medium = x >= 14 && x < 20 && y >= 3 && y < 9;
                if (!small && !medium) blocks.put(new BlockPos(x, y, 0), Blocks.WHITE_WOOL.defaultBlockState());
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        BlockPos o = origin(craft);

        int[] counts = report("wall", craft);
        helper.assertTrue(SubLevelIndexedCollision.outlines(craft, 1.0D, 0.6D, 0.6D, 1.8D, 0.6D).size() == counts[0],
                "a 1x player on a 1x craft must use the exact LOD0 mesh");

        List<SubLevelShape.Box> exact = player(craft, 0.5D);
        List<SubLevelShape.Box> lod1 = player(craft, 1.0D);
        List<SubLevelShape.Box> lod2 = player(craft, 4.0D);
        helper.assertFalse(covered(exact, o, 6, 4, 0), "LOD0 filled the 2x2 hole");
        helper.assertTrue(covered(lod1, o, 6, 4, 0), "LOD1 kept a 2x2 hole the entity cannot use");
        helper.assertFalse(covered(lod1, o, 16, 5, 0), "LOD1 closed a 6x6 opening wider than its element");
        helper.assertTrue(covered(lod2, o, 16, 5, 0), "LOD2 kept a 6x6 hole a 77-block-wide entity cannot use");
        helper.assertTrue(counts[0] > counts[1] && counts[1] > counts[3], "wall colliders did not shrink with LOD");
        helper.assertTrue(lod2.size() == 1, "LOD2 wall should be one box, got " + lod2.size());
        helper.assertTrue(player(craft, 16.0D).size() == 1, "LOD4 should be one box");

        for (int x = 4; x < 10; x++) {
            for (int y = 2; y < 8; y++) helper.getLevel().setBlock(o.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 3);
        }
        helper.assertFalse(covered(player(craft, 1.0D), o, 6, 4, 0),
                "LOD1 did not rebuild after the hole was widened to 6x6");
        helper.succeed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 100)
    public static void lodCoherentFilledVolumes(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 40; x++) {
            for (int y = 0; y < 12; y++) {
                for (int z = 0; z < 6; z++) {
                    if (x % 5 == 2 && y % 4 == 2 && z > 0 && z < 5) continue;
                    blocks.put(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
                }
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        BlockPos o = origin(craft);
        List<SubLevelShape.Box> exact = player(craft, 0.5D);
        List<SubLevelShape.Box> coarse = player(craft, 4.0D);
        helper.assertFalse(covered(exact, o, 2, 2, 2), "LOD0 filled an original cavity");
        helper.assertTrue(covered(coarse, o, 2, 2, 2), "LOD2 failed to close a cavity smaller than the player");
        helper.assertTrue(coarse.size() < exact.size(), "LOD2 increased the collider count");
        for (int i = 0; i < coarse.size(); i++) {
            for (int j = i + 1; j < coarse.size(); j++) {
                helper.assertFalse(overlap(coarse.get(i), coarse.get(j)), "LOD2 generated intersecting boxes");
            }
        }
        helper.succeed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 100)
    public static void lodJaggedTerrain(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Random random = new Random(7L);
        int top = 0;
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 20; z++) {
                int height = x == 0 && z == 0 ? 6 : 1 + random.nextInt(6);
                top = Math.max(top, height);
                for (int y = 0; y < height; y++) blocks.put(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        BlockPos o = origin(craft);
        int[] counts = report("jagged", craft);
        helper.assertTrue(counts[0] > counts[1] && counts[1] >= counts[2] && counts[2] > counts[4],
                "jagged colliders did not shrink with LOD");
        for (double scale : PLAYERS) {
            for (SubLevelShape.Box box : player(craft, scale)) {
                helper.assertTrue(box.maxY() - o.getY() <= top + 1.0E-6,
                        "player " + scale + " raised jagged terrain above its highest column");
            }
        }
        helper.succeed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 100)
    public static void lodSlope(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        BlockState stair = Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 12; z++) {
                int height = x / 2;
                for (int y = 0; y <= height; y++) blocks.put(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
                blocks.put(new BlockPos(x, height + 1, z), x % 2 == 0 ? stair : Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        int[] counts = report("slope", craft);
        int stepless = SubLevelIndexedCollision.outlines(craft, CRAFT, 0.6D, 0.6D, 1.8D, 0.0D).size();
        LOGGER.info("[Pym LOD] slope player 1 without step: {} colliders (stairs and slabs stay exact)", stepless);
        helper.assertTrue(counts[1] < stepless, "promoting steppable stairs did not reduce colliders");
        helper.assertTrue(counts[0] > counts[1] && counts[1] >= counts[2], "slope colliders did not shrink with LOD");
        helper.succeed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 100)
    public static void lodHollow(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 30; x++) {
            for (int y = 0; y < 12; y++) {
                for (int z = 0; z < 20; z++) {
                    boolean shell = x == 0 || x == 29 || y == 0 || y == 11 || z == 0 || z == 19;
                    boolean door = x == 0 && z >= 8 && z < 12 && y >= 1 && y < 7;
                    if (shell && !door) blocks.put(new BlockPos(x, y, z), Blocks.OAK_PLANKS.defaultBlockState());
                }
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        BlockPos o = origin(craft);
        int[] counts = report("hollow", craft);
        List<SubLevelShape.Box> lod1 = player(craft, 1.0D);
        helper.assertFalse(covered(lod1, o, 15, 5, 10), "LOD1 filled a room larger than its element");
        helper.assertFalse(covered(lod1, o, 0, 3, 9), "LOD1 closed a 4x6 doorway");
        helper.assertTrue(covered(player(craft, 4.0D), o, 15, 5, 10),
                "LOD2 kept a room a 77-block-wide entity cannot enter");
        helper.assertTrue(counts[0] >= counts[1] && counts[1] > counts[3], "hollow colliders did not shrink with LOD");
        helper.succeed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void lodRotatedGiantProbe(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 40; x++) {
            for (int y = 0; y < 12; y++) {
                for (int z = 0; z < 2; z++) {
                    boolean hole = x >= 16 && x < 24 && y >= 3 && y < 9;
                    if (!hole) blocks.put(new BlockPos(x, y, z), Blocks.WHITE_WOOL.defaultBlockState());
                }
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        helper.assertTrue(Pym.resize().request(craft).scaleTo(CRAFT).immediate().submit().accepted(), "resize refused");
        Player giant = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(EntityScaleTracker.set(giant, 16.0D, 0), "could not scale the player to 16x");
        giant.refreshDimensions();
        Quaterniond tilt = new Quaterniond().rotateY(Math.toRadians(30.0D)).rotateZ(Math.toRadians(15.0D));

        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft, tilt, 4.0D))
                .thenIdle(40)
                .thenExecute(() -> {
                    BoundingBox3ic plot = craft.getPlot().getBoundingBox();
                    Vector3d centre = craft.lastPose().transformPosition(new Vector3d(
                            (plot.minX() + plot.maxX() + 1) * 0.5D, (plot.minY() + plot.maxY() + 1) * 0.5D,
                            (plot.minZ() + plot.maxZ() + 1) * 0.5D));
                    double angle = 2.0D * Math.acos(Math.min(1.0D, Math.abs(craft.lastPose().orientation().w())));
                    double w = giant.getBbWidth() * 0.5D, h = giant.getBbHeight();
                    AABB inside = new AABB(centre.x - w, centre.y - h * 0.5D, centre.z - w,
                            centre.x + w, centre.y + h * 0.5D, centre.z + w);
                    AABB beside = inside.move(w * 2.0D + 4.0D, 0.0D, 0.0D);
                    AABB above = inside.move(0.0D, h * 0.5D + 2.0D, 0.0D);
                    boolean[] scoped = {probe(helper, giant, inside), probe(helper, giant, beside), probe(helper, giant, above)};
                    boolean[] bare = {probe(helper, null, inside), probe(helper, null, beside), probe(helper, null, above)};
                    LOGGER.info("[Pym LOD] rotated 1/32 wall ({} deg) vs 16x player: scoped inside/beside/above={}/{}/{}, unscoped={}/{}/{}",
                            String.format(Locale.ROOT, "%.1f", Math.toDegrees(angle)),
                            scoped[0], scoped[1], scoped[2], bare[0], bare[1], bare[2]);
                    helper.assertTrue(angle > Math.toRadians(5.0D), "craft did not rotate");
                    helper.assertTrue(scoped[0] && bare[0], "16x player inside the rotated wall reported no collision");
                    helper.assertTrue(!scoped[1] && !bare[1], "16x player beside the rotated wall reported a collision");
                    helper.assertTrue(!scoped[2] && !bare[2], "16x player above the rotated wall reported a collision");
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 100)
    public static void lodGeneratedBoxesDoNotOverlap(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Random random = new Random(3L);
        BlockState[] decor = {Blocks.OAK_FENCE.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState(),
                Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST),
                Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.CHEST.defaultBlockState()};
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 20; z++) {
                blocks.put(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
                if (x == 0 || x == 39 || z == 0 || z == 19) {
                    for (int y = 1; y < 12; y++) blocks.put(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
                } else if (random.nextInt(5) == 0) {
                    int height = 1 + random.nextInt(4);
                    for (int y = 1; y <= height; y++) blocks.put(new BlockPos(x, y, z), decor[random.nextInt(decor.length)]);
                }
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        int[] counts = report("decorated room", craft);
        for (double scale : PLAYERS) {
            List<SubLevelShape.Box> boxes = player(craft, scale);
            List<SubLevelShape.Box> generated = new ArrayList<>();
            List<SubLevelShape.Box> exact = new ArrayList<>();
            boolean fence = false;
            for (SubLevelShape.Box box : boxes) {
                boolean whole = box.minX() == Math.floor(box.minX()) && box.minY() == Math.floor(box.minY())
                        && box.minZ() == Math.floor(box.minZ()) && box.maxX() == Math.floor(box.maxX())
                        && box.maxY() == Math.floor(box.maxY()) && box.maxZ() == Math.floor(box.maxZ());
                (whole ? generated : exact).add(box);
                if (box.maxY() - Math.floor(box.minY()) == 1.5D) fence = true;
            }
            for (int i = 0; i < generated.size(); i++) {
                for (int j = i + 1; j < generated.size(); j++) {
                    helper.assertFalse(overlap(generated.get(i), generated.get(j)),
                            "player " + scale + " generated overlapping boxes " + generated.get(i) + " and " + generated.get(j));
                }
                for (SubLevelShape.Box part : exact) {
                    boolean inCell = part.maxX() <= Math.floor(part.minX()) + 1.0D && part.maxY() <= Math.floor(part.minY()) + 1.0D
                            && part.maxZ() <= Math.floor(part.minZ()) + 1.0D;
                    helper.assertFalse(inCell && overlap(generated.get(i), part),
                            "player " + scale + " generated a box through an exact block shape " + part);
                }
            }
            helper.assertTrue(scale >= 8.0D || fence, "player " + scale + " lost the 1.5-block fence height");
        }
        helper.assertTrue(counts[0] > counts[1] && counts[1] >= counts[2], "decorated room colliders did not shrink with LOD");
        helper.succeed();
    }

    @GameTestGenerator
    public static Collection<TestFunction> lodSingleBlockWalls() {
        List<TestFunction> tests = new ArrayList<>();
        double[][] cases = {{1.0D / 8.0D, 1.0D / 8.0D, 0.0D}, {1.0D / 16.0D, 1.0D / 32.0D, 0.0D},
                {1.0D / 32.0D, 1.0D / 32.0D, 0.0D}, {1.0D / 4.0D, 1.0D / 32.0D, 0.0D},
                {1.0D, 1.0D / 16.0D, 0.0D}, {2.0D, 1.0D / 16.0D, 0.05D}, {4.0D, 1.0D / 32.0D, 0.05D},
                {1.0D / 32.0D, 1.0D, 0.0D}, {1.0D, 1.0D, 0.0D}, {16.0D, 1.0D, 0.05D}};
        for (double[] entry : cases) {
            String name = "lodwall_player_" + tag(entry[0]) + "_on_" + tag(entry[1]);
            tests.add(new TestFunction("defaultBatch", name, Pym.MOD_ID + ":" + FLOOR, 200, 0L, true,
                    helper -> wallWalk(helper, entry[0], entry[1], entry[2])));
        }
        return tests;
    }

    private static String tag(double value) {
        return value >= 1.0D ? String.valueOf((int) value) : "1_" + Math.round(1.0D / value);
    }

    private static void wallWalk(GameTestHelper helper, double personal, double craftScale, double step) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 20; z++) {
                blocks.put(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
                if (x == 30) {
                    for (int y = 1; y <= 12; y++) blocks.put(new BlockPos(x, y, z), Blocks.WHITE_WOOL.defaultBlockState());
                }
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        BlockPos o = origin(craft);
        helper.assertTrue(Pym.resize().request(craft).scaleTo(craftScale).immediate().submit().accepted(), "resize refused");
        Player walker = helper.makeMockPlayer(GameType.SURVIVAL);
        if (personal != 1.0D) helper.assertTrue(EntityScaleTracker.set(walker, personal, 0), "could not scale the player");
        if (step > 0.0D) walker.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(step);
        walker.refreshDimensions();
        double half = walker.getBbWidth() * 0.5D / craftScale;
        double face = o.getX() + 30.0D;
        double gap = Math.min(3.0D, personal / craftScale * 3.0D);
        double[] front = {Double.NEGATIVE_INFINITY};
        int[] ticks = {0};
        String label = "player " + tag(personal) + " on " + tag(craftScale);
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft, new Quaterniond(craft.logicalPose().orientation()), 0.0D))
                .thenIdle(30)
                .thenExecute(() -> clearBarriers(helper, craft))
                .thenExecute(() -> {
                    Vector3d world = craft.logicalPose().transformPosition(
                            new Vector3d(face - half - gap, o.getY() + 1.05D, o.getZ() + 10.0D));
                    walker.setPos(world.x, world.y, world.z);
                })
                .thenExecuteFor(120, () -> {
                    Vector3d forward = craft.logicalPose().transformNormal(new Vector3d(1.0D, 0.0D, 0.0D)).normalize();
                    double pace = ticks[0]++ >= 3 ? 0.1D : 0.0D;
                    walker.move(MoverType.SELF, new Vec3(forward.x * pace, -0.08D, forward.z * pace));
                    Vector3d local = craft.logicalPose().transformPositionInverse(
                            new Vector3d(walker.getX(), walker.getY(), walker.getZ()));
                    front[0] = Math.max(front[0], local.x + half - face);
                })
                .thenExecute(() -> {
                    LOGGER.info("[Pym LOD] single-block wall, {}: deepest front edge {} blocks past the wall face",
                            label, String.format(Locale.ROOT, "%.4f", front[0]));
                    helper.assertTrue(front[0] > -1.0D, label + " never reached the wall");
                    helper.assertTrue(front[0] <= 0.05D, label + " went " + front[0] + " blocks into a one-block wall");
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 300)
    public static void lodCrouchTunnel(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        BlockState topSlab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 22; z++) {
                blocks.put(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
                blocks.put(new BlockPos(x, 49, z), Blocks.STONE.defaultBlockState());
                if (z == 0 || z == 21) {
                    for (int y = 1; y < 49; y++) blocks.put(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
                } else if (z == 1 || z == 20) {
                    BlockState side = x % 2 == 0 ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.OAK_FENCE.defaultBlockState();
                    blocks.put(new BlockPos(x, 1, z), side);
                    blocks.put(new BlockPos(x, 2, z), side);
                } else {
                    if (x >= 10 && x <= 30) blocks.put(new BlockPos(x, 48, z), topSlab);
                    if (x >= 20 && x <= 30) blocks.put(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
                }
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        BlockPos o = origin(craft);
        helper.assertTrue(Pym.resize().request(craft).scaleTo(CRAFT).immediate().submit().accepted(), "resize refused");
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "pym-lod-crouch"), ClientInformation.createDefault());
        helper.assertTrue(EntityScaleTracker.set(player, 0.97D, 0), "could not scale the player");
        player.refreshDimensions();
        player.setShiftKeyDown(true);
        double[] progress = {Double.NEGATIVE_INFINITY};
        int[] ticks = {0};
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft, new Quaterniond(craft.logicalPose().orientation()), 0.0D))
                .thenIdle(30)
                .thenExecute(() -> {
                    for (int x : new int[]{5, 15, 25}) {
                        double floor = o.getY() + (x >= 10 ? 1.5D : 1.0D) + 0.001D / CRAFT;
                        AABB crouch = poseBox(craft, player, Pose.CROUCHING, o.getX() + x + 0.5D, floor, o.getZ() + 11.0D);
                        AABB stand = poseBox(craft, player, Pose.STANDING, o.getX() + x + 0.5D, floor, o.getZ() + 11.0D);
                        helper.assertFalse(probe(helper, player, crouch), "crouching does not fit the tunnel at x=" + x);
                        helper.assertFalse(probe(helper, null, crouch), "exact geometry rejects crouching at x=" + x);
                        helper.assertTrue(probe(helper, player, stand), "standing fits a tunnel lower than the player at x=" + x);
                    }
                    List<SubLevelShape.Box> boxes = SubLevelIndexedCollision.outlines(craft, CRAFT, player.getBbWidth(),
                            0.6D * 0.97D, 1.8D * 0.97D, player.maxUpStep());
                    for (int y = 2; y < 47; y += 4) {
                        for (int z = 2; z < 20; z++) {
                            helper.assertFalse(covered(boxes, o, 25, y, z), "LOD filled the tunnel at y=" + y + " z=" + z);
                        }
                    }
                    Vector3d world = craft.logicalPose().transformPosition(
                            new Vector3d(o.getX() + 4.0D, o.getY() + 1.02D, o.getZ() + 11.0D));
                    player.setPos(world.x, world.y, world.z);
                    player.setPose(Pose.CROUCHING);
                })
                .thenExecuteFor(120, () -> {
                    Vector3d forward = craft.logicalPose().transformNormal(new Vector3d(1.0D, 0.0D, 0.0D)).normalize();
                    double pace = ticks[0]++ >= 3 ? 0.05D : 0.0D;
                    player.move(MoverType.SELF, new Vec3(forward.x * pace, -0.08D, forward.z * pace));
                    updatePose(player);
                    Vector3d local = craft.logicalPose().transformPositionInverse(
                            new Vector3d(player.getX(), player.getY(), player.getZ()));
                    if (local.x < o.getX() + 34.0D) progress[0] = Math.max(progress[0], local.x - o.getX());
                    helper.assertTrue(player.getPose() == Pose.CROUCHING || local.x > o.getX() + 39.0D,
                            "player stood up inside a tunnel lower than its standing height");
                })
                .thenExecute(() -> {
                    LOGGER.info("[Pym LOD] crouch tunnel: furthest point {} of 34", String.format(Locale.ROOT, "%.2f", progress[0]));
                    helper.assertTrue(progress[0] > 32.0D, "crouching player was blocked at x=" + progress[0]);
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void lodRapidPlayerScaling(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 20; z++) blocks.put(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
        }
        ServerSubLevel craft = assemble(helper, blocks);
        BlockPos o = origin(craft);
        helper.assertTrue(Pym.resize().request(craft).scaleTo(CRAFT).immediate().submit().accepted(), "resize refused");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double[] scales = {0.5D, 0.75D, 1.0D, 1.5D, 2.0D, 3.0D, 4.0D, 6.0D, 4.0D, 2.5D, 1.0D, 0.6D, 0.5D, 2.0D, 6.0D, 1.0D};
        double[] drift = {0.0D, 0.0D, 0.0D};
        int[] ticks = {0};
        Vector3d start = new Vector3d(o.getX() + 20.0D, o.getY() + 1.02D, o.getZ() + 10.0D);
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft, new Quaterniond(craft.logicalPose().orientation()), 0.0D))
                .thenIdle(30)
                .thenExecute(() -> {
                    Vector3d world = craft.logicalPose().transformPosition(new Vector3d(start));
                    player.setPos(world.x, world.y, world.z);
                })
                .thenExecuteFor(5 + scales.length * 3, () -> {
                    int tick = ticks[0]++;
                    if (tick >= 5 && (tick - 5) % 3 == 0) {
                        EntityScaleTracker.set(player, scales[(tick - 5) / 3], 0);
                        player.refreshDimensions();
                    }
                    player.move(MoverType.SELF, new Vec3(0.0D, -0.08D, 0.0D));
                    Vector3d local = craft.logicalPose().transformPositionInverse(
                            new Vector3d(player.getX(), player.getY(), player.getZ()));
                    if (tick < 4) return;
                    drift[0] = Math.max(drift[0], Math.abs(local.y - (o.getY() + 1.0D)));
                    drift[1] = Math.max(drift[1], Math.abs(local.x - start.x));
                    drift[2] = Math.max(drift[2], Math.abs(local.z - start.z));
                })
                .thenExecute(() -> {
                    LOGGER.info("[Pym LOD] rapid player scaling 1/2..6x on 1/32: max drift y={} x={} z={}",
                            String.format(Locale.ROOT, "%.4f", drift[0]), String.format(Locale.ROOT, "%.4f", drift[1]),
                            String.format(Locale.ROOT, "%.4f", drift[2]));
                    helper.assertTrue(drift[0] <= 0.05D, "player feet moved " + drift[0] + " blocks off the floor while scaling");
                    helper.assertTrue(drift[1] <= 0.05D && drift[2] <= 0.05D, "player was pushed sideways while scaling");
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void lodRapidCraftScaling(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 20; z++) {
                blocks.put(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
                if (x == 30) {
                    for (int y = 1; y <= 12; y++) blocks.put(new BlockPos(x, y, z), Blocks.WHITE_WOOL.defaultBlockState());
                }
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        BlockPos o = origin(craft);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double[] scales = {1.0D / 8.0D, 1.0D / 16.0D, 1.0D / 32.0D, 1.0D / 16.0D, 1.0D / 8.0D, 1.0D / 32.0D, 1.0D / 12.0D, 1.0D / 24.0D};
        var sequence = helper.startSequence();
        for (double scale : scales) {
            sequence = sequence
                    .thenExecute(() -> helper.assertTrue(Pym.resize().request(craft).scaleTo(scale).immediate().submit().accepted(),
                            "resize refused"))
                    .thenIdle(2)
                    .thenExecute(() -> {
                        double half = player.getBbWidth() * 0.5D / scale, gap = 0.01D / scale, side = 0.08D / scale;
                        double[][] probes = {
                                {10.0D, 1.0D + gap, 0}, {10.0D, 1.0D - gap, 1},
                                {30.0D - half - side, 1.0D + gap, 0}, {30.0D - half + side, 1.0D + gap, 1}};
                        for (double[] entry : probes) {
                            AABB box = poseBox(craft, player, Pose.STANDING, o.getX() + entry[0], o.getY() + entry[1], o.getZ() + 10.0D);
                            boolean expected = entry[2] == 1.0D;
                            helper.assertTrue(probe(helper, player, box) == expected && probe(helper, null, box) == expected,
                                    "craft at " + tag(scale) + " gave the wrong fit at local x=" + entry[0] + " y=" + entry[1]);
                        }
                    });
        }
        sequence.thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void lodExactAtOneToOne(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 20; z++) blocks.put(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
        }
        ServerSubLevel craft = assemble(helper, blocks);
        BlockPos o = origin(craft);
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft, new Quaterniond(craft.logicalPose().orientation()), 0.0D))
                .thenIdle(20)
                .thenExecute(() -> {
                    List<SubLevelShape.Box> before = SubLevelIndexedCollision.outlines(craft, 1.0D, 0.6D, 0.6D, 1.8D, 0.6D);
                    helper.assertTrue(before != null, "a 1x player on a 1x craft must use LOD0, not Sable's per-block scan");
                    helper.assertFalse(covered(before, o, 5, 1, 5), "LOD0 has a block that was never placed");
                    helper.getLevel().setBlock(o.offset(5, 1, 5), Blocks.STONE.defaultBlockState(), 3);
                    helper.assertTrue(covered(SubLevelIndexedCollision.outlines(craft, 1.0D, 0.6D, 0.6D, 1.8D, 0.6D), o, 5, 1, 5),
                            "LOD0 missed a block placed after it was built");
                    helper.getLevel().setBlock(o.offset(5, 1, 5), Blocks.AIR.defaultBlockState(), 3);
                    helper.getLevel().setBlock(o.offset(30, 0, 5), Blocks.AIR.defaultBlockState(), 3);
                    List<SubLevelShape.Box> after = SubLevelIndexedCollision.outlines(craft, 1.0D, 0.6D, 0.6D, 1.8D, 0.6D);
                    helper.assertFalse(covered(after, o, 5, 1, 5), "LOD0 kept a block that was removed");
                    helper.assertFalse(covered(after, o, 30, 0, 5), "LOD0 kept a floor block that was removed");
                    helper.assertTrue(covered(after, o, 31, 0, 5), "LOD0 lost a floor block next to an edit");
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void lodScaffoldingAtOneToOne(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 20; z++) blocks.put(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
        }
        for (int y = 1; y <= 3; y++) blocks.put(new BlockPos(10, y, 10), Blocks.SCAFFOLDING.defaultBlockState());
        ServerSubLevel craft = assemble(helper, blocks);
        BlockPos o = origin(craft);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Player giant = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(EntityScaleTracker.set(giant, 8.0D, 0), "could not scale the player");
        giant.refreshDimensions();
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft, new Quaterniond(craft.logicalPose().orientation()), 0.0D))
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(SubLevelIndexedCollision.outlines(craft, 1.0D / 16.0D, giant.getBbWidth(),
                            4.8D, 14.4D, giant.maxUpStep()) != null, "no LOD mesh for the giant");
                    for (Player who : new Player[]{player, giant}) {
                        double scale = who == player ? 1.0D : 1.0D / 16.0D;
                        Iterable<BlockPos> candidates = SubLevelIndexedCollision.candidates(craft, scale, who,
                                o.getX() + 8, o.getY(), o.getZ() + 8, o.getX() + 12, o.getY() + 4, o.getZ() + 12);
                        int found = 0;
                        for (BlockPos pos : candidates) {
                            if (pos.getX() == o.getX() + 10 && pos.getZ() == o.getZ() + 10 && pos.getY() > o.getY()) found++;
                        }
                        SubLevelIndexedCollision.reset();
                        helper.assertTrue(found == 3, "scaffolding is not a collision candidate for the "
                                + (who == player ? "1x player at LOD0" : "giant at LOD2") + ": " + found + " of 3");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void lodGiantInsideHollowCube(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 40; x++) {
            for (int y = 0; y < 40; y++) {
                for (int z = 0; z < 40; z++) {
                    if (x == 0 || y == 0 || z == 0 || x == 39 || y == 39 || z == 39) {
                        blocks.put(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
                    }
                }
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        BlockPos o = origin(craft);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(EntityScaleTracker.set(player, 16.0D, 0), "could not scale the player");
        player.refreshDimensions();
        double half = player.getBbWidth() * 0.5D;
        double[] extent = {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft, new Quaterniond(craft.logicalPose().orientation()), 0.0D))
                .thenIdle(20)
                .thenExecute(() -> {
                    clearBarriers(helper, craft);
                    Vector3d world = craft.logicalPose().transformPosition(new Vector3d(o.getX() + 20.0D, o.getY() + 1.02D, o.getZ() + 20.0D));
                    player.setPos(world.x, world.y, world.z);
                })
                .thenExecuteFor(80, () -> {
                    Vector3d x = craft.logicalPose().transformNormal(new Vector3d(1.0D, 0.0D, 0.0D)).normalize();
                    Vector3d z = craft.logicalPose().transformNormal(new Vector3d(0.0D, 0.0D, 1.0D)).normalize();
                    player.move(MoverType.SELF, new Vec3(x.x * 0.4D + z.x * 0.25D, -0.08D, x.z * 0.4D + z.z * 0.25D));
                    Vector3d local = craft.logicalPose().transformPositionInverse(new Vector3d(player.getX(), player.getY(), player.getZ()));
                    extent[0] = Math.max(extent[0], Math.max(local.x - o.getX(), local.z - o.getZ()) + half);
                    extent[1] = Math.min(extent[1], local.y - o.getY());
                    extent[2] = Math.min(extent[2], Math.min(local.x - o.getX(), local.z - o.getZ()) - half);
                })
                .thenExecute(() -> {
                    LOGGER.info("[Pym LOD] 16x player in a 1x hollow cube: furthest edge {} of 39, lowest feet {}",
                            String.format(Locale.ROOT, "%.4f", extent[0]), String.format(Locale.ROOT, "%.4f", extent[1]));
                    helper.assertTrue(extent[0] > 38.5D, "the giant never reached the corner");
                    helper.assertTrue(extent[0] <= 39.05D, "the giant pushed " + (extent[0] - 39.0D) + " into the wall");
                    helper.assertTrue(extent[2] >= 0.95D, "the giant left the cube through a wall");
                    helper.assertTrue(extent[1] >= 0.95D, "the giant sank into the floor to " + extent[1]);
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void lodEmbeddedPlayerIsPushedOut(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 20; z++) blocks.put(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
        }
        for (int x = 18; x < 21; x++) {
            for (int y = 1; y < 4; y++) {
                for (int z = 8; z < 11; z++) {
                    blocks.put(new BlockPos(x, y, z), (x + y + z) % 2 == 0 ? Blocks.STONE.defaultBlockState()
                            : Blocks.OAK_SLAB.defaultBlockState());
                }
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        BlockPos o = origin(craft);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        AABB[] last = {null};
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft, new Quaterniond(craft.logicalPose().orientation()), 0.0D))
                .thenIdle(20)
                .thenExecute(() -> {
                    Vector3d world = craft.logicalPose().transformPosition(new Vector3d(o.getX() + 19.5D, o.getY() + 1.5D, o.getZ() + 9.5D));
                    player.setPos(world.x, world.y, world.z);
                })
                .thenExecuteFor(60, () -> {
                    player.move(MoverType.SELF, new Vec3(0.0D, -0.08D, 0.0D));
                    last[0] = player.getBoundingBox().deflate(0.01D);
                })
                .thenExecute(() -> {
                    Vector3d local = craft.logicalPose().transformPositionInverse(new Vector3d(player.getX(), player.getY(), player.getZ()));
                    LOGGER.info("[Pym LOD] embedded 1x player ended at local ({}, {}, {})",
                            String.format(Locale.ROOT, "%.3f", local.x - o.getX()), String.format(Locale.ROOT, "%.3f", local.y - o.getY()),
                            String.format(Locale.ROOT, "%.3f", local.z - o.getZ()));
                    helper.assertTrue(Double.isFinite(local.x) && Double.isFinite(local.y) && Double.isFinite(local.z), "embedded player position is not finite");
                    helper.assertFalse(CanFallAtleastHelper.canFallAtleastWithSubLevels(helper.getLevel(), last[0]) != null,
                            "embedded player was never pushed out of the block cluster");
                })
                .thenSucceed();
    }

    @GameTestGenerator
    public static Collection<TestFunction> lodResizeOnFloor() {
        List<TestFunction> tests = new ArrayList<>();
        double[] crafts = {1.0D / 32.0D, 1.0D / 16.0D, 1.0D};
        String[] modes = {"grow", "shrink", "growFall", "shrinkFall", "shrinkEdge"};
        for (double craftScale : crafts) {
            for (String mode : modes) {
                for (boolean tilt : new boolean[]{false, true}) {
                    if (tilt && mode.endsWith("Edge")) continue;
                    String name = "lodresize_" + mode + "_" + tag(craftScale).replace('/', '_') + (tilt ? "_tilt" : "");
                    tests.add(new TestFunction("pym:resize", name, Pym.MOD_ID + ":" + FLOOR, 300, 0L, true,
                            helper -> resizeOnFloor(helper, craftScale, mode, tilt)));
                }
            }
        }
        return tests;
    }

    private static void resizeOnFloor(GameTestHelper helper, double craftScale, String mode, boolean tilt) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 20; z++) blocks.put(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
        }
        ServerSubLevel craft = assemble(helper, blocks);
        BlockPos o = origin(craft);
        if (craftScale != 1.0D) {
            helper.assertTrue(Pym.resize().request(craft).scaleTo(craftScale).immediate().submit().accepted(), "resize refused");
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        boolean grow = mode.startsWith("grow"), edge = mode.endsWith("Edge"), fall = mode.endsWith("Fall");
        double from = grow ? 1.0D / 32.0D : 16.0D, to = grow ? 16.0D : 1.0D / 32.0D;
        int steps = 24;
        double[] stats = {Double.POSITIVE_INFINITY, Double.NaN, 0.0D, 0.0D, 0.0D};
        double[] velocity = {0.0D};
        int[] ticks = {0};
        Vector3d start = new Vector3d();
        Quaterniond orientation = new Quaterniond(craft.logicalPose().orientation());
        if (tilt) orientation.rotateLocalZ(Math.toRadians(12.0D));
        String label = mode + " " + tag(from) + "->" + tag(to) + " on " + tag(craftScale) + (tilt ? " tilted" : "");
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft, orientation, 0.0D))
                .thenIdle(40)
                .thenExecute(() -> {
                    clearBarriers(helper, craft);
                    EntityScaleTracker.set(player, from, 0);
                    player.refreshDimensions();
                    double half = player.getBbWidth() * 0.5D / craftScale;
                    double lift = fall ? 2.0D / craftScale : 0.02D * Math.min(1.0D, from) / craftScale;
                    start.set(o.getX() + (edge ? 40.0D + half * 0.5D : 20.0D), o.getY() + 1.0D + lift, o.getZ() + 10.0D);
                    Vector3d world = craft.logicalPose().transformPosition(new Vector3d(start));
                    player.setPos(world.x, world.y, world.z);
                })
                .thenExecuteFor(steps + 60, () -> {
                    int tick = ticks[0]++;
                    if (tick < steps) {
                        long begin = System.nanoTime();
                        EntityScaleTracker.set(player, from * Math.pow(to / from, tick / (double) (steps - 1)), 0);
                        player.refreshDimensions();
                        stats[4] += System.nanoTime() - begin;
                    }
                    velocity[0] = player.onGround() && velocity[0] <= 0.0D ? -0.08D : Math.max(-3.92D, (velocity[0] - 0.08D) * 0.98D);
                    player.move(MoverType.SELF, new Vec3(0.0D, velocity[0], 0.0D));
                    if (player.onGround()) velocity[0] = 0.0D;
                    Vector3d local = craft.logicalPose().transformPositionInverse(new Vector3d(player.getX(), player.getY(), player.getZ()));
                    double feet = (local.y - (o.getY() + 1.0D)) * craftScale;
                    stats[0] = Math.min(stats[0], feet);
                    stats[1] = feet;
                    stats[2] = Math.max(stats[2], Math.hypot(local.x - start.x, local.z - start.z) * craftScale);
                    if (local.y < o.getY() && stats[3] == 0.0D) stats[3] = tick + 1;
                })
                .thenExecute(() -> {
                    LOGGER.info("[Pym RESIZE] {}: lowest feet {} final {} sideways {} (world) resize {} us",
                            label, String.format(Locale.ROOT, "%.4f", stats[0]), String.format(Locale.ROOT, "%.4f", stats[1]),
                            String.format(Locale.ROOT, "%.4f", stats[2]), (long) (stats[4] / 1000.0D));
                    helper.assertTrue(stats[3] == 0.0D, label + ": the player went through the floor on tick " + (int) stats[3]);
                    helper.assertTrue(player.onGround(), label + ": the player is not standing on the craft");
                    helper.assertTrue(stats[1] >= -0.01D, label + ": the player ended " + stats[1] + " inside the floor");
                    if (!tilt) {
                        helper.assertTrue(stats[0] >= -0.01D && stats[1] <= 0.01D,
                                label + ": the feet left the floor surface, lowest " + stats[0] + " final " + stats[1]);
                    }
                    if (edge) {
                        helper.assertTrue(stats[2] <= from * 0.3D,
                                label + ": footing moved " + stats[2] + ", beyond the old footprint");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void lodZeroMatchesSableShapes(GameTestHelper helper) {
        BlockState[] palette = {
                Blocks.STONE.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Blocks.OAK_STAIRS.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.COBBLESTONE_WALL.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState(),
                Blocks.WHITE_CARPET.defaultBlockState(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3),
                Blocks.SCAFFOLDING.defaultBlockState(), Blocks.POWDER_SNOW.defaultBlockState(),
                Blocks.CHEST.defaultBlockState(), Blocks.HOPPER.defaultBlockState(), Blocks.LANTERN.defaultBlockState(),
                Blocks.COBWEB.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState(), Blocks.HONEY_BLOCK.defaultBlockState(),
                Blocks.DIRT_PATH.defaultBlockState(), Blocks.ANVIL.defaultBlockState(), Blocks.CHAIN.defaultBlockState(),
                Blocks.GLASS.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(), Blocks.SLIME_BLOCK.defaultBlockState()};
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Random random = new Random(7L);
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 16; z++) {
                blocks.put(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
                for (int y = 1; y < 6; y++) {
                    if (random.nextInt(3) == 0) continue;
                    blocks.put(new BlockPos(x, y, z), random.nextInt(3) == 0 ? Blocks.STONE.defaultBlockState()
                            : palette[random.nextInt(palette.length)]);
                }
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft, new Quaterniond(craft.logicalPose().orientation()), 0.0D))
                .thenIdle(20)
                .thenExecute(() -> {
                    var level = helper.getLevel();
                    BoundingBox3ic plot = craft.getPlot().getBoundingBox();
                    SubLevelIndexedCollision.reset();
                    Iterable<BlockPos> found = SubLevelIndexedCollision.candidates(craft, 1.0D, player,
                            plot.minX(), plot.minY(), plot.minZ(), plot.maxX(), plot.maxY(), plot.maxZ());
                    helper.assertTrue(found != null && SubLevelIndexedCollision.lod() == 0, "a 1x player on a 1x craft is not on LOD0");
                    List<AABB> merged = new ArrayList<>();
                    Map<Long, Boolean> candidates = new LinkedHashMap<>();
                    for (BlockPos pos : found) {
                        VoxelShape proxy = SubLevelIndexedCollision.shape(pos);
                        candidates.put(pos.asLong(), proxy == null);
                        if (proxy != null) merged.add(proxy.bounds().move(pos));
                    }
                    SubLevelIndexedCollision.reset();
                    int full = 0, partial = 0, empty = 0;
                    for (BlockPos pos : BlockPos.betweenClosed(plot.minX(), plot.minY(), plot.minZ(), plot.maxX(), plot.maxY(), plot.maxZ())) {
                        BlockState state = level.getBlockState(pos);
                        VoxelShape original = state.getCollisionShape(level, pos);
                        Vec3 centre = Vec3.atCenterOf(pos);
                        boolean inside = false;
                        for (AABB box : merged) inside |= box.contains(centre);
                        boolean scaffolding = state.is(Blocks.SCAFFOLDING);
                        if (!scaffolding && Block.isShapeFullBlock(original)) {
                            full++;
                            helper.assertTrue(inside || Boolean.TRUE.equals(candidates.get(pos.asLong())),
                                    "LOD0 lost the full block " + state + " at " + pos);
                        } else {
                            if (original.isEmpty() && !scaffolding) empty++;
                            else partial++;
                            helper.assertFalse(inside, "LOD0 merged " + state + " at " + pos + " into a full box");
                            if (!state.isAir() && (!original.isEmpty() || scaffolding)) {
                                helper.assertTrue(Boolean.TRUE.equals(candidates.get(pos.asLong())),
                                        "LOD0 does not hand " + state + " at " + pos + " to Sable's own shape");
                            }
                        }
                    }
                    LOGGER.info("[Pym LOD] LOD0 vs Sable shapes: {} full, {} partial or entity-dependent, {} empty cells checked, {} merged boxes",
                            full, partial, empty, merged.size());
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 300, batch = "pym_worst_case")
    public static void lodWorstCaseSectionBuild(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int i = 0; i < 113; i++) {
            blocks.put(new BlockPos(0, i, 0), Blocks.STONE.defaultBlockState());
            for (int y = 0; y < 8; y++) {
                blocks.put(new BlockPos(0, y * 16, i), Blocks.STONE.defaultBlockState());
                for (int z = 0; z < 8; z++) blocks.put(new BlockPos(i, y * 16, z * 16), Blocks.STONE.defaultBlockState());
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        helper.startSequence()
                .thenExecute(() -> {
                    long[] times = new long[3];
                    List<SubLevelShape.Box> boxes = null;
                    for (int i = 0; i < times.length; i++) {
                        PlotShapeCache.invalidate(craft);
                        long begin = System.nanoTime();
                        boxes = SubLevelIndexedCollision.outlines(craft, 1.0D, 0.6D, 0.6D, 1.8D, 0.6D);
                        times[i] = (System.nanoTime() - begin) / 1000L;
                    }
                    BoundingBox3ic plot = craft.getPlot().getBoundingBox();
                    int occupied = 0;
                    for (int sx = plot.minX() >> 4; sx <= plot.maxX() >> 4; sx++) {
                        for (int sz = plot.minZ() >> 4; sz <= plot.maxZ() >> 4; sz++) {
                            var sections = helper.getLevel().getChunk(sx, sz).getSections();
                            for (int sy = plot.minY() >> 4; sy <= plot.maxY() >> 4; sy++) {
                                int index = helper.getLevel().getSectionIndexFromSectionY(sy);
                                if (index >= 0 && index < sections.length && !sections[index].hasOnlyAir()) occupied++;
                            }
                        }
                    }
                    helper.assertTrue(boxes != null && occupied == 512, "LOD0 was not built for " + occupied + " occupied sections in " + plot);
                    BlockPos edit = new BlockPos(plot.minX() + 40, plot.minY() + 40, plot.minZ() + 40);
                    PlotShapeCache.get(craft);
                    helper.getLevel().setBlock(edit, Blocks.STONE.defaultBlockState(), 3);
                    long begin = System.nanoTime();
                    List<SubLevelShape.Box> edited = SubLevelIndexedCollision.outlines(craft, 1.0D, 0.6D, 0.6D, 1.8D, 0.6D);
                    long rebuild = (System.nanoTime() - begin) / 1000L;
                    helper.getLevel().setBlock(edit, Blocks.AIR.defaultBlockState(), 3);
                    helper.assertTrue(edited != null && edited.size() == boxes.size() + 1, "LOD0 missed an edit on the 512-section craft");
                    LOGGER.info("[Pym LOD] worst-case LOD0 build, {} sections each scanned in full, {} boxes: {} / {} / {} us cold, one-section edit {} us",
                            occupied, boxes.size(), times[0], times[1], times[2], rebuild);
                })
                .thenSucceed();
    }

    @GameTest(template = FLOOR, timeoutTicks = 300)
    public static void lodExactWhenPhysicsMeshCoarsens(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 20; z++) {
                for (int y = 0; y < 20; y++) {
                    blocks.put(new BlockPos(x, y, z), y < 19 || x % 3 != 0 ? Blocks.STONE.defaultBlockState()
                            : Blocks.OAK_SLAB.defaultBlockState());
                }
            }
        }
        ServerSubLevel craft = assemble(helper, blocks);
        BlockPos o = origin(craft);
        helper.assertTrue(Pym.resize().request(craft).scaleTo(2.0D).immediate().submit().accepted(), "resize refused");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double[] lowest = {Double.POSITIVE_INFINITY};
        long[] stats = {-1L, 0L, 0L};
        String[] level = {""};
        helper.startSequence()
                .thenExecuteAfter(1, () -> pin(helper, craft, new Quaterniond(craft.logicalPose().orientation()), 0.0D))
                .thenIdle(30)
                .thenExecute(() -> {
                    clearBarriers(helper, craft);
                    var physics = PlotShapeCache.get(craft);
                    helper.assertTrue(physics != null && !physics.level().exact(),
                            "the physics mesh did not coarsen, so this test proves nothing: " + (physics == null ? null : physics.level()));
                    level[0] = physics.level().toString();
                    PlotShapeCache.invalidate(craft);
                    long begin = System.nanoTime();
                    List<SubLevelShape.Box> boxes = SubLevelIndexedCollision.outlines(craft, 2.0D, 0.6D, 0.6D, 1.8D, 0.6D);
                    stats[1] = (System.nanoTime() - begin) / 1000L;
                    stats[0] = boxes == null ? -1L : boxes.size();
                    Vector3d world = craft.logicalPose().transformPosition(new Vector3d(o.getX() + 21.5D, o.getY() + 20.5D, o.getZ() + 2.5D));
                    player.setPos(world.x, world.y, world.z);
                })
                .thenExecuteFor(40, () -> {
                    Vector3d z = craft.logicalPose().transformNormal(new Vector3d(0.0D, 0.0D, 1.0D)).normalize();
                    long begin = System.nanoTime();
                    player.move(MoverType.SELF, new Vec3(z.x * 0.2D, -0.08D, z.z * 0.2D));
                    stats[2] += System.nanoTime() - begin;
                    Vector3d local = craft.logicalPose().transformPositionInverse(new Vector3d(player.getX(), player.getY(), player.getZ()));
                    lowest[0] = Math.min(lowest[0], local.y - o.getY());
                })
                .thenExecute(() -> {
                    LOGGER.info("[Pym LOD] 40x20x20 craft at 2x with a {} physics mesh: {} in {} us, 40 moves took {} us, lowest y {}",
                            level[0], stats[0] < 0L ? "Original" : "LOD0 " + stats[0] + " boxes built", stats[1], stats[2] / 1000L,
                            String.format(Locale.ROOT, "%.4f", lowest[0]));
                    helper.assertTrue(stats[0] >= 0L, "LOD0 fell back to Original because the physics mesh is " + level[0]);
                    helper.assertTrue(lowest[0] > 19.45D && lowest[0] < 19.55D,
                            "a player did not stay on the slab row of the coarsened craft, lowest y=" + lowest[0]);
                })
                .thenSucceed();
    }

    private static void clearBarriers(GameTestHelper helper, ServerSubLevel craft) {
        var bounds = craft.boundingBox();
        BlockPos.betweenClosed(Mth.floor(bounds.minX()) - 12, Mth.floor(bounds.minY()) - 2, Mth.floor(bounds.minZ()) - 12,
                Mth.floor(bounds.maxX()) + 12, Mth.floor(bounds.maxY()) + 32, Mth.floor(bounds.maxZ()) + 12).forEach(pos -> {
            if (helper.getLevel().getBlockState(pos).is(Blocks.BARRIER)) {
                helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        });
    }

    private static AABB poseBox(ServerSubLevel craft, Player player, Pose pose, double x, double y, double z) {
        Vector3d feet = craft.lastPose().transformPosition(new Vector3d(x, y, z));
        var size = player.getDimensions(pose);
        double half = size.width() * 0.5D;
        return new AABB(feet.x - half, feet.y, feet.z - half, feet.x + half, feet.y + size.height(), feet.z + half);
    }

    private static void updatePose(Player player) {
        try {
            Method update = Player.class.getDeclaredMethod("updatePlayerPose");
            update.setAccessible(true);
            update.invoke(player);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean overlap(SubLevelShape.Box a, SubLevelShape.Box b) {
        return Math.min(a.maxX(), b.maxX()) - Math.max(a.minX(), b.minX()) > 1.0E-6
                && Math.min(a.maxY(), b.maxY()) - Math.max(a.minY(), b.minY()) > 1.0E-6
                && Math.min(a.maxZ(), b.maxZ()) - Math.max(a.minZ(), b.minZ()) > 1.0E-6;
    }

    private static boolean probe(GameTestHelper helper, Player player, AABB box) {
        return player == null
                ? CanFallAtleastHelper.canFallAtleastWithSubLevels(helper.getLevel(), box) != null
                : SubLevelIndexedCollision.probing(player,
                        () -> CanFallAtleastHelper.canFallAtleastWithSubLevels(helper.getLevel(), box) != null);
    }

    private static int[] report(String name, ServerSubLevel craft) {
        int[] counts = new int[PLAYERS.length];
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < PLAYERS.length; i++) {
            long start = System.nanoTime();
            counts[i] = player(craft, PLAYERS[i]).size();
            long micros = (System.nanoTime() - start) / 1000L;
            double reach = 0.6D * PLAYERS[i] / CRAFT;
            String lod = PLAYERS[i] >= 16.0D ? "LOD4" : reach >= 256.0D ? "LOD3" : reach >= 64.0D ? "LOD2" : reach >= 16.0D ? "LOD1" : "LOD0";
            line.append(String.format(Locale.ROOT, " | %sx %s %d colliders (%d us)", PLAYERS[i] < 1.0D ? "1/2" : String.valueOf((int) PLAYERS[i]), lod, counts[i], micros));
        }
        int stepless = SubLevelIndexedCollision.outlines(craft, CRAFT, 0.6D * 16.0D, 0.6D * 16.0D, 1.8D * 16.0D, 0.0D).size();
        int vanilla = SubLevelIndexedCollision.outlines(craft, CRAFT, 0.6D * 16.0D, 0.6D * 16.0D, 1.8D * 16.0D, 0.6D).size();
        LOGGER.info("[Pym LOD] {} on a 1/32 craft:{} | 16x with 0.6 step {} | 16x stepless LOD3 {}", name, line, vanilla, stepless);
        return counts;
    }

    private static List<SubLevelShape.Box> player(ServerSubLevel craft, double scale) {
        List<SubLevelShape.Box> boxes = SubLevelIndexedCollision.outlines(craft, CRAFT, 0.6D * scale, 0.6D * scale, 1.8D * scale, 0.6D * scale);
        return boxes == null ? List.of() : boxes;
    }

    private static boolean covered(List<SubLevelShape.Box> boxes, BlockPos origin, int x, int y, int z) {
        double cx = origin.getX() + x + 0.5D, cy = origin.getY() + y + 0.5D, cz = origin.getZ() + z + 0.5D;
        for (SubLevelShape.Box box : boxes) {
            if (cx > box.minX() && cx < box.maxX() && cy > box.minY() && cy < box.maxY()
                    && cz > box.minZ() && cz < box.maxZ()) return true;
        }
        return false;
    }

    private static BlockPos origin(ServerSubLevel craft) {
        BoundingBox3ic plot = craft.getPlot().getBoundingBox();
        return new BlockPos(plot.minX(), plot.minY(), plot.minZ());
    }

    private static ServerSubLevel assemble(GameTestHelper helper, Map<BlockPos, BlockState> blocks) {
        List<BlockPos> placed = new ArrayList<>();
        BlockPos min = null, max = null;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos relative = entry.getKey().offset(1, 2, 1);
            helper.setBlock(relative, entry.getValue());
            BlockPos absolute = helper.absolutePos(relative);
            placed.add(absolute);
            min = min == null ? absolute : BlockPos.min(min, absolute);
            max = max == null ? absolute : BlockPos.max(max, absolute);
        }
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(helper.getLevel(), min, placed, new BoundingBox3i(min, max));
        helper.assertTrue(subLevel != null && !subLevel.isRemoved(), "Sable did not assemble the craft");
        BoundingBox3ic plot = subLevel.getPlot().getBoundingBox();
        helper.assertTrue(plot.maxX() - plot.minX() == max.getX() - min.getX()
                        && plot.maxY() - plot.minY() == max.getY() - min.getY()
                        && plot.maxZ() - plot.minZ() == max.getZ() - min.getZ(),
                "plot bounds do not match the assembled blocks");
        return subLevel;
    }

    private static void pin(GameTestHelper helper, ServerSubLevel craft, Quaterniond orientation, double rise) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        helper.assertTrue(container != null, "no sublevel container");
        BoundingBox3ic plot = craft.getPlot().getBoundingBox();
        Vector3d anchor = new Vector3d((plot.minX() + plot.maxX() + 1) * 0.5D,
                (plot.minY() + plot.maxY() + 1) * 0.5D, (plot.minZ() + plot.maxZ() + 1) * 0.5D);
        Vector3d world = craft.logicalPose().transformPosition(new Vector3d(anchor)).add(0.0D, rise, 0.0D);
        helper.assertTrue(container.physicsSystem().getPipeline().addConstraint(
                craft, null, new FixedConstraintConfiguration(anchor, world, orientation)) != null, "could not pin the craft");
    }

    private PymLodGameTests() {}
}
