package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.network.PocketKnifeCutPayload;
import com.misterblusky9.pym.api.Pym;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllKeys;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.index.SimSoundEvents;
import dev.simulated_team.simulated.util.SimColors;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = PocketSized.MOD_ID, value = Dist.CLIENT)
public final class PocketKnifeCutPreview {
    private static final int CUT_COPPER = 0xFF8A3D;
    private static final int TEXT_GREY = 0xC8C8C8;
    private static final long SCAN_LIMIT = 32768L;
    private static final RenderType GLUE = PonderRenderTypes.outlineTranslucent(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "textures/misc/pocket_knife/cut_glue.png"), false);
    private static final List<int[]> faces = new ArrayList<>();
    private static final List<double[]> dashes = new ArrayList<>();
    private static final List<double[]> gaps = new ArrayList<>();
    private static final double EDGE_TOLERANCE = 1.0E-3D;
    private static AABB scanned;
    private static long scannedAt;
    private static int tint = CUT_COPPER;
    private static UUID sublevel;
    private static BlockPos selected;
    private static BlockPos end;
    private static BlockPos anchor;
    private static InteractionHand hand;
    private static boolean locked;
    private static boolean clickHeld;
    private static final int[] offsets = new int[6];
    private static final double FRAME_HALF_WIDTH = 1.0D / 32.0D;
    private static AABB frame;
    private static AABB shown;
    private static BlockPos frameReference;
    private static boolean frameChases;
    private static int frameTint = CUT_COPPER;

    @SubscribeEvent
    public static void onAttackInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || selected == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || hand == null
                || !player.getItemInHand(hand).is(ModItems.POCKET_KNIFE.get())) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent
    public static void onInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null
                || !player.getItemInHand(event.getHand()).is(ModItems.POCKET_KNIFE.get())) return;
        if (clickHeld) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (selected != null && player.isShiftKeyDown()) {
            clear();
            clickHeld = true;
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (selected != null && hand == event.getHand()) {
            if (!locked) {
                if (updateEnd(minecraft)) {
                    locked = true;
                    SimSoundEvents.HONEY_ADDED.playAt(player.level(), end, 0.5F, 0.95F, false);
                    player.level().playSound(player, end, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75F, 1.0F);
                    player.swing(hand);
                }
                clickHeld = true;
                event.setCanceled(true);
                event.setSwingHand(false);
                return;
            }
            AABB box = bounds();
            if (box != null && slices(box, player)) {
                cut(player, event.getHand(), box);
                clickHeld = true;
                event.setCanceled(true);
                event.setSwingHand(false);
                return;
            }
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit)) return;
        SubLevel target = hit.getType() == HitResult.Type.BLOCK
                ? Sable.HELPER.getContaining(minecraft.level, hit.getBlockPos()) : null;
        if (target == null && !(AllKeys.altDown() && hit.getType() == HitResult.Type.MISS)) return;
        if (target != null && (target.isRemoved() || !Pym.scale().isSettled(target)
                || CrossScaleWelds.weldNear(minecraft.level, hit.getLocation()) != null)) return;
        clear();
        selected = hit.getBlockPos().immutable();
        end = selected;
        anchor = target == null ? null : selected;
        sublevel = target == null ? null : target.getUniqueId();
        hand = event.getHand();
        locked = false;
        clickHeld = true;
        SimSoundEvents.HONEY_ADDED.playAt(player.level(), selected, 0.5F, 0.95F, false);
        player.level().playSound(player, selected, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75F, 1.0F);
        player.swing(hand);
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    private static void cut(LocalPlayer player, InteractionHand used, AABB box) {
        if (sublevel == null || anchor == null || hand != used) return;
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1);
        PacketDistributor.sendToServer(new PocketKnifeCutPayload(sublevel, anchor, hand, min, max));
        player.swing(hand);
        clear();
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (selected == null || !locked || !AllKeys.ctrlDown()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || minecraft.level == null
                || !minecraft.player.getItemInHand(hand).is(ModItems.POCKET_KNIFE.get())) return;
        event.setCanceled(true);
        AABB box = bounds();
        if (box == null) return;
        Direction face = hoveredFace(box, minecraft.player);
        if (face == null) return;
        int delta = (int) event.getScrollDeltaY();
        if (delta == 0) return;
        Vec3 eye = minecraft.player.getEyePosition();
        SubLevel owner = Sable.HELPER.getContaining(minecraft.level, anchor);
        if (owner != null) eye = owner.logicalPose().transformPositionInverse(eye);
        if (box.contains(eye)) delta = -delta;
        int slot = face.get3DDataValue();
        offsets[slot] -= delta;
        AABB resized = bounds();
        if (resized == null) offsets[slot] += delta;
    }

    private static boolean slices(AABB box, LocalPlayer player) {
        SubLevel source = anchor == null ? null : Sable.HELPER.getContaining(player.level(), anchor);
        if (source == null) return false;
        Vec3 eye = player.getEyePosition();
        Vector3d local = source.logicalPose().transformPositionInverse(new Vector3d(eye.x, eye.y, eye.z));
        Vector3d nearest = new Vector3d(
                Mth.clamp(local.x, box.minX, box.maxX),
                Mth.clamp(local.y, box.minY, box.maxY),
                Mth.clamp(local.z, box.minZ, box.maxZ));
        source.logicalPose().transformPosition(nearest);
        double reach = player.blockInteractionRange() + 1.0D;
        if (eye.distanceToSqr(nearest.x, nearest.y, nearest.z) > reach * reach) return false;
        Vec3 target = eye.add(player.getLookAngle().scale(player.blockInteractionRange() * 5.0D));
        Vector3d localTarget = source.logicalPose().transformPositionInverse(new Vector3d(target.x, target.y, target.z));
        Vec3 from = new Vec3(local.x, local.y, local.z);
        Vec3 to = new Vec3(localTarget.x, localTarget.y, localTarget.z);
        AABB forgiving = box.inflate(0.125D);
        return forgiving.contains(from) || forgiving.clip(from, to).isPresent();
    }

    private static Direction hoveredFace(AABB bb, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = eye.add(player.getViewVector(1).scale(player.blockInteractionRange() * 5));
        SubLevel source = anchor == null ? null : Sable.HELPER.getContaining(player.level(), anchor);
        if (source != null) {
            eye = source.logicalPose().transformPositionInverse(eye);
            target = source.logicalPose().transformPositionInverse(target);
        }
        if (bb.contains(eye)) {
            Vec3 outside = eye.add(target.subtract(eye).normalize().scale(bb.getXsize() + bb.getYsize() + bb.getZsize()));
            target = eye;
            eye = outside;
        }
        var hit = bb.clip(eye, target);
        if (hit.isEmpty()) return null;
        Vec3 pos = hit.get();
        double tolerance = 0.001D;
        if (Math.abs(pos.x - bb.minX) < tolerance) return Direction.WEST;
        if (Math.abs(pos.x - bb.maxX) < tolerance) return Direction.EAST;
        if (Math.abs(pos.y - bb.minY) < tolerance) return Direction.DOWN;
        if (Math.abs(pos.y - bb.maxY) < tolerance) return Direction.UP;
        if (Math.abs(pos.z - bb.minZ) < tolerance) return Direction.NORTH;
        if (Math.abs(pos.z - bb.maxZ) < tolerance) return Direction.SOUTH;
        return null;
    }

    private static AABB bounds() {
        if (selected == null || end == null) return null;
        AABB original = AABB.encapsulatingFullBlocks(selected, end);
        double x0 = original.minX - offsets[Direction.WEST.get3DDataValue()];
        double y0 = original.minY - offsets[Direction.DOWN.get3DDataValue()];
        double z0 = original.minZ - offsets[Direction.NORTH.get3DDataValue()];
        double x1 = original.maxX + offsets[Direction.EAST.get3DDataValue()];
        double y1 = original.maxY + offsets[Direction.UP.get3DDataValue()];
        double z1 = original.maxZ + offsets[Direction.SOUTH.get3DDataValue()];
        return x0 >= x1 || y0 >= y1 || z0 >= z1 ? null : new AABB(x0, y0, z0, x1, y1, z1);
    }

    private static boolean updateEnd(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit)) return false;
        if (hit.getType() == HitResult.Type.MISS) {
            if (!AllKeys.altDown()) return false;
            if (sublevel == null) {
                end = hit.getBlockPos().immutable();
                return false;
            }
            SubLevel source = Sable.HELPER.getContaining(minecraft.level, anchor);
            if (source == null) return false;
            Vector3d local = new Vector3d(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
            source.logicalPose().transformPositionInverse(local);
            end = BlockPos.containing(local.x, local.y, local.z);
            return true;
        }
        SubLevel target = Sable.HELPER.getContaining(minecraft.level, hit.getBlockPos());
        if (target == null || target.isRemoved() || !Pym.scale().isSettled(target)) return false;
        if (sublevel == null) {
            Vector3d local = new Vector3d(selected.getX() + 0.5D, selected.getY() + 0.5D, selected.getZ() + 0.5D);
            target.logicalPose().transformPositionInverse(local);
            selected = BlockPos.containing(local.x, local.y, local.z);
            anchor = hit.getBlockPos().immutable();
            sublevel = target.getUniqueId();
        }
        if (!sublevel.equals(target.getUniqueId())) return false;
        end = hit.getBlockPos().immutable();
        return true;
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!minecraft.options.keyUse.isDown()) clickHeld = false;
        frame = null;
        if (player == null || minecraft.level == null || minecraft.screen != null) {
            clear();
            return;
        }
        if (selected == null) {
            if (AllKeys.altDown() && !player.isShiftKeyDown()
                    && (player.getMainHandItem().is(ModItems.POCKET_KNIFE.get())
                    || player.getOffhandItem().is(ModItems.POCKET_KNIFE.get()))
                    && minecraft.hitResult instanceof BlockHitResult hit) {
                showFrame(new AABB(hit.getBlockPos()), hit.getBlockPos(), false, false);
            }
            return;
        }
        if (!player.getItemInHand(hand).is(ModItems.POCKET_KNIFE.get())) {
            InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (!player.getItemInHand(other).is(ModItems.POCKET_KNIFE.get())) {
                clear();
                return;
            }
            hand = other;
        }
        if (!locked) updateEnd(minecraft);
        SubLevel source = anchor == null ? null : Sable.HELPER.getContaining(minecraft.level, anchor);
        if (sublevel != null && (source == null || source.isRemoved() || !sublevel.equals(source.getUniqueId()))) {
            clear();
            return;
        }
        AABB box = bounds();
        if (box == null) return;
        boolean invalid = source != null && !Pym.scale().isSettled(source);
        showFrame(box, anchor == null ? selected : anchor, locked,
                invalid || player.isShiftKeyDown());
        tint = player.isShiftKeyDown() || invalid ? SimColors.DISCARDABLE_ORANGE : CUT_COPPER;
        if (invalid) CreateLang.text("Cannot cut while resizing").color(TEXT_GREY).sendStatus(player);
        else if (player.isShiftKeyDown()) CreateLang.translate("super_glue.click_to_discard").color(TEXT_GREY).sendStatus(player);
        else if (!locked) CreateLang.text("Click to confirm selection").color(TEXT_GREY).sendStatus(player);
        else CreateLang.text("Click to confirm slice").color(TEXT_GREY).sendStatus(player);
    }

    private static void showFrame(AABB box, BlockPos reference, boolean locked, boolean invalid) {
        frame = box;
        frameReference = reference;
        frameChases = locked;
        frameTint = invalid ? SimColors.DISCARDABLE_ORANGE : CUT_COPPER;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        AABB box = selected == null || sublevel == null || anchor == null ? null : bounds();
        if (box != null) refresh(minecraft.level, box);

        if (frame == null) {
            shown = null;
        } else {
            double step = 1.0D - Math.pow(0.5D, event.getPartialTick().getRealtimeDeltaTicks());
            shown = !frameChases || shown == null ? frame : new AABB(
                    Mth.lerp(step, shown.minX, frame.minX), Mth.lerp(step, shown.minY, frame.minY),
                    Mth.lerp(step, shown.minZ, frame.minZ), Mth.lerp(step, shown.maxX, frame.maxX),
                    Mth.lerp(step, shown.maxY, frame.maxY), Mth.lerp(step, shown.maxZ, frame.maxZ));
            Pose3dc framePose = Sable.HELPER.getContaining(minecraft.level, frameReference) instanceof ClientSubLevel client
                    && !client.isRemoved() ? client.renderPose(partial) : null;
            Color frameColor = new Color(frameTint, false);
            VertexConsumer lines = buffers.getBuffer(RenderType.debugQuads());
            double[] lo = {shown.minX, shown.minY, shown.minZ};
            double[] hi = {shown.maxX, shown.maxY, shown.maxZ};
            for (int along = 0; along < 3; along++) {
                int u = (along + 1) % 3;
                int v = (along + 2) % 3;
                for (double a : new double[]{lo[u], hi[u]}) {
                    for (double b : new double[]{lo[v], hi[v]}) {
                        double[] edge = new double[6];
                        edge[u] = edge[3 + u] = a;
                        edge[v] = edge[3 + v] = b;
                        edge[along] = lo[along];
                        edge[3 + along] = hi[along];
                        frameEdge(poses, lines, framePose, camera, frameColor, edge, along);
                    }
                }
            }
            buffers.endBatch(RenderType.debugQuads());
        }

        if (box == null) return;
        if (!(Sable.HELPER.getContaining(minecraft.level, anchor) instanceof ClientSubLevel owner)
                || owner.isRemoved()) return;
        Pose3dc pose = owner.renderPose(partial);
        double lift = Math.max(0.002D, 0.004D / Math.max(1.0E-6D, Math.abs(pose.scale().x())));
        Color color = new Color(tint, false);

        VertexConsumer glue = buffers.getBuffer(GLUE);
        for (int[] face : faces) {
            Direction direction = Direction.from3DDataValue(face[3]);
            glueQuad(poses, glue, pose, camera, color, direction,
                    face[0], face[1], face[2], face[0] + 1, face[1] + 1, face[2] + 1, lift, 1.0F);
        }
        buffers.endBatch(GLUE);

        VertexConsumer lines = buffers.getBuffer(RenderType.debugQuads());
        for (double[] dash : dashes) dash(poses, lines, pose, camera, color, dash);
        buffers.endBatch(RenderType.debugQuads());
    }

    private static void refresh(Level level, AABB box) {
        long time = level.getGameTime();
        if (box.equals(scanned) && time - scannedAt < 4L && time >= scannedAt) return;
        scanned = box;
        scannedAt = time;
        faces.clear();
        dashes.clear();
        gaps.clear();
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1);
        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (volume > SCAN_LIMIT) return;
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).isAir()) continue;
            for (Direction direction : Direction.values()) {
                next.setWithOffset(pos, direction);
                boolean inside = contains(next, min, max);
                BlockState neighbour = level.getBlockState(next);
                if (inside && !neighbour.isAir()) continue;
                if (!inside && !neighbour.isAir()) {
                    seam(level, pos, direction, min, max);
                    if (neighbour.isSolidRender(level, next)) continue;
                }
                faces.add(new int[]{pos.getX(), pos.getY(), pos.getZ(), direction.get3DDataValue()});
            }
        }
    }

    private static void seam(Level level, BlockPos pos, Direction face, BlockPos min, BlockPos max) {
        for (Direction side : Direction.values()) {
            if (side.getAxis() == face.getAxis()) continue;
            BlockPos beside = pos.relative(side);
            boolean edge = !contains(beside, min, max);
            if (!edge && cut(level, beside, face)) continue;
            double[] start = {pos.getX(), pos.getY(), pos.getZ()};
            double[] end = {pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1};
            for (Direction fixed : new Direction[]{face, side}) {
                int axis = fixed.getAxis().ordinal();
                double value = fixed.getAxisDirection() == Direction.AxisDirection.POSITIVE ? end[axis] : start[axis];
                start[axis] = value;
                end[axis] = value;
            }
            int along = 3 - face.getAxis().ordinal() - side.getAxis().ordinal();
            if (edge) {
                double[] gap = {start[0], start[1], start[2], start[0], start[1], start[2]};
                gap[3 + along] += 1.0D;
                gaps.add(gap);
            }
            for (double offset = 0.125D; offset < 1.0D; offset += 0.5D) {
                double[] dash = {start[0], start[1], start[2], start[0], start[1], start[2]};
                dash[along] += offset;
                dash[3 + along] += offset + 0.25D;
                dashes.add(dash);
            }
        }
    }

    private static boolean cut(Level level, BlockPos pos, Direction face) {
        return !level.getBlockState(pos).isAir() && !level.getBlockState(pos.relative(face)).isAir();
    }

    private static boolean contains(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static void glueQuad(PoseStack poses, VertexConsumer consumer, Pose3dc pose, Vec3 camera,
                                 Color color, Direction direction, double x0, double y0, double z0,
                                 double x1, double y1, double z1, double lift, float alpha) {
        int axis = direction.getAxis().ordinal();
        double[] lo = {x0, y0, z0};
        double[] hi = {x1, y1, z1};
        double plane = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? hi[axis] + lift : lo[axis] - lift;
        int u = (axis + 1) % 3;
        int v = (axis + 2) % 3;
        Vector3d normal = new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        pose.transformNormal(normal).normalize();
        double[][] corners = {{lo[u], lo[v]}, {lo[u], hi[v]}, {hi[u], hi[v]}, {hi[u], lo[v]}};
        for (double[] corner : corners) {
            double[] point = new double[3];
            point[axis] = plane;
            point[u] = corner[0];
            point[v] = corner[1];
            Vector3d world = new Vector3d(point[0], point[1], point[2]);
            pose.transformPosition(world);
            world.sub(camera.x, camera.y, camera.z);
            PoseStack.Pose last = poses.last();
            Vector3f n = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z)
                    .mul(last.normal()).normalize();
            consumer.addVertex(last.pose(), (float) world.x, (float) world.y, (float) world.z)
                    .setColor(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), alpha)
                    .setUv((float) (corner[0] - lo[u]), (float) (corner[1] - lo[v]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(0xF000F0)
                    .setNormal(n.x, n.y, n.z);
        }
    }

    private static void frameEdge(PoseStack poses, VertexConsumer consumer, Pose3dc pose, Vec3 camera,
                                  Color color, double[] edge, int along) {
        int u = (along + 1) % 3;
        int v = (along + 2) % 3;
        List<double[]> hidden = new ArrayList<>();
        for (double[] gap : gaps) {
            if (Math.abs(gap[u] - edge[u]) < EDGE_TOLERANCE && Math.abs(gap[v] - edge[v]) < EDGE_TOLERANCE) {
                hidden.add(new double[]{gap[along], gap[3 + along]});
            }
        }
        hidden.sort(Comparator.comparingDouble(range -> range[0]));
        double from = edge[along];
        for (double[] range : hidden) {
            if (range[0] > from + EDGE_TOLERANCE) framePiece(poses, consumer, pose, camera, color, edge, along, from, range[0]);
            from = Math.max(from, range[1]);
        }
        if (edge[3 + along] > from + EDGE_TOLERANCE) {
            framePiece(poses, consumer, pose, camera, color, edge, along, from, edge[3 + along]);
        }
    }

    private static void framePiece(PoseStack poses, VertexConsumer consumer, Pose3dc pose, Vec3 camera,
                                   Color color, double[] edge, int along, double from, double to) {
        double[] piece = edge.clone();
        piece[along] = from;
        piece[3 + along] = to;
        dash(poses, consumer, pose, camera, color, piece);
    }

    private static void dash(PoseStack poses, VertexConsumer consumer, Pose3dc pose, Vec3 camera,
                             Color color, double[] dash) {
        double half = FRAME_HALF_WIDTH;
        double[] lo = new double[3];
        double[] hi = new double[3];
        for (int i = 0; i < 3; i++) {
            lo[i] = Math.min(dash[i], dash[3 + i]) - half;
            hi[i] = Math.max(dash[i], dash[3 + i]) + half;
        }
        Matrix4f matrix = poses.last().pose();
        for (Direction direction : Direction.values()) {
            int axis = direction.getAxis().ordinal();
            int u = (axis + 1) % 3;
            int v = (axis + 2) % 3;
            double plane = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? hi[axis] : lo[axis];
            double[][] corners = {{lo[u], lo[v]}, {lo[u], hi[v]}, {hi[u], hi[v]}, {hi[u], lo[v]}};
            for (double[] corner : corners) {
                double[] point = new double[3];
                point[axis] = plane;
                point[u] = corner[0];
                point[v] = corner[1];
                Vector3d world = new Vector3d(point[0], point[1], point[2]);
                if (pose != null) pose.transformPosition(world);
                world.sub(camera.x, camera.y, camera.z);
                consumer.addVertex(matrix, (float) world.x, (float) world.y, (float) world.z)
                        .setColor(color.getRedAsFloat(), color.getGreenAsFloat(), color.getBlueAsFloat(), 1.0F);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    private static void clear() {
        selected = null;
        end = null;
        anchor = null;
        sublevel = null;
        hand = null;
        locked = false;
        frame = null;
        shown = null;
        scanned = null;
        faces.clear();
        dashes.clear();
        gaps.clear();
        java.util.Arrays.fill(offsets, 0);
    }

    private PocketKnifeCutPreview() {}
}
