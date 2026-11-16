package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWeldSync;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldGeometry;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.compat.simulated.WeldStore;
import com.misterblusky9.pocket.compat.simulated.WeldRuntime;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pym.api.Hosted;
import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Set;
import java.util.UUID;

public record CrossScaleWeldPayload(
        BlockPos smallPos,
        BlockPos bigPos,
        Direction smallFacing,
        Direction bigFacing,
        InteractionHand hand,
        double hitX,
        double hitY,
        double hitZ,
        int snapMode,
        int rotationDegrees
) implements CustomPacketPayload {
    public static final Type<CrossScaleWeldPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "cross_scale_weld")
    );
    private static final double MIN_REACH = 8.0D;
    private static final double REACH_SLACK = 1.0D;
    private static final double FACE_HALF_DIAGONAL = Math.sqrt(0.5D);

    public static final StreamCodec<RegistryFriendlyByteBuf, CrossScaleWeldPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        BlockPos.STREAM_CODEC.encode(buf, packet.smallPos());
                        BlockPos.STREAM_CODEC.encode(buf, packet.bigPos());
                        Direction.STREAM_CODEC.encode(buf, packet.smallFacing());
                        Direction.STREAM_CODEC.encode(buf, packet.bigFacing());
                        buf.writeByte(packet.hand() == InteractionHand.MAIN_HAND ? 0 : 1);
                        buf.writeDouble(packet.hitX());
                        buf.writeDouble(packet.hitY());
                        buf.writeDouble(packet.hitZ());
                        buf.writeByte(packet.snapMode());
                        buf.writeShort(packet.rotationDegrees());
                    },
                    buf -> new CrossScaleWeldPayload(
                            BlockPos.STREAM_CODEC.decode(buf),
                            BlockPos.STREAM_CODEC.decode(buf),
                            Direction.STREAM_CODEC.decode(buf),
                            Direction.STREAM_CODEC.decode(buf),
                            buf.readUnsignedByte() == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readUnsignedByte(),
                            buf.readUnsignedShort())
            );

    @Override
    public Type<CrossScaleWeldPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        if (player == null || this.smallPos == null || this.bigPos == null
                || this.smallFacing == null || this.bigFacing == null) return;
        if (!validHit(this.hitX) || !validHit(this.hitY) || !validHit(this.hitZ)) return;
        if (this.rotationDegrees >= 360) return;

        ServerLevel level = player.serverLevel();
        ItemStack glue = player.getItemInHand(this.hand);
        if (!glue.is(ModItems.GLUE_GUN.get())) return;

        Vector3d smallFace = WeldGeometry.faceCentre(this.smallPos, this.smallFacing);
        Vector3d bigFace = new Vector3d(
                this.bigPos.getX() + this.hitX, this.bigPos.getY() + this.hitY, this.bigPos.getZ() + this.hitZ);
        if (!withinReach(player, this.smallPos, smallFace, FACE_HALF_DIAGONAL)
                || !withinReach(player, this.bigPos, bigFace, 0.0D)) {
            message(player, CrossScaleWelds.Refusal.OUT_OF_RANGE);
            return;
        }

        CrossScaleWelds.Weld weld = CrossScaleWelds.Weld.resolve(
                level,
                this.smallPos,
                this.smallFacing,
                null,
                this.bigPos,
                this.bigFacing,
                new Vec3(this.hitX, this.hitY, this.hitZ),
                mode());
        if (weld == null) {
            message(player, CrossScaleWelds.Refusal.CANNOT_WELD);
            return;
        }

        CrossScaleWelds.Refusal refusal = weld.check();
        if (!refusal.allowed()) {
            message(player, refusal);
            return;
        }

        if (!(weld.small() instanceof ServerSubLevel small)) return;
        ServerSubLevel big = weld.big() instanceof ServerSubLevel subLevel ? subLevel : null;
        if (!weld.worldAnchored() && big == null) return;

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) return;
        if (big != null && Pym.connections().areJoined(small, big)) {
            message(player, CrossScaleWelds.Refusal.ALREADY_CONNECTED);
            return;
        }
        boolean mounting = weld.mounting();
        if (!CrossScaleWelds.componentSettled(container, small.getUniqueId())
                || (big != null && !mounting && !CrossScaleWelds.componentSettled(container, big.getUniqueId()))) {
            message(player, CrossScaleWelds.Refusal.SCALE_CHANGING);
            return;
        }
        if (mounting && CrossScaleWelds.snapshot(container).worldAnchored(small.getUniqueId())) {
            player.displayClientMessage(Component.translatable("pocket.weld.welded_to_world"), true);
            return;
        }
        Set<UUID> riders = mounting ? CrossScaleWelds.componentIds(container, small.getUniqueId()) : Set.of();

        WeldRecord record = weld.toRecord(
                UUID.randomUUID(), CrossScaleWelds.weldOrientation(weld, this.rotationDegrees));
        WeldStore.get(level).add(record);
        WeldRuntime.markNew(level, record.weldId());
        if (mounting) BuildSites.mount(level, big, riders, record);
        CrossScaleWeldSync.broadcast(level);

        glue.hurtAndBreak(1, player, LivingEntity.getSlotForHand(this.hand));
        player.awardStat(Stats.ITEM_USED.get(glue.getItem()));
    }

    private static boolean withinReach(ServerPlayer player, BlockPos pos, Vector3d point, double faceSlack) {
        ServerLevel level = player.serverLevel();
        Vector3d world = new Vector3d(point);
        double scale = 1.0D;
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel != null) {
            var shown = Hosted.presented(subLevel);
            (shown != null ? shown : subLevel.logicalPose()).transformPosition(world);
            double own = Pym.scale().of(subLevel);
            if (Double.isFinite(own) && own > 0.0D) scale = own;
        }
        Vec3 eye = player.getEyePosition();
        double distance = world.distance(eye.x, eye.y, eye.z);
        double reach = Math.max(MIN_REACH, player.blockInteractionRange() + REACH_SLACK)
                + faceSlack * scale;
        return Double.isFinite(distance) && distance <= reach;
    }

    private WeldGeometry.SnapMode mode() {
        WeldGeometry.SnapMode[] modes = WeldGeometry.SnapMode.values();
        return this.snapMode >= 0 && this.snapMode < modes.length
                ? modes[this.snapMode]
                : WeldGeometry.SnapMode.SMART;
    }

    private static boolean validHit(double value) {
        return Double.isFinite(value) && value >= -0.01D && value <= 1.01D;
    }

    private static void message(ServerPlayer player, CrossScaleWelds.Refusal refusal) {
        Component message = refusal.component();
        if (message != null) player.displayClientMessage(message, true);
    }
}
