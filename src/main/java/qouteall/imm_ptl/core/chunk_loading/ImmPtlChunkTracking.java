package qouteall.imm_ptl.core.chunk_loading;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEChunkMap;
import qouteall.imm_ptl.core.mixin.common.chunk_sync.IEServerCommonPacketListenerImpl;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.platform.Platform;

public class ImmPtlChunkTracking {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int updateInterval = 13;
   public static final int defaultDelayUnloadGenerations = 4;
   private static final Map<RegistryKey<World>, Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>>> chunkWatchRecords = new Object2ObjectOpenHashMap();
   private static final ArrayList<ChunkLoader> additionalChunkLoaders = new ArrayList<>();
   private static final Object2ObjectOpenHashMap<ServerPlayerEntity, PlayerChunkLoading> playerInfoMap = new Object2ObjectOpenHashMap();
   private static int generationCounter = 0;

   public static void init() {
      Platform.get().onServerTickEnd(ImmPtlChunkTracking::tick);
      IPGlobal.SERVER_CLEANUP_EVENT.register(ImmPtlChunkTracking::cleanup);
      DimensionAPI.SERVER_PRE_REMOVE_DIMENSION_EVENT.register(ImmPtlChunkTracking::onDimensionRemove);
   }

   public static void onChunkProvidedDeferred(WorldChunk chunk) {
   }

   public static void removePlayerFromChunkTrackersAndEntityTrackers(ServerPlayerEntity oldPlayer) {
      for (ServerWorld world : oldPlayer.server.getWorlds()) {
         ServerChunkManager chunkManager = world.getChunkManager();
         IEChunkMap storage = (IEChunkMap)(Object)chunkManager.chunkLoadingManager;
         storage.ip_onPlayerUnload(oldPlayer);
      }

      forceRemovePlayer(oldPlayer);
   }

   public static void onDimensionRemove(ServerWorld world) {
      ServerChunkManager chunkManager = world.getChunkManager();
      IEChunkMap storage = (IEChunkMap)(Object)chunkManager.chunkLoadingManager;
      storage.ip_onDimensionRemove();
      forceRemoveDimension(world);
   }

   private static Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>> getDimChunkWatchRecords(
      RegistryKey<World> dimension
   ) {
      return chunkWatchRecords.computeIfAbsent(dimension, k -> new Long2ObjectOpenHashMap());
   }

   public static PlayerChunkLoading getPlayerInfo(ServerPlayerEntity player) {
      return (PlayerChunkLoading)playerInfoMap.computeIfAbsent(
         player, p -> new PlayerChunkLoading(((IEServerCommonPacketListenerImpl)((ServerPlayerEntity)p).networkHandler).ip_getConnection().isLocal())
      );
   }

   public static void immediatelyUpdateForPlayer(ServerPlayerEntity player) {
      updateForPlayer(player);
      getPlayerInfo(player).doChunkSending(player);
      EntitySync.update(player.server);
   }

