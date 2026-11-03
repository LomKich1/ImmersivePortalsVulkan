package qouteall.imm_ptl.core.chunk_loading;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickManager;

public enum PerformanceLevel {
   good,
   medium,
   bad;

   public static PerformanceLevel getClientPerformanceLevel(int averageFPS, int averageFreeMemoryMB) {
      if (averageFPS > 50 && averageFreeMemoryMB > 800) {
         return good;
      } else {
         return averageFPS > 30 && averageFreeMemoryMB > 300 ? medium : bad;
      }
   }

   public static PerformanceLevel getServerPerformanceLevel(MinecraftServer server) {
      ServerTickManager tickRateManager = server.getTickManager();
      long averageTickTimeNanos = server.getAverageNanosPerTick();
      long nanosecondsPerTick = tickRateManager.getNanosPerTick();
      if (averageTickTimeNanos < nanosecondsPerTick * 0.8) {
         return good;
      } else {
         return averageTickTimeNanos < nanosecondsPerTick ? medium : bad;
      }
   }

   public static int getVisiblePortalRangeChunks(PerformanceLevel level) {
      if (level == good) {
         return 8;
      } else {
         return level == medium ? 3 : 1;
      }
   }

   public static int getIndirectVisiblePortalRangeChunks(PerformanceLevel level) {
      if (level == good) {
         return 2;
      } else {
         return level == medium ? 1 : 0;
      }
   }

   public static int getIndirectLoadingRadiusCap(PerformanceLevel level) {
      if (level == good) {
         return 32;
      } else {
         return level == medium ? 7 : 2;
      }
   }

   public static int getPortalRenderingDistance(PerformanceLevel level, int originalDistance) {
      if (level == good) {
         return originalDistance;
      } else {
         return level == medium ? Math.max(2, originalDistance / 2) : 2;
      }
   }
}
