package qouteall.imm_ptl.core.chunk_loading;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongPredicate;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkLevelManager;
import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.OptionalChunk;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEChunkMap;
import qouteall.imm_ptl.core.ducks.IEServerChunkCache;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.mixin.common.chunk_sync.IEDistanceManager;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.RateStat;

public class ImmPtlChunkTickets {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ChunkTicketType TICKET_TYPE = new ChunkTicketType(0L, 6);
   private static boolean enableDebugRateStat = false;
   private static final RateStat debugRateStat = new RateStat("imm_ptl_chunk_ticket");
   public static final WeakHashMap<ServerWorld, ImmPtlChunkTickets> BY_DIMENSION = new WeakHashMap<>();
   private final Long2ObjectOpenHashMap<ImmPtlChunkTickets.ChunkTicketInfo> chunkPosToTicketInfo = new Long2ObjectOpenHashMap();
   private final ArrayList<LongLinkedOpenHashSet> chunksToAddTicketByDistance = new ArrayList<>();
   private final LongOpenHashSet waitingForLoading = new LongOpenHashSet();
   private boolean isValid = true;
   public final int throttlingLimit = 4;

   public static void init() {
      DimensionAPI.SERVER_PRE_REMOVE_DIMENSION_EVENT.register(ImmPtlChunkTickets::onDimensionRemove);
      IPGlobal.SERVER_CLEANUP_EVENT.register(ImmPtlChunkTickets::cleanup);
   }

   private ImmPtlChunkTickets() {
   }

   public static ImmPtlChunkTickets get(ServerWorld world) {
      return BY_DIMENSION.computeIfAbsent(world, k -> new ImmPtlChunkTickets());
   }

   public void markForLoading(long chunkPos, int distanceToSource, int generation) {
      Validate.isTrue(distanceToSource >= 0);
      ImmPtlChunkTickets.ChunkTicketInfo info = (ImmPtlChunkTickets.ChunkTicketInfo)this.chunkPosToTicketInfo.get(chunkPos);
      if (info == null) {
         info = new ImmPtlChunkTickets.ChunkTicketInfo(generation, distanceToSource);
         this.chunkPosToTicketInfo.put(chunkPos, info);
         this.getQueueByDistance(distanceToSource).add(chunkPos);
      } else if (generation != info.lastUpdateGeneration) {
         info.lastUpdateGeneration = generation;
         int oldDistanceToSource = info.distanceToSource;
         info.distanceToSource = distanceToSource;
         if (this.getQueueByDistance(oldDistanceToSource).remove(chunkPos)) {
            this.getQueueByDistance(distanceToSource).add(chunkPos);
         }
      } else if (distanceToSource < info.distanceToSource) {
         int oldDistanceToSource = info.distanceToSource;
         info.distanceToSource = distanceToSource;
         if (this.getQueueByDistance(oldDistanceToSource).remove(chunkPos)) {
            this.getQueueByDistance(distanceToSource).add(chunkPos);
         }
      }
   }

   private LongLinkedOpenHashSet getQueueByDistance(int distanceToSource) {
      return Helper.arrayListComputeIfAbsent(this.chunksToAddTicketByDistance, distanceToSource, LongLinkedOpenHashSet::new);
   }

   public void tick(ServerWorld world) {
      this.flushThrottling(world);
   }

   public void flushThrottling(ServerWorld world) {
      if (Thread.currentThread() != ((IEWorld)world).portal_getThread()) {
         LOGGER.error("Called in a non-server-main (or server-world) thread.", new Throwable());
      } else {
         if (enableDebugRateStat) {
            debugRateStat.update();
         }

         if (!this.isValid) {
            LOGGER.error("flushing when invalid {}", world);
         } else if (world.getServer().isRunning()) {
            ChunkLevelManager distanceManager = getDistanceManager(world);
            Executor mainThreadExecutor = ((IEDistanceManager)distanceManager).ip_getMainThreadExecutor();
            this.waitingForLoading.removeIf(chunkPosx -> {
               ChunkHolder chunkHolder = getChunkHolder(world, chunkPosx);
               if (chunkHolder == null) {
                  return true;
               } else {
                  OptionalChunk<WorldChunk> resultNow = (OptionalChunk<WorldChunk>)chunkHolder.getEntityTickingFuture().getNow(null);
                  if (resultNow == null) {
                     return false;
                  } else {
                     if (!resultNow.isPresent()) {
                        LOGGER.error("Chunk loading failure {} {} {}", world, new ChunkPos(chunkPosx));
                     }

                     return true;
                  }
               }
            });

            for (LongLinkedOpenHashSet queue : this.chunksToAddTicketByDistance) {
               if (queue != null) {
                  while (!queue.isEmpty()) {
                     if (this.waitingForLoading.size() >= 4) {
                        return;
                     }

                     long chunkPos = queue.removeFirstLong();
                     if (this.chunkPosToTicketInfo.containsKey(chunkPos)) {
                        addTicket(world, chunkPos);
                        this.waitingForLoading.add(chunkPos);
                     } else {
                        LOGGER.warn("Chunk {} is not in the queue", new ChunkPos(chunkPos));
                     }
                  }
               }
            }
         }
      }
   }