   public static void updateForPlayer(ServerPlayerEntity player) {
      PlayerChunkLoading playerInfo = getPlayerInfo(player);
      playerInfo.visibleDimensions.clear();
      int lastLoadedChunks = playerInfo.loadedChunks;
      playerInfo.loadedChunks = 0;
      ObjectOpenHashSet<ChunkLoader> chunkLoaders = new ObjectOpenHashSet();
      ChunkVisibility.foreachBaseChunkLoaders(player, chunkLoaders::add);
      chunkLoaders.addAll(playerInfo.additionalChunkLoaders);
      playerInfo.currentChunkLoaders.clear();
      playerInfo.currentChunkLoaders.addAll(chunkLoaders);
      MinecraftServer server = player.server;
      ObjectIterator var5 = chunkLoaders.iterator();

      while (var5.hasNext()) {
         ChunkLoader chunkLoader = (ChunkLoader)var5.next();
         RegistryKey<World> dimension = chunkLoader.dimension();
         Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>> chunkRecordMap = getDimChunkWatchRecords(
            dimension
         );
         ServerWorld world = server.getWorld(dimension);
         if (world == null) {
            LOGGER.warn("Dimension not loaded {} in chunk loader {}", dimension, chunkLoader);
            return;
         }

         playerInfo.visibleDimensions.add(dimension);
         ImmPtlChunkTickets ticketInfo = ImmPtlChunkTickets.get(world);
         chunkLoader.foreachChunkPos(
            (dim, x, z, distanceToSource) -> {
               long chunkPos = ChunkPos.toLong(x, z);
               Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord> records = (Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>)chunkRecordMap.computeIfAbsent(
                  chunkPos, k -> new Object2ObjectOpenHashMap()
               );
               ticketInfo.markForLoading(chunkPos, distanceToSource, generationCounter);
               records.compute(
                  player,
                  (k, record) -> {
                     boolean isBoundary = distanceToSource == chunkLoader.radius();
                     if (record == null) {
                        ImmPtlChunkTracking.PlayerWatchRecord newRecord = new ImmPtlChunkTracking.PlayerWatchRecord(
                           player, dimension, chunkPos, generationCounter, distanceToSource, false, isBoundary
                        );
                        playerInfo.markPendingLoading(newRecord);
                        playerInfo.loadedChunks++;
                        return newRecord;
                     } else {
                        int oldDistance = record.distanceToSource;
                        if (record.lastWatchGeneration == generationCounter) {
                           if (distanceToSource < oldDistance) {
                              record.distanceToSource = distanceToSource;
                              playerInfo.markPendingLoading(record);
                           }

                           record.isBoundary = record.isBoundary && isBoundary;
                        } else {
                           playerInfo.loadedChunks++;
                           if (distanceToSource < oldDistance) {
                              playerInfo.markPendingLoading(record);
                           }

                           record.distanceToSource = distanceToSource;
                           record.lastWatchGeneration = generationCounter;
                           record.isBoundary = isBoundary;
                        }

                        return record;
                     }
                  }
               );
            }
         );
      }
   }

   private static void purge(MinecraftServer server, Object2ObjectOpenHashMap<RegistryKey<World>, LongOpenHashSet> additionalLoadedChunks) {
      Set<ImmPtlChunkTracking.PlayerWatchRecord> releasedForMemory = IPGlobal.keepChunksLoadedForever && KeepChunksMemoryValve.shouldRelease(server)
         ? selectFarthestUncoveredRecords(256)
         : Collections.emptySet();
      chunkWatchRecords.forEach(
         (dimensionx, chunkRecords) -> chunkRecords.long2ObjectEntrySet()
            .removeIf(
               entry -> {
                  long chunkPosLong = entry.getLongKey();
                  Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord> dimChunkWatchRecords = (Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>)entry.getValue();
                  dimChunkWatchRecords.entrySet()
                     .removeIf(
                        e -> {
                           ServerPlayerEntity player = (ServerPlayerEntity)e.getKey();
                           if (player.isRemoved()) {
                              return true;
                           } else {
                              ImmPtlChunkTracking.PlayerWatchRecord record = (ImmPtlChunkTracking.PlayerWatchRecord)e.getValue();
                              boolean shouldRemove;
                              if (IPGlobal.keepChunksLoadedForever) {
                                 shouldRemove = releasedForMemory.contains(record);
                              } else {
                                 int delayUnloadGenerations = getDelayUnloadGenerationForPlayer(player);
                                 shouldRemove = generationCounter - record.lastWatchGeneration > delayUnloadGenerations;
                              }

                              if (shouldRemove) {
                                 if (record.isLoadedToPlayer) {
                                    player.networkHandler
                                       .sendPacket(
                                          PacketRedirection.createRedirectedMessage(
                                             player.getEntityWorld().getServer(), record.dimension, new UnloadChunkS2CPacket(new ChunkPos(record.chunkPos))
                                          )
                                       );
                                 }

                                 record.isValid = false;
                              }

                              return shouldRemove;
                           }
                        }
                     );
                  return dimChunkWatchRecords.isEmpty();
               }
            )
      );
      playerInfoMap.entrySet().removeIf(e -> ((ServerPlayerEntity)e.getKey()).isRemoved());

      for (ServerWorld world : server.getWorlds()) {
         RegistryKey<World> dimension = world.getRegistryKey();
         LongOpenHashSet additional = (LongOpenHashSet)additionalLoadedChunks.get(dimension);
         Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>> watchRecs = chunkWatchRecords.get(
            dimension
         );
         ImmPtlChunkTickets dimTicketManager = ImmPtlChunkTickets.get(world);
         dimTicketManager.purge(
            world, chunkPos -> watchRecs != null && watchRecs.containsKey(chunkPos) ? true : additional != null && additional.contains(chunkPos)
         );
      }
   }

