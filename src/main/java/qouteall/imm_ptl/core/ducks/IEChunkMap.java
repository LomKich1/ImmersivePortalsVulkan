package qouteall.imm_ptl.core.ducks;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ServerChunkLoadingManager.EntityTracker;
import org.jetbrains.annotations.Nullable;

public interface IEChunkMap {
   int ip_getPlayerViewDistance(ServerPlayerEntity var1);

   ServerWorld ip_getWorld();

   ServerLightingProvider ip_getLightingProvider();

   ChunkHolder ip_getChunkHolder(long var1);

   void ip_onPlayerUnload(ServerPlayerEntity var1);

   void ip_onDimensionRemove();

   void ip_resendSpawnPacketToTrackers(Entity var1);

   Int2ObjectMap<EntityTracker> ip_getEntityTrackerMap();

   @Nullable
   ChunkHolder ip_getUpdatingChunkIfPresent(long var1);
}
