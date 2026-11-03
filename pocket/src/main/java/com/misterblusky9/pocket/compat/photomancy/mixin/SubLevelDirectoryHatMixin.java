package com.misterblusky9.pocket.compat.photomancy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.build.BuildSites;
import com.misterblusky9.pocket.compat.photomancy.PhotomancyCompat;
import dev.rew1nd.sableschematicapi.sublevel.SubLevelDirectoryService;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SubLevelDirectoryService.class, remap = false)
public abstract class SubLevelDirectoryHatMixin {
    @WrapOperation(
            method = "addLoadedRecords",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;isRemoved()Z"))
    private static boolean pocket$skipLoadedHats(ServerSubLevel subLevel, Operation<Boolean> original) {
        return original.call(subLevel) || PhotomancyCompat.isHat(subLevel);
    }

    @WrapOperation(
            method = "addStoredRecordsFromChunk",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelStorage;attemptLoadSubLevel(Lnet/minecraft/world/level/ChunkPos;Ldev/ryanhcode/sable/sublevel/storage/holding/SavedSubLevelPointer;)Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;"))
    private static SubLevelData pocket$skipStoredHats(SubLevelStorage storage, ChunkPos chunk,
                                                      SavedSubLevelPointer pointer, Operation<SubLevelData> original) {
        SubLevelData data = original.call(storage, chunk, pointer);
        return data != null && BuildSites.serialized(data.fullTag().getCompound("user_data")) ? null : data;
    }
}