   private static int getDelayUnloadGenerationForPlayer(ServerPlayerEntity player) {
      PlayerChunkLoading playerInfo = getPlayerInfo(player);
      if (playerInfo == null) {
         return 4;
      } else {
         int loadedChunks = playerInfo.loadedChunks;
         if (loadedChunks > 2000) {
            return 1;
         } else {
            return loadedChunks > 1200 ? 2 : 4;
         }
      }
   }

   private static Set<ImmPtlChunkTracking.PlayerWatchRecord> selectFarthestUncoveredRecords(int batch) {
      record Candidate(ImmPtlChunkTracking.PlayerWatchRecord record, int distance) {
      }

      List<Candidate> candidates = new ArrayList<>();
      chunkWatchRecords.forEach(
         (dimension, chunkRecords) -> {
            ObjectIterator var3x = chunkRecords.long2ObjectEntrySet().iterator();

            while (var3x.hasNext()) {
               Entry<Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>> entry = (Entry<Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>>)var3x.next();
               int x = ChunkPos.getPackedX(entry.getLongKey());
               int z = ChunkPos.getPackedZ(entry.getLongKey());
               ObjectIterator var7 = ((Object2ObjectOpenHashMap)entry.getValue()).values().iterator();

               while (var7.hasNext()) {
                  ImmPtlChunkTracking.PlayerWatchRecord record = (ImmPtlChunkTracking.PlayerWatchRecord)var7.next();
                  if (record.lastWatchGeneration != generationCounter && !record.player.isRemoved()) {
                     candidates.add(new Candidate(record, distanceBeyondLoaders(record.player, (RegistryKey<World>)dimension, x, z)));
                  }
               }
            }
         }
      );
      candidates.sort(Comparator.comparingInt(Candidate::distance).reversed());
      Set<ImmPtlChunkTracking.PlayerWatchRecord> picked = Collections.newSetFromMap(new IdentityHashMap<>());

      for (int i = 0; i < Math.min(batch, candidates.size()); i++) {
         picked.add(candidates.get(i).record());
      }

      KeepChunksMemoryValve.onReleased(picked.size());
      return picked;
   }

   private static int distanceBeyondLoaders(ServerPlayerEntity player, RegistryKey<World> dimension, int x, int z) {
      PlayerChunkLoading info = (PlayerChunkLoading)playerInfoMap.get(player);
      int nearest = Integer.MAX_VALUE;
      if (info != null) {
         for (ChunkLoader loader : info.currentChunkLoaders) {
            if (loader.dimension() == dimension) {
               int beyond = Math.max(Math.abs(x - loader.x()), Math.abs(z - loader.z())) - loader.radius();
               nearest = Math.min(nearest, Math.max(beyond, 0));
            }
         }
      }

      return nearest;
   }

   private static Object2ObjectOpenHashMap<RegistryKey<World>, LongOpenHashSet> refreshAdditionalChunkLoaders(MinecraftServer server) {
      Object2ObjectOpenHashMap<RegistryKey<World>, LongOpenHashSet> additionalLoadedChunks = new Object2ObjectOpenHashMap();
      additionalChunkLoaders.removeIf(chunkLoader -> {
         RegistryKey<World> dimension = chunkLoader.dimension();
         ServerWorld world = server.getWorld(dimension);
         if (world == null) {
            LOGGER.error("Missing dimension in chunk loader {}", dimension.getValue());
            return true;
         } else {
            final ImmPtlChunkTickets dimTicketManager = ImmPtlChunkTickets.get(world);
            final LongOpenHashSet set = (LongOpenHashSet)additionalLoadedChunks.computeIfAbsent(dimension, k -> new LongOpenHashSet());
            chunkLoader.foreachChunkPos(new ChunkLoader.ChunkPosConsumer() {
               @Override
               public void consume(RegistryKey<World> dimension, int x, int z, int distanceToSource) {
                  long chunkPos = ChunkPos.toLong(x, z);
                  dimTicketManager.markForLoading(chunkPos, distanceToSource, ImmPtlChunkTracking.generationCounter);
                  set.add(chunkPos);
               }
            });
            return false;
         }
      });
      return additionalLoadedChunks;
   }

