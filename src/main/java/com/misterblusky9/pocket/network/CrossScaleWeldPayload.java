package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWeldSync;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldGeometry;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.compat.simulated.WeldStore;
import com.misterblusky9.pocket.compat.simulated.WeldRuntime;
import com.misterblusky9.pocket.item.ModItems;
import com.misterblusky9.pocket.scale.SubLevelParentage;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

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
        int rotationTurns
) implements CustomPacketPayload {
    public static final Type<CrossScaleWeldPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "cross_scale_weld")
    );
    private static final double MAX_REACH_SQ = 64.0D;

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
                        buf.writeByte(packet.rotationTurns());
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
                            buf.readUnsignedByte())
            );

    @Override
    public Type<CrossScaleWeldPayload> type() {
        return TYPE;
    }

    public void handle(final ServerPlayer player) {
        if (player == null || this.smallPos == null || this.bigPos == null
                || this.smallFacing == null || this.bigFacing == null) return;
        if (!validHit(this.hitX) || !validHit(this.hitY) || !validHit(this.hitZ)) return;

        final ServerLevel level = player.serverLevel();
        final ItemStack glue = player.getItemInHand(this.hand);
        if (!glue.is(ModItems.GLUE_GUN.get())) return;

        if (!withinReach(player, this.smallPos) || !withinReach(player, this.bigPos)) return;

        final CrossScaleWelds.Weld weld = CrossScaleWelds.Weld.resolve(
                level,
                this.smallPos,
                this.smallFacing,
                null,
                this.bigPos,
                this.bigFacing,
                new Vec3(this.hitX, this.hitY, this.hitZ),
                mode());
        if (weld == null) return;

        final CrossScaleWelds.Refusal refusal = weld.check();
        if (!refusal.allowed()) {
            message(player, refusal);
            return;
        }

        if (!(weld.small() instanceof final ServerSubLevel small)) return;
        final ServerSubLevel big = weld.big() instanceof final ServerSubLevel subLevel ? subLevel : null;
        if (!weld.worldAnchored() && big == null) return;

        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) return;
        if (big != null && SubLevelParentage.areJoined(small, big)) {
            message(player, CrossScaleWelds.Refusal.ALREADY_CONNECTED);
            return;
        }
        if (!CrossScaleWelds.componentSettled(container, small.getUniqueId())
                || (big != null && !CrossScaleWelds.componentSettled(container, big.getUniqueId()))) {
            message(player, CrossScaleWelds.Refusal.SCALE_CHANGING);
            return;
        }

        final WeldRecord record = weld.toRecord(
                UUID.randomUUID(), CrossScaleWelds.weldOrientation(weld, this.rotationTurns));
        WeldStore.get(level).add(record);
        WeldRuntime.markNew(level, record.weldId());
        CrossScaleWeldSync.broadcast(level);

        player.awardStat(Stats.ITEM_USED.get(glue.getItem()));
    }

    private static boolean withinReach(final ServerPlayer player, final BlockPos pos) {
        final ServerLevel level = player.serverLevel();
        final Vec3 center = Vec3.atCenterOf(pos);
        final Vector3d world = new Vector3d(center.x, center.y, center.z);
        final SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel != null) subLevel.logicalPose().transformPosition(world);
        final Vec3 playerPos = player.position();
        final double dx = world.x - playerPos.x;
        final double dy = world.y - playerPos.y;
        final double dz = world.z - playerPos.z;
        final double distanceSquared = dx * dx + dy * dy + dz * dz;
        return Double.isFinite(distanceSquared) && distanceSquared <= MAX_REACH_SQ;
    }

    private WeldGeometry.SnapMode mode() {
        final WeldGeometry.SnapMode[] modes = WeldGeometry.SnapMode.values();
        return this.snapMode >= 0 && this.snapMode < modes.length
                ? modes[this.snapMode]
                : WeldGeometry.SnapMode.SMART;
    }

    private static boolean validHit(final double value) {
        return Double.isFinite(value) && value >= -0.01D && value <= 1.01D;
    }

    private static void message(final ServerPlayer player, final CrossScaleWelds.Refusal refusal) {
        if (refusal.message() == null) return;
        player.displayClientMessage(Component.literal(refusal.message()), true);
    }
}
