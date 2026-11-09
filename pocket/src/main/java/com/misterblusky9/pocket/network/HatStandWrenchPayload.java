package com.misterblusky9.pocket.network;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.HatStandBlock;
import com.misterblusky9.pocket.block.HatStandBlockEntity;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.item.ToolPermissions;
import com.misterblusky9.pym.api.Hosted;
import com.simibubi.create.AllItems;
import dev.ryanhcode.sable.companion.math.Pose3d;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

public record HatStandWrenchPayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final int START = 0;
    public static final int TOGGLE = 1;
    public static final int STOP = 2;

    public static final Type<HatStandWrenchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "hat_stand_wrench")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, HatStandWrenchPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        BlockPos.STREAM_CODEC.encode(buf, packet.pos());
                        buf.writeByte(packet.action());
                    },
                    buf -> new HatStandWrenchPayload(BlockPos.STREAM_CODEC.decode(buf), buf.readByte())
            );

    @Override
    public Type<HatStandWrenchPayload> type() {
        return TYPE;
    }

    public void handle(ServerPlayer player) {
        HatStandBlockEntity turning = HatStandBlockEntity.turning(player);
        if (turning != null) turning.stopTurning();
        ServerLevel level = player.serverLevel();
        if (this.action == STOP || !level.isLoaded(this.pos) || !withinReach(player, level, this.pos)
                || !AllItems.WRENCH.isIn(player.getMainHandItem()) && !AllItems.WRENCH.isIn(player.getOffhandItem()))
            return;
        BlockState state = level.getBlockState(this.pos);
        if (state.getBlock() instanceof HatStandBlock block) {
            if (!(level.getBlockEntity(block.standPos(state, this.pos)) instanceof HatStandBlockEntity stand)
                    || !ToolPermissions.mayAlter(player, level, stand.getBlockPos())) return;
            if (this.action == TOGGLE && !stand.getActivator().isEmpty()) {
                stand.toggleHeadwear();
                IWrenchable.playRotateSound(level, this.pos);
            } else if (this.action == START && !stand.isActive()) {
                stand.startTurning(player);
            }
            return;
        }
        if (this.action == START && Sable.HELPER.getContaining(level, this.pos) instanceof ServerSubLevel hat
                && BuildSites.hostOf(hat) instanceof HatStandBlockEntity stand
                && ToolPermissions.mayAlter(player, level, stand.getBlockPos())) stand.startTurning(player);
    }

    private static boolean withinReach(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Vector3d at = new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        SubLevel containing = Sable.HELPER.getContaining(level, pos);
        Pose3d shown = Hosted.presented(containing);
        if (shown != null) at = shown.transformPosition(at);
        else if (containing != null) at = containing.logicalPose().transformPosition(at);
        double reach = player.blockInteractionRange() + 1.0D;
        return player.getEyePosition().distanceToSqr(at.x, at.y, at.z) <= reach * reach;
    }
}
