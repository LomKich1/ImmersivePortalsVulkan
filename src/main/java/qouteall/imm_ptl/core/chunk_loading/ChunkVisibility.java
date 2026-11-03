package qouteall.imm_ptl.core.chunk_loading;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.q_misc_util.my_util.LimitedLogger;

public class ChunkVisibility {
   private static final LimitedLogger limitedLogger = new LimitedLogger(10);
   private static final int portalLoadingRange = 48;
   public static final int secondaryPortalLoadingRange = 16;

   public static ChunkLoader playerDirectLoader(ServerPlayerEntity player) {
      return new ChunkLoader(new DimensionalChunkPos(player.getServerWorld().getRegistryKey(), player.getChunkPos()), McHelper.getPlayerLoadDistance(player));
   }

   private static int getDirectLoadingDistance(int renderDistance, double distanceToPortal) {
      if (distanceToPortal < 5.0) {
         return renderDistance;
      } else {
         return distanceToPortal < 15.0 ? renderDistance * 2 / 3 : renderDistance / 3;
      }
   }

   private static int getCappedLoadingDistance(Portal portal, ServerPlayerEntity player, int targetLoadingDistance) {
      PerformanceLevel performanceLevel = ImmPtlChunkTracking.getPlayerInfo(player).performanceLevel;
      int cap1 = PerformanceLevel.getIndirectLoadingRadiusCap(performanceLevel);
      int cap2 = IPGlobal.indirectLoadingRadiusCap;
      int cap3 = PerformanceLevel.getIndirectLoadingRadiusCap(ServerPerformanceMonitor.getLevel());
      int cap = Math.min(cap1, cap2);
      if (portal.getScale() > 2.0) {
         cap *= 2;
      }

      return Math.min(targetLoadingDistance, cap);
   }

   public static List<Portal> getNearbyPortals(ServerWorld world, Vec3d pos, Predicate<Portal> predicate, int radiusChunks, int radiusChunksForGlobalPortals) {
      List<Portal> result = McHelper.findEntitiesRough(Portal.class, world, pos, radiusChunks, predicate);

      for (Portal globalPortal : GlobalPortalStorage.getGlobalPortals(world)) {
         double distance = globalPortal.getDistanceToNearestPointInPortal(pos);
         if (distance < radiusChunksForGlobalPortals * 16) {
            result.add(globalPortal);
         }
      }

      if (result.size() > 100) {
         limitedLogger.err("too many portal nearby " + world + pos);
         Optional<Portal> nearest = result.stream().min(Comparator.comparingDouble(p -> p.getDistanceToNearestPointInPortal(pos)));
         return List.of(nearest.get());
      } else {
         return result;
      }
   }

   private static ChunkLoader getGeneralDirectPortalLoader(ServerPlayerEntity player, Portal portal) {
      if (portal.getIsGlobal()) {
         int renderDistance = Math.min(
            IPGlobal.indirectLoadingRadiusCap * 2,
            Math.max(2, McHelper.getPlayerLoadDistance(player) - Math.floorDiv((int)portal.getDistanceToNearestPointInPortal(player.getEntityPos()), 16))
         );
         return new ChunkLoader(
            new DimensionalChunkPos(portal.getDestDim(), new ChunkPos(BlockPos.ofFloored(portal.transformPoint(player.getEntityPos())))), renderDistance
         );
      } else {
         int loadDistance = McHelper.getPlayerLoadDistance(player);
         double distance = portal.getDistanceToNearestPointInPortal(player.getEntityPos());
         if (portal.getScaling() > 2.0 && distance < 5.0) {
            loadDistance = (int)(portal.getDestAreaRadiusEstimation() * 1.4 / 16.0);
         }

         return new ChunkLoader(
            new DimensionalChunkPos(portal.getDestDim(), new ChunkPos(BlockPos.ofFloored(portal.getDestPos()))),
            getCappedLoadingDistance(portal, player, getDirectLoadingDistance(loadDistance, distance))
         );
      }
   }

   private static ChunkLoader getGeneralPortalIndirectLoader(ServerPlayerEntity player, Vec3d transformedPos, Portal portal) {
      int loadDistance = McHelper.getPlayerLoadDistance(player);
      if (portal.getIsGlobal()) {
         int renderDistance = Math.min(IPGlobal.indirectLoadingRadiusCap, loadDistance / 3);
         return new ChunkLoader(new DimensionalChunkPos(portal.getDestDim(), new ChunkPos(BlockPos.ofFloored(transformedPos))), renderDistance);
      } else {
         return new ChunkLoader(
            new DimensionalChunkPos(portal.getDestDim(), new ChunkPos(BlockPos.ofFloored(portal.getDestPos()))),
            getCappedLoadingDistance(portal, player, loadDistance / 4)
         );
      }
   }

   public static void foreachBaseChunkLoaders(ServerPlayerEntity player, Consumer<ChunkLoader> func) {
      PerformanceLevel perfLevel = ImmPtlChunkTracking.getPlayerInfo(player).performanceLevel;
      int visiblePortalRangeChunks = PerformanceLevel.getVisiblePortalRangeChunks(perfLevel);
      int indirectVisiblePortalRangeChunks = PerformanceLevel.getIndirectVisiblePortalRangeChunks(perfLevel);
      ChunkLoader playerDirectLoader = playerDirectLoader(player);
      func.accept(playerDirectLoader);

      for (Portal portal : getNearbyPortals(player.getServerWorld(), player.getEntityPos(), portalx -> portalx.canBeSpectated(player), visiblePortalRangeChunks, 256)) {
         World destinationWorld = portal.getDestinationWorld();
         if (destinationWorld != null) {
            Vec3d transformedPlayerPos = portal.transformPoint(player.getEntityPos());
            func.accept(getGeneralDirectPortalLoader(player, portal));
            if (!isShrinkLoading()) {
               for (Portal innerPortal : getNearbyPortals(
                  (ServerWorld)destinationWorld, transformedPlayerPos, p -> p.canBeSpectated(player), indirectVisiblePortalRangeChunks, 32
               )) {
                  func.accept(getGeneralPortalIndirectLoader(player, transformedPlayerPos, innerPortal));
               }
            }
         }
      }
   }

   public static boolean isShrinkLoading() {
      return ServerPerformanceMonitor.getLevel() != PerformanceLevel.good;
   }
}
