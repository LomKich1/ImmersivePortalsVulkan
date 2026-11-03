package qouteall.imm_ptl.core.portal.nether_portal;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.ChunkSection;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.MiscHelper;

public class FrameSearching {
   public static <T> void startSearchingPortalFrameAsync(
      FastBlockAccess region,
      int regionRadius,
      BlockPos centerPoint,
      Predicate<BlockState> framePredicate,
      FrameSearching.FrameSearchingFunc<T> matchShape,
      Consumer<T> onFound,
      Runnable onNotFound
   ) {
      CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
         try {
            T result = searchPortalFrame(region, regionRadius, centerPoint, framePredicate, matchShape);
            MiscHelper.getServer().execute(() -> {
               if (result != null) {
                  onFound.accept(result);
               } else {
                  onNotFound.run();
               }
            });
         } catch (Throwable var8) {
            var8.printStackTrace();
            onNotFound.run();
         }
      }, Util.getMainWorkerExecutor());
   }

   @Nullable
   public static <T> T searchPortalFrame(
      FastBlockAccess region, int regionRadius, BlockPos centerPoint, Predicate<BlockState> framePredicate, FrameSearching.FrameSearchingFunc<T> matchShape
   ) {
      List<ChunkPos> chunks = getChunksFromNearToFar(region, centerPoint, regionRadius);
      int minSectionY = region.minSectionY();
      int maxSectionYExclusive = region.maxSectionYExclusive();
      return searchPortalFrameWithYRange(
         region, framePredicate, matchShape, chunks, minSectionY, McHelper.getMinY(region.world()), McHelper.getMaxYExclusive(region.world())
      );
   }

   @Nullable
   private static <T> T searchPortalFrameWithYRange(
      FastBlockAccess fastBlockAccess,
      Predicate<BlockState> framePredicate,
      FrameSearching.FrameSearchingFunc<T> matchShape,
      List<ChunkPos> chunkPoses,
      int minSectionY,
      int yRangeStart,
      int yRangeEnd
   ) {
      for (ChunkPos chunkPos : chunkPoses) {
         for (int cy = fastBlockAccess.minSectionY(); cy < fastBlockAccess.maxSectionYExclusive(); cy++) {
            ChunkSection chunkSection = fastBlockAccess.getSection(chunkPos.x, cy, chunkPos.z);
            if (chunkSection != null && !chunkSection.isEmpty()) {
               int localYStart = Math.max(0, yRangeStart - cy * 16);
               int localYEnd = Math.min(16, yRangeEnd - cy * 16);

               for (int localY = localYStart; localY < localYEnd; localY++) {
                  for (int localZ = 0; localZ < 16; localZ++) {
                     for (int localX = 0; localX < 16; localX++) {
                        BlockState blockState = chunkSection.getBlockState(localX, localY, localZ);
                        if (framePredicate.test(blockState)) {
                           int worldX = localX + chunkPos.getStartX();
                           int worldY = localY + cy * 16;
                           int worldZ = localZ + chunkPos.getStartZ();
                           T result = matchShape.searchAt(fastBlockAccess, worldX, worldY, worldZ);
                           if (result != null) {
                              return result;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   private static List<ChunkPos> getChunksFromNearToFar(FastBlockAccess region, BlockPos centerPoint, int regionRadius) {
      return region.chunkPoses().sorted(Comparator.comparingDouble(chunk -> chunk.getStartPos().getSquaredDistance(centerPoint))).collect(Collectors.toList());
   }

   @FunctionalInterface
   public interface FrameSearchingFunc<T> {
      T searchAt(FastBlockAccess var1, int var2, int var3, int var4);
   }
}
