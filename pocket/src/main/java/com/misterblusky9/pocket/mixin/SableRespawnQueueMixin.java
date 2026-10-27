package com.misterblusky9.pocket.mixin;

import it.unimi.dsi.fastutil.Pair;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

@Mixin(value = ServerPlayer.class, priority = 900)
public interface SableRespawnQueueMixin {
    @Accessor("sable$queuedFreeze")
    void pocket$setQueuedFreeze(Pair<UUID, Vector3d> anchor);
}