   private static void tick(MinecraftServer server) {
      Profilers.get().push("portal_chunk_tracking");
      boolean updates = false;
      long gameTime = server.getOverworld().getTime();

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         PlayerChunkLoading playerInfo = getPlayerInfo(player);
         if (playerInfo.shouldUpdateImmediately || player.getId() % 13 == gameTime % 13L) {
            playerInfo.shouldUpdateImmediately = false;
            updateForPlayer(player);
            updates = true;
         }
      }

      if (gameTime % 13L == 0L) {
         Object2ObjectOpenHashMap<RegistryKey<World>, LongOpenHashSet> additionalLoadedChunks = refreshAdditionalChunkLoaders(server);
         purge(server, additionalLoadedChunks);
         generationCounter++;
         updates = true;
      }

      for (ServerWorld world : server.getWorlds()) {
         ImmPtlChunkTickets dimTicketManager = ImmPtlChunkTickets.get(world);
         dimTicketManager.tick(world);
      }

      Profilers.get().pop();
      if (updates) {
         EntitySync.update(server);
      }

      EntitySync.tick(server);
   }

   public static boolean isPlayerWatchingChunk(
      ServerPlayerEntity player, RegistryKey<World> dimension, int x, int z, Predicate<ImmPtlChunkTracking.PlayerWatchRecord> predicate
   ) {
      long chunkPos = ChunkPos.toLong(x, z);
      Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord> recordMap = (Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>)getDimChunkWatchRecords(
            dimension
         )
         .get(chunkPos);
      if (recordMap == null) {
         return false;
      } else {
         ImmPtlChunkTracking.PlayerWatchRecord record = (ImmPtlChunkTracking.PlayerWatchRecord)recordMap.get(player);
         if (record == null) {
            return false;
         } else {
            return !record.isLoadedToPlayer ? false : predicate.test(record);
         }
      }
   }

   public static boolean isPlayerWatchingChunk(ServerPlayerEntity player, RegistryKey<World> dimension, int x, int z) {
      return isPlayerWatchingChunk(player, dimension, x, z, r -> true);
   }

   public static boolean isPlayerWatchingChunkWithinRadius(ServerPlayerEntity player, RegistryKey<World> dimension, int x, int z, int radiusBlocks) {
      return isPlayerWatchingChunk(player, dimension, x, z, r -> r.distanceToSource * 16 <= radiusBlocks);
   }

   public static boolean isChunkPendingToPlayer(ServerPlayerEntity player, RegistryKey<World> dimension, long chunkPos) {
      Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>> dimRecords = chunkWatchRecords.get(dimension);
      if (dimRecords == null) {
         return false;
      } else {
         Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord> records = (Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>)dimRecords.get(
            chunkPos
         );
         if (records == null) {
            return false;
         } else {
            ImmPtlChunkTracking.PlayerWatchRecord record = (ImmPtlChunkTracking.PlayerWatchRecord)records.get(player);
            return record != null && record.isValid && !record.isLoadedToPlayer;
         }
      }
   }

   private static void cleanup(MinecraftServer server) {
      chunkWatchRecords.clear();
      additionalChunkLoaders.clear();
      playerInfoMap.clear();
   }

   public static Stream<ServerPlayerEntity> getPlayersViewingChunk(RegistryKey<World> dimension, int x, int z) {
      Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord> records = getWatchRecordForChunk(dimension, x, z);
      return records == null ? Stream.empty() : records.values().stream().filter(e -> e.isLoadedToPlayer).map(e -> e.player);
   }

   public static List<ServerPlayerEntity> getPlayersViewingChunk(RegistryKey<World> dimension, int x, int z, boolean boundaryOnly) {
      Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord> recs = getWatchRecordForChunk(dimension, x, z);
      if (recs == null) {
         return Collections.emptyList();
      } else {
         ArrayList<ServerPlayerEntity> result = new ArrayList<>();
         ObjectIterator var6 = recs.values().iterator();

         while (var6.hasNext()) {
            ImmPtlChunkTracking.PlayerWatchRecord rec = (ImmPtlChunkTracking.PlayerWatchRecord)var6.next();
            if (rec.isLoadedToPlayer && (!boundaryOnly || rec.isBoundary)) {
               result.add(rec.player);
            }
         }

         return result;
      }
   }

   @Nullable
   public static Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord> getWatchRecordForChunk(
      RegistryKey<World> dimension, int x, int z
   ) {
      return (Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>)getDimChunkWatchRecords(dimension).get(ChunkPos.toLong(x, z));
   }

   public static void forceRemovePlayer(ServerPlayerEntity oldPlayer) {
      playerInfoMap.remove(oldPlayer);
      chunkWatchRecords.forEach(
         (dim, dimMap) -> dimMap.long2ObjectEntrySet()
            .removeIf(
               e -> {
                  long chunkPos = e.getLongKey();
                  Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord> records = (Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>)e.getValue();
                  ImmPtlChunkTracking.PlayerWatchRecord rec = (ImmPtlChunkTracking.PlayerWatchRecord)records.remove(oldPlayer);
                  if (rec != null) {
                     PacketRedirection.sendRedirectedMessage(oldPlayer, (RegistryKey<World>)dim, new UnloadChunkS2CPacket(new ChunkPos(chunkPos)));
                  }

                  return records.isEmpty();
               }
            )
      );
   }

   public static void forceRemoveDimension(ServerWorld world) {
      RegistryKey<World> dim = world.getRegistryKey();
      MinecraftServer server = world.getServer();
      Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>> map = chunkWatchRecords.get(dim);
      if (map != null) {
         map.forEach(
            (chunkPos, records) -> {
               Packet<ClientPlayPacketListener> unloadPacket = PacketRedirection.createRedirectedMessage(
                  server, dim, new UnloadChunkS2CPacket(new ChunkPos(chunkPos))
               );
               ObjectIterator var6 = records.values().iterator();

               while (var6.hasNext()) {
                  ImmPtlChunkTracking.PlayerWatchRecord record = (ImmPtlChunkTracking.PlayerWatchRecord)var6.next();
                  if (record.isValid && record.isLoadedToPlayer) {
                     record.player.networkHandler.sendPacket(unloadPacket);
                  }

                  record.isValid = false;
               }
            }
         );
         chunkWatchRecords.remove(dim);
         additionalChunkLoaders.removeIf(chunkLoader -> chunkLoader.dimension() == dim);
         ObjectIterator var4 = playerInfoMap.values().iterator();

         while (var4.hasNext()) {
            PlayerChunkLoading playerInfo = (PlayerChunkLoading)var4.next();
            playerInfo.additionalChunkLoaders.removeIf(l -> l.dimension() == dim);
         }
      }
   }

   public static boolean shouldLoadDimension(RegistryKey<World> dimension) {
      if (!chunkWatchRecords.containsKey(dimension)) {
         return false;
      } else {
         Long2ObjectOpenHashMap<Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>> map = chunkWatchRecords.get(dimension);
         return !map.isEmpty();
      }
   }

   public static void addGlobalAdditionalChunkLoader(MinecraftServer server, ChunkLoader chunkLoader) {
      additionalChunkLoaders.add(chunkLoader);
      RegistryKey<World> dimension = chunkLoader.dimension();
      ServerWorld world = server.getWorld(dimension);
      if (world == null) {
         LOGGER.error("Missing dimension in chunk loader {}", dimension.getValue());
      } else {
         ImmPtlChunkTickets dimTicketManager = ImmPtlChunkTickets.get(world);
         chunkLoader.foreachChunkPos(
            (dim, x, z, distanceToSource) -> dimTicketManager.markForLoading(ChunkPos.toLong(x, z), distanceToSource, generationCounter)
         );
      }
   }

   public static void removeGlobalAdditionalChunkLoader(MinecraftServer server, ChunkLoader chunkLoader) {
      additionalChunkLoaders.removeIf(c -> c == chunkLoader);
   }

   public static int getLoadedChunkNum(RegistryKey<World> dimension) {
      return getDimChunkWatchRecords(dimension).size();
   }

   public static void addPerPlayerAdditionalChunkLoader(ServerPlayerEntity player, ChunkLoader chunkLoader) {
      PlayerChunkLoading playerInfo = getPlayerInfo(player);
      playerInfo.additionalChunkLoaders.add(chunkLoader);
      playerInfo.shouldUpdateImmediately = true;
   }

   public static void removePerPlayerAdditionalChunkLoader(ServerPlayerEntity player, ChunkLoader chunkLoader) {
      ArrayList<ChunkLoader> chunkLoaderList = getPlayerInfo(player).additionalChunkLoaders;
      chunkLoaderList.removeIf(c -> c == chunkLoader);
   }

   public static Set<RegistryKey<World>> getVisibleDimensions(ServerPlayerEntity player) {
      return getPlayerInfo(player).visibleDimensions;
   }

   public static void syncBlockUpdateToClientImmediately(ServerWorld world, IntBox box) {
      ChunkPos lowPos = new ChunkPos(box.l);
      ChunkPos highPos = new ChunkPos(box.h);
      Set<ServerPlayerEntity> playersViewingRegion = new HashSet<>();
      RegistryKey<World> dimension = world.getRegistryKey();

      for (int x = lowPos.x; x <= highPos.x; x++) {
         for (int z = lowPos.z; z <= highPos.z; z++) {
            Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord> rec = getWatchRecordForChunk(dimension, x, z);
            if (rec != null) {
               ObjectIterator chunkPosLong = rec.values().iterator();

               while (chunkPosLong.hasNext()) {
                  ImmPtlChunkTracking.PlayerWatchRecord r = (ImmPtlChunkTracking.PlayerWatchRecord)chunkPosLong.next();
                  if (r.isValid && !r.isLoadedToPlayer) {
                     playersViewingRegion.add(r.player);
                  }
               }
            }
         }
      }

      for (ServerPlayerEntity player : playersViewingRegion) {
         getPlayerInfo(player).doChunkSending(player);
      }

      IEChunkMap chunkMap = (IEChunkMap)(Object)world.getChunkManager().chunkLoadingManager;

      for (int x = lowPos.x; x <= highPos.x; x++) {
         for (int zx = lowPos.z; zx <= highPos.z; zx++) {
            long chunkPosLong = ChunkPos.toLong(x, zx);
            ChunkHolder chunkHolder = chunkMap.ip_getChunkHolder(chunkPosLong);
            if (chunkHolder != null) {
               WorldChunk tickingChunk = chunkHolder.getWorldChunk();
               if (tickingChunk != null) {
                  chunkHolder.flushUpdates(tickingChunk);
               }
            }
         }
      }
   }

   public static class PlayerWatchRecord {
      public final ServerPlayerEntity player;
      public final RegistryKey<World> dimension;
      public final long chunkPos;
      public int lastWatchGeneration;
      public int distanceToSource;
      public boolean isLoadedToPlayer;
      public boolean isValid = true;
      public boolean isBoundary = false;

      public PlayerWatchRecord(
         ServerPlayerEntity player,
         RegistryKey<World> dimension,
         long chunkPos,
         int lastWatchGeneration,
         int distanceToSource,
         boolean isLoadedToPlayer,
         boolean isBoundary
      ) {
         this.player = player;
         this.dimension = dimension;
         this.chunkPos = chunkPos;
         this.lastWatchGeneration = lastWatchGeneration;
         this.distanceToSource = distanceToSource;
         this.isLoadedToPlayer = isLoadedToPlayer;
         this.isBoundary = isBoundary;
      }

      @Override
      public String toString() {
         return String.format(
            "%s (%d,%d) distance:%d valid:%s loaded:%s",
            this.dimension.getValue(),
            ChunkPos.getPackedX(this.chunkPos),
            ChunkPos.getPackedZ(this.chunkPos),
            this.distanceToSource,
            this.isValid,
            this.isLoadedToPlayer
         );
      }
   }

   public static class RemoteCallables {
      public static void acceptClientPerformanceInfo(ServerPlayerEntity player, PerformanceLevel performanceLevel) {
         PlayerChunkLoading playerInfo = ImmPtlChunkTracking.getPlayerInfo(player);
         playerInfo.performanceLevel = performanceLevel;
      }
   }
}
