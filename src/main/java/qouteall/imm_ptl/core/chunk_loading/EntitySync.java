package qouteall.imm_ptl.core.chunk_loading;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.server.world.ChunkLevelManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerChunkLoadingManager.class_3208;
import net.minecraft.server.MinecraftServer;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.ducks.IEChunkMap;
import qouteall.imm_ptl.core.ducks.IETrackedEntity;
import qouteall.imm_ptl.core.network.PacketRedirection;

public class EntitySync {
   public static void init() {
      DimensionAPI.SERVER_PRE_REMOVE_DIMENSION_EVENT.register(EntitySync::forceRemoveDimension);
   }

   public static void update(MinecraftServer server) {
      Profilers.get().push("ip_entity_tracking_update");

      for (ServerWorld world : server.getWorlds()) {
         PacketRedirection.withForceRedirect(world, () -> {
            ServerChunkLoadingManager chunkMap = world.method_14178().chunkLoadingManager;
            Int2ObjectMap<class_3208> entityTrackerMap = ((IEChunkMap)chunkMap).ip_getEntityTrackerMap();
            ChunkLevelManager distanceManager = chunkMap.getLevelManager();
            ObjectIterator var4 = entityTrackerMap.values().iterator();

            while (var4.hasNext()) {
               class_3208 trackedEntity = (class_3208)var4.next();
               IETrackedEntity ieTrackedEntity = (IETrackedEntity)trackedEntity;
               ieTrackedEntity.ip_updateEntityTrackingStatus();
            }
         });
      }

      Profilers.get().pop();
   }

   public static void tick(MinecraftServer server) {
      Profilers.get().push("ip_entity_tracking_tick");

      for (ServerWorld world : server.getWorlds()) {
         PacketRedirection.withForceRedirect(world, () -> {
            ServerChunkLoadingManager chunkMap = world.method_14178().chunkLoadingManager;
            Int2ObjectMap<class_3208> entityTrackerMap = ((IEChunkMap)chunkMap).ip_getEntityTrackerMap();
            ChunkLevelManager distanceManager = chunkMap.getLevelManager();
            ObjectIterator var4 = entityTrackerMap.values().iterator();

            while (var4.hasNext()) {
               class_3208 trackedEntity = (class_3208)var4.next();
               IETrackedEntity ieTrackedEntity = (IETrackedEntity)trackedEntity;
               long chunkPos = ieTrackedEntity.ip_getEntity().getChunkPos().toLong();
               if (distanceManager.shouldTickEntities(chunkPos)) {
                  ieTrackedEntity.ip_sendChanges();
               }
            }
         });
      }

      Profilers.get().pop();
   }

   private static void forceRemoveDimension(ServerWorld world) {
   }
}