   private static void addTicket(ServerWorld world, long chunkPos) {
      if (IPConfig.getConfig().enableImmPtlChunkLoading) {
         ChunkPos chunkPosObj = new ChunkPos(chunkPos);
         world.getChunkManager().addTicket(TICKET_TYPE, chunkPosObj, getLoadingRadius());
         if (enableDebugRateStat) {
            debugRateStat.hit();
         }
      }
   }

   public void purge(ServerWorld world, LongPredicate shouldKeepLoadingFunc) {
      this.chunkPosToTicketInfo.long2ObjectEntrySet().removeIf(e -> {
         long chunkPos = e.getLongKey();
         ImmPtlChunkTickets.ChunkTicketInfo ticketInfo = (ImmPtlChunkTickets.ChunkTicketInfo)e.getValue();
         boolean keepLoading = shouldKeepLoadingFunc.test(chunkPos);
         if (!keepLoading) {
            this.waitingForLoading.remove(chunkPos);
            boolean pendingTicketAdding = this.getQueueByDistance(ticketInfo.distanceToSource).remove(chunkPos);
            if (!pendingTicketAdding) {
               ChunkPos chunkPosObj = new ChunkPos(chunkPos);
               world.getChunkManager().removeTicket(TICKET_TYPE, chunkPosObj, getLoadingRadius());
            }

            return true;
         } else {
            return false;
         }
      });
   }

   public int getLoadedChunkNum() {
      return this.chunkPosToTicketInfo.size();
   }

   public static void onDimensionRemove(ServerWorld world) {
      ImmPtlChunkTickets dimTicketManager = BY_DIMENSION.remove(world);
      if (dimTicketManager != null) {
         removeAllTicketsInWorld(world, dimTicketManager);
      }
   }

   private static void removeAllTicketsInWorld(ServerWorld world, ImmPtlChunkTickets dimTicketManager) {
      ChunkTicketManager ticketStorage = ((IEDistanceManager)getDistanceManager(world)).ip_getTicketStorage();
      dimTicketManager.chunkPosToTicketInfo.keySet().forEach(pos -> {
         List<ChunkTicket> tickets = ((qouteall.imm_ptl.core.ducks.IEDistanceManager)getDistanceManager(world)).portal_getTicketSet(pos);

         for (ChunkTicket ticket : tickets.stream().filter(t -> t.getType() == TICKET_TYPE).toList()) {
            ticketStorage.removeTicket(pos, ticket);
         }
      });
      dimTicketManager.isValid = false;
   }

   public static int getLoadingRadius() {
      return IPGlobal.activeLoading ? 2 : 1;
   }

   public static ChunkHolder getChunkHolder(ServerWorld world, long chunkPos) {
      return ((IEChunkMap)world.getChunkManager().chunkLoadingManager).ip_getChunkHolder(chunkPos);
   }

   public static ChunkLevelManager getDistanceManager(ServerWorld world) {
      return ((IEServerChunkCache)world.getChunkManager()).ip_getDistanceManager();
   }

   private static void cleanup(MinecraftServer server) {
      for (ImmPtlChunkTickets immPtlChunkTickets : BY_DIMENSION.values()) {
         immPtlChunkTickets.isValid = false;
      }

      BY_DIMENSION.clear();
   }

   public static class ChunkTicketInfo {
      public int lastUpdateGeneration;
      public int distanceToSource;

      public ChunkTicketInfo(int lastUpdateGeneration, int distanceToSource) {
         this.lastUpdateGeneration = lastUpdateGeneration;
         this.distanceToSource = distanceToSource;
      }
   }
}
